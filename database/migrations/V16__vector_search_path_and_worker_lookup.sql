-- V16__vector_search_path_and_worker_lookup.sql
--
-- Three defects found by running the stack rather than reading it. See
-- DECISIONS.md D-22.

-- ---------------------------------------------------------------------------
-- 1. The runtime roles were pinned to `search_path = rw, pg_catalog`, which
--    excludes the schema pgvector installs into. Every statement that names the
--    `vector` type from the application — the RAG query cast, the embedding
--    write — failed with `type "vector" does not exist`.
--
--    The schema is resolved from the catalogue rather than hard-coded, so this
--    is also correct on a hosted PostgreSQL that puts extensions somewhere
--    other than `public` (Supabase uses `extensions`).
--
--    Runs as the bootstrap superuser: ALTER ROLE on another role is not
--    something rw_migrator (NOCREATEROLE) may do.
DO $do$
DECLARE
  v_vector_schema text;
BEGIN
  SELECT n.nspname INTO v_vector_schema
  FROM pg_extension e
  JOIN pg_namespace n ON n.oid = e.extnamespace
  WHERE e.extname = 'vector';

  IF v_vector_schema IS NULL THEN
    RAISE EXCEPTION 'pgvector is not installed; V1 should have created it';
  END IF;

  EXECUTE format('ALTER ROLE rw_app      SET search_path = rw, %I, pg_catalog', v_vector_schema);
  EXECUTE format('ALTER ROLE rw_migrator SET search_path = rw, %I, pg_catalog', v_vector_schema);
END
$do$;

SET ROLE rw_migrator;
SET search_path = rw, public;

-- ---------------------------------------------------------------------------
-- 2. The embedding worker runs on the scheduler, outside any request, so no
--    actor is bound to its connection. Everything else it needs is reachable
--    through the SECURITY DEFINER outbox functions in V4, but choosing which
--    embedding model to stamp on a vector was still a plain SELECT against
--    rw_embedding_models — a table whose RLS policy requires an actor. That
--    read returned zero rows and the outbox never drained.
--
--    Adding the lookup as a SECURITY DEFINER function keeps the worker
--    connected as rw_app (SEC-11 unchanged); it gains the id of a catalogue
--    row and nothing else.
CREATE FUNCTION rw.rw_fn_active_embedding_model_id(p_model_code text DEFAULT NULL)
RETURNS smallint
LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = rw, pg_catalog, pg_temp
AS $$
  SELECT rw_embedding_model_id
  FROM rw.rw_embedding_models
  WHERE rw_is_active
  ORDER BY (rw_model_code IS DISTINCT FROM p_model_code), rw_embedding_model_id
  LIMIT 1
$$;
COMMENT ON FUNCTION rw.rw_fn_active_embedding_model_id(text) IS
  'Catalogue lookup for the background embedding worker, which holds no actor. Returns the requested model when it is registered and active, otherwise the lowest active model id. Exposes no user content.';

REVOKE ALL ON FUNCTION rw.rw_fn_active_embedding_model_id(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION rw.rw_fn_active_embedding_model_id(text) TO rw_app;

-- ---------------------------------------------------------------------------
-- 3. rw_embedding_jobs was the one table in the schema left without row
--    security. It holds no bodies, only message ids, and rw_app has no grant on
--    it — but "every table in rw has RLS enabled and forced" is a property
--    worth asserting without an exception list (SEC-10, SEC-12), and TEST-11
--    now does exactly that.
--
--    No policy is added, so the table is deny-all for every ordinary role. The
--    outbox keeps working because the functions that drain it are SECURITY
--    DEFINER owned by rw_migrator, which holds BYPASSRLS and is therefore not
--    subject to row security at all, forced or not.
ALTER TABLE rw.rw_embedding_jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE rw.rw_embedding_jobs FORCE ROW LEVEL SECURITY;

RESET ROLE;
