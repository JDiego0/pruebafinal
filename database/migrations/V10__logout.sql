-- V10__logout.sql
-- Phase 4 finding: logout needs to revoke a refresh token's whole family by
-- presenting its hash, WITHOUT rotating (unlike /auth/refresh, which both
-- authenticates AND issues a successor). rw_fn_rotate_refresh_token always
-- creates a new token, which is wrong for a logout — this narrow function
-- fills that gap. Added as a new migration rather than editing the
-- already-applied V4, per CLAUDE.md's "never rewrite an applied migration"
-- convention (mirrors real Flyway checksum behavior).
SET ROLE rw_migrator;

CREATE FUNCTION rw.rw_fn_revoke_by_token_hash(p_token_hash bytea, p_reason text) RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_family_id uuid;
BEGIN
  SELECT rw_family_id INTO v_family_id FROM rw.rw_refresh_tokens WHERE rw_token_hash = p_token_hash;
  IF v_family_id IS NULL THEN
    RETURN false;
  END IF;

  UPDATE rw.rw_refresh_tokens
     SET rw_revoked_at = now(), rw_revoked_reason = p_reason
   WHERE rw_family_id = v_family_id AND rw_revoked_at IS NULL;
  RETURN true;
END;
$$;
REVOKE ALL ON FUNCTION rw.rw_fn_revoke_by_token_hash(bytea, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw.rw_fn_revoke_by_token_hash(bytea, text) TO rw_app;
COMMENT ON FUNCTION rw.rw_fn_revoke_by_token_hash IS
  'AUTH-07 (logout): authorizes by token possession (its hash), exactly like rotation — never by a trusted user id. Idempotent: revoking an already-revoked or unknown token is a no-op that returns false rather than an error.';

RESET ROLE;
