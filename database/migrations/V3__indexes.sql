-- V3__indexes.sql
-- Partial unique indexes (DB-10), keyset/FK/full-text/vector indexes (DB-11).
SET ROLE rw_migrator;

-- ===================================================== partial unique indexes --
-- Four delivered (exceeds the DB-10 minimum of one), each a database-enforced
-- business rule rather than an application convention.

-- BR-11: email is unique among ACTIVE users only — a deactivated user's address
-- can be reused by someone else without a manual cleanup step.
CREATE UNIQUE INDEX rw_ux_users_email_active
  ON rw.rw_users (lower(rw_email))
  WHERE rw_is_active;

-- Slugs identify PUBLIC/PRIVATE channels; DIRECT channels have none (BR-07), so
-- the uniqueness only needs to hold where a slug exists.
CREATE UNIQUE INDEX rw_ux_channels_slug
  ON rw.rw_channels (rw_slug)
  WHERE rw_slug IS NOT NULL;

-- PU-1 (BR-13): at most one *live* refresh token per rotation family. Making two
-- concurrently-live tokens in one family unrepresentable is what turns "rotation"
-- into a database guarantee instead of an application promise.
CREATE UNIQUE INDEX rw_ux_refresh_tokens_family_active
  ON rw.rw_refresh_tokens (rw_family_id)
  WHERE rw_revoked_at IS NULL;

-- PU-2 (BR-15): exactly one active system prompt version. The indexed expression
-- is constant (TRUE) for every matching row, so a second active row collides.
CREATE UNIQUE INDEX rw_ux_system_prompts_single_active
  ON rw.rw_system_prompts ((true))
  WHERE rw_is_active;

-- ================================================================ keyset ------
-- The index that makes OBJ-04 (message history) and MSG-04 (no OFFSET, ever)
-- possible: a strict, index-only ordering per channel over live messages.
CREATE INDEX rw_ix_messages_channel_keyset
  ON rw.rw_messages (rw_channel_id, rw_created_at DESC, rw_message_id DESC)
  WHERE NOT rw_is_deleted;

-- =================================================================== FK -------
CREATE INDEX rw_ix_messages_sender ON rw.rw_messages (rw_sender_id);
CREATE INDEX rw_ix_messages_parent ON rw.rw_messages (rw_parent_message_id) WHERE rw_parent_message_id IS NOT NULL;
CREATE INDEX rw_ix_messages_deleted_by ON rw.rw_messages (rw_deleted_by) WHERE rw_deleted_by IS NOT NULL;

CREATE INDEX rw_ix_channel_members_user ON rw.rw_channel_members (rw_user_id);
CREATE INDEX rw_ix_channel_members_last_read ON rw.rw_channel_members (rw_last_read_message_id) WHERE rw_last_read_message_id IS NOT NULL;

CREATE INDEX rw_ix_channels_created_by ON rw.rw_channels (rw_created_by);
CREATE INDEX rw_ix_channels_type_active ON rw.rw_channels (rw_channel_type_id) WHERE NOT rw_is_archived;

CREATE INDEX rw_ix_refresh_tokens_user ON rw.rw_refresh_tokens (rw_user_id);
CREATE INDEX rw_ix_refresh_tokens_replaced_by ON rw.rw_refresh_tokens (rw_replaced_by_token_id) WHERE rw_replaced_by_token_id IS NOT NULL;

CREATE INDEX rw_ix_message_edits_message ON rw.rw_message_edits (rw_message_id);

CREATE INDEX rw_ix_embedding_models_provider ON rw.rw_embedding_models (rw_ai_provider_id);

CREATE INDEX rw_ix_copilot_threads_user ON rw.rw_copilot_threads (rw_user_id, rw_updated_at DESC);
CREATE INDEX rw_ix_copilot_threads_channel ON rw.rw_copilot_threads (rw_channel_id) WHERE rw_channel_id IS NOT NULL;

CREATE INDEX rw_ix_copilot_turns_thread ON rw.rw_copilot_turns (rw_copilot_thread_id, rw_created_at DESC);

CREATE INDEX rw_ix_copilot_citations_message ON rw.rw_copilot_citations (rw_message_id);

-- Backs OBJ-07 (accumulated copilot consumption per user): a per-user scan
-- ordered by time, exactly the access pattern rw_fn_copilot_usage_summary uses.
CREATE INDEX rw_ix_copilot_usage_user ON rw.rw_copilot_usage (rw_user_id, rw_created_at DESC);

-- ============================================================ full-text -------
-- SRCH-01: backs websearch_to_tsquery matches against the generated column.
CREATE INDEX rw_ix_messages_search_vector ON rw.rw_messages USING GIN (rw_search_vector);

-- ============================================================== vector --------
-- AI-05/AI-07: HNSW cosine index. Retrieval still runs inside the RLS-filtered
-- set (docs/SECURITY_MODEL.md §6); this index accelerates the ANN operator
-- within whatever subset the authorization layer has already produced.
CREATE INDEX rw_ix_message_embeddings_hnsw
  ON rw.rw_message_embeddings USING hnsw (rw_embedding vector_cosine_ops);

-- ============================================================== outbox --------
-- Partial index over only the unprocessed hot set (D-18's transactional outbox).
CREATE INDEX rw_ix_embedding_jobs_pending
  ON rw.rw_embedding_jobs (rw_enqueued_at)
  WHERE rw_processed_at IS NULL;

RESET ROLE;
