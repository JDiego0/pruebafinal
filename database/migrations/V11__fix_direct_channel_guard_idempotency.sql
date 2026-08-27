-- V11__fix_direct_channel_guard_idempotency.sql
-- Phase 4 finding: rw_fn_guard_direct_channel_membership (V7) fires as a
-- BEFORE INSERT trigger, which runs BEFORE PostgreSQL evaluates
-- ON CONFLICT DO NOTHING — so re-running the seed loader (or any idempotent
-- re-insert of an ALREADY-EXISTING (channel_id, user_id) pair) hit the
-- "more than two members" guard unconditionally, because the trigger
-- counted existing rows without checking whether the incoming row was one
-- of them. Fixed by letting an exact-pair re-insert fall through to the
-- constraint's own conflict resolution; only a genuinely NEW third member
-- is blocked. CREATE OR REPLACE keeps the existing trigger binding intact —
-- no need to re-create rw_trg_channel_members_guard_direct itself.
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
    -- An exact re-insert of a pair that already exists is not a new member —
    -- it is the idempotent case (a retried request, or a re-run seed load).
    -- Let it fall through to ON CONFLICT DO NOTHING / the PK constraint
    -- instead of raising.
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

  RAISE EXCEPTION 'DIRECT channel membership cannot be changed once established (BR-07)' USING ERRCODE = '23514';
END;
$$;

RESET ROLE;
