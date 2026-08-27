-- V7__triggers.sql
-- Every trigger enforces something no application layer can be trusted to
-- remember on every code path. Each is named after the rule it exists for.
SET ROLE rw_migrator;

-- ============================================================ updated_at ----
CREATE FUNCTION rw.rw_fn_set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.rw_updated_at := now();
  RETURN NEW;
END;
$$;

CREATE TRIGGER rw_trg_users_set_updated_at
  BEFORE UPDATE ON rw.rw_users FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_set_updated_at();
CREATE TRIGGER rw_trg_channels_set_updated_at
  BEFORE UPDATE ON rw.rw_channels FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_set_updated_at();
CREATE TRIGGER rw_trg_copilot_threads_set_updated_at
  BEFORE UPDATE ON rw.rw_copilot_threads FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_set_updated_at();

-- ========================================================= BR-04 / MSG-01 ---
-- Belt-and-braces: rw_app never receives a DELETE grant on rw_messages (V9)
-- and there is no DELETE RLS policy either (V8) — this trigger is the third,
-- role-independent layer, so even a future migration mistake that added a
-- DELETE grant still could not delete a message row.
CREATE FUNCTION rw.rw_fn_block_delete() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  RAISE EXCEPTION 'rows in % may never be physically deleted (MSG-01)', TG_TABLE_NAME
    USING ERRCODE = '42501';
END;
$$;

CREATE TRIGGER rw_trg_messages_block_delete
  BEFORE DELETE ON rw.rw_messages FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_block_delete();

-- Edit history is immutable by the same reasoning (no UPDATE/DELETE policy
-- either, V8) — this trigger is the backstop for both operations.
CREATE TRIGGER rw_trg_message_edits_block_mutation
  BEFORE UPDATE OR DELETE ON rw.rw_message_edits FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_block_delete();

-- ===================================================== BR-05 / MSG-02 -------
-- Captures the previous body into history and bumps the revision, BEFORE the
-- new row is written — so history and the live row change atomically in one
-- statement, and rw_fn_edit_message (V4) never has to do this itself.
-- SECURITY DEFINER: it writes to rw_message_edits, on which rw_app holds only
-- a SELECT grant (V9) — application code can read history but can never
-- insert a fabricated entry directly; only this trigger, firing on a
-- genuinely authorized UPDATE of rw_messages, can add one.
CREATE FUNCTION rw.rw_fn_messages_before_update() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
BEGIN
  IF NOT OLD.rw_is_deleted AND NOT NEW.rw_is_deleted AND OLD.rw_body IS DISTINCT FROM NEW.rw_body THEN
    INSERT INTO rw.rw_message_edits (rw_message_id, rw_previous_body, rw_previous_revision, rw_edited_by)
    VALUES (OLD.rw_message_id, OLD.rw_body, OLD.rw_revision, rw.rw_fn_current_user_id());
    NEW.rw_revision := OLD.rw_revision + 1;
    NEW.rw_edited_at := now();
  END IF;
  RETURN NEW;
END;
$$;

CREATE TRIGGER rw_trg_messages_before_update
  BEFORE UPDATE ON rw.rw_messages FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_messages_before_update();

-- ============================================================= AI-06 --------
-- Keeps the vector index consistent with content: a brand-new message needs
-- an embedding; an edited message's old embedding is now describing text
-- that no longer exists, so it is removed and a fresh job queued; a deleted
-- message's embedding is removed outright (it must never be a RAG candidate
-- again, IBR-06). No message body ever appears in a NOTIFY payload — realtime
-- carries ids only (RT-01, docs/ARCHITECTURE.md §5).
-- SECURITY DEFINER: writes to rw_embedding_jobs, which rw_app only holds a
-- SELECT/UPDATE grant on (the worker claims and marks jobs, but never
-- inserts one directly — only a genuine message insert can create one).
CREATE FUNCTION rw.rw_fn_messages_after_insert() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
BEGIN
  INSERT INTO rw.rw_embedding_jobs (rw_message_id) VALUES (NEW.rw_message_id);
  PERFORM pg_notify('rw_message_created', json_build_object(
    'channelId', NEW.rw_channel_id, 'messageId', NEW.rw_message_id,
    'senderId', NEW.rw_sender_id, 'clientMessageId', NEW.rw_client_message_id,
    'createdAt', NEW.rw_created_at
  )::text);
  RETURN NULL;
END;
$$;

CREATE TRIGGER rw_trg_messages_after_insert
  AFTER INSERT ON rw.rw_messages FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_messages_after_insert();

-- SECURITY DEFINER: deletes from rw_message_embeddings, on which rw_app
-- holds only a SELECT grant (V9) — invalidating a stale vector is something
-- only this trigger does, never a direct application DELETE.
CREATE FUNCTION rw.rw_fn_messages_after_update() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
BEGIN
  IF NEW.rw_is_deleted AND NOT OLD.rw_is_deleted THEN
    DELETE FROM rw.rw_message_embeddings WHERE rw_message_id = NEW.rw_message_id;
    PERFORM pg_notify('rw_message_deleted', json_build_object(
      'channelId', NEW.rw_channel_id, 'messageId', NEW.rw_message_id
    )::text);
  ELSIF NOT NEW.rw_is_deleted AND OLD.rw_body IS DISTINCT FROM NEW.rw_body THEN
    DELETE FROM rw.rw_message_embeddings WHERE rw_message_id = NEW.rw_message_id;
    INSERT INTO rw.rw_embedding_jobs (rw_message_id) VALUES (NEW.rw_message_id);
    PERFORM pg_notify('rw_message_updated', json_build_object(
      'channelId', NEW.rw_channel_id, 'messageId', NEW.rw_message_id, 'revision', NEW.rw_revision
    )::text);
  END IF;
  RETURN NULL;
END;
$$;

CREATE TRIGGER rw_trg_messages_after_update
  AFTER UPDATE ON rw.rw_messages FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_messages_after_update();

-- ========================================================== BR-10 -----------
-- A channel must always keep at least one OWNER. Fires on the row being
-- removed or demoted, regardless of which role or function performed the
-- statement — it inspects the resulting membership state, not the caller.
CREATE FUNCTION rw.rw_fn_guard_last_owner() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_old_role_code text;
  v_new_role_code text;
  v_remaining_owners integer;
BEGIN
  SELECT rw_channel_member_role_code INTO v_old_role_code
  FROM rw.rw_channel_member_roles WHERE rw_channel_member_role_id = OLD.rw_channel_member_role_id;

  IF v_old_role_code IS DISTINCT FROM 'OWNER' THEN
    RETURN COALESCE(NEW, OLD);
  END IF;

  IF TG_OP = 'UPDATE' THEN
    SELECT rw_channel_member_role_code INTO v_new_role_code
    FROM rw.rw_channel_member_roles WHERE rw_channel_member_role_id = NEW.rw_channel_member_role_id;
    IF v_new_role_code = 'OWNER' THEN
      RETURN NEW;
    END IF;
  END IF;

  SELECT count(*) INTO v_remaining_owners
  FROM rw.rw_channel_members m
  JOIN rw.rw_channel_member_roles r ON r.rw_channel_member_role_id = m.rw_channel_member_role_id
  WHERE m.rw_channel_id = OLD.rw_channel_id
    AND r.rw_channel_member_role_code = 'OWNER'
    AND m.rw_user_id <> OLD.rw_user_id;

  IF v_remaining_owners = 0 THEN
    RAISE EXCEPTION 'a channel must always keep at least one OWNER (BR-10)' USING ERRCODE = '23514';
  END IF;

  RETURN COALESCE(NEW, OLD);
END;
$$;

CREATE TRIGGER rw_trg_channel_members_guard_last_owner
  BEFORE UPDATE OR DELETE ON rw.rw_channel_members FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_guard_last_owner();

-- ========================================================== BR-07 -----------
-- A DIRECT channel may hold at most two membership rows, ever, and once both
-- exist neither may be changed or removed. This is enforced on the DATA
-- SHAPE, not on who is calling — it holds even for a SECURITY DEFINER
-- function running as rw_migrator, which is exactly why rw_fn_leave_channel
-- and rw_fn_remove_channel_member (V4) correctly refuse to touch a DIRECT
-- channel's membership without needing their own special-case code.
CREATE FUNCTION rw.rw_fn_guard_direct_channel_membership() RETURNS trigger
LANGUAGE plpgsql AS $$
DECLARE
  v_type_code text;
  v_existing_count integer;
BEGIN
  SELECT ct.rw_channel_type_code INTO v_type_code
  FROM rw.rw_channels c JOIN rw.rw_channel_types ct ON ct.rw_channel_type_id = c.rw_channel_type_id
  WHERE c.rw_channel_id = COALESCE(NEW.rw_channel_id, OLD.rw_channel_id);

  IF v_type_code IS DISTINCT FROM 'DIRECT' THEN
    RETURN COALESCE(NEW, OLD);
  END IF;

  IF TG_OP = 'INSERT' THEN
    SELECT count(*) INTO v_existing_count FROM rw.rw_channel_members WHERE rw_channel_id = NEW.rw_channel_id;
    IF v_existing_count >= 2 THEN
      RAISE EXCEPTION 'a DIRECT channel may never have more than two members (BR-07)' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  RAISE EXCEPTION 'DIRECT channel membership cannot be changed once established (BR-07)' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER rw_trg_channel_members_guard_direct
  BEFORE INSERT OR UPDATE OR DELETE ON rw.rw_channel_members FOR EACH ROW EXECUTE FUNCTION rw.rw_fn_guard_direct_channel_membership();

RESET ROLE;
