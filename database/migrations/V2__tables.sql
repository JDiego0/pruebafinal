-- V2__tables.sql
-- 20 relations. Naming: every table and column is English, prefixed rw_ (DB-03).
-- Full rationale for every column and constraint: docs/DATA_MODEL.md.
--
-- All objects created here are owned by rw_migrator (D-07): this migration must
-- run as a role that can SET ROLE to it (the bootstrap superuser can always do
-- so without a password).
SET ROLE rw_migrator;

-- ============================================================== lookup tables --

CREATE TABLE rw.rw_roles (
  rw_role_id    smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_role_code  text NOT NULL,
  rw_role_name  text NOT NULL,
  rw_description text NOT NULL,
  CONSTRAINT rw_roles_code_uk UNIQUE (rw_role_code),
  CONSTRAINT rw_roles_code_ck CHECK (rw_role_code IN ('ADMIN', 'MEMBER'))
);
COMMENT ON TABLE rw.rw_roles IS 'System-wide role catalogue. Governs administrative capability only — never message-content access (IBR-01).';

CREATE TABLE rw.rw_channel_types (
  rw_channel_type_id   smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_channel_type_code text NOT NULL,
  rw_description        text NOT NULL,
  CONSTRAINT rw_channel_types_code_uk UNIQUE (rw_channel_type_code),
  CONSTRAINT rw_channel_types_code_ck CHECK (rw_channel_type_code IN ('PUBLIC', 'PRIVATE', 'DIRECT'))
);

CREATE TABLE rw.rw_channel_member_roles (
  rw_channel_member_role_id   smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_channel_member_role_code text NOT NULL,
  rw_description               text NOT NULL,
  CONSTRAINT rw_channel_member_roles_code_uk UNIQUE (rw_channel_member_role_code),
  CONSTRAINT rw_channel_member_roles_code_ck CHECK (rw_channel_member_role_code IN ('OWNER', 'MODERATOR', 'MEMBER'))
);

CREATE TABLE rw.rw_ai_providers (
  rw_ai_provider_id       smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_provider_code        text NOT NULL,
  rw_display_name         text NOT NULL,
  rw_is_openai_compatible boolean NOT NULL,
  rw_is_active            boolean NOT NULL DEFAULT true,
  CONSTRAINT rw_ai_providers_code_uk UNIQUE (rw_provider_code)
);
COMMENT ON TABLE rw.rw_ai_providers IS 'Which AI vendor produced a given artifact (D-21). No URLs or keys stored — those are environment configuration.';

CREATE TABLE rw.rw_embedding_models (
  rw_embedding_model_id smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_ai_provider_id     smallint NOT NULL,
  rw_model_code         text NOT NULL,
  rw_dimensions         integer NOT NULL,
  rw_is_active          boolean NOT NULL DEFAULT true,
  CONSTRAINT rw_embedding_models_model_uk UNIQUE (rw_model_code),
  CONSTRAINT rw_embedding_models_provider_fk FOREIGN KEY (rw_ai_provider_id)
    REFERENCES rw.rw_ai_providers (rw_ai_provider_id) ON DELETE RESTRICT,
  CONSTRAINT rw_embedding_models_dimensions_ck CHECK (rw_dimensions = 1536)
);
COMMENT ON COLUMN rw.rw_embedding_models.rw_dimensions IS 'Fixed at 1536 to match the rw_message_embeddings.rw_embedding column width. Changing it is a migration, not a setting (D-21).';

CREATE TABLE rw.rw_system_prompts (
  rw_system_prompt_id smallint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_prompt_version    text NOT NULL,
  rw_prompt_body       text NOT NULL,
  rw_is_active         boolean NOT NULL DEFAULT false,
  rw_created_at        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_system_prompts_version_uk UNIQUE (rw_prompt_version)
);
COMMENT ON TABLE rw.rw_system_prompts IS 'Versioned copilot system instructions (AI-14). Exactly one row may be active at a time — enforced by a partial unique index in V3.';

-- =============================================================== identity ----

CREATE TABLE rw.rw_users (
  rw_user_id       uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  rw_email         text NOT NULL,
  rw_display_name  text NOT NULL,
  rw_role_id       smallint NOT NULL,
  rw_locale        text NOT NULL DEFAULT 'es',
  rw_avatar_url    text,
  rw_is_active     boolean NOT NULL DEFAULT true,
  rw_created_at    timestamptz NOT NULL DEFAULT now(),
  rw_updated_at    timestamptz NOT NULL DEFAULT now(),
  rw_last_seen_at  timestamptz,
  CONSTRAINT rw_users_role_fk FOREIGN KEY (rw_role_id)
    REFERENCES rw.rw_roles (rw_role_id) ON DELETE RESTRICT,
  CONSTRAINT rw_users_email_ck CHECK (rw_email ~ '^[^@[:space:]]+@[^@[:space:]]+\.[^@[:space:]]+$'),
  CONSTRAINT rw_users_display_name_ck CHECK (char_length(rw_display_name) BETWEEN 2 AND 80),
  CONSTRAINT rw_users_locale_ck CHECK (rw_locale IN ('es', 'en'))
);
COMMENT ON TABLE rw.rw_users IS 'People in the workspace directory. Deactivated (BR-12), never physically deleted — see the ON DELETE RESTRICT from rw_messages.rw_sender_id.';

CREATE TABLE rw.rw_user_credentials (
  rw_user_id               uuid NOT NULL PRIMARY KEY,
  rw_password_hash         text NOT NULL,
  rw_password_algorithm    text NOT NULL DEFAULT 'argon2id',
  rw_password_updated_at   timestamptz NOT NULL DEFAULT now(),
  rw_failed_attempts       integer NOT NULL DEFAULT 0,
  rw_locked_until          timestamptz,
  CONSTRAINT rw_user_credentials_user_fk FOREIGN KEY (rw_user_id)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_user_credentials_failed_attempts_ck CHECK (rw_failed_attempts >= 0)
);
COMMENT ON TABLE rw.rw_user_credentials IS 'Secrets isolated from the directory (D-16). rw_app has NO grant on this table; reachable only through SECURITY DEFINER authentication functions.';

CREATE TABLE rw.rw_refresh_tokens (
  rw_refresh_token_id      uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  rw_user_id               uuid NOT NULL,
  rw_family_id             uuid NOT NULL,
  rw_token_hash            bytea NOT NULL,
  rw_issued_at             timestamptz NOT NULL DEFAULT now(),
  rw_expires_at            timestamptz NOT NULL,
  rw_revoked_at            timestamptz,
  rw_revoked_reason        text,
  rw_replaced_by_token_id  uuid,
  rw_user_agent            text,
  rw_ip_address            inet,
  CONSTRAINT rw_refresh_tokens_user_fk FOREIGN KEY (rw_user_id)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_refresh_tokens_replaced_by_fk FOREIGN KEY (rw_replaced_by_token_id)
    REFERENCES rw.rw_refresh_tokens (rw_refresh_token_id) ON DELETE SET NULL,
  CONSTRAINT rw_refresh_tokens_token_hash_uk UNIQUE (rw_token_hash),
  CONSTRAINT rw_refresh_tokens_expiry_ck CHECK (rw_expires_at > rw_issued_at),
  CONSTRAINT rw_refresh_tokens_reason_ck CHECK (rw_revoked_reason IS NULL OR rw_revoked_reason IN ('ROTATED', 'LOGOUT', 'REUSE_DETECTED', 'EXPIRED')),
  CONSTRAINT rw_refresh_tokens_revoked_consistency_ck CHECK (
    (rw_revoked_at IS NULL AND rw_revoked_reason IS NULL) OR
    (rw_revoked_at IS NOT NULL AND rw_revoked_reason IS NOT NULL)
  )
);
COMMENT ON TABLE rw.rw_refresh_tokens IS 'Rotation ledger (AUTH-05). rw_token_hash is sha256 of the opaque token — plaintext is never stored (AUTH-04).';

-- ============================================================= conversation --

CREATE TABLE rw.rw_channels (
  rw_channel_id      uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  rw_channel_type_id smallint NOT NULL,
  rw_slug            text,
  rw_name            text,
  rw_topic           text,
  rw_created_by      uuid NOT NULL,
  rw_is_archived     boolean NOT NULL DEFAULT false,
  rw_created_at      timestamptz NOT NULL DEFAULT now(),
  rw_updated_at      timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_channels_type_fk FOREIGN KEY (rw_channel_type_id)
    REFERENCES rw.rw_channel_types (rw_channel_type_id) ON DELETE RESTRICT,
  CONSTRAINT rw_channels_created_by_fk FOREIGN KEY (rw_created_by)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE RESTRICT,
  -- BR-07: a DIRECT channel has no name/slug; a PUBLIC/PRIVATE channel has both.
  -- rw_channel_type_id 3 = DIRECT (seeded in V1 of the seed loader / this migration's companion data).
  CONSTRAINT rw_channels_direct_shape_ck CHECK (
    (rw_channel_type_id = 3 AND rw_name IS NULL AND rw_slug IS NULL) OR
    (rw_channel_type_id <> 3 AND rw_name IS NOT NULL AND rw_slug IS NOT NULL)
  ),
  CONSTRAINT rw_channels_name_ck CHECK (rw_name IS NULL OR char_length(rw_name) BETWEEN 2 AND 80),
  CONSTRAINT rw_channels_slug_ck CHECK (rw_slug IS NULL OR rw_slug ~ '^[a-z0-9][a-z0-9-]{1,78}[a-z0-9]$')
);
COMMENT ON TABLE rw.rw_channels IS 'A conversation: PUBLIC, PRIVATE, or DIRECT. Discoverability and content access are governed by RLS (docs/SECURITY_MODEL.md §4).';

CREATE TABLE rw.rw_channel_members (
  rw_channel_id               uuid NOT NULL,
  rw_user_id                  uuid NOT NULL,
  rw_channel_member_role_id   smallint NOT NULL,
  rw_joined_at                timestamptz NOT NULL DEFAULT now(),
  rw_last_read_message_id     bigint,
  rw_is_muted                 boolean NOT NULL DEFAULT false,
  CONSTRAINT rw_channel_members_pk PRIMARY KEY (rw_channel_id, rw_user_id),
  CONSTRAINT rw_channel_members_channel_fk FOREIGN KEY (rw_channel_id)
    REFERENCES rw.rw_channels (rw_channel_id) ON DELETE CASCADE,
  CONSTRAINT rw_channel_members_user_fk FOREIGN KEY (rw_user_id)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_channel_members_role_fk FOREIGN KEY (rw_channel_member_role_id)
    REFERENCES rw.rw_channel_member_roles (rw_channel_member_role_id) ON DELETE RESTRICT
  -- rw_last_read_message_id FK to rw.rw_messages is added in V2b, once that table exists.
);
COMMENT ON TABLE rw.rw_channel_members IS 'Membership — the authorization spine every RLS policy on message content ultimately consults (docs/SECURITY_MODEL.md §3.3).';

CREATE TABLE rw.rw_direct_channel_pairs (
  rw_channel_id    uuid NOT NULL PRIMARY KEY,
  rw_user_id_low   uuid NOT NULL,
  rw_user_id_high  uuid NOT NULL,
  CONSTRAINT rw_direct_channel_pairs_channel_fk FOREIGN KEY (rw_channel_id)
    REFERENCES rw.rw_channels (rw_channel_id) ON DELETE CASCADE,
  CONSTRAINT rw_direct_channel_pairs_low_fk FOREIGN KEY (rw_user_id_low)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_direct_channel_pairs_high_fk FOREIGN KEY (rw_user_id_high)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_direct_channel_pairs_order_ck CHECK (rw_user_id_low < rw_user_id_high),
  CONSTRAINT rw_direct_channel_pairs_pair_uk UNIQUE (rw_user_id_low, rw_user_id_high)
);
COMMENT ON TABLE rw.rw_direct_channel_pairs IS 'Canonicalizes a DIRECT channel to its unordered pair of participants — makes BR-08 (at most one DM per pair) a database guarantee, not an application convention.';

-- ================================================================= content --

CREATE TABLE rw.rw_messages (
  rw_message_id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_channel_id         uuid NOT NULL,
  rw_sender_id          uuid NOT NULL,
  rw_parent_message_id  bigint,
  rw_body               text,
  rw_client_message_id  uuid NOT NULL,
  rw_is_deleted         boolean NOT NULL DEFAULT false,
  rw_deleted_at         timestamptz,
  rw_deleted_by         uuid,
  rw_edited_at           timestamptz,
  rw_revision            integer NOT NULL DEFAULT 1,
  rw_created_at           timestamptz NOT NULL DEFAULT now(),
  rw_search_vector        tsvector GENERATED ALWAYS AS (to_tsvector('spanish'::regconfig, coalesce(rw_body, ''))) STORED,
  CONSTRAINT rw_messages_channel_fk FOREIGN KEY (rw_channel_id)
    REFERENCES rw.rw_channels (rw_channel_id) ON DELETE CASCADE,
  CONSTRAINT rw_messages_sender_fk FOREIGN KEY (rw_sender_id)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE RESTRICT,
  CONSTRAINT rw_messages_parent_fk FOREIGN KEY (rw_parent_message_id)
    REFERENCES rw.rw_messages (rw_message_id) ON DELETE SET NULL,
  CONSTRAINT rw_messages_deleted_by_fk FOREIGN KEY (rw_deleted_by)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE RESTRICT,
  CONSTRAINT rw_messages_client_message_uk UNIQUE (rw_channel_id, rw_sender_id, rw_client_message_id),
  CONSTRAINT rw_messages_revision_ck CHECK (rw_revision >= 1),
  -- BR-06: a logically deleted message keeps its row (id, channel, sender, timestamps)
  -- but its body is cleared, not merely hidden by a view. A live message always has a
  -- non-empty body within the 4000-character limit.
  CONSTRAINT rw_messages_deleted_shape_ck CHECK (
    (rw_is_deleted = false AND rw_body IS NOT NULL AND char_length(btrim(rw_body)) BETWEEN 1 AND 4000
       AND rw_deleted_at IS NULL AND rw_deleted_by IS NULL) OR
    (rw_is_deleted = true AND rw_body IS NULL AND rw_deleted_at IS NOT NULL AND rw_deleted_by IS NOT NULL)
  )
);
COMMENT ON TABLE rw.rw_messages IS 'Message content. Never physically deleted (BR-04, D-13): no DELETE grant, no DELETE policy, and V7''s trigger raises on any DELETE attempt.';
COMMENT ON COLUMN rw.rw_messages.rw_body IS 'NULL exactly when rw_is_deleted — logical deletion clears the content at the database level rather than relying on every consumer to mask it (BR-06).';

-- Deferred FK: rw_channel_members.rw_last_read_message_id -> rw_messages, now that
-- rw_messages exists. A member's read cursor may point at any message in a channel
-- they belong to; if that message is later hard-blocked from deletion, it never
-- disappears, so SET NULL only fires if the message row itself were ever removed
-- (it cannot be — BR-04), making this effectively stable for the life of the row.
ALTER TABLE rw.rw_channel_members
  ADD CONSTRAINT rw_channel_members_last_read_fk FOREIGN KEY (rw_last_read_message_id)
  REFERENCES rw.rw_messages (rw_message_id) ON DELETE SET NULL;

CREATE TABLE rw.rw_message_edits (
  rw_message_edit_id    bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_message_id         bigint NOT NULL,
  rw_previous_body       text NOT NULL,
  rw_previous_revision    integer NOT NULL,
  rw_edited_by             uuid NOT NULL,
  rw_edited_at             timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_message_edits_message_fk FOREIGN KEY (rw_message_id)
    REFERENCES rw.rw_messages (rw_message_id) ON DELETE CASCADE,
  CONSTRAINT rw_message_edits_edited_by_fk FOREIGN KEY (rw_edited_by)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE RESTRICT,
  CONSTRAINT rw_message_edits_revision_uk UNIQUE (rw_message_id, rw_previous_revision),
  CONSTRAINT rw_message_edits_body_ck CHECK (char_length(rw_previous_body) BETWEEN 1 AND 4000)
);
COMMENT ON TABLE rw.rw_message_edits IS 'Immutable revision history, written by a BEFORE UPDATE trigger on every body edit (BR-05, MSG-02). No UPDATE or DELETE policy — history cannot be altered.';

-- =============================================================== retrieval --

CREATE TABLE rw.rw_message_embeddings (
  rw_message_id          bigint NOT NULL PRIMARY KEY,
  rw_embedding_model_id  smallint NOT NULL,
  rw_embedding            vector(1536) NOT NULL,
  rw_source_revision      integer NOT NULL,
  rw_content_hash         bytea NOT NULL,
  rw_created_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_message_embeddings_message_fk FOREIGN KEY (rw_message_id)
    REFERENCES rw.rw_messages (rw_message_id) ON DELETE CASCADE,
  CONSTRAINT rw_message_embeddings_model_fk FOREIGN KEY (rw_embedding_model_id)
    REFERENCES rw.rw_embedding_models (rw_embedding_model_id) ON DELETE RESTRICT,
  CONSTRAINT rw_message_embeddings_revision_ck CHECK (rw_source_revision >= 1)
);
COMMENT ON TABLE rw.rw_message_embeddings IS 'Vector for a message. RLS-protected exactly like the message itself (SEC-05) — see docs/SECURITY_MODEL.md §4.';

CREATE TABLE rw.rw_embedding_jobs (
  rw_embedding_job_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_message_id         bigint NOT NULL,
  rw_enqueued_at         timestamptz NOT NULL DEFAULT now(),
  rw_processed_at        timestamptz,
  rw_attempts             integer NOT NULL DEFAULT 0,
  rw_last_error           text,
  CONSTRAINT rw_embedding_jobs_message_fk FOREIGN KEY (rw_message_id)
    REFERENCES rw.rw_messages (rw_message_id) ON DELETE CASCADE,
  CONSTRAINT rw_embedding_jobs_attempts_ck CHECK (rw_attempts >= 0)
);
COMMENT ON TABLE rw.rw_embedding_jobs IS 'Transactional outbox for (re)vectorization (D-18 pattern table), enqueued by trigger whenever a message is created or its body changes (AI-06).';

-- ================================================================= copilot --

CREATE TABLE rw.rw_copilot_threads (
  rw_copilot_thread_id  uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  rw_user_id             uuid NOT NULL,
  rw_channel_id           uuid,
  rw_title                text,
  rw_created_at            timestamptz NOT NULL DEFAULT now(),
  rw_updated_at            timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_copilot_threads_user_fk FOREIGN KEY (rw_user_id)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_copilot_threads_channel_fk FOREIGN KEY (rw_channel_id)
    REFERENCES rw.rw_channels (rw_channel_id) ON DELETE CASCADE
);

CREATE TABLE rw.rw_copilot_turns (
  rw_copilot_turn_id     uuid NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
  rw_copilot_thread_id    uuid NOT NULL,
  rw_system_prompt_id      smallint NOT NULL,
  rw_question               text NOT NULL,
  rw_answer                 text,
  rw_answer_status           text NOT NULL,
  rw_candidate_count         integer NOT NULL DEFAULT 0,
  rw_created_at               timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_copilot_turns_thread_fk FOREIGN KEY (rw_copilot_thread_id)
    REFERENCES rw.rw_copilot_threads (rw_copilot_thread_id) ON DELETE CASCADE,
  CONSTRAINT rw_copilot_turns_prompt_fk FOREIGN KEY (rw_system_prompt_id)
    REFERENCES rw.rw_system_prompts (rw_system_prompt_id) ON DELETE RESTRICT,
  CONSTRAINT rw_copilot_turns_question_ck CHECK (char_length(btrim(rw_question)) BETWEEN 1 AND 2000),
  CONSTRAINT rw_copilot_turns_status_ck CHECK (rw_answer_status IN ('ANSWERED', 'INSUFFICIENT_CONTEXT', 'NO_PERMISSION', 'OUT_OF_SCOPE', 'PROVIDER_ERROR')),
  CONSTRAINT rw_copilot_turns_candidate_count_ck CHECK (rw_candidate_count >= 0)
);
COMMENT ON TABLE rw.rw_copilot_turns IS 'One question/answer exchange. rw_answer_status ANSWERED implies rw_answer IS NOT NULL — enforced by application logic and by AI-10''s refusal contract.';

CREATE TABLE rw.rw_copilot_citations (
  rw_copilot_citation_id  bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_copilot_turn_id        uuid NOT NULL,
  rw_message_id              bigint NOT NULL,
  rw_similarity                real NOT NULL,
  rw_rank                      smallint NOT NULL,
  CONSTRAINT rw_copilot_citations_turn_fk FOREIGN KEY (rw_copilot_turn_id)
    REFERENCES rw.rw_copilot_turns (rw_copilot_turn_id) ON DELETE CASCADE,
  CONSTRAINT rw_copilot_citations_message_fk FOREIGN KEY (rw_message_id)
    REFERENCES rw.rw_messages (rw_message_id) ON DELETE CASCADE,
  CONSTRAINT rw_copilot_citations_turn_message_uk UNIQUE (rw_copilot_turn_id, rw_message_id),
  CONSTRAINT rw_copilot_citations_similarity_ck CHECK (rw_similarity BETWEEN -1 AND 1),
  CONSTRAINT rw_copilot_citations_rank_ck CHECK (rw_rank >= 1)
);
COMMENT ON TABLE rw.rw_copilot_citations IS 'One row per cited message — makes AI-12 (citation ⊆ authorized candidate set) checkable with a join, not by parsing (BR-17).';

CREATE TABLE rw.rw_copilot_usage (
  rw_copilot_usage_id      bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  rw_user_id                 uuid NOT NULL,
  rw_copilot_turn_id          uuid,
  rw_ai_provider_id            smallint NOT NULL,
  rw_model_code                 text NOT NULL,
  rw_operation                   text NOT NULL,
  rw_prompt_tokens                 integer NOT NULL DEFAULT 0,
  rw_completion_tokens             integer NOT NULL DEFAULT 0,
  rw_total_tokens                   integer NOT NULL DEFAULT 0,
  rw_latency_ms                      integer NOT NULL DEFAULT 0,
  rw_created_at                        timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT rw_copilot_usage_user_fk FOREIGN KEY (rw_user_id)
    REFERENCES rw.rw_users (rw_user_id) ON DELETE CASCADE,
  CONSTRAINT rw_copilot_usage_turn_fk FOREIGN KEY (rw_copilot_turn_id)
    REFERENCES rw.rw_copilot_turns (rw_copilot_turn_id) ON DELETE SET NULL,
  CONSTRAINT rw_copilot_usage_provider_fk FOREIGN KEY (rw_ai_provider_id)
    REFERENCES rw.rw_ai_providers (rw_ai_provider_id) ON DELETE RESTRICT,
  CONSTRAINT rw_copilot_usage_operation_ck CHECK (rw_operation IN ('CHAT', 'EMBEDDING')),
  CONSTRAINT rw_copilot_usage_prompt_tokens_ck CHECK (rw_prompt_tokens >= 0),
  CONSTRAINT rw_copilot_usage_completion_tokens_ck CHECK (rw_completion_tokens >= 0),
  CONSTRAINT rw_copilot_usage_total_tokens_ck CHECK (rw_total_tokens >= 0),
  CONSTRAINT rw_copilot_usage_latency_ck CHECK (rw_latency_ms >= 0)
);
COMMENT ON TABLE rw.rw_copilot_usage IS 'Token consumption ledger — source for OBJ-07 (accumulated copilot consumption per user). A ledger, not a counter: embedding calls have no turn (rw_copilot_turn_id IS NULL).';

RESET ROLE;
