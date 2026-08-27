# Riwi Messaging Platform

Internal messaging platform for **Riwi Co. S.A.S.** — channels and direct messages, realtime
delivery, full-text search, and an AI copilot that answers questions over the conversations
**you are allowed to read, and only those**.

The defining property of this system is that authorization lives in PostgreSQL. Every query that
touches user content runs through Row-Level Security under an actor taken from the verified JWT.
The backend cannot see a message the database refuses to return — including the vector search that
feeds the AI.

---

## 1. Architecture overview

```
                      ┌───────────────────────────────────────────┐
  Browser  ──────────►│  frontend  React 19 + Vite + Tailwind v4   │
                      │  nginx serves the bundle and proxies /api  │
                      └──────────────────┬────────────────────────┘
                                         │  HTTPS/JSON + SSE
                      ┌──────────────────▼────────────────────────┐
                      │  backend  Spring Boot 3.3 (Java 21)        │
                      │                                            │
                      │  presentation ─► application ─► domain      │
                      │                         ▲                  │
                      │              infrastructure                │
                      │                                            │
                      │  connects ONLY as rw_app                   │
                      │  (NOSUPERUSER, NOBYPASSRLS)                │
                      └──────────────────┬────────────────────────┘
                                         │  JDBC, one transaction per request,
                                         │  app.current_user_id bound as a parameter
                      ┌──────────────────▼────────────────────────┐
                      │  PostgreSQL 16 + pgvector                  │
                      │  RLS enabled AND forced on every table      │
                      │  functions · procedures · views · triggers  │
                      └────────────────────────────────────────────┘
```

**Layer rule.** `domain` is plain Java — no Spring, JPA, JDBC, servlet or Jackson import reaches it.
`application` holds thin use cases (validate → invoke port → map → return). `infrastructure`
implements the domain ports. `presentation` holds controllers and DTOs. ArchUnit enforces this in
`ArchitectureTest`; a violation fails the build.

**Two database roles.** `rw_migrator` owns the schema and holds `BYPASSRLS`; it is used by the
`migrate` and `seed` services only. `rw_app` is the sole identity the running application uses, and
it is `NOSUPERUSER NOBYPASSRLS` with no `DELETE` grant anywhere.

---

## 2. Prerequisites

| Software | Version | Needed for |
|----------|---------|-----------|
| Docker Engine | 25+ (tested on 29.2) | the whole stack, and the test suite's throwaway database |
| Docker Compose | v2+ (tested on v5.0) | `docker compose up` |
| JDK | **21** (Temurin) | building or running the backend outside Docker |
| Node.js | 22+ (tested on 24) | building or running the frontend outside Docker |
| PostgreSQL client (`psql`) | 16 | optional, for inspecting the database by hand |

Docker alone is enough to run everything. JDK 21 and Node are only needed for local development
and for `./mvnw test`.

> On Windows, run the commands below from Git Bash or WSL. PowerShell works too, but the
> `export VAR=… command` form used here is Bash syntax.

---

## 3. Repository structure

```
.
├── backend/                    Spring Boot service
│   └── src/main/java/co/riwi/messaging/
│       ├── domain/             entities, value objects, errors, ports  (no framework imports)
│       ├── application/        use cases, one public execute() each
│       ├── infrastructure/     JDBC adapters, JWT, SSE, AI providers, config, seed loader
│       └── presentation/       controllers, DTOs, RFC 9457 problem details
├── database/
│   ├── migrations/             V1..V16, applied by Flyway
│   └── seeds/seed.json         demo corpus: 8 users, 6 channels, 46 messages
├── frontend/                   React 19 + Vite + Tailwind v4
├── docs/                       DATA_MODEL, SECURITY_MODEL, API, EVIDENCE, DEPLOYMENT
├── docker-compose.yml          db → migrate → seed → backend → frontend
├── .env.example                every environment variable, documented
├── CLAUDE.md                   working agreement and invariants
├── REQUIREMENTS.md             acceptance criteria
├── DECISIONS.md                why the system is shaped this way
└── PROGRESS.md                 what is done and verified
```

---

## 4. Quick start (Docker — the exact end-to-end procedure)

From a clean machine with Docker running:

```bash
git clone <repository-url>
cd mensajeria

cp .env.example .env            # optional: every value has a working default
docker compose up --build
```

That single command performs, in order:

1. **`db`** — starts `pgvector/pgvector:pg16`, creates `bd_juandiego_garcia_nakamoto`, waits until
   `pg_isready` passes.
2. **`migrate`** — runs Flyway over `database/migrations` as the bootstrap superuser (the only role
   permitted to `CREATE ROLE`), applying V1..V16. Exits on success.
3. **`seed`** — runs the backend image with the `seed` Spring profile as `rw_migrator`, loading
   `database/seeds/seed.json`. Idempotent; exits on success.
4. **`backend`** — starts the API as `rw_app` and waits for `/api/v1/health` to answer.
5. **`frontend`** — serves the built bundle on nginx and proxies `/api` to the backend.

When it settles:

| What | URL |
|------|-----|
| Web app | <http://localhost:8081> |
| API | <http://localhost:8080/api/v1> |
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/api/v1/openapi.json> |
| Health | <http://localhost:8080/api/v1/health> |
| PostgreSQL | `localhost:5432` |

**Sign in** with any seeded user. The password for all of them is `RiwiDemo#2026`.

| Email | Name | Role |
|-------|------|------|
| `ana.torres@riwi.co` | Ana Torres | ADMIN |
| `bruno.salas@riwi.co` | Bruno Salas | MEMBER |
| `carla.nino@riwi.co` | Carla Nino | MEMBER |
| `diego.mejia@riwi.co` | Diego Mejia | MEMBER |
| `elena.rios@riwi.co` | Elena Rios | MEMBER |
| `frank.walsh@riwi.co` | Frank Walsh | MEMBER |
| `grace.kim@riwi.co` | Grace Kim | MEMBER |
| `hugo.paredes@riwi.co` | Hugo Paredes | ADMIN |

The corpus contains `general` and `proyecto-atlas` (public), `finance-leadership` and
`incident-response` (private), and two direct conversations — enough that signing in as two
different users demonstrates the isolation rules directly in the UI.

To stop, and to wipe the database volume:

```bash
docker compose down          # stop
docker compose down -v       # stop and delete all data
```

---

## 5. Environment variables

Every variable lives in `.env.example`, already documented inline. The ones that matter most:

| Variable | Default | Meaning |
|----------|---------|---------|
| `POSTGRES_DB` | `bd_juandiego_garcia_nakamoto` | database name (fixed by the assessment) |
| `POSTGRES_USER` / `POSTGRES_PASSWORD` | `postgres` / `postgres` | bootstrap superuser, used only to create roles and migrate |
| `RW_MIGRATOR_PASSWORD` | `mig_pass` | password for the schema-owning role |
| `RW_APP_PASSWORD` | `app_pass` | password for the runtime role |
| `JWT_SECRET` | dev-only literal | base64 secret for HS512 access tokens — **replace in any real environment** |
| `JWT_ACCESS_TOKEN_TTL_SECONDS` | `900` | access-token lifetime (15 min) |
| `REFRESH_TOKEN_TTL_SECONDS` | `1209600` | refresh-token lifetime (14 days) |
| `COOKIE_SECURE` | `false` | set `true` when serving over HTTPS |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:8081` | browser origins allowed to call the API |
| `AI_ENABLED` | `false` | set `true` once a chat key is present |
| `AI_API_KEY` | empty | Groq API key — see §9 |
| `EMBEDDING_BASE_URL` | empty | empty means the built-in offline embedding provider |
| `EMBEDDING_MODEL_CODE` | `deterministic-1536` | must match a `rw_embedding_models.rw_model_code` row |
| `BACKEND_PORT` / `FRONTEND_PORT` / `POSTGRES_PORT_HOST` | `8080` / `8081` / `5432` | host ports |

Generate a real JWT secret with:

```bash
openssl rand -base64 32
```

Secrets are read from the environment only. There is no secret literal in any committed file, and
`.env` is git-ignored.

---

## 6. Configuring PostgreSQL yourself (without Docker)

Skip this if you use `docker compose`. To run against a PostgreSQL you manage:

1. **Version.** PostgreSQL **15 or newer**. The stack is developed and tested on 16.
2. **Extensions.** The migrations create them, so the connecting bootstrap user needs the right to
   do so (superuser, or `rds_superuser`-equivalent):
   ```sql
   CREATE EXTENSION IF NOT EXISTS pgcrypto;
   CREATE EXTENSION IF NOT EXISTS vector;   -- pgvector
   ```
   `gen_random_uuid()` is built into PostgreSQL 13+; no extension is needed for it.
   V16 discovers which schema `vector` was installed into and puts that schema on both runtime
   roles' `search_path`, so an installation under `extensions` (as on Supabase) works unchanged.
3. **Database.** Create it with the exact required name:
   ```sql
   CREATE DATABASE bd_juandiego_garcia_nakamoto;
   ```
4. **Roles.** Do *not* create them by hand — `V1__extensions_and_roles.sql` creates `rw_migrator`
   and `rw_app` with the correct attributes, from the passwords you pass as Flyway placeholders.
5. Point the backend at it with `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `RW_APP_PASSWORD`.

### Supabase

Supabase is an optional deployment target; nothing in local development depends on it.

1. Create a project at <https://supabase.com/dashboard>.
2. In **Database → Extensions**, enable `vector` and `pgcrypto`.
3. Copy the connection string from **Project Settings → Database → Connection string → URI**.
4. Run the migrations against it with the Flyway command in §7, substituting that URI. The
   connecting user must be able to `CREATE ROLE`; on Supabase use the `postgres` superuser.
5. Point the backend at the pooled connection string and set `RW_APP_PASSWORD` to whatever you
   passed as `-placeholders.rwAppPassword`.

Note that Supabase's own `auth` schema is unused here: this system authenticates against
`rw_user_credentials` with Argon2id and issues its own JWTs.

---

## 7. Running migrations by hand

Migrations are plain SQL under `database/migrations`, applied in order by Flyway. Two passwords are
supplied as placeholders so no secret is ever written into a migration file.

```bash
docker run --rm --network host \
  -v "$PWD/database/migrations:/flyway/sql" \
  flyway/flyway:10.15 \
  -url=jdbc:postgresql://localhost:5432/bd_juandiego_garcia_nakamoto \
  -user=postgres -password=postgres \
  -placeholders.rwMigratorPassword=mig_pass \
  -placeholders.rwAppPassword=app_pass \
  migrate
```

| Migration | Contents |
|-----------|----------|
| `V1` | extensions, `rw` schema, the two roles |
| `V2` | all tables — PK/FK with explicit `ON DELETE`, UNIQUE, NOT NULL, CHECK, `timestamptz` |
| `V3` | indexes, including the keyset and partial-unique ones |
| `V4` | functions (`rw_fn_*`) — history, search, RAG retrieval, send/edit/delete, auth, embeddings |
| `V5` | procedures (`rw_sp_query_users`, `rw_sp_modify_user`) |
| `V6` | views (`rw_v_user_conversations`) |
| `V7` | triggers — delete block, edit history, embedding enqueue, `updated_at` |
| `V8` | RLS policies |
| `V9` | grants for `rw_app` |
| `V10`–`V15` | logout, direct-channel guard fixes, refresh-rotation ordering, add-member, realtime fan-out |
| `V16` | `search_path` fix for the pgvector schema, worker model lookup, RLS forced on the outbox |

To verify afterwards:

```bash
psql -h localhost -U postgres -d bd_juandiego_garcia_nakamoto \
  -c "select version, description, success from flyway_schema_history order by installed_rank;"
```

---

## 8. Loading the seed corpus

`database/seeds/seed.json` holds the demo corpus. The loader is the backend itself under the `seed`
Spring profile, connecting as `rw_migrator`. Every insert uses `ON CONFLICT DO NOTHING`, so
re-running it is a safe no-op.

With Docker:

```bash
docker compose run --rm seed
```

Without Docker, from `backend/`:

```bash
export JAVA_HOME=/path/to/jdk-21
export POSTGRES_HOST=localhost POSTGRES_PORT=5432
export RW_MIGRATOR_PASSWORD=mig_pass RW_APP_PASSWORD=app_pass
export JWT_SECRET=$(openssl rand -base64 32)

./mvnw -DskipTests package
java -jar target/messaging-0.1.0.jar --spring.profiles.active=seed
```

Expect a log line per table and `Seeding complete.` The process exits by itself.

Verify:

```bash
psql -h localhost -U postgres -d bd_juandiego_garcia_nakamoto -c \
  "select (select count(*) from rw.rw_users) users,
          (select count(*) from rw.rw_channels) channels,
          (select count(*) from rw.rw_messages) messages;"
```

Expect 8 / 6 / 46.

---

## 9. Configuring Groq (the chat provider)

The copilot's chat model is Groq. Groq speaks the OpenAI-compatible protocol, so the same adapter
reaches OpenAI or Ollama by changing three variables and no code.

1. Sign in at <https://console.groq.com>.
2. Open **API Keys → Create API Key** and copy it (it is shown once). It starts with `gsk_`.
3. Put it in `.env` — never in source, never in a committed file:
   ```dotenv
   AI_ENABLED=true
   AI_PROVIDER_CODE=groq
   AI_BASE_URL=https://api.groq.com/openai/v1
   AI_MODEL=openai/gpt-oss-120b
   AI_API_KEY=gsk_your_key_here
   ```
4. Restart the backend: `docker compose up -d backend`.

Groq retires models regularly, and a retired id fails with HTTP 404 and status `PROVIDER_ERROR`.
List what your account can actually reach:

```bash
curl -s https://api.groq.com/openai/v1/models   -H "Authorization: Bearer $AI_API_KEY" | jq -r '.data[].id' | sort
```

Pick a general instruction-following model from that list and set `AI_MODEL` to it.

Without a key the copilot answers with status `PROVIDER_ERROR` and the UI says the provider is
unavailable. Everything else — messaging, search, realtime, embeddings, and every security
property including the full test suite — works exactly the same.

## 10. Configuring the embedding provider

Groq publishes no embedding models and no `/embeddings` endpoint, so embeddings are configured
**separately from chat**.

**Default (recommended for the assessment):** leave `EMBEDDING_BASE_URL` empty. The backend uses
`DeterministicLocalEmbeddingProvider` — offline, no credentials, stable 1536-dimension vectors, and
what the security suite runs against.

It is **feature hashing**, not a trained model: tokens are accent-folded, stop-worded, and hashed
into 1536 buckets, so cosine similarity measures shared vocabulary. That is a real signal and it is
enough for the copilot to retrieve and cite correctly — but it matches words, not meaning. A
question phrased entirely differently from the message it should find will miss. Configure a hosted
provider below for better recall. The isolation boundary is identical either way: what a user may
retrieve is decided by PostgreSQL, not by the embedder.

**Hosted alternative:**

```dotenv
EMBEDDING_BASE_URL=https://api.openai.com/v1
EMBEDDING_MODEL=text-embedding-3-small
EMBEDDING_API_KEY=sk_your_key_here
EMBEDDING_MODEL_CODE=text-embedding-3-small
```

Two constraints:

- The model **must** emit 1536 dimensions — that is the declared width of the `vector(1536)` column
  and a `CHECK` on `rw_embedding_models`. Changing it is a migration, not a setting.
- `EMBEDDING_MODEL_CODE` must match a row in `rw_embedding_models`; register the model there before
  switching, or the worker falls back to the lowest active model and stamps the wrong code.

**Changing embedding provider invalidates every stored vector**, and the outbox will not re-enqueue
on its own — `rw_content_hash` hashes the message body, which has not changed. Re-embed explicitly:

```sql
TRUNCATE rw.rw_message_embeddings;
INSERT INTO rw.rw_embedding_jobs (rw_message_id) SELECT rw_message_id FROM rw.rw_messages;
```

Vectors are produced by a background worker that drains `rw_embedding_jobs`, a transactional outbox
filled by a trigger whenever a message is created or its body changes. Check that it is keeping up:

```bash
psql -h localhost -U postgres -d bd_juandiego_garcia_nakamoto -c \
  "select count(*) filter (where rw_processed_at is null) as pending,
          (select count(*) from rw.rw_message_embeddings) as vectors
   from rw.rw_embedding_jobs;"
```

---

## 11. Running the backend outside Docker

```bash
docker compose up -d db                # or point at your own PostgreSQL
docker compose run --rm migrate
docker compose run --rm seed

cd backend
export JAVA_HOME=/path/to/jdk-21
export POSTGRES_HOST=localhost POSTGRES_PORT=5432
export RW_APP_PASSWORD=app_pass RW_MIGRATOR_PASSWORD=mig_pass
export JWT_SECRET=$(openssl rand -base64 32)
./mvnw spring-boot:run
```

The API comes up on <http://localhost:8080/api/v1>.

## 12. Running the frontend outside Docker

```bash
cd frontend
npm install
npm run dev
```

Vite serves <http://localhost:5173> and proxies `/api` to `http://localhost:8080`, so no API URL
needs configuring. `http://localhost:5173` is already in the default `CORS_ALLOWED_ORIGINS`.

Other scripts:

```bash
npm run build       # tsc -b && vite build
npm run lint        # eslint
npm run typecheck   # tsc -b --noEmit
```

---

## 13. API documentation

Swagger UI is served by the running backend at <http://localhost:8080/swagger-ui.html>, and the
raw spec at <http://localhost:8080/api/v1/openapi.json>. `docs/API.md` describes the same surface in
prose, with the error contract.

Every endpoint is under `/api/v1`. Errors are RFC 9457 problem details carrying a `code` and a
`correlationId` that matches the server log line.

| Area | Endpoints |
|------|-----------|
| Auth | `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout` |
| Profile | `GET /me`, `PATCH /me` |
| Directory | `GET /users` |
| Conversations | `GET /conversations` |
| Channels | `GET /channels`, `POST /channels`, `GET|PATCH /channels/{id}`, `POST /channels/direct` |
| Membership | `GET|POST /channels/{id}/members`, `DELETE /channels/{id}/members/{userId}`, `PUT /channels/{id}/read-cursor` |
| Messages | `GET|POST /channels/{id}/messages`, `PATCH /messages/{id}`, `DELETE /messages/{id}` |
| Search | `GET /search/messages` |
| Realtime | `POST /realtime/ticket`, `GET /realtime/stream?ticket=…` |
| Copilot | `POST /copilot/ask`, `GET /copilot/usage` |
| Health | `GET /health` |

A resource the caller may not see answers `404`, never `403` — a `403` would confirm that it
exists.

---

## 14. Tests

```bash
cd backend
./mvnw test
```

This runs both suites — 17 tests:

- **`ArchitectureTest`** (6) — ArchUnit. Proves `domain` imports no framework, and that the
  dependency direction `presentation → application → domain ← infrastructure` holds.
- **`RlsSecurityIT`** (11) — the PostgreSQL security suite, below.

### Database security tests

`RlsSecurityIT` runs against a **real PostgreSQL** — never an in-memory substitute. It starts a
throwaway `pgvector/pgvector:pg16` container through Testcontainers, applies the production
migrations to it, and connects the application as `rw_app` exactly as production does.

| Test | Property proven |
|------|-----------------|
| `nonMemberCannotReadPrivateChannelHistory` | a non-member gets `ResourceNotVisible` from the use case **and** zero rows from a direct `SELECT` on `rw_messages` |
| `thirdPartyCannotReadDirectChannel` | the two participants see the DM; a third user does not |
| `searchDoesNotLeakUnauthorizedMessages` | full-text search returns the private hit to the member and nothing to the outsider |
| `ragRetrievalIsRlsScoped` | two messages with identical text and identical vectors: the outsider's ANN search returns the public one and never the private one |
| `applicationRoleCannotBypassRls` | `rw_app` is `NOSUPERUSER`, `NOBYPASSRLS`, and is who the pool actually connects as |
| `unsetActorSeesNothing` | with no `app.current_user_id` bound, messages and channels are invisible |
| `physicalDeleteIsImpossible` | `DELETE` is refused by grant, there is no `DELETE` policy, and the blocking trigger exists |
| `authenticationWorks` | the right password issues access and refresh tokens; the wrong one raises `AuthenticationFailed` |
| `editDeleteAndKeysetPagination` | edit bumps the revision and writes history, delete is a tombstone with the row still present, keyset pages do not overlap |
| `rlsIsEnabledAndForcedEverywhere` | every table in the `rw` schema has RLS **enabled and forced** — no exception list |
| `ragRetrievalRanksByMeaningAndStillRefusesOutsiders` | a question that matches neither message verbatim retrieves both for the member and only the public one for the outsider |

Run just that suite:

```bash
./mvnw test -Dtest=RlsSecurityIT
```

If the JVM cannot reach the Docker socket, point the suite at a PostgreSQL you already have
instead — the migrations are applied to it the same way:

```bash
export TEST_POSTGRES_URL=jdbc:postgresql://localhost:5432/bd_juandiego_garcia_nakamoto
export TEST_POSTGRES_USER=postgres TEST_POSTGRES_PASSWORD=postgres
./mvnw test
```

Use a scratch database for this: the suite migrates whatever it is pointed at.

---

## 15. Authentication flow

```
POST /auth/login  { email, password }
   │  Argon2id verify against rw_user_credentials (SECURITY DEFINER lookup,
   │  constant work regardless of whether the email exists)
   ▼
   ├─ access token   HS512 JWT, 15 min, returned in the JSON body
   └─ refresh token  opaque 256-bit random, 14 days,
                     HttpOnly + SameSite=Strict cookie scoped to /api/v1/auth,
                     stored only as a SHA-256 hash

Every request:  Authorization: Bearer <access token>
   │  JwtAuthenticationFilter verifies signature, issuer, audience, expiry
   ▼
   AuthenticatedActor  ── the ONLY source of the actor id
   │
   ▼
   TransactionRunner.runAsActor(actor, work)
   │  BEGIN; set_config('app.current_user_id', ?, true)   ← bound parameter
   ▼
   RLS policies evaluate rw_fn_current_user_id()
```

`POST /auth/refresh` rotates: the presented token is consumed and a new one issued in the same
transaction. Replaying a consumed token revokes the entire token family — the standard response to
a stolen refresh token. `POST /auth/logout` revokes the presented token.

No endpoint, use case, or SQL function accepts a caller-supplied actor id. There is no parameter to
spoof: `rw_fn_send_message` takes a channel and a body, and always attributes the message to
`rw_fn_current_user_id()`.

---

## 16. Security model (RLS)

Full detail is in `docs/SECURITY_MODEL.md`. The essentials:

- **RLS is enabled and forced on every table in `rw`.** Forcing matters because the schema owner
  would otherwise bypass its own policies.
- **`app.current_user_id` is set per transaction** with `set_config(..., true)` — transaction-local,
  so a pooled connection can never carry one request's actor into the next. It is always a bound
  parameter; there is no SQL string concatenation anywhere in the codebase.
- **`rw_app` is `NOSUPERUSER NOBYPASSRLS`**, holds column-scoped `UPDATE` grants, and has **no
  `DELETE` grant on anything**.
- **Messages are never physically deleted.** No `DELETE` grant, no `DELETE` policy, and a
  `BEFORE DELETE` trigger that raises. Deletion sets `rw_is_deleted` and nulls the body; the row and
  its edit history survive.
- **Backend filtering is UX, never the control.** Every negative path is proven by asking the
  database directly as the unauthorized user, not by trusting the use case.
- **Not-visible reads answer `404`.** A `403` would leak the resource's existence.

---

## 17. Realtime

Realtime is genuine server push, not polling.

```
message INSERT ──► AFTER INSERT trigger ──► pg_notify('rw_channel_events', payload)
                                                    │
        PostgresNotificationListener  LISTEN ◄──────┘
                     │
                     ▼
                  SseHub ──► SseEmitter per connected user ──► EventSource in the browser
```

The browser cannot put an `Authorization` header on an `EventSource`, so the stream is opened with
a ticket instead of a bearer token:

```
POST /api/v1/realtime/ticket      (Bearer auth)  →  { ticket, expiresIn: 60 }
GET  /api/v1/realtime/stream?ticket=…            →  text/event-stream
```

The ticket is single-use, expires in 60 seconds, and is bound to the issuing user. **Membership is
re-evaluated at delivery time**, per event, from `rw_fn_list_channel_member_ids` — leaving a channel
stops the events immediately, without reconnecting. Events carry only identifiers and a
`Last-Event-ID` for resume; message bodies are fetched through the normal RLS-governed read path.

Verified live: with an SSE stream open as Bruno, a message posted by Ana arrived as a
`message.created` event with no polling involved.

---

## 18. RAG architecture

```
authenticated actor (from the JWT)
   │
   ├─ channel scope requested?  membership checked first → NO_PERMISSION
   │
   ├─ embed the question                     EmbeddingProvider (local by default)
   │
   ├─ retrieve candidates                    rw_fn_rag_retrieve_candidates
   │     SECURITY INVOKER, called inside runAsActor
   │     FROM clause already RLS-constrained, plus an explicit membership EXISTS
   │     ── this is the ONLY place the `<=>` operator appears in the codebase ──
   │
   ├─ no candidates?                         → INSUFFICIENT_CONTEXT, no LLM call
   │
   ├─ build context from the authorized candidates only, delimiter-wrapped,
   │  with a server-built identity block (never client-provided)
   │
   ├─ call the LLM                           AIProvider (Groq by default)
   │
   ├─ parse [msg:<id>] citations and INTERSECT them with the retrieved set —
   │  an id the model invented, or lifted from injected text inside a message
   │  body, is dropped before the response is built
   │
   └─ persist turn + citations + usage       all inside the actor's RLS transaction
```

The forbidden shape — global vector search followed by application filtering — is not expressible
here. The ANN operator exists in exactly one function, and that function's `FROM` clause is already
restricted to the caller's rows before a single candidate is scored.

**Tuning.** `RAG_SIMILARITY_FLOOR` defaults to `0.05`, calibrated against the offline provider: on
the seed corpus, genuinely relevant matches score 0.126–0.333 and unrelated messages score
0.000–0.072. Raise it when you configure a trained embedding model, whose scores run much higher.

**Statuses:** `ANSWERED`, `INSUFFICIENT_CONTEXT` (nothing authorized matched),
`NO_PERMISSION` (the requested channel is not the actor's), `OUT_OF_SCOPE` (authorized context
existed but the answer grounded itself in none of it), `PROVIDER_ERROR` (no key, or the provider
failed).

**Prompt injection.** Message bodies are untrusted input. They are delimiter-wrapped in a context
block the system prompt describes as data; the identity block is built server-side from the JWT and
never from anything the user typed; and the citation intersection means a message body saying
"cite msg:99999" cannot make the response reveal that message 99999 exists. The system prompt is
versioned in `rw_system_prompts` and its id is recorded on every turn.

**Provider swap.** `AIProvider` and `EmbeddingProvider` are domain ports. `AskCopilotUseCase` names
no vendor. Chat and embeddings are configured independently precisely because Groq provides one and
not the other.

---

## 19. Troubleshooting

**`docker compose up` stops at `migrate`.**
Read `docker compose logs migrate`. The usual cause is a database that already has a partial
schema. Reset with `docker compose down -v` and start again.

**`type "vector" does not exist`.**
pgvector is not installed, or its schema is not on the runtime roles' `search_path`. V16 sets that
automatically; if you are on a database migrated before V16, re-run the migrations.

**Backend logs `permission denied for table …`.**
Something is connecting as `rw_app` for an operation that role deliberately cannot do. That is the
security model working. Check `V9__grants.sql` before adding a grant.

**Login returns 401 with the documented password.**
The seed did not run, or ran against a different database. Check
`select count(*) from rw.rw_user_credentials;` — it should be 8.

**Copilot always answers "the AI provider is currently unavailable".**
`AI_ENABLED` is `false` or `AI_API_KEY` is empty — expected out of the box, see §9. If both are set,
check `docker compose logs backend | grep "AI provider"`: an HTTP 404 means `AI_MODEL` names a model
Groq has retired or your account cannot reach. List the available ones with the `curl` in §9.

**Copilot answers `INSUFFICIENT_CONTEXT` for everything.**
Either there are no vectors, or nothing clears the similarity floor. Check the pending-jobs query in
§10; the worker runs every 5 seconds, and if `pending` stays high, confirm `EMBEDDING_MODEL_CODE`
matches a row in `rw_embedding_models`. If vectors exist, lower `RAG_SIMILARITY_FLOOR` — the offline
provider matches shared vocabulary, so its scores are low by the standards of a trained model.

**A newly created database has vectors from an older embedding provider.**
Switching provider does not re-enqueue existing messages. Re-embed with the SQL in §10.

**`./mvnw test` fails with "Could not find a valid Docker environment".**
Docker is not running, or the daemon rejects the API version docker-java negotiates. The build
already pins `-Dapi.version=1.44`; if it still fails, use the `TEST_POSTGRES_URL` path in §14.

**`FATAL: password authentication failed for user "postgres"` from the `migrate` service.**
The `pgdata` volume was initialised with different credentials than `.env` now supplies — PostgreSQL
only applies `POSTGRES_PASSWORD` on first initialisation. This happens if you run the stack, then
create or edit `.env`. Reset the volume: `docker compose down -v && docker compose up --build`.

**Port already in use.**
Set `BACKEND_PORT`, `FRONTEND_PORT`, or `POSTGRES_PORT_HOST` in `.env`.

**`UnsupportedClassVersionError` running the jar.**
The backend targets Java 21. Point `JAVA_HOME` at a JDK 21.

---

## 20. Further reading

| Document | Contents |
|----------|----------|
| `CLAUDE.md` | working agreement, naming conventions, non-negotiable invariants |
| `REQUIREMENTS.md` | the acceptance criteria this implementation is measured against |
| `DECISIONS.md` | every significant design decision and why it was taken |
| `PROGRESS.md` | phase-by-phase status, with what was verified and how |
| `docs/DATA_MODEL.md` | ER diagram, 3NF justification, every table and constraint |
| `docs/SECURITY_MODEL.md` | policy-by-policy walkthrough of the authorization model |
| `docs/API.md` | endpoint reference and error contract |
| `docs/EVIDENCE.md` | reproduction steps for the security properties |
| `docs/DEPLOYMENT.md` | deploying to a hosted PostgreSQL |
