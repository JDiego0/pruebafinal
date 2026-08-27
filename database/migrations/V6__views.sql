-- V6__views.sql
-- OBJ-03: the authenticated user's conversations. A plain view (SECURITY
-- INVOKER is the only mode views support) — every table it reads is
-- RLS-protected for whichever role queries it, and the explicit join
-- predicate below (cm.rw_user_id = current actor) means an unjoined PUBLIC
-- channel a member merely COULD see via discovery never appears here: this
-- view is "my conversations", not "channels visible to me" (docs/API.md §4).
SET ROLE rw_migrator;

CREATE VIEW rw.rw_v_user_conversations AS
SELECT
  c.rw_channel_id,
  ct.rw_channel_type_code                    AS rw_channel_type,
  c.rw_name,
  c.rw_slug,
  c.rw_topic,
  c.rw_is_archived,
  cmr.rw_channel_member_role_code            AS rw_member_role,
  cm.rw_last_read_message_id,
  cm.rw_is_muted,
  cm.rw_joined_at,
  lm.rw_message_id                           AS rw_last_message_id,
  lm.rw_body                                 AS rw_last_message_preview,
  lm.rw_sender_id                            AS rw_last_message_sender_id,
  su.rw_display_name                         AS rw_last_message_sender_display_name,
  lm.rw_created_at                           AS rw_last_message_at,
  lm.rw_is_deleted                           AS rw_last_message_is_deleted,
  (
    SELECT count(*) FROM rw.rw_messages um
    WHERE um.rw_channel_id = c.rw_channel_id
      AND NOT um.rw_is_deleted
      AND (cm.rw_last_read_message_id IS NULL OR um.rw_message_id > cm.rw_last_read_message_id)
  )                                           AS rw_unread_count,
  CASE WHEN ct.rw_channel_type_code = 'DIRECT' THEN cp_user.rw_user_id END        AS rw_counterpart_user_id,
  CASE WHEN ct.rw_channel_type_code = 'DIRECT' THEN cp_user.rw_display_name END   AS rw_counterpart_display_name,
  CASE WHEN ct.rw_channel_type_code = 'DIRECT' THEN cp_user.rw_avatar_url END     AS rw_counterpart_avatar_url
FROM rw.rw_channels c
JOIN rw.rw_channel_members cm
  ON cm.rw_channel_id = c.rw_channel_id AND cm.rw_user_id = rw.rw_fn_current_user_id()
JOIN rw.rw_channel_types ct ON ct.rw_channel_type_id = c.rw_channel_type_id
JOIN rw.rw_channel_member_roles cmr ON cmr.rw_channel_member_role_id = cm.rw_channel_member_role_id
LEFT JOIN LATERAL (
  SELECT m2.rw_message_id, m2.rw_body, m2.rw_sender_id, m2.rw_created_at, m2.rw_is_deleted
  FROM rw.rw_messages m2
  WHERE m2.rw_channel_id = c.rw_channel_id
  ORDER BY m2.rw_created_at DESC, m2.rw_message_id DESC
  LIMIT 1
) lm ON true
LEFT JOIN rw.rw_users su ON su.rw_user_id = lm.rw_sender_id
LEFT JOIN rw.rw_channel_members cpm
  ON ct.rw_channel_type_code = 'DIRECT'
 AND cpm.rw_channel_id = c.rw_channel_id
 AND cpm.rw_user_id <> rw.rw_fn_current_user_id()
LEFT JOIN rw.rw_users cp_user ON cp_user.rw_user_id = cpm.rw_user_id;

COMMENT ON VIEW rw.rw_v_user_conversations IS
  'OBJ-03. Scoped to the current actor by the inner JOIN on rw_channel_members (not by RLS alone): a discoverable-but-unjoined PUBLIC channel never appears, only channels the actor has actually joined.';

RESET ROLE;
