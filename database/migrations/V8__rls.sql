-- V8__rls.sql
-- Row Level Security: the mechanism that makes the absolute security
-- requirement true. Every policy here is documented in
-- docs/SECURITY_MODEL.md §4 — this file is that document made executable.
--
-- Pattern for every content table: ENABLE + FORCE (SEC-10, SEC-12), so even
-- the owning role rw_migrator would be subject to policy were it not for its
-- BYPASSRLS attribute (V1) — FORCE only matters for a non-BYPASSRLS owner,
-- but setting it is free and correct regardless. rw_app has NOBYPASSRLS
-- (V1), so every one of these policies is live for it, unconditionally.
SET ROLE rw_migrator;

-- =============================================================== lookups ----
-- Read-only reference data: visible to any authenticated actor, writable by
-- nobody through RLS (migrations/seed load as rw_migrator, which bypasses).
ALTER TABLE rw.rw_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_roles_select ON rw.rw_roles FOR SELECT
  USING (rw.rw_fn_current_user_id() IS NOT NULL);

ALTER TABLE rw.rw_channel_types ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_channel_types FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_channel_types_select ON rw.rw_channel_types FOR SELECT
  USING (rw.rw_fn_current_user_id() IS NOT NULL);

ALTER TABLE rw.rw_channel_member_roles ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_channel_member_roles FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_channel_member_roles_select ON rw.rw_channel_member_roles FOR SELECT
  USING (rw.rw_fn_current_user_id() IS NOT NULL);

ALTER TABLE rw.rw_ai_providers ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_ai_providers FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_ai_providers_select ON rw.rw_ai_providers FOR SELECT
  USING (rw.rw_fn_current_user_id() IS NOT NULL);

ALTER TABLE rw.rw_embedding_models ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_embedding_models FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_embedding_models_select ON rw.rw_embedding_models FOR SELECT
  USING (rw.rw_fn_current_user_id() IS NOT NULL);

ALTER TABLE rw.rw_system_prompts ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_system_prompts FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_system_prompts_select ON rw.rw_system_prompts FOR SELECT
  USING (rw.rw_fn_current_user_id() IS NOT NULL);

-- ================================================================ users -----
ALTER TABLE rw.rw_users ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_users FORCE ROW LEVEL SECURITY;

CREATE POLICY rw_users_select ON rw.rw_users FOR SELECT
  USING (rw_is_active AND rw.rw_fn_current_user_id() IS NOT NULL);

-- Self-service profile edits only. Which COLUMNS may be touched this way
-- (never rw_role_id, rw_is_active, rw_email) is restricted by the column
-- grant in V9, not by this row predicate — RLS decides which ROWS, GRANT
-- decides which COLUMNS. Administrative changes go through the SECURITY
-- DEFINER rw_sp_modify_user procedure (V5), which bypasses both.
CREATE POLICY rw_users_update_self ON rw.rw_users FOR UPDATE
  USING (rw_user_id = rw.rw_fn_current_user_id())
  WITH CHECK (rw_user_id = rw.rw_fn_current_user_id());

-- No INSERT, no DELETE policy: registration and deactivation are
-- administrative operations with no RLS-governed direct path.

-- ======================================================= user_credentials ---
-- No policies at all, and rw_app holds no table grant whatsoever (V9).
-- Reachable only via the SECURITY DEFINER functions in V4, which run as
-- rw_migrator (BYPASSRLS) and never return the hash to any HTTP client.
ALTER TABLE rw.rw_user_credentials ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_user_credentials FORCE ROW LEVEL SECURITY;

-- =========================================================== refresh_tokens --
-- Same posture: zero policies, zero grants. rw_fn_issue_refresh_token,
-- rw_fn_rotate_refresh_token and rw_fn_revoke_family are the only path in.
ALTER TABLE rw.rw_refresh_tokens ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_refresh_tokens FORCE ROW LEVEL SECURITY;

-- ============================================================== channels ----
ALTER TABLE rw.rw_channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_channels FORCE ROW LEVEL SECURITY;

-- IBR-02: a PUBLIC, non-archived channel is discoverable by any authenticated
-- actor even before joining. PRIVATE and DIRECT channels are visible only to
-- members — this is what keeps SEC-09 (no metadata leakage) true.
CREATE POLICY rw_channels_select ON rw.rw_channels FOR SELECT
  USING (
    rw.rw_fn_is_channel_member(rw_channel_id)
    OR (
      rw.rw_fn_current_user_id() IS NOT NULL
      AND NOT rw_is_archived
      AND rw_channel_type_id = (SELECT t.rw_channel_type_id FROM rw.rw_channel_types t WHERE t.rw_channel_type_code = 'PUBLIC')
    )
  );

CREATE POLICY rw_channels_insert ON rw.rw_channels FOR INSERT
  WITH CHECK (rw_created_by = rw.rw_fn_current_user_id());

CREATE POLICY rw_channels_update ON rw.rw_channels FOR UPDATE
  USING (rw.rw_fn_is_channel_owner_or_moderator(rw_channel_id))
  WITH CHECK (rw.rw_fn_is_channel_owner_or_moderator(rw_channel_id));

-- No DELETE policy: channels are archived, never removed.

-- ======================================================= channel_members ----
ALTER TABLE rw.rw_channel_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_channel_members FORCE ROW LEVEL SECURITY;

-- Includes "it's my own row" as an independent branch, not only "I'm a
-- member of this channel" (which is trivially implied when true anyway).
-- This is required, not cosmetic: PostgreSQL's INSERT ... ON CONFLICT needs
-- to be able to see a would-be-conflicting row under the SELECT policy to
-- resolve the arbiter, even for DO NOTHING. Without this branch, a user who
-- is not yet a member (which is exactly the case for a first-time join)
-- could never see their own absent row, and rw_fn_join_channel's
-- ON CONFLICT DO NOTHING would fail RLS unconditionally — verified against
-- a live database during Phase 3 before this fix was added.
CREATE POLICY rw_channel_members_select ON rw.rw_channel_members FOR SELECT
  USING (rw_user_id = rw.rw_fn_current_user_id() OR rw.rw_fn_is_channel_member(rw_channel_id));

-- Only a self-join to a non-archived PUBLIC, non-DIRECT channel is reachable
-- through ordinary rw_app privilege. Every other membership write (owner
-- adding to a PRIVATE channel, role changes, leave, remove) goes through the
-- SECURITY DEFINER functions in V4 — rw_app gets no UPDATE or DELETE grant
-- on this table at all (V9), so there is no policy to write for those verbs.
CREATE POLICY rw_channel_members_insert_self_join ON rw.rw_channel_members FOR INSERT
  WITH CHECK (
    rw_user_id = rw.rw_fn_current_user_id()
    AND EXISTS (
      SELECT 1 FROM rw.rw_channels c
      JOIN rw.rw_channel_types ct ON ct.rw_channel_type_id = c.rw_channel_type_id
      WHERE c.rw_channel_id = rw_channel_members.rw_channel_id
        AND ct.rw_channel_type_code = 'PUBLIC'
        AND NOT c.rw_is_archived
    )
  );

-- =================================================== direct_channel_pairs ---
ALTER TABLE rw.rw_direct_channel_pairs ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_direct_channel_pairs FORCE ROW LEVEL SECURITY;

CREATE POLICY rw_direct_channel_pairs_select ON rw.rw_direct_channel_pairs FOR SELECT
  USING (rw.rw_fn_is_channel_member(rw_channel_id));

-- No INSERT/UPDATE/DELETE policy: rw_fn_open_direct_channel (SECURITY
-- DEFINER) is the only writer.

-- =============================================================== messages ---
-- The primary control. Note what is absent: there is no "OR actor is ADMIN"
-- anywhere below. That omission is IBR-01, and it is what makes SEC-01
-- absolute rather than role-dependent.
ALTER TABLE rw.rw_messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_messages FORCE ROW LEVEL SECURITY;

CREATE POLICY rw_messages_select ON rw.rw_messages FOR SELECT
  USING (rw.rw_fn_is_channel_member(rw_channel_id));

CREATE POLICY rw_messages_insert ON rw.rw_messages FOR INSERT
  WITH CHECK (
    rw_sender_id = rw.rw_fn_current_user_id()
    AND rw.rw_fn_is_channel_member(rw_channel_id)
    AND NOT EXISTS (SELECT 1 FROM rw.rw_channels c WHERE c.rw_channel_id = rw_messages.rw_channel_id AND c.rw_is_archived)
  );

CREATE POLICY rw_messages_update ON rw.rw_messages FOR UPDATE
  USING (rw_sender_id = rw.rw_fn_current_user_id() AND rw.rw_fn_is_channel_member(rw_channel_id))
  WITH CHECK (rw_sender_id = rw.rw_fn_current_user_id() AND rw.rw_fn_is_channel_member(rw_channel_id));

-- No DELETE policy, no DELETE grant (V9), and a BEFORE DELETE trigger that
-- raises (V7) — three independent layers for MSG-01/BR-04.

-- =========================================================== message_edits --
ALTER TABLE rw.rw_message_edits ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_message_edits FORCE ROW LEVEL SECURITY;

CREATE POLICY rw_message_edits_select ON rw.rw_message_edits FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM rw.rw_messages m
      WHERE m.rw_message_id = rw_message_edits.rw_message_id
        AND rw.rw_fn_is_channel_member(m.rw_channel_id)
    )
  );

-- No INSERT/UPDATE/DELETE policy: only the SECURITY DEFINER
-- rw_fn_messages_before_update trigger writes here; history is immutable.

-- ======================================================= message_embeddings --
-- SEC-05: an embedding is exactly as protected as the message it derives
-- from — same membership predicate, not merely "trust the retrieval
-- function to filter."
ALTER TABLE rw.rw_message_embeddings ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_message_embeddings FORCE ROW LEVEL SECURITY;

CREATE POLICY rw_message_embeddings_select ON rw.rw_message_embeddings FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM rw.rw_messages m
      WHERE m.rw_message_id = rw_message_embeddings.rw_message_id
        AND rw.rw_fn_is_channel_member(m.rw_channel_id)
    )
  );

-- No INSERT/UPDATE/DELETE policy: only rw_fn_store_message_embedding
-- (SECURITY DEFINER, worker-only) and the after-update trigger write here.

-- ============================================================ copilot_* ----
ALTER TABLE rw.rw_copilot_threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_copilot_threads FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_copilot_threads_select ON rw.rw_copilot_threads FOR SELECT
  USING (rw_user_id = rw.rw_fn_current_user_id());
CREATE POLICY rw_copilot_threads_insert ON rw.rw_copilot_threads FOR INSERT
  WITH CHECK (rw_user_id = rw.rw_fn_current_user_id());
CREATE POLICY rw_copilot_threads_update ON rw.rw_copilot_threads FOR UPDATE
  USING (rw_user_id = rw.rw_fn_current_user_id())
  WITH CHECK (rw_user_id = rw.rw_fn_current_user_id());

ALTER TABLE rw.rw_copilot_turns ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_copilot_turns FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_copilot_turns_select ON rw.rw_copilot_turns FOR SELECT
  USING (EXISTS (SELECT 1 FROM rw.rw_copilot_threads t WHERE t.rw_copilot_thread_id = rw_copilot_turns.rw_copilot_thread_id AND t.rw_user_id = rw.rw_fn_current_user_id()));
CREATE POLICY rw_copilot_turns_insert ON rw.rw_copilot_turns FOR INSERT
  WITH CHECK (EXISTS (SELECT 1 FROM rw.rw_copilot_threads t WHERE t.rw_copilot_thread_id = rw_copilot_turns.rw_copilot_thread_id AND t.rw_user_id = rw.rw_fn_current_user_id()));

ALTER TABLE rw.rw_copilot_citations ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_copilot_citations FORCE ROW LEVEL SECURITY;
-- BR-17 / AI-12: a citation is readable only while BOTH the owning turn is
-- the actor's own AND the cited message is still one the actor can read —
-- if the actor later leaves that channel, the citation (and its quoted
-- text, resolved separately through rw_messages) stops resolving too
-- (IBR-09).
CREATE POLICY rw_copilot_citations_select ON rw.rw_copilot_citations FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM rw.rw_copilot_turns t
      JOIN rw.rw_copilot_threads th ON th.rw_copilot_thread_id = t.rw_copilot_thread_id
      WHERE t.rw_copilot_turn_id = rw_copilot_citations.rw_copilot_turn_id
        AND th.rw_user_id = rw.rw_fn_current_user_id()
    )
    AND rw.rw_fn_is_channel_member((SELECT m.rw_channel_id FROM rw.rw_messages m WHERE m.rw_message_id = rw_copilot_citations.rw_message_id))
  );
CREATE POLICY rw_copilot_citations_insert ON rw.rw_copilot_citations FOR INSERT
  WITH CHECK (
    EXISTS (
      SELECT 1 FROM rw.rw_copilot_turns t
      JOIN rw.rw_copilot_threads th ON th.rw_copilot_thread_id = t.rw_copilot_thread_id
      WHERE t.rw_copilot_turn_id = rw_copilot_citations.rw_copilot_turn_id
        AND th.rw_user_id = rw.rw_fn_current_user_id()
    )
  );

ALTER TABLE rw.rw_copilot_usage ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_copilot_usage FORCE ROW LEVEL SECURITY;
CREATE POLICY rw_copilot_usage_select ON rw.rw_copilot_usage FOR SELECT
  USING (rw_user_id = rw.rw_fn_current_user_id());
CREATE POLICY rw_copilot_usage_insert ON rw.rw_copilot_usage FOR INSERT
  WITH CHECK (rw_user_id = rw.rw_fn_current_user_id());

RESET ROLE;
