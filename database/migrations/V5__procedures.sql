-- V5__procedures.sql
-- The two mandated stored PROCEDURES (OBJ-01, OBJ-02, DB-14). PostgreSQL
-- procedures return result sets only via REFCURSOR OUT parameters; JDBC's
-- CallableStatement supports this natively when the call runs inside an
-- explicit transaction (which RlsTransactionRunner always provides).
SET ROLE rw_migrator;

-- ================================================================ OBJ-01 ----
-- User directory query. SECURITY INVOKER (the default for procedures too):
-- the SELECT inside is governed by rw_users' own RLS SELECT policy (any
-- authenticated active user may read the directory) — no elevation needed.
CREATE PROCEDURE rw.rw_sp_query_users(
  IN p_search text,
  IN p_limit integer,
  IN p_cursor_display_name text,
  IN p_cursor_user_id uuid,
  INOUT p_cursor refcursor DEFAULT 'rw_sp_query_users_cursor'
)
LANGUAGE plpgsql
AS $$
BEGIN
  -- p_search is bound as a parameter value into a static query text (via ||
  -- on the VALUE, not by building the query string itself) — this is not the
  -- dynamic-SQL string concatenation MSG-03 forbids; no EXECUTE is involved
  -- and the query plan never changes shape based on input.
  OPEN p_cursor FOR
    SELECT u.rw_user_id, u.rw_display_name, u.rw_email, u.rw_avatar_url,
           r.rw_role_code, u.rw_locale
    FROM rw.rw_users u
    JOIN rw.rw_roles r ON r.rw_role_id = u.rw_role_id
    WHERE u.rw_is_active
      AND (p_search IS NULL OR p_search = '' OR
           u.rw_display_name ILIKE '%' || p_search || '%' OR
           u.rw_email ILIKE '%' || p_search || '%')
      AND (p_cursor_display_name IS NULL OR (u.rw_display_name, u.rw_user_id) > (p_cursor_display_name, p_cursor_user_id))
    ORDER BY u.rw_display_name, u.rw_user_id
    LIMIT LEAST(GREATEST(coalesce(p_limit, 50), 1), 100);
END;
$$;
COMMENT ON PROCEDURE rw.rw_sp_query_users IS
  'OBJ-01. Call via CALL rw.rw_sp_query_users(:search, :limit, :cursorName, :cursorId, NULL) then FETCH from the returned refcursor, inside one transaction.';

-- ================================================================ OBJ-02 ----
-- User edit / (logical) delete. SECURITY DEFINER: role changes and
-- deactivation of ANOTHER user require admin capability that ordinary RLS
-- (self-row-only UPDATE) does not grant. The procedure re-implements that
-- authorization check itself rather than relying on RLS, exactly like the
-- SECURITY DEFINER functions in V4. IBR-01 still holds: this is
-- administrative capability over the directory, not a path to message
-- content — it touches rw_users only.
CREATE PROCEDURE rw.rw_sp_modify_user(
  IN p_target_user_id uuid,
  IN p_display_name text DEFAULT NULL,
  IN p_locale text DEFAULT NULL,
  IN p_avatar_url text DEFAULT NULL,
  IN p_role_code text DEFAULT NULL,
  IN p_deactivate boolean DEFAULT NULL
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
DECLARE
  v_actor uuid := rw.rw_fn_current_user_id();
  v_actor_is_admin boolean;
  v_new_role_id smallint;
BEGIN
  IF v_actor IS NULL THEN
    RAISE EXCEPTION 'not authenticated' USING ERRCODE = '28000';
  END IF;

  SELECT (r.rw_role_code = 'ADMIN') INTO v_actor_is_admin
  FROM rw.rw_users u JOIN rw.rw_roles r ON r.rw_role_id = u.rw_role_id
  WHERE u.rw_user_id = v_actor;

  IF v_actor <> p_target_user_id AND NOT coalesce(v_actor_is_admin, false) THEN
    RAISE EXCEPTION 'only an administrator may modify another user' USING ERRCODE = '42501';
  END IF;

  IF (p_role_code IS NOT NULL OR p_deactivate IS TRUE) AND NOT coalesce(v_actor_is_admin, false) THEN
    RAISE EXCEPTION 'only an administrator may change a role or deactivate an account, including your own' USING ERRCODE = '42501';
  END IF;

  IF p_role_code IS NOT NULL THEN
    SELECT rw_role_id INTO v_new_role_id FROM rw.rw_roles WHERE rw_role_code = p_role_code;
    IF v_new_role_id IS NULL THEN
      RAISE EXCEPTION 'unknown role code %', p_role_code USING ERRCODE = '22023';
    END IF;
  END IF;

  UPDATE rw.rw_users SET
    rw_display_name = coalesce(p_display_name, rw_display_name),
    rw_locale        = coalesce(p_locale, rw_locale),
    rw_avatar_url    = coalesce(p_avatar_url, rw_avatar_url),
    rw_role_id       = coalesce(v_new_role_id, rw_role_id),
    rw_is_active     = CASE WHEN p_deactivate IS TRUE THEN false ELSE rw_is_active END,
    rw_updated_at    = now()
  WHERE rw_user_id = p_target_user_id;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'user % not found', p_target_user_id USING ERRCODE = 'P0002';
  END IF;
END;
$$;
REVOKE ALL ON PROCEDURE rw.rw_sp_modify_user(uuid, text, text, text, text, boolean) FROM PUBLIC;
COMMENT ON PROCEDURE rw.rw_sp_modify_user IS
  'OBJ-02. "Delete" is always logical (rw_is_active = false, BR-12) — there is no physical-delete path for users, matching the ON DELETE RESTRICT from rw_messages.rw_sender_id.';

RESET ROLE;
