-- V15__realtime_fanout.sql
-- Phase 6: one narrow function backing SSE fan-out. The notification
-- listener is a background system process with no authenticated actor
-- (same structural reason as the embedding worker's functions, V4) — it
-- needs to know WHICH connected users belong to a channel to decide who
-- receives a push, but that decision carries no content: the notification
-- payload is ids only (V7's pg_notify calls), and the client must still
-- fetch the actual message through the normal RLS-protected REST endpoint
-- regardless. A bug here can misroute a "new message" ping; it cannot leak
-- a message body, because this function never returns one.
SET ROLE rw_migrator;

CREATE FUNCTION rw.rw_fn_list_channel_member_ids(p_channel_id uuid) RETURNS TABLE (rw_user_id uuid)
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  SELECT rw_user_id FROM rw.rw_channel_members WHERE rw_channel_id = p_channel_id
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_list_channel_member_ids(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw.rw_fn_list_channel_member_ids(uuid) TO rw_app;
COMMENT ON FUNCTION rw.rw_fn_list_channel_member_ids IS
  'Realtime fan-out only (RT-06) — never exposed by any endpoint. Returns ids only, never message content; the client still fetches content through the normal RLS-protected path.';

RESET ROLE;
