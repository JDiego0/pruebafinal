-- V13__add_channel_member.sql
-- Phase 5 finding: the only INSERT path into rw_channel_members was
-- self-join to a non-archived PUBLIC channel (V8's
-- rw_channel_members_insert_self_join policy). There was no way for an
-- OWNER/MODERATOR to add someone else to a PRIVATE channel at all — a real
-- functional gap for a private-channel product, not just a missing test.
-- SECURITY DEFINER for the same reason as rw_fn_remove_channel_member: it
-- inserts a row for someone OTHER than the caller, which the ordinary RLS
-- INSERT policy (self-only) cannot satisfy — so it re-implements the
-- authorization check itself instead of relying on RLS.
SET ROLE rw_migrator;

CREATE FUNCTION rw.rw_fn_add_channel_member(
  p_channel_id uuid, p_target_user_id uuid, p_role_code text DEFAULT 'MEMBER'
) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_role_id smallint;
  v_found integer;
BEGIN
  IF NOT rw.rw_fn_is_channel_owner_or_moderator(p_channel_id) THEN
    RAISE EXCEPTION 'only an owner or moderator may add a member' USING ERRCODE = '42501';
  END IF;

  SELECT rw_channel_member_role_id INTO v_role_id
  FROM rw.rw_channel_member_roles WHERE rw_channel_member_role_code = p_role_code;
  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'unknown channel member role %', p_role_code USING ERRCODE = '22023';
  END IF;

  INSERT INTO rw.rw_channel_members (rw_channel_id, rw_user_id, rw_channel_member_role_id)
  VALUES (p_channel_id, p_target_user_id, v_role_id)
  ON CONFLICT (rw_channel_id, rw_user_id) DO NOTHING;
  -- BR-07 (DIRECT immutability) still applies: the guard trigger (V7) fires
  -- regardless of the executing role and blocks this for a DIRECT channel.

  GET DIAGNOSTICS v_found = ROW_COUNT;
  RETURN v_found > 0;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_add_channel_member(uuid, uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw.rw_fn_add_channel_member(uuid, uuid, text) TO rw_app;
COMMENT ON FUNCTION rw.rw_fn_add_channel_member IS
  'Owner/moderator invites another user to a PUBLIC or PRIVATE channel. Self-join (any member, PUBLIC only) still goes through rw_fn_join_channel and ordinary RLS.';

RESET ROLE;
