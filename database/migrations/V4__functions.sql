-- V4__functions.sql
-- Every read/write path a member is allowed to reach directly. Two trust tiers:
--
--   SECURITY INVOKER (default) — the caller's own RLS applies. Used for every
--   function that reads or writes content: message history, search, RAG
--   retrieval, sending/editing/deleting a message, self-joining a public
--   channel. These functions add NOTHING a plain parameterized query couldn't
--   do; RLS is still the only thing standing between an actor and someone
--   else's channel (docs/SECURITY_MODEL.md §5, §6).
--
--   SECURITY DEFINER — a short, fully-enumerated, justified exception list
--   (D-08). Each one exists because rw_app structurally cannot do the
--   operation any other way: breaking RLS self-recursion, reaching a table
--   rw_app has no grant on (credentials, refresh tokens), or performing a
--   DELETE (rw_app never holds a DELETE grant on anything, D-07). Every
--   SECURITY DEFINER function below either takes no caller-supplied actor
--   parameter, or re-implements its own authorization check before touching
--   data — it never simply trusts a parameter as identity.
SET ROLE rw_migrator;

-- ======================================================== actor propagation --

CREATE FUNCTION rw.rw_fn_current_user_id() RETURNS uuid
LANGUAGE sql STABLE
AS $$
  SELECT NULLIF(current_setting('app.current_user_id', true), '')::uuid
$$;
COMMENT ON FUNCTION rw.rw_fn_current_user_id() IS
  'Reads the transaction-scoped actor set by RlsTransactionRunner via set_config(...,is_local=>true). NULL when unset, which makes every RLS predicate referencing it evaluate to NOT TRUE — deny by default (SEC-13).';

-- =============================================== RLS recursion breakers -----
-- Both SECURITY DEFINER. Neither takes a "user to check" parameter — only the
-- CURRENT actor's membership can ever be asked about, so the elevated
-- privilege cannot be turned into an oracle for someone else's membership.

CREATE FUNCTION rw.rw_fn_is_channel_member(p_channel_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  SELECT EXISTS (
    SELECT 1 FROM rw.rw_channel_members m
    WHERE m.rw_channel_id = p_channel_id
      AND m.rw_user_id = rw.rw_fn_current_user_id()
  )
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_is_channel_member(uuid) FROM PUBLIC;
COMMENT ON FUNCTION rw.rw_fn_is_channel_member(uuid) IS
  'SECURITY DEFINER to break the RLS self-recursion between rw_messages and rw_channel_members (D-08, R1). No user parameter: can only answer for the current actor.';

CREATE FUNCTION rw.rw_fn_is_channel_owner_or_moderator(p_channel_id uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM rw.rw_channel_members m
    JOIN rw.rw_channel_member_roles r ON r.rw_channel_member_role_id = m.rw_channel_member_role_id
    WHERE m.rw_channel_id = p_channel_id
      AND m.rw_user_id = rw.rw_fn_current_user_id()
      AND r.rw_channel_member_role_code IN ('OWNER', 'MODERATOR')
  )
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_is_channel_owner_or_moderator(uuid) FROM PUBLIC;
COMMENT ON FUNCTION rw.rw_fn_is_channel_owner_or_moderator(uuid) IS
  'Same recursion-breaking role as rw_fn_is_channel_member, for the narrower OWNER/MODERATOR check used by channel management policies and functions.';

-- ================================================================ OBJ-04 ----
-- Channel message history, keyset paginated. SECURITY INVOKER: the SELECT
-- below is filtered by rw_messages' own RLS policy for whichever role calls
-- it. A non-member gets zero rows regardless of p_channel_id (SEC-01, SEC-03).
CREATE FUNCTION rw.rw_fn_message_history(
  p_channel_id uuid,
  p_before_created_at timestamptz DEFAULT NULL,
  p_before_message_id bigint DEFAULT NULL,
  p_limit integer DEFAULT 50
) RETURNS TABLE (
  rw_message_id bigint,
  rw_channel_id uuid,
  rw_sender_id uuid,
  rw_parent_message_id bigint,
  rw_body text,
  rw_client_message_id uuid,
  rw_is_deleted boolean,
  rw_revision integer,
  rw_created_at timestamptz,
  rw_edited_at timestamptz
)
LANGUAGE sql STABLE
AS $$
  SELECT m.rw_message_id, m.rw_channel_id, m.rw_sender_id, m.rw_parent_message_id,
         m.rw_body, m.rw_client_message_id, m.rw_is_deleted, m.rw_revision,
         m.rw_created_at, m.rw_edited_at
  FROM rw.rw_messages m
  WHERE m.rw_channel_id = p_channel_id
    AND (
      p_before_created_at IS NULL
      OR (m.rw_created_at, m.rw_message_id) < (p_before_created_at, p_before_message_id)
    )
  ORDER BY m.rw_created_at DESC, m.rw_message_id DESC
  LIMIT LEAST(GREATEST(p_limit, 1), 100)
$$;
COMMENT ON FUNCTION rw.rw_fn_message_history IS
  'Keyset pagination via row-value comparison on (created_at, message_id) — no OFFSET anywhere (MSG-04). Deleted messages are included with rw_body NULL (BR-06) so the client can render a tombstone.';

-- ================================================================ OBJ-05 ----
-- Full-text search with highlight, keyset paginated by rank. SECURITY INVOKER:
-- the tsquery match runs against the RLS-filtered rw_messages relation, so an
-- unauthorized row is never a candidate, never scored, never returned
-- (SEC-04, SRCH-02) — there is no "search everywhere then filter" step to
-- forget.
CREATE FUNCTION rw.rw_fn_search_messages(
  p_query text,
  p_channel_id uuid DEFAULT NULL,
  p_before_rank real DEFAULT NULL,
  p_before_message_id bigint DEFAULT NULL,
  p_limit integer DEFAULT 50
) RETURNS TABLE (
  rw_message_id bigint,
  rw_channel_id uuid,
  rw_sender_id uuid,
  rw_created_at timestamptz,
  rw_highlight text,
  rw_rank real
)
LANGUAGE sql STABLE
AS $$
  WITH q AS (
    SELECT websearch_to_tsquery('spanish', p_query) AS tsq
  )
  SELECT m.rw_message_id, m.rw_channel_id, m.rw_sender_id, m.rw_created_at,
         ts_headline('spanish', m.rw_body, q.tsq,
           'StartSel=<mark>, StopSel=</mark>, MaxFragments=2, MaxWords=20, MinWords=5') AS rw_highlight,
         ts_rank(m.rw_search_vector, q.tsq) AS rw_rank
  FROM rw.rw_messages m, q
  WHERE NOT m.rw_is_deleted
    AND m.rw_search_vector @@ q.tsq
    AND (p_channel_id IS NULL OR m.rw_channel_id = p_channel_id)
    AND (
      p_before_rank IS NULL
      OR (ts_rank(m.rw_search_vector, q.tsq), m.rw_message_id) < (p_before_rank, p_before_message_id)
    )
  ORDER BY rw_rank DESC, m.rw_message_id DESC
  LIMIT LEAST(GREATEST(p_limit, 1), 100)
$$;
COMMENT ON FUNCTION rw.rw_fn_search_messages IS
  'p_query is bound as a value into websearch_to_tsquery — never concatenated into SQL text (MSG-03). p_channel_id only ever narrows; passing NULL still leaves every unauthorized row excluded by RLS on rw.rw_messages.';

-- ================================================================ OBJ-06 ----
-- Permission-aware AI context retrieval. SECURITY INVOKER + an explicit
-- membership EXISTS as a second, independent layer on top of RLS (D-08 §6.2):
-- two mechanisms must both fail for a leak. The ANN operator appears exactly
-- once in this codebase, and only inside this FROM clause (AI-07, AI-08).
CREATE FUNCTION rw.rw_fn_rag_retrieve_candidates(
  p_query_embedding vector(1536),
  p_channel_id uuid DEFAULT NULL,
  p_limit integer DEFAULT 12
) RETURNS TABLE (
  rw_message_id bigint,
  rw_channel_id uuid,
  rw_sender_id uuid,
  rw_body text,
  rw_created_at timestamptz,
  rw_similarity real
)
LANGUAGE sql STABLE
AS $$
  SELECT m.rw_message_id, m.rw_channel_id, m.rw_sender_id, m.rw_body, m.rw_created_at,
         (1 - (e.rw_embedding <=> p_query_embedding))::real AS rw_similarity
  FROM rw.rw_messages m
  JOIN rw.rw_message_embeddings e ON e.rw_message_id = m.rw_message_id
  WHERE NOT m.rw_is_deleted
    AND (p_channel_id IS NULL OR m.rw_channel_id = p_channel_id)
    -- Redundant with RLS by design (defence in depth, D-08 §6.2): even if a
    -- future migration weakened the rw_messages SELECT policy, this EXISTS
    -- still confines candidates to the caller's own channels.
    AND EXISTS (
      SELECT 1 FROM rw.rw_channel_members cm
      WHERE cm.rw_channel_id = m.rw_channel_id
        AND cm.rw_user_id = rw.rw_fn_current_user_id()
    )
  ORDER BY e.rw_embedding <=> p_query_embedding
  LIMIT LEAST(GREATEST(p_limit, 1), 50)
$$;
COMMENT ON FUNCTION rw.rw_fn_rag_retrieve_candidates IS
  'The forbidden shape (global ANN then application filtering) is structurally impossible here: this is the ONLY place <=> appears, and its FROM clause is already RLS-constrained plus an explicit membership EXISTS (AI-07, AI-08).';

-- ================================================================ OBJ-07 ----
-- Accumulated copilot consumption for the CURRENT actor only — no user-id
-- parameter, so there is nothing to spoof. RLS on rw_copilot_usage restricts
-- the underlying SELECT to the actor's own rows regardless.
CREATE FUNCTION rw.rw_fn_copilot_usage_summary() RETURNS jsonb
LANGUAGE sql STABLE
AS $$
  SELECT jsonb_build_object(
    'userId', rw.rw_fn_current_user_id(),
    'totals', (
      SELECT jsonb_build_object(
        'requests', count(*),
        'promptTokens', coalesce(sum(rw_prompt_tokens), 0),
        'completionTokens', coalesce(sum(rw_completion_tokens), 0),
        'totalTokens', coalesce(sum(rw_total_tokens), 0)
      )
      FROM rw.rw_copilot_usage
    ),
    'byOperation', (
      SELECT coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb) FROM (
        SELECT rw_operation AS operation, count(*) AS requests, coalesce(sum(rw_total_tokens), 0) AS "totalTokens"
        FROM rw.rw_copilot_usage GROUP BY rw_operation ORDER BY rw_operation
      ) t
    ),
    'byModel', (
      SELECT coalesce(jsonb_agg(row_to_json(t)), '[]'::jsonb) FROM (
        SELECT p.rw_provider_code AS provider, u.rw_model_code AS model, u.rw_operation AS operation,
               count(*) AS requests, coalesce(sum(u.rw_total_tokens), 0) AS "totalTokens"
        FROM rw.rw_copilot_usage u
        JOIN rw.rw_ai_providers p ON p.rw_ai_provider_id = u.rw_ai_provider_id
        GROUP BY p.rw_provider_code, u.rw_model_code, u.rw_operation
        ORDER BY p.rw_provider_code, u.rw_model_code
      ) t
    ),
    'firstUsedAt', (SELECT min(rw_created_at) FROM rw.rw_copilot_usage),
    'lastUsedAt', (SELECT max(rw_created_at) FROM rw.rw_copilot_usage)
  )
$$;

-- ============================================================= messaging ----

-- Idempotent send (IBR-04): sender is ALWAYS the current actor, never a
-- parameter — nothing here could be tricked into sending as someone else.
-- SECURITY INVOKER: the INSERT is still governed by rw_messages' own
-- WITH CHECK policy (BR-02, BR-09).
CREATE FUNCTION rw.rw_fn_send_message(
  p_channel_id uuid,
  p_body text,
  p_client_message_id uuid,
  p_parent_message_id bigint DEFAULT NULL
) RETURNS TABLE (rw_message_id bigint, rw_created_at timestamptz, rw_was_created boolean)
LANGUAGE plpgsql
AS $$
DECLARE
  v_sender uuid := rw.rw_fn_current_user_id();
  v_id bigint;
  v_created_at timestamptz;
BEGIN
  IF v_sender IS NULL THEN
    RAISE EXCEPTION 'not authenticated' USING ERRCODE = '28000';
  END IF;

  INSERT INTO rw.rw_messages (rw_channel_id, rw_sender_id, rw_body, rw_client_message_id, rw_parent_message_id)
  VALUES (p_channel_id, v_sender, p_body, p_client_message_id, p_parent_message_id)
  ON CONFLICT (rw_channel_id, rw_sender_id, rw_client_message_id) DO NOTHING
  RETURNING rw.rw_messages.rw_message_id, rw.rw_messages.rw_created_at INTO v_id, v_created_at;

  IF v_id IS NOT NULL THEN
    RETURN QUERY SELECT v_id, v_created_at, true;
    RETURN;
  END IF;

  -- Conflict: the same client_message_id was already used by this sender in
  -- this channel (a retried optimistic send or a replayed realtime event).
  -- Return the existing row instead of erroring (IBR-04) — RLS still applies
  -- to this SELECT, so it only succeeds if the row genuinely belongs to the
  -- caller's own send.
  RETURN QUERY
    SELECT m.rw_message_id, m.rw_created_at, false
    FROM rw.rw_messages m
    WHERE m.rw_channel_id = p_channel_id
      AND m.rw_sender_id = v_sender
      AND m.rw_client_message_id = p_client_message_id;
END;
$$;

CREATE FUNCTION rw.rw_fn_edit_message(p_message_id bigint, p_new_body text) RETURNS TABLE (rw_revision integer, rw_edited_at timestamptz)
LANGUAGE plpgsql
AS $$
DECLARE
  v_revision integer;
  v_edited_at timestamptz;
BEGIN
  UPDATE rw.rw_messages
     SET rw_body = p_new_body
   WHERE rw_message_id = p_message_id
     AND rw_sender_id = rw.rw_fn_current_user_id()
     AND NOT rw_is_deleted
  RETURNING rw.rw_messages.rw_revision, rw.rw_messages.rw_edited_at INTO v_revision, v_edited_at;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'message % not found, not owned by the caller, or already deleted', p_message_id USING ERRCODE = 'P0002';
  END IF;

  RETURN QUERY SELECT v_revision, v_edited_at;
END;
$$;
COMMENT ON FUNCTION rw.rw_fn_edit_message IS
  'History write, revision bump and edited_at are all handled by the BEFORE UPDATE trigger (V7) — this function only performs the authorized UPDATE (MSG-02).';

CREATE FUNCTION rw.rw_fn_soft_delete_message(p_message_id bigint) RETURNS boolean
LANGUAGE plpgsql
AS $$
DECLARE
  v_actor uuid := rw.rw_fn_current_user_id();
  v_found integer;
BEGIN
  UPDATE rw.rw_messages
     SET rw_is_deleted = true,
         rw_deleted_at = now(),
         rw_deleted_by = v_actor,
         rw_body = NULL
   WHERE rw_message_id = p_message_id
     AND rw_sender_id = v_actor
     AND NOT rw_is_deleted;
  GET DIAGNOSTICS v_found = ROW_COUNT;
  RETURN v_found > 0;
END;
$$;
COMMENT ON FUNCTION rw.rw_fn_soft_delete_message IS
  'MSG-01: the row is never removed. rw_body is cleared here (not merely masked downstream) so BR-06 holds even for a raw SELECT (see docs/DATA_MODEL.md §3.10 note).';

-- ================================================================= channels --

CREATE FUNCTION rw.rw_fn_join_channel(p_channel_id uuid) RETURNS boolean
LANGUAGE plpgsql
AS $$
DECLARE
  v_found integer;
BEGIN
  INSERT INTO rw.rw_channel_members (rw_channel_id, rw_user_id, rw_channel_member_role_id)
  SELECT p_channel_id, rw.rw_fn_current_user_id(), r.rw_channel_member_role_id
  FROM rw.rw_channel_member_roles r WHERE r.rw_channel_member_role_code = 'MEMBER'
  ON CONFLICT (rw_channel_id, rw_user_id) DO NOTHING;
  GET DIAGNOSTICS v_found = ROW_COUNT;
  RETURN v_found > 0;
END;
$$;
COMMENT ON FUNCTION rw.rw_fn_join_channel IS
  'SECURITY INVOKER: relies entirely on the rw_channel_members INSERT WITH CHECK policy, which only allows self-join to a non-archived PUBLIC channel (docs/SECURITY_MODEL.md §4). No elevated privilege needed or granted.';

-- The three functions below are SECURITY DEFINER because rw_app holds no
-- DELETE grant on ANY table (D-07) — leaving and removing a member both
-- require a DELETE, so there is no RLS-only path for them. Each
-- re-implements its own authorization check before mutating, since
-- SECURITY DEFINER bypasses RLS for its own duration.

CREATE FUNCTION rw.rw_fn_leave_channel(p_channel_id uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_actor uuid := rw.rw_fn_current_user_id();
  v_found integer;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'not authenticated' USING ERRCODE = '28000';
  END IF;

  DELETE FROM rw.rw_channel_members
   WHERE rw_channel_id = p_channel_id AND rw_user_id = v_actor;
  GET DIAGNOSTICS v_found = ROW_COUNT;
  RETURN v_found > 0;
  -- BR-10 (last-owner guard) and BR-07 (DIRECT channels are immutable once
  -- established) are both enforced by triggers on rw_channel_members that
  -- fire regardless of the executing role (V7) — this function does not need
  -- to duplicate that logic, only the "is this the caller's own row" check.
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_leave_channel(uuid) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_remove_channel_member(p_channel_id uuid, p_target_user_id uuid) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_found integer;
BEGIN
  IF NOT rw.rw_fn_is_channel_owner_or_moderator(p_channel_id) THEN
    RAISE EXCEPTION 'only an owner or moderator may remove a member' USING ERRCODE = '42501';
  END IF;

  DELETE FROM rw.rw_channel_members
   WHERE rw_channel_id = p_channel_id AND rw_user_id = p_target_user_id;
  GET DIAGNOSTICS v_found = ROW_COUNT;
  RETURN v_found > 0;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_remove_channel_member(uuid, uuid) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_update_channel_member(
  p_channel_id uuid,
  p_target_user_id uuid DEFAULT NULL,   -- NULL means "the caller's own row"
  p_new_role_code text DEFAULT NULL,
  p_last_read_message_id bigint DEFAULT NULL,
  p_is_muted boolean DEFAULT NULL
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_actor uuid := rw.rw_fn_current_user_id();
  v_target uuid := coalesce(p_target_user_id, v_actor);
  v_new_role_id smallint;
  v_found integer;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'not authenticated' USING ERRCODE = '28000';
  END IF;

  IF v_target = v_actor THEN
    -- Self-service path: only the read cursor and mute flag, never a role change.
    IF p_new_role_code IS NOT NULL THEN
      RAISE EXCEPTION 'cannot change your own channel role' USING ERRCODE = '42501';
    END IF;
    UPDATE rw.rw_channel_members
       SET rw_last_read_message_id = coalesce(p_last_read_message_id, rw_last_read_message_id),
           rw_is_muted = coalesce(p_is_muted, rw_is_muted)
     WHERE rw_channel_id = p_channel_id AND rw_user_id = v_actor;
  ELSE
    -- Owner/moderator path: only a role change on someone else, never their
    -- personal read cursor or mute preference.
    IF p_last_read_message_id IS NOT NULL OR p_is_muted IS NOT NULL THEN
      RAISE EXCEPTION 'cannot set another member''s read cursor or mute preference' USING ERRCODE = '42501';
    END IF;
    IF NOT rw.rw_fn_is_channel_owner_or_moderator(p_channel_id) THEN
      RAISE EXCEPTION 'only an owner or moderator may change a member role' USING ERRCODE = '42501';
    END IF;
    SELECT rw_channel_member_role_id INTO v_new_role_id
    FROM rw.rw_channel_member_roles WHERE rw_channel_member_role_code = p_new_role_code;
    IF v_new_role_id IS NULL THEN
      RAISE EXCEPTION 'unknown channel member role %', p_new_role_code USING ERRCODE = '22023';
    END IF;
    UPDATE rw.rw_channel_members
       SET rw_channel_member_role_id = v_new_role_id
     WHERE rw_channel_id = p_channel_id AND rw_user_id = v_target;
  END IF;

  GET DIAGNOSTICS v_found = ROW_COUNT;
  RETURN v_found > 0;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_update_channel_member(uuid, uuid, text, bigint, boolean) FROM PUBLIC;
COMMENT ON FUNCTION rw.rw_fn_update_channel_member IS
  'Single UPDATE entry point for rw_channel_members. Enforces two disjoint column sets by branch: self may set only (read cursor, mute); an owner/moderator acting on someone else may set only (role). This is what lets rw_app hold no direct UPDATE grant on the table.';

CREATE FUNCTION rw.rw_fn_create_channel(
  p_channel_type_code text,
  p_name text,
  p_slug text,
  p_topic text DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_actor uuid := rw.rw_fn_current_user_id();
  v_type_id smallint;
  v_owner_role_id smallint;
  v_channel_id uuid;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'not authenticated' USING ERRCODE = '28000';
  END IF;
  IF p_channel_type_code NOT IN ('PUBLIC', 'PRIVATE') THEN
    RAISE EXCEPTION 'rw_fn_create_channel only creates PUBLIC or PRIVATE channels; use rw_fn_open_direct_channel for DIRECT' USING ERRCODE = '22023';
  END IF;

  SELECT rw_channel_type_id INTO v_type_id FROM rw.rw_channel_types WHERE rw_channel_type_code = p_channel_type_code;
  SELECT rw_channel_member_role_id INTO v_owner_role_id FROM rw.rw_channel_member_roles WHERE rw_channel_member_role_code = 'OWNER';

  INSERT INTO rw.rw_channels (rw_channel_type_id, rw_name, rw_slug, rw_topic, rw_created_by)
  VALUES (v_type_id, p_name, p_slug, p_topic, v_actor)
  RETURNING rw_channel_id INTO v_channel_id;

  -- Bootstrap problem: the creator cannot yet satisfy the ordinary
  -- self-join-to-PUBLIC INSERT policy for PRIVATE channels (and even for
  -- PUBLIC, we want them seated as OWNER, not MEMBER, atomically with
  -- creation). Running as rw_migrator (SECURITY DEFINER owner, BYPASSRLS)
  -- for this one INSERT is what makes "creator becomes OWNER in the same
  -- transaction" (docs/API.md §4) possible without a second round trip.
  INSERT INTO rw.rw_channel_members (rw_channel_id, rw_user_id, rw_channel_member_role_id)
  VALUES (v_channel_id, v_actor, v_owner_role_id);

  RETURN v_channel_id;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_create_channel(text, text, text, text) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_open_direct_channel(p_counterpart_user_id uuid) RETURNS TABLE (rw_channel_id uuid, rw_was_created boolean)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_actor uuid := rw.rw_fn_current_user_id();
  v_low uuid; v_high uuid;
  v_existing uuid;
  v_direct_type_id smallint;
  v_owner_role_id smallint;
  v_new_channel uuid;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'not authenticated' USING ERRCODE = '28000';
  END IF;
  IF p_counterpart_user_id = v_actor THEN
    RAISE EXCEPTION 'cannot open a direct channel with yourself' USING ERRCODE = '22023';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM rw.rw_users WHERE rw_user_id = p_counterpart_user_id AND rw_is_active) THEN
    RAISE EXCEPTION 'counterpart user % not found' , p_counterpart_user_id USING ERRCODE = 'P0002';
  END IF;

  v_low := LEAST(v_actor, p_counterpart_user_id);
  v_high := GREATEST(v_actor, p_counterpart_user_id);

  -- BR-08: at most one DIRECT channel per unordered pair. This lookup is
  -- what makes opening the "same" DM idempotent instead of a duplicate.
  SELECT p.rw_channel_id INTO v_existing
  FROM rw.rw_direct_channel_pairs p
  WHERE p.rw_user_id_low = v_low AND p.rw_user_id_high = v_high;

  IF v_existing IS NOT NULL THEN
    RETURN QUERY SELECT v_existing, false;
    RETURN;
  END IF;

  SELECT rw_channel_type_id INTO v_direct_type_id FROM rw.rw_channel_types WHERE rw_channel_type_code = 'DIRECT';
  SELECT rw_channel_member_role_id INTO v_owner_role_id FROM rw.rw_channel_member_roles WHERE rw_channel_member_role_code = 'OWNER';

  -- Qualified rw.rw_channels.rw_channel_id: the function's own RETURNS TABLE
  -- declares an OUT parameter also named rw_channel_id, which PL/pgSQL would
  -- otherwise treat as ambiguous against the table column of the same name.
  INSERT INTO rw.rw_channels (rw_channel_type_id, rw_created_by) VALUES (v_direct_type_id, v_actor)
  RETURNING rw.rw_channels.rw_channel_id INTO v_new_channel;

  INSERT INTO rw.rw_direct_channel_pairs (rw_channel_id, rw_user_id_low, rw_user_id_high)
  VALUES (v_new_channel, v_low, v_high);

  -- Exactly two membership rows, exactly these two participants — this
  -- function is the ONLY place able to reach the "insert to a DIRECT
  -- channel" path (the guard trigger in V7 blocks a third insert or any
  -- later mutation), so BR-07 holds by construction, not by caller trust.
  INSERT INTO rw.rw_channel_members (rw_channel_id, rw_user_id, rw_channel_member_role_id)
  VALUES (v_new_channel, v_actor, v_owner_role_id), (v_new_channel, p_counterpart_user_id, v_owner_role_id);

  RETURN QUERY SELECT v_new_channel, true;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_open_direct_channel(uuid) FROM PUBLIC;

-- ============================================================== authentication --
-- Argon2id itself is verified in the application (Postgres has no Argon2
-- support); these functions carry the hash across a boundary rw_app cannot
-- otherwise cross (rw_user_credentials has zero grants, D-16) and record the
-- outcome. They are SECURITY DEFINER by structural necessity, not choice.

CREATE FUNCTION rw.rw_fn_get_credential_for_login(p_email text) RETURNS TABLE (
  rw_user_id uuid, rw_password_hash text, rw_failed_attempts integer, rw_locked_until timestamptz, rw_is_active boolean
)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  SELECT u.rw_user_id, c.rw_password_hash, c.rw_failed_attempts, c.rw_locked_until, u.rw_is_active
  FROM rw.rw_users u
  JOIN rw.rw_user_credentials c ON c.rw_user_id = u.rw_user_id
  WHERE lower(u.rw_email) = lower(p_email)
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_get_credential_for_login(text) FROM PUBLIC;
COMMENT ON FUNCTION rw.rw_fn_get_credential_for_login IS
  'Returns the hash to the application layer ONLY — this function is never exposed by any endpoint, only called from the internal authentication code path, which compares it with Argon2id and never returns it further.';

CREATE FUNCTION rw.rw_fn_record_login_attempt(p_user_id uuid, p_success boolean) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
BEGIN
  IF p_success THEN
    UPDATE rw.rw_user_credentials SET rw_failed_attempts = 0, rw_locked_until = NULL WHERE rw_user_id = p_user_id;
    UPDATE rw.rw_users SET rw_last_seen_at = now() WHERE rw_user_id = p_user_id;
  ELSE
    UPDATE rw.rw_user_credentials
       SET rw_failed_attempts = rw_failed_attempts + 1,
           rw_locked_until = CASE WHEN rw_failed_attempts + 1 >= 10 THEN now() + interval '15 minutes' ELSE rw_locked_until END
     WHERE rw_user_id = p_user_id;
  END IF;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_record_login_attempt(uuid, boolean) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_issue_refresh_token(
  p_user_id uuid, p_family_id uuid, p_token_hash bytea, p_expires_at timestamptz,
  p_user_agent text DEFAULT NULL, p_ip inet DEFAULT NULL
) RETURNS uuid
LANGUAGE sql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  INSERT INTO rw.rw_refresh_tokens (rw_user_id, rw_family_id, rw_token_hash, rw_expires_at, rw_user_agent, rw_ip_address)
  VALUES (p_user_id, p_family_id, p_token_hash, p_expires_at, p_user_agent, p_ip)
  RETURNING rw_refresh_token_id
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_issue_refresh_token(uuid, uuid, bytea, timestamptz, text, inet) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_rotate_refresh_token(
  p_presented_token_hash bytea, p_new_token_hash bytea, p_new_expires_at timestamptz,
  p_user_agent text DEFAULT NULL, p_ip inet DEFAULT NULL
) RETURNS TABLE (
  rw_status text, rw_user_id uuid, rw_family_id uuid, rw_new_refresh_token_id uuid, rw_expires_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_row rw.rw_refresh_tokens%ROWTYPE;
  v_new_id uuid;
BEGIN
  SELECT * INTO v_row FROM rw.rw_refresh_tokens WHERE rw_token_hash = p_presented_token_hash;

  IF NOT FOUND THEN
    RETURN QUERY SELECT 'NOT_FOUND', NULL::uuid, NULL::uuid, NULL::uuid, NULL::timestamptz;
    RETURN;
  END IF;

  IF v_row.rw_revoked_at IS NOT NULL THEN
    -- AUTH-06: replaying an already-rotated (or already-revoked) token means
    -- the token was stolen. Burn the whole family, not just this token.
    UPDATE rw.rw_refresh_tokens
       SET rw_revoked_at = now(), rw_revoked_reason = 'REUSE_DETECTED'
     WHERE rw_family_id = v_row.rw_family_id AND rw_revoked_at IS NULL;
    RETURN QUERY SELECT 'REUSE_DETECTED', v_row.rw_user_id, v_row.rw_family_id, NULL::uuid, NULL::timestamptz;
    RETURN;
  END IF;

  IF v_row.rw_expires_at <= now() THEN
    UPDATE rw.rw_refresh_tokens SET rw_revoked_at = now(), rw_revoked_reason = 'EXPIRED' WHERE rw_refresh_token_id = v_row.rw_refresh_token_id;
    RETURN QUERY SELECT 'EXPIRED', v_row.rw_user_id, v_row.rw_family_id, NULL::uuid, NULL::timestamptz;
    RETURN;
  END IF;

  INSERT INTO rw.rw_refresh_tokens (rw_user_id, rw_family_id, rw_token_hash, rw_expires_at, rw_user_agent, rw_ip_address)
  VALUES (v_row.rw_user_id, v_row.rw_family_id, p_new_token_hash, p_new_expires_at, p_user_agent, p_ip)
  RETURNING rw_refresh_token_id INTO v_new_id;

  UPDATE rw.rw_refresh_tokens
     SET rw_revoked_at = now(), rw_revoked_reason = 'ROTATED', rw_replaced_by_token_id = v_new_id
   WHERE rw_refresh_token_id = v_row.rw_refresh_token_id;

  RETURN QUERY SELECT 'ROTATED', v_row.rw_user_id, v_row.rw_family_id, v_new_id, p_new_expires_at;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_rotate_refresh_token(bytea, bytea, timestamptz, text, inet) FROM PUBLIC;
COMMENT ON FUNCTION rw.rw_fn_rotate_refresh_token IS
  'AUTH-05/AUTH-06 in one transaction: the presented token is looked up by its hash (never trusted by user id), and PU-1 (at most one live token per family) makes "two concurrently-live successors" unrepresentable even under a race.';

CREATE FUNCTION rw.rw_fn_revoke_family(p_family_id uuid, p_reason text) RETURNS void
LANGUAGE sql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  UPDATE rw.rw_refresh_tokens
     SET rw_revoked_at = now(), rw_revoked_reason = p_reason
   WHERE rw_family_id = p_family_id AND rw_revoked_at IS NULL
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_revoke_family(uuid, text) FROM PUBLIC;

-- ==================================================== embedding worker ------
-- The embedding worker is a background system process, not an end-user
-- request: it has no authenticated actor (app.current_user_id is unset), so
-- the ordinary membership-gated RLS on rw_messages would deny it every row.
-- These three functions are SECURITY DEFINER for that reason alone — they
-- are never exposed by any REST endpoint and never return message content to
-- an HTTP client. The confidentiality boundary these functions sit upstream
-- of is enforced downstream instead, at RETRIEVAL time, by
-- rw_fn_rag_retrieve_candidates' RLS + EXISTS check (AI-07). Computing and
-- storing a vector is not itself a disclosure: rw_message_embeddings keeps
-- its own RLS SELECT policy (SEC-05), so only an actual channel member can
-- ever read back what the worker wrote.

CREATE FUNCTION rw.rw_fn_claim_pending_embedding_jobs(p_batch_size integer DEFAULT 20)
RETURNS TABLE (rw_embedding_job_id bigint, rw_message_id bigint, rw_body text, rw_revision integer)
LANGUAGE sql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  SELECT j.rw_embedding_job_id, j.rw_message_id, m.rw_body, m.rw_revision
  FROM rw.rw_embedding_jobs j
  JOIN rw.rw_messages m ON m.rw_message_id = j.rw_message_id
  WHERE j.rw_processed_at IS NULL AND NOT m.rw_is_deleted
  ORDER BY j.rw_enqueued_at
  LIMIT LEAST(GREATEST(p_batch_size, 1), 100)
  FOR UPDATE OF j SKIP LOCKED
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_claim_pending_embedding_jobs(integer) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_store_message_embedding(
  p_embedding_job_id bigint, p_message_id bigint, p_embedding_model_id smallint,
  p_embedding vector(1536), p_source_revision integer, p_content_hash bytea
) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
BEGIN
  INSERT INTO rw.rw_message_embeddings (rw_message_id, rw_embedding_model_id, rw_embedding, rw_source_revision, rw_content_hash)
  VALUES (p_message_id, p_embedding_model_id, p_embedding, p_source_revision, p_content_hash)
  ON CONFLICT (rw_message_id) DO UPDATE SET
    rw_embedding_model_id = EXCLUDED.rw_embedding_model_id,
    rw_embedding = EXCLUDED.rw_embedding,
    rw_source_revision = EXCLUDED.rw_source_revision,
    rw_content_hash = EXCLUDED.rw_content_hash,
    rw_created_at = now();

  UPDATE rw.rw_embedding_jobs SET rw_processed_at = now() WHERE rw_embedding_job_id = p_embedding_job_id;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_store_message_embedding(bigint, bigint, smallint, vector, integer, bytea) FROM PUBLIC;

CREATE FUNCTION rw.rw_fn_mark_embedding_job_failed(p_embedding_job_id bigint, p_error text) RETURNS void
LANGUAGE sql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  UPDATE rw.rw_embedding_jobs
     SET rw_attempts = rw_attempts + 1, rw_last_error = p_error
   WHERE rw_embedding_job_id = p_embedding_job_id
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_mark_embedding_job_failed(bigint, text) FROM PUBLIC;

RESET ROLE;
