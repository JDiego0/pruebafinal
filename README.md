# Riwi Messaging Platform

Internal messaging platform for **Riwi Co. S.A.S.** — channels and direct messages, realtime delivery, full-text search, and an AI copilot that answers questions over the conversations **you are allowed to read, and only those**.

> **Status:** Phase 0 (architecture and design) complete. Implementation has not started.
> Plan: [`docs/BACKLOG.md`](docs/BACKLOG.md) · Live state: [`PROGRESS.md`](PROGRESS.md)

---

## The governing requirement

An authenticated user must never reach content from a channel they are not a member of — through reads, history, search, embeddings, vector similarity, AI context, AI answers, citations, or metadata.

This platform enforces that **inside PostgreSQL**, with Row Level Security, not in application code:

- the runtime database role `rw_app` is `NOSUPERUSER`, `NOBYPASSRLS`, and has no `DELETE` privilege on any table;
- RLS is `ENABLE`d **and** `FORCE`d, so even the table owner is subject to policy;
- the actor arrives as `app.current_user_id`, set per transaction with a bind parameter from the verified JWT — never from a request body;
- with the actor unset, every protected table returns zero rows: the default is deny;
- vector similarity runs *inside* the RLS-filtered set, never globally-then-filtered;
- every AI citation is intersected with the RLS-produced candidate set before the answer is returned.

Strip every `WHERE` clause out of the Java and the product returns nothing. It does not leak.

Full detail: [`docs/SECURITY_MODEL.md`](docs/SECURITY_MODEL.md).

---

## Stack

| Layer | Technology |
|-------|-----------|
| Database | PostgreSQL 16 + `pgvector` — RLS, functions, procedures, views, triggers, FTS, HNSW |
| Backend | Java 21 · Spring Boot 3.3 · Maven · Spring JDBC · Flyway — Clean Architecture |
| Frontend | React 19 · TypeScript · Vite · TanStack Query · i18next (es/en) |
| Realtime | Server-Sent Events over PostgreSQL `LISTEN`/`NOTIFY` |
| AI | `AIProvider` port → **Groq** (OpenAI-compatible chat) · `EmbeddingProvider` port → local or any OpenAI-compatible `/embeddings` |
| Testing | JUnit 5 · Testcontainers against real PostgreSQL · ArchUnit |
| Delivery | Docker Compose (`db` → `migrate` → `backend` → `frontend`) |

---

## Documentation

| Document | Contents |
|----------|----------|
| [`REQUIREMENTS.md`](REQUIREMENTS.md) | 113 traceable requirement ids — the acceptance spine |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | System view, Clean Architecture layers, data flows, realtime, RAG, frontend |
| [`DECISIONS.md`](DECISIONS.md) | 19 ADRs with rejected alternatives, plus MVP / POST-MVP scope |
| [`CLAUDE.md`](CLAUDE.md) | Conventions and the ten non-negotiable invariants |
| [`docs/DATA_MODEL.md`](docs/DATA_MODEL.md) | ER model, 20 relations, cardinalities, 18 explicit + 9 implicit business rules, 1NF → 3NF |
| [`docs/SECURITY_MODEL.md`](docs/SECURITY_MODEL.md) | Threat model, database roles, every RLS policy, RAG security, evidence map |
| [`docs/API.md`](docs/API.md) | REST contract, error model, keyset cursors, SSE, copilot |
| [`docs/BACKLOG.md`](docs/BACKLOG.md) | 14 phases, task level, each mapped to requirement ids |
| [`PROGRESS.md`](PROGRESS.md) | Phase status, risks, next action |

---

## Quick start

> Available from Phase 11. Recorded here so the contract is fixed up front.

```bash
git clone <repository-url> && cd mensajeria
cp .env.example .env          # then set POSTGRES_PASSWORD, RW_*_PASSWORD, JWT_SECRET
docker compose up --build     # db → migrate → backend → frontend
```

| Service | URL |
|---------|-----|
| Frontend | http://localhost:8081 |
| API | http://localhost:8080/api/v1 |
| OpenAPI | http://localhost:8080/swagger-ui.html |
| PostgreSQL | `localhost:5432/bd_juandiego_garcia_nakamoto` |

```bash
docker compose run --rm migrate                                   # migrations
docker compose run --rm backend java -jar app.jar --spring.profiles.active=seed   # seed
cd backend && ./mvnw test                                         # full suite
cd backend && ./mvnw test -Dtest=RlsSecurityIT                    # the two mandatory security tests
```

Generate a JWT secret: `openssl rand -base64 32`

---

## Human actions required

The stack is designed to run **fully offline with no external credentials**. Nothing below blocks development, the security test suite, or the MVP.

### HUMAN-01 — Supabase project *(optional: only to deploy to Supabase)*
- **What:** project URL, database password, pooled connection string.
- **Why:** the assessment names Supabase as the hosting target. Local Docker Compose is the primary environment (see `DECISIONS.md` D-04); the same migrations apply unchanged to Supabase.
- **Where to obtain:** supabase.com → your project → *Project Settings → Database → Connection string*. `pgvector` must be enabled under *Database → Extensions*.
- **Where to configure:** `SUPABASE_DB_URL`, `SUPABASE_DB_PASSWORD` in `.env`.
- **Security:** paste it into `.env` only — never into source, never into a committed file. `.env` is git-ignored.

### HUMAN-02 — Groq API key *(optional: only for real copilot answers)*
- **What:** a Groq API key.
- **Why:** Groq is the MVP chat provider. Without a key the copilot returns `PROVIDER_UNAVAILABLE`. **The security properties — including the RAG isolation tests — hold and are proven either way**, because they do not depend on any provider.
- **Where to obtain:** [console.groq.com](https://console.groq.com) → *API Keys* → Create API Key.
- **Where to configure:** `.env` — set `AI_ENABLED=true` and `AI_API_KEY=<key>`. `AI_BASE_URL` (`https://api.groq.com/openai/v1`) and `AI_MODEL` (`llama-3.3-70b-versatile`) are pre-filled.
- **Security:** read from the environment only — never a literal in source, never logged, never sent to the frontend, never committed. `.env` is git-ignored.

### HUMAN-02b — Embedding provider key *(optional: only for semantic retrieval quality)*
- **What:** a base URL, model and key for any OpenAI-compatible `/embeddings` endpoint (OpenAI, a local Ollama, etc.).
- **Why:** **Groq publishes no embedding models**, so embeddings come from a separate provider (`DECISIONS.md` D-21). The default is a built-in deterministic local provider that needs no credentials and no network — the whole stack and the entire security suite run on it. A real embedding model improves retrieval *relevance* only.
- **Where to obtain:** OpenAI → [platform.openai.com](https://platform.openai.com) → API Keys. Or run Ollama locally and leave the key empty.
- **Where to configure:** `EMBEDDING_BASE_URL`, `EMBEDDING_MODEL`, `EMBEDDING_API_KEY` in `.env`.
- **Security:** same handling as above. The configured model **must** emit 1536 dimensions — the pgvector column width, validated at startup.

### HUMAN-03 — GitHub repository *(only when publishing)*
- **What:** the repository URL and authorization to push.
- **Why:** no remote is configured, and pushing is an outward-facing action that will not be taken without an explicit instruction.
- **Where to configure:** `git remote add origin <url>`.

### HUMAN-04 — Production JWT secret *(only for a non-local deployment)*
- **What:** a 256-bit random secret.
- **Why:** access tokens are HS256-signed; a shared or weak secret means forgeable identities.
- **Where to obtain:** generate it — `openssl rand -base64 32`.
- **Where to configure:** `JWT_SECRET` in the deployment environment's secret store.
- **Security:** a distinct value per environment; never reuse the development value.

---

## Repository layout

```
├── README.md  ARCHITECTURE.md  DECISIONS.md  REQUIREMENTS.md  PROGRESS.md  CLAUDE.md
├── .env.example  .gitignore  docker-compose.yml
├── docs/          DATA_MODEL · SECURITY_MODEL · API · BACKLOG · TESTING · EVIDENCE · DEPLOYMENT · diagrams/
├── database/      migrations/ (Flyway)  seeds/seed.json  scripts/
├── backend/       Spring Boot — domain · application · infrastructure · presentation
└── frontend/      React + TypeScript + Vite
```
