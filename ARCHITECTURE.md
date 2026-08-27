# ARCHITECTURE.md — Riwi Messaging Platform

Companion documents: `docs/DATA_MODEL.md` (ER, 3NF), `docs/SECURITY_MODEL.md` (RLS, RAG), `docs/API.md` (contract), `DECISIONS.md` (rationale and rejected alternatives).

---

## 1. System overview

```
┌──────────────────────────────────────────────────────────────────────┐
│  Browser — React 19 + TypeScript + Vite                              │
│  conversations · copilot panel · profile · i18n (es/en)              │
└───────────┬───────────────────────────────────┬──────────────────────┘
            │ REST (JSON, JWT bearer)           │ SSE (single-use ticket)
┌───────────▼───────────────────────────────────▼──────────────────────┐
│  Spring Boot 3.3 (Java 21) — Clean Architecture                      │
│                                                                      │
│  presentation  controllers · problem-details · correlation id        │
│  application   thin use cases · commands · results                   │
│  domain        entities · value objects · ports  (no framework)      │
│  infrastructure JDBC adapters · JWT · SSE hub · AIProvider           │
└───────────┬───────────────────────────────────┬──────────────────────┘
            │ JDBC as rw_app (NOBYPASSRLS)      │ HTTPS (OpenAI-compatible)
┌───────────▼───────────────────────────────────▼──────────────────────┐
│  PostgreSQL 16 + pgvector                     │  Groq — chat          │
│  RLS · functions · procedures · views         │  Embedding provider   │
│  triggers · FTS · HNSW · LISTEN/NOTIFY        │  (interchangeable)    │
└──────────────────────────────────────────────────────────────────────┘
```

The database is not a persistence detail here — it is the authorization layer. Everything above it is written on the assumption that it will independently refuse anything unauthorized.

---

## 2. Repository layout

```
.
├── README.md  ARCHITECTURE.md  DECISIONS.md  REQUIREMENTS.md  PROGRESS.md
├── CLAUDE.md                      # working agreement / context map
├── FINAL_AUDIT.md                 # produced in Phase 13
├── .env.example  .gitignore  docker-compose.yml
├── docs/
│   ├── DATA_MODEL.md  SECURITY_MODEL.md  API.md  TESTING.md
│   ├── EVIDENCE.md  DEPLOYMENT.md  BACKLOG.md
│   └── diagrams/er.mmd  diagrams/er.svg
├── database/
│   ├── migrations/                # V1__…sql  V2__…sql  (Flyway, versioned)
│   ├── seeds/seed.json  seed.schema.json
│   └── scripts/                   # psql helpers, role bootstrap
├── backend/
│   ├── pom.xml  Dockerfile
│   └── src/main/java/co/riwi/messaging/
│       ├── domain/          application/          infrastructure/          presentation/
│   └── src/test/java/co/riwi/messaging/   # Testcontainers security suite + ArchUnit
└── frontend/
    ├── package.json  vite.config.ts  Dockerfile  nginx.conf
    └── src/{app,features,shared,types}
```

---

## 3. Backend — Clean Architecture

### 3.1 Dependency rule

```
presentation ──► application ──► domain ◄── infrastructure
```

Dependencies point inward. `domain` depends on nothing but the JDK. `infrastructure` depends inward on `domain` by *implementing its ports*; nothing in `domain` names an infrastructure type. Enforced mechanically by ArchUnit (`TEST-11`):

- no class in `..domain..` may import `org.springframework..`, `jakarta.persistence..`, `org.postgresql..`, `java.sql..`, `com.fasterxml.jackson..`, or `jakarta.servlet..`
- no class in `..application..` may import `jakarta.servlet..` or `org.postgresql..`
- `..presentation..` may not be accessed from any other layer

### 3.2 `domain`

Pure Java. Entities and value objects (`UserId`, `ChannelId`, `MessageId`, `MessageBody`, `Cursor`, `Email`, `Locale`), domain errors, and the **ports**:

| Port | Purpose |
|------|---------|
| `UserRepository` | directory queries, profile update, logical deactivation |
| `CredentialRepository` | verify / set password (never returns a hash) |
| `ChannelRepository` | channels, membership, direct-channel resolution |
| `MessageRepository` | history (keyset), send, edit, logical delete |
| `MessageSearchRepository` | full-text search with highlight |
| `RagRepository` | permission-aware candidate retrieval |
| `EmbeddingRepository` | store vectors, drain the job queue |
| `RefreshTokenRepository` | issue, rotate, revoke family |
| `CopilotRepository` | threads, turns, citations, usage ledger and summary |
| `AIProvider` | `chat(ChatRequest) → ChatResponse` |
| `EmbeddingProvider` | `embed(List<String>) → List<Vector>` |
| `SystemPromptRepository` | active versioned prompt |
| `EventPublisher` | realtime fan-out |
| `TransactionRunner` | `runAsActor(UserId, Supplier<T>) → T` |
| `Clock`, `IdGenerator`, `TokenHasher` | deterministic seams for tests |

Value objects validate in their constructors, so an invalid `MessageBody` cannot exist further in. That is where most "validation" lives — the use cases stay thin because the types are not anaemic.

### 3.3 `application`

One class per use case, each with a single public method. The shape is fixed:

```java
public final class SendMessageUseCase {
    public SendMessageResult execute(SendMessageCommand command) {
        // 1. validate  → value objects + explicit preconditions
        // 2. invoke    → repository port (which calls a DB function)
        // 3. map       → result record
        // 4. return
    }
}
```

Use cases never build SQL, never touch HTTP, never call the AI provider directly except through its port. The actor arrives as a constructor-injected `ActorProvider` value, not as a method parameter that a controller could forge.

Planned use cases: `Login`, `RefreshSession`, `Logout`, `GetProfile`, `UpdateProfile`, `SearchUsers`, `DeactivateUser`, `ListConversations`, `CreateChannel`, `OpenDirectChannel`, `JoinChannel`, `LeaveChannel`, `ListChannelMembers`, `GetMessageHistory`, `SendMessage`, `EditMessage`, `DeleteMessage`, `SearchMessages`, `AskCopilot`, `GetCopilotUsage`, `IssueRealtimeTicket`, `SubscribeToEvents`, `ProcessEmbeddingJobs`.

### 3.4 `infrastructure`

- **Persistence** — `NamedParameterJdbcTemplate` adapters, one per port. Every adapter calls a database function or a parameterized statement; there is no string concatenation and no `OFFSET` (`MSG-03`, `MSG-04`). No JPA (see `DECISIONS.md` D-03).
- **`RlsTransactionRunner`** — the only place a transaction begins. It opens the transaction, runs `SELECT set_config('app.current_user_id', :actor, true)`, executes the work, commits. A guard rejects repository calls made outside it.
- **Security** — JWT issue/verify, Argon2id encoder, the authentication filter that builds `AuthenticatedActor` from the token, refresh-token rotation service.
- **AI** — `OpenAiCompatibleChatProvider` (base URL + model + key from config; MVP target is Groq at `https://api.groq.com/openai/v1`, and the same adapter reaches OpenAI, Together, Ollama or LM Studio), `OpenAiCompatibleEmbeddingProvider`, `DeterministicLocalEmbeddingProvider` (offline fallback so the stack runs with no key), `NullAIProvider` (returns `PROVIDER_UNAVAILABLE`). Chat and embeddings are configured and credentialed **independently**, because Groq serves chat only — see `DECISIONS.md` D-21. Selected by configuration, injected by interface: the use case cannot tell which is active.
- **Realtime** — `PostgresNotificationListener` on a dedicated connection running `LISTEN rw_message_created`, plus an in-process `SseHub` fanning out to subscribers filtered by membership.
- **Config** — Flyway, datasource, CORS, rate limiting, OpenAPI.

### 3.5 `presentation`

Thin controllers: bind and validate the request, call one use case, map the result to a response, choose the status code. Cross-cutting concerns are filters and advice: correlation id, authentication, rate limit, and a `@RestControllerAdvice` that renders every failure as RFC 9457 Problem Details.

Domain errors map to status codes in exactly one place:

| Domain error | Status |
|--------------|--------|
| `ValidationError` | 400 / 422 |
| `AuthenticationFailed` | 401 |
| `OperationNotPermitted` | 403 |
| `ResourceNotVisible` (includes "exists but not authorized") | 404 |
| `ConflictError` | 409 |
| `RateLimited` | 429 |
| `ProviderUnavailable` | 503 |

A second, narrower translation sits alongside this one: `GlobalExceptionHandler` also inspects `DataAccessException`'s PostgreSQL SQLSTATE and maps a handful of codes raised by `RAISE EXCEPTION` inside a SECURITY DEFINER function/procedure straight to the same statuses (`42501` insufficient-privilege → 403, `P0002` no-data-found → 404, `23514`/`23505` constraint violation → 409, `28000` → 401, `22023` → 422) — a repository never has to catch and re-wrap these itself. Everything else falls through to the generic 500 handler, unmodified (Phase 4 finding: without this, a database-level authorization rejection like `rw_sp_modify_user`'s internal admin check surfaced as an opaque 500 instead of the 403 it actually is).

---

## 4. Data flow — sending a message

```
POST /api/v1/channels/{id}/messages   { body, clientMessageId }
  → CorrelationIdFilter          assigns/propagates X-Correlation-Id
  → JwtAuthenticationFilter      verifies token → AuthenticatedActor
  → MessageController            binds + validates DTO
  → SendMessageUseCase           builds MessageBody value object
  → RlsTransactionRunner         BEGIN; set_config('app.current_user_id', actor, true)
  → MessageRepository            SELECT * FROM rw_fn_send_message(:channel,:body,:clientId)
        ├── RLS INSERT WITH CHECK: sender = me() AND member(channel) AND not archived
        ├── ON CONFLICT (channel, sender, client_message_id) DO NOTHING → idempotent
        ├── generated tsvector column stays consistent
        └── AFTER INSERT trigger: enqueue rw_embedding_jobs + pg_notify(...)
                                  COMMIT
  → 201 Created + message resource
  → NOTIFY → PostgresNotificationListener → SseHub → members' open streams
```

A non-member reaching this endpoint is stopped by the RLS `WITH CHECK`, not by a Java `if`. The Java code has no membership check to forget.

---

## 5. Realtime

**Server-Sent Events fed by PostgreSQL `LISTEN`/`NOTIFY`.**

An `AFTER INSERT` trigger on `rw_messages` emits `pg_notify('rw_message_created', json)` carrying ids only — no message body. A single listener connection receives it, and `SseHub` delivers to subscribers, filtering by membership *at delivery time* so a user who just left a channel stops receiving immediately (IBR-07). Clients then fetch the message through the normal RLS-protected endpoint, which means the realtime path can never become a second, weaker read path.

| Concern | Handling |
|---------|----------|
| Subscription | `GET /api/v1/realtime/stream?ticket=…`, heartbeat every 20 s |
| Duplicates | events carry `clientMessageId`; the client reconciles optimistic entries by that key (`RT-03`) |
| Reconnect | `EventSource` auto-reconnects with `Last-Event-ID`; the client then runs a keyset catch-up query from its newest known message, so a missed event cannot leave a permanent gap (`RT-04`) |
| Failure | a dropped stream flips the UI to a "reconnecting" state and the catch-up query repairs history (`RT-05`) |
| Membership | fan-out filter + the fetch itself is RLS-protected (`RT-06`) |
| Scale-out | in-process hub; every instance `LISTEN`s, so N instances fan out correctly without extra infrastructure |

Rejected: Supabase Realtime (would require Supabase-issued JWTs and an anon key in the browser, splitting the auth model in two) and WebSockets (bidirectional transport for a unidirectional need). See `DECISIONS.md` D-05.

---

## 6. AI copilot

```
AskCopilotUseCase
  ├── EmbeddingProvider.embed(question)
  ├── RagRepository.retrieveCandidates(vector, channelId?, k)     ← RLS-enforced, SECURITY INVOKER
  ├── SystemPromptRepository.active()                             ← versioned, from DB
  ├── identity block built from the DB user row                   ← never from the client
  ├── AIProvider.chat(system, identity, untrusted context, question)
  ├── validate citations ⊆ candidate ids                          ← hard gate
  └── persist turn + citations + usage ledger
```

`AIProvider` and `EmbeddingProvider` are **two separate domain ports**, and that separation is load-bearing rather than decorative: the MVP chat provider is **Groq**, which publishes no embedding models, so the two roles must be satisfied by different vendors (`DECISIONS.md` D-21).

| Port | MVP implementation | Configuration |
|------|--------------------|---------------|
| `AIProvider` (chat) | Groq, OpenAI-compatible | `AI_BASE_URL=https://api.groq.com/openai/v1`, `AI_MODEL=llama-3.3-70b-versatile`, `AI_API_KEY` |
| `EmbeddingProvider` | deterministic local by default; any OpenAI-compatible `/embeddings` endpoint when configured | `EMBEDDING_BASE_URL`, `EMBEDDING_MODEL`, `EMBEDDING_API_KEY`, `EMBEDDING_DIMENSIONS=1536` |

Switching either provider is an environment change, not a code change (`AI-02`, `AI-03`), and the two fail independently — an unreachable Groq degrades chat to `PROVIDER_UNAVAILABLE` without stopping message indexing. With neither key configured the stack still runs: the deterministic local embedding provider keeps retrieval and all security tests meaningful. The security properties are provider-independent by construction, which is why changing vendors touches configuration and documentation and nothing in the authorization path.

Full retrieval and injection detail: `docs/SECURITY_MODEL.md` §6.

---

## 7. Frontend

React 19 + TypeScript + Vite, organised by feature:

```
src/
  app/        router, providers, layout shell
  features/
    auth/          login, token refresh interceptor, session store
    conversations/ conversation list (from the DB view), channel header
    messages/      virtualized list, composer, optimistic queue, search
    copilot/       panel, question box, answer with citation chips
    profile/       profile view, locale switch
  shared/
    api/     typed client, problem-details parsing, silent refresh, correlation id
    i18n/    i18next + locales/{es,en}.json
    ui/      primitives, states (loading / empty / error)
    hooks/   useSse, useKeysetInfiniteQuery, useScrollAnchor
  types/     generated from OpenAPI
```

- **Optimistic send** — a `pending` entry with a client-generated `clientMessageId` is appended immediately; the REST response promotes it to `sent`; failure marks it `failed` with a retry action; the SSE echo reconciles by `clientMessageId` instead of duplicating (`FE-05`, `FE-06`, `RT-03`).
- **Lazy history + scroll anchoring** — upward infinite scroll on the keyset cursor; before prepending, `scrollHeight` is captured and restored after paint so the viewport does not jump (`FE-07`, `FE-08`).
- **State** — TanStack Query for server state (cache, retry, invalidation), a small Zustand store for session and UI state. No Redux.
- **i18n** — every user-facing string is a key in `locales/{es,en}.json`; a lint rule forbids string literals in JSX text positions (`FE-16`). Dates and numbers via `Intl` with the active locale; timestamps arrive UTC and are formatted client-side (IBR-08).
- **Responsive** — single-column with a drawer under `768px`, two-pane at `md`, three-pane with the copilot docked at `xl` (`FE-12`, `FE-13`).

---

## 8. Migrations, seeding, testing, deployment

- **Migrations** — Flyway, versioned SQL in `database/migrations`, run by a dedicated `migrate` compose service as `rw_migrator`. Ordering: extensions and roles → tables → indexes → functions → procedures → views → triggers → RLS policies → grants.
- **Seeding** — a Spring Boot runner activated with `--spring.profiles.active=seed` reads `database/seeds/seed.json`, validates it against the JSON schema, and inserts the `data` block in one transaction in FK order, idempotently.
- **Testing** — JUnit 5 + Testcontainers against `pgvector/pgvector:pg16`, the real schema applied by the real Flyway migrations. Security tests connect **as `rw_app`** and set `app.current_user_id` exactly as production does; asserting against a superuser connection would prove nothing. Details in `docs/TESTING.md`.
- **Deployment** — `docker compose up` starts `db` → `migrate` (runs to completion) → `backend` → `frontend`, with healthcheck-gated ordering. The same migrations apply unchanged to a Supabase project; Supabase specifics in `docs/DEPLOYMENT.md`.
