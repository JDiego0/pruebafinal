-- V14__fix_direct_channel_guard_scope.sql
-- Phase 5 finding: rw_fn_guard_direct_channel_membership (V7, patched in
-- V11) blocked EVERY UPDATE to a DIRECT channel's membership row, including
-- a participant setting their own read cursor or mute flag — entirely
-- legitimate, harmless personal preferences that have nothing to do with
-- BR-07's actual intent ("no membership changes", i.e. who is in the
-- channel). This made PUT /channels/{id}/read-cursor unusable for any DM.
-- Fixed by narrowing the UPDATE branch to only block an actual ROLE change
-- (the one thing that meaningfully alters "membership" for a fixed
-- two-participant channel); read-cursor and mute updates now pass through.
SET ROLE rw_migrator;

CREATE OR REPLACE FUNCTION rw.rw_fn_guard_direct_channel_membership() RETURNS trigger
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
    IF EXISTS (
      SELECT 1 FROM rw.rw_channel_members
      WHERE rw_channel_id = NEW.rw_channel_id AND rw_user_id = NEW.rw_user_id
    ) THEN
      RETURN NEW;
    END IF;

    SELECT count(*) INTO v_existing_count FROM rw.rw_channel_members WHERE rw_channel_id = NEW.rw_channel_id;
    IF v_existing_count >= 2 THEN
      RAISE EXCEPTION 'a DIRECT channel may never have more than two members (BR-07)' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  IF TG_OP = 'UPDATE' THEN
    -- Only a role change touches "membership" in the BR-07 sense. A
    -- participant's own read cursor or mute preference is personal state,
    -- not membership composition, and must remain freely updatable.
    IF NEW.rw_channel_member_role_id IS DISTINCT FROM OLD.rw_channel_member_role_id THEN
      RAISE EXCEPTION 'DIRECT channel membership cannot be changed once established (BR-07)' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
  END IF;

  -- TG_OP = 'DELETE'
  RAISE EXCEPTION 'DIRECT channel membership cannot be changed once established (BR-07)' USING ERRCODE = '23514';
END;
$$;

RESET ROLE;
