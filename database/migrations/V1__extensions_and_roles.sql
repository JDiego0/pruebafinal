-- V1__extensions_and_roles.sql
-- Extensions, schema and the two-role security boundary (D-07).
--
-- This migration connects as the bootstrap superuser (POSTGRES_USER — typically
-- `postgres`), which is the only role permitted to CREATE ROLE. It creates the
-- application schema and the two runtime roles, then hands schema ownership to
-- rw_migrator. Every later migration issues `SET ROLE rw_migrator;` as its first
-- statement, so all subsequent objects are owned by rw_migrator, not by the
-- bootstrap superuser — this is what makes `FORCE ROW LEVEL SECURITY` meaningful.
--
-- Passwords are Flyway placeholders (${...}), resolved at migration time from
-- environment variables. No secret literal is ever written to this file (REPO-01).

-- ---------------------------------------------------------------- extensions --
-- gen_random_uuid() has been built into PostgreSQL core since v13; no extension
-- needed for it. pgcrypto supplies digest()/hashing helpers used by later
-- migrations; vector supplies pgvector (DB-01).
CREATE EXTENSION IF NOT EXISTS pgcrypto;
CREATE EXTENSION IF NOT EXISTS vector;

-- -------------------------------------------------------------------- schema --
CREATE SCHEMA IF NOT EXISTS rw;

-- ---------------------------------------------------------------------- roles --
-- rw_migrator: owns every object in the rw schema. BYPASSRLS so it can seed and
-- migrate data while RLS is FORCEd on every table. Used ONLY by the `migrate`
-- compose service and the seed loader; never given to the backend (D-07).
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'rw_migrator') THEN
    CREATE ROLE rw_migrator
      LOGIN
      PASSWORD '${rwMigratorPassword}'
      NOSUPERUSER
      NOCREATEDB
      NOCREATEROLE
      NOREPLICATION
      BYPASSRLS
      CONNECTION LIMIT 5;
  END IF;
END
$do$;

-- rw_app: the ONLY role the running backend ever authenticates as. No BYPASSRLS,
-- no SUPERUSER. DML grants are added explicitly in V9__grants.sql, and DELETE is
-- never among them on any table (defence in depth for MSG-01, alongside D-13's
-- trigger and the absence of a DELETE policy).
DO $do$
BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_catalog.pg_roles WHERE rolname = 'rw_app') THEN
    CREATE ROLE rw_app
      LOGIN
      PASSWORD '${rwAppPassword}'
      NOSUPERUSER
      NOCREATEDB
      NOCREATEROLE
      NOREPLICATION
      NOBYPASSRLS
      CONNECTION LIMIT 50;
  END IF;
END
$do$;

ALTER SCHEMA rw OWNER TO rw_migrator;

-- USAGE on the schema is granted to rw_app here (a schema-level grant, not a
-- table-level DML grant — those come once the tables exist, in V9).
GRANT USAGE ON SCHEMA rw TO rw_app;

-- Every session using rw_app should default to the rw schema without relying on
-- an application-supplied search_path.
ALTER ROLE rw_app SET search_path = rw, pg_catalog;
ALTER ROLE rw_migrator SET search_path = rw, pg_catalog;
