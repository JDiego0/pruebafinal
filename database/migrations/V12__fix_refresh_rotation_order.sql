-- V12__fix_refresh_rotation_order.sql
-- Phase 4 finding: rw_fn_rotate_refresh_token (V4) inserted the successor
-- token BEFORE revoking the presented one. For the instant between those two
-- statements, two "live" rows (rw_revoked_at IS NULL) existed in the same
-- family — which PU-1 (rw_ux_refresh_tokens_family_active) exists
-- specifically to make impossible, so the INSERT itself failed with a
-- unique-violation on every single rotation. Fixed by revoking first, then
-- inserting, then linking rw_replaced_by_token_id once the new id is known —
-- three statements instead of two, but never a window with two live rows.
-- Caught only by exercising POST /auth/refresh against a live database; a
-- SQL-only review would not have surfaced a constraint-timing bug like this.
SET ROLE rw_migrator;

CREATE OR REPLACE FUNCTION rw.rw_fn_rotate_refresh_token(
  p_presented_token_hash bytea, p_new_token_hash bytea, p_new_expires_at timestamptz,
  p_user_agent text DEFAULT NULL, p_ip inet DEFAULT NULL
) RETURNS TABLE (
  rw_status text, rw_user_id uuid, rw_family_id uuid, rw_new_refresh_token_id uuid, rw_expires_at timestamptz
)
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_row rw.rw_refresh_tokens%ROWTYPE;
  v_new_id uuid;
BEGIN
  SELECT * INTO v_row FROM rw.rw_refresh_tokens WHERE rw_token_hash = p_presented_token_hash;

  IF NOT FOUND THEN
    RETURN QUERY SELECT 'NOT_FOUND', NULL::uuid, NULL::uuid, NULL::uuid, NULL::timestamptz;
    RETURN;
  END IF;

  IF v_row.rw_revoked_at IS NOT NULL THEN
    -- AUTH-06: replaying an already-rotated (or already-revoked) token means
    -- the token was stolen. Burn the whole family, not just this token.
    -- Column qualified (rw.rw_refresh_tokens.rw_family_id): this function's
    -- own RETURNS TABLE declares an OUT parameter also named rw_family_id,
    -- which PL/pgSQL would otherwise treat as ambiguous against the table
    -- column — the same class of bug fixed in rw_fn_open_direct_channel
    -- (V4), caught here the same way: by running the actual reuse-detection
    -- path against a live database, not by reading the SQL.
    UPDATE rw.rw_refresh_tokens
       SET rw_revoked_at = now(), rw_revoked_reason = 'REUSE_DETECTED'
     WHERE rw.rw_refresh_tokens.rw_family_id = v_row.rw_family_id AND rw_revoked_at IS NULL;
    RETURN QUERY SELECT 'REUSE_DETECTED', v_row.rw_user_id, v_row.rw_family_id, NULL::uuid, NULL::timestamptz;
    RETURN;
  END IF;

  IF v_row.rw_expires_at <= now() THEN
    UPDATE rw.rw_refresh_tokens SET rw_revoked_at = now(), rw_revoked_reason = 'EXPIRED' WHERE rw_refresh_token_id = v_row.rw_refresh_token_id;
    RETURN QUERY SELECT 'EXPIRED', v_row.rw_user_id, v_row.rw_family_id, NULL::uuid, NULL::timestamptz;
    RETURN;
  END IF;

  -- Revoke the presented token FIRST, so PU-1's "at most one live token per
  -- family" partial unique index never sees two live rows at once.
  UPDATE rw.rw_refresh_tokens
     SET rw_revoked_at = now(), rw_revoked_reason = 'ROTATED'
   WHERE rw_refresh_token_id = v_row.rw_refresh_token_id;

  INSERT INTO rw.rw_refresh_tokens (rw_user_id, rw_family_id, rw_token_hash, rw_expires_at, rw_user_agent, rw_ip_address)
  VALUES (v_row.rw_user_id, v_row.rw_family_id, p_new_token_hash, p_new_expires_at, p_user_agent, p_ip)
  RETURNING rw_refresh_token_id INTO v_new_id;

  UPDATE rw.rw_refresh_tokens SET rw_replaced_by_token_id = v_new_id WHERE rw_refresh_token_id = v_row.rw_refresh_token_id;

  RETURN QUERY SELECT 'ROTATED', v_row.rw_user_id, v_row.rw_family_id, v_new_id, p_new_expires_at;
END;
$$;

RESET ROLE;
