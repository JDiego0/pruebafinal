# DEPLOYMENT.md — Deployment Guide

This project is built to run locally via Docker Compose, and to be seamlessly deployable to Supabase (the assessment target).

## 1. Local Deployment (Docker Compose)

The local environment matches the production architecture exactly: it runs a real PostgreSQL 16 database with `pgvector`, applies migrations via Flyway, and runs the Java backend and React frontend in separate containers.

### Requirements
- Docker and Docker Compose
- No external credentials required (AI chat will gracefully degrade to `PROVIDER_UNAVAILABLE` without an API key).

### Steps
1. Clone the repository.
2. `cp .env.example .env`
3. Edit `.env` to set local passwords (e.g., `POSTGRES_PASSWORD`, `RW_MIGRATOR_PASSWORD`, `RW_APP_PASSWORD`, `JWT_SECRET`).
4. Run `docker compose up --build -d`.

### Startup Sequence (Healthchecks)
1. `db` (PostgreSQL) starts.
2. `migrate` (Flyway container) starts, waiting for `db` to be healthy. It applies V1 through V16 and exits.
3. `backend` starts, waiting for `migrate` to finish successfully. It connects as `rw_app`.
4. `frontend` (Nginx) starts, waiting for `backend` to answer `/api/v1/health`.

## 2. Supabase Deployment

The assessment specifies Supabase as a target. Supabase is essentially a managed PostgreSQL database. Because we built our authorization model directly in PostgreSQL using standard SQL and RLS—rather than relying on Supabase-specific features (like GoTrue or Supabase Realtime)—deploying to Supabase is just a matter of running our migrations against it.

### Steps

1. Create a new Supabase project.
2. In the Supabase dashboard, go to **Database → Extensions** and enable `vector`.
3. Go to **Database → Connection Pooling** and copy the connection string.
4. Set `SUPABASE_DB_URL` and `SUPABASE_DB_PASSWORD` in your environment.
5. Apply migrations to Supabase using Flyway locally:
   ```bash
   # Note: Supabase provides the `postgres` superuser by default.
   docker run --rm -v $(pwd)/database/migrations:/flyway/sql flyway/flyway:10.15 \
     -url="jdbc:postgresql://<SUPABASE_HOST>:6543/postgres?sslmode=require" \
     -user="postgres" \
     -password="<SUPABASE_PASSWORD>" \
     -placeholders.rwMigratorPassword="<MIGRATOR_PASS>" \
     -placeholders.rwAppPassword="<APP_PASS>" \
     migrate
   ```
6. The Supabase database now has the `rw` schema, `rw_migrator`, `rw_app`, and all RLS policies configured correctly.
7. Deploy the backend (e.g., to AWS ECS, Render, or Heroku) and point `POSTGRES_HOST`, `RW_APP_USER`, and `RW_APP_PASSWORD` to the Supabase database.
8. Deploy the frontend (e.g., to Vercel or Netlify) pointing `VITE_API_BASE_URL` to the deployed backend.

### Why not Supabase Auth and Realtime?

As documented in `DECISIONS.md` (D-05), we implemented our own JWT authentication and SSE realtime fan-out.
- Using Supabase Auth would require Supabase-issued JWTs and an anon key in the browser, splitting the auth model in two.
- Using Supabase Realtime bypasses our RlsTransactionRunner and requires complex setup for Row Level Security matching.
By building the core logic natively in PostgreSQL and the Backend, the system remains completely portable. Supabase is used purely as a robust, managed PostgreSQL 16 + pgvector provider.
