-- V9__grants.sql
-- rw_app's complete privilege surface. Two rules held throughout:
--   1. rw_app NEVER receives DELETE on anything (D-07) — every operation
--      that needs a row physically removed (leave channel, remove member,
--      rotate a token) goes through a SECURITY DEFINER function instead.
--   2. Column-level grants restrict self-service UPDATEs to the columns an
--      ordinary member should be able to touch; anything privileged
--      (role, active flag, channel type/creator, message identity/sender)
--      is only reachable through a SECURITY DEFINER function or is never
--      updatable at all.
SET ROLE rw_migrator;

-- ============================================================== lookups ----
GRANT SELECT ON rw.rw_roles                 TO rw_app;
GRANT SELECT ON rw.rw_channel_types         TO rw_app;
GRANT SELECT ON rw.rw_channel_member_roles  TO rw_app;
GRANT SELECT ON rw.rw_ai_providers          TO rw_app;
GRANT SELECT ON rw.rw_embedding_models      TO rw_app;
GRANT SELECT ON rw.rw_system_prompts        TO rw_app;

-- ================================================================ users ----
GRANT SELECT ON rw.rw_users TO rw_app;
GRANT UPDATE (rw_display_name, rw_locale, rw_avatar_url, rw_last_seen_at, rw_updated_at)
  ON rw.rw_users TO rw_app;
-- rw_role_id, rw_is_active, rw_email: not grantable — only rw_sp_modify_user
-- (SECURITY DEFINER, V5) can change them.

-- rw_user_credentials, rw_refresh_tokens: NO grants whatsoever. Every access
-- is through the SECURITY DEFINER functions in V4.

-- ============================================================= channels ----
GRANT SELECT, INSERT ON rw.rw_channels TO rw_app;
GRANT UPDATE (rw_name, rw_topic, rw_is_archived, rw_updated_at) ON rw.rw_channels TO rw_app;
-- rw_channel_type_id, rw_created_by, rw_slug: immutable after creation.

GRANT SELECT, INSERT ON rw.rw_channel_members TO rw_app;
-- No UPDATE, no DELETE: role changes, leaving and removal all go through
-- rw_fn_update_channel_member / rw_fn_leave_channel / rw_fn_remove_channel_member.

GRANT SELECT ON rw.rw_direct_channel_pairs TO rw_app;
-- Written only by rw_fn_open_direct_channel.

-- ============================================================= messages ----
GRANT SELECT, INSERT ON rw.rw_messages TO rw_app;
GRANT UPDATE (rw_body, rw_is_deleted, rw_deleted_at, rw_deleted_by, rw_edited_at, rw_revision)
  ON rw.rw_messages TO rw_app;
-- rw_channel_id, rw_sender_id, rw_client_message_id, rw_parent_message_id,
-- rw_created_at: immutable. No DELETE grant anywhere (MSG-01).

GRANT SELECT ON rw.rw_message_edits TO rw_app;
-- Written only by the SECURITY DEFINER edit-history trigger.

GRANT SELECT ON rw.rw_message_embeddings TO rw_app;
-- Written only by rw_fn_store_message_embedding and the invalidation trigger.

-- rw_embedding_jobs: NO direct grant. rw_fn_claim_pending_embedding_jobs,
-- rw_fn_store_message_embedding and rw_fn_mark_embedding_job_failed (all
-- SECURITY DEFINER) are the only access path — this table carries no
-- per-user data, so the boundary that matters is "only the worker's own
-- narrow functions touch it," not a row-level policy.

-- ============================================================== copilot ----
GRANT SELECT, INSERT ON rw.rw_copilot_threads TO rw_app;
GRANT UPDATE (rw_title, rw_updated_at) ON rw.rw_copilot_threads TO rw_app;

GRANT SELECT, INSERT ON rw.rw_copilot_turns TO rw_app;
-- A turn is written once, complete, after the answer/refusal is decided —
-- no UPDATE grant needed.

GRANT SELECT, INSERT ON rw.rw_copilot_citations TO rw_app;
GRANT SELECT, INSERT ON rw.rw_copilot_usage TO rw_app;

-- ================================================================ views ----
-- A view is a distinct grantable object in PostgreSQL — access to its
-- underlying tables does not imply access to the view itself, even though
-- the view runs with the caller's own RLS (views are always invoker-rights).
GRANT SELECT ON rw.rw_v_user_conversations TO rw_app;

-- ========================================================= EXECUTE allow-list --
-- Deny by default, then name every callable explicitly — the complete
-- privileged-and-unprivileged surface rw_app may invoke, in one place.
REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA rw FROM PUBLIC;

GRANT EXECUTE ON FUNCTION rw.rw_fn_current_user_id() TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_is_channel_member(uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_is_channel_owner_or_moderator(uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_message_history(uuid, timestamptz, bigint, integer) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_search_messages(text, uuid, real, bigint, integer) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_rag_retrieve_candidates(vector, uuid, integer) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_copilot_usage_summary() TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_send_message(uuid, text, uuid, bigint) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_edit_message(bigint, text) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_soft_delete_message(bigint) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_join_channel(uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_leave_channel(uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_remove_channel_member(uuid, uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_update_channel_member(uuid, uuid, text, bigint, boolean) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_create_channel(text, text, text, text) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_open_direct_channel(uuid) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_get_credential_for_login(text) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_record_login_attempt(uuid, boolean) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_issue_refresh_token(uuid, uuid, bytea, timestamptz, text, inet) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_rotate_refresh_token(bytea, bytea, timestamptz, text, inet) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_revoke_family(uuid, text) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_claim_pending_embedding_jobs(integer) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_store_message_embedding(bigint, bigint, smallint, vector, integer, bytea) TO rw_app;
GRANT EXECUTE ON FUNCTION rw.rw_fn_mark_embedding_job_failed(bigint, text) TO rw_app;

GRANT EXECUTE ON PROCEDURE rw.rw_sp_query_users(text, integer, text, uuid, refcursor) TO rw_app;
GRANT EXECUTE ON PROCEDURE rw.rw_sp_modify_user(uuid, text, text, text, text, boolean) TO rw_app;

-- Trigger functions (rw_fn_set_updated_at, rw_fn_block_delete,
-- rw_fn_messages_before_update, rw_fn_messages_after_insert,
-- rw_fn_messages_after_update, rw_fn_guard_last_owner,
-- rw_fn_guard_direct_channel_membership) are deliberately NOT granted here:
-- they are invoked exclusively by trigger machinery, never by direct EXECUTE
-- from application code, so no role needs a standing EXECUTE grant on them.

RESET ROLE;
