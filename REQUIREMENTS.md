# REQUIREMENTS.md — Riwi Messaging Platform

Traceability spine for the technical assessment. Every requirement has a stable ID.
`FINAL_AUDIT.md` reports one row per ID, stating what was executed to verify it:
`VERIFIED` / `PARTIAL` / `BUILDS - NOT EXECUTED` / `NOT PRESENT`.

Scope classification: **MVP** (must exist in the vertical slice) / **POST-MVP** (deferred, cut documented in `DECISIONS.md`).

---

## SEC — Absolute security requirement

| ID | Requirement | Scope |
|----|-------------|-------|
| SEC-01 | An authenticated user can never read messages of a channel they are not a member of. | MVP |
| SEC-02 | The same guarantee holds for direct-message conversations. | MVP |
| SEC-03 | The same guarantee holds for conversation history / pagination endpoints. | MVP |
| SEC-04 | The same guarantee holds for full-text message search. | MVP |
| SEC-05 | The same guarantee holds for stored embeddings (embedding rows unreadable for unauthorized messages). | MVP |
| SEC-06 | The same guarantee holds for vector similarity search (ANN runs *inside* the authorized set, never globally). | MVP |
| SEC-07 | The AI copilot never receives unauthorized message content as context. | MVP |
| SEC-08 | AI answers and citations never reference unauthorized messages. | MVP |
| SEC-09 | No metadata leakage (channel names, member lists, message counts, existence of a private channel). | MVP |
| SEC-10 | Authorization enforced in PostgreSQL (RLS), not only in backend/frontend code. | MVP |
| SEC-11 | The application database role does **not** have `BYPASSRLS` and is not `SUPERUSER`. | MVP |
| SEC-12 | RLS is `FORCE`d so that even the table owner is subject to policies. | MVP |
| SEC-13 | When `app.current_user_id` is unset, every RLS-protected table returns zero rows (deny by default). | MVP |
| SEC-14 | Unauthorized access to an existing private resource returns `404`, not `403` (no existence disclosure). | MVP |

## DB — Database

| ID | Requirement | Scope |
|----|-------------|-------|
| DB-01 | PostgreSQL 15+ (target 16) with `pgvector`. | MVP |
| DB-02 | Logical database name `bd_juandiego_garcia_nakamoto`. | MVP |
| DB-03 | All table and column names in English and prefixed `rw_`. | MVP |
| DB-04 | Primary keys on every table. | MVP |
| DB-05 | Foreign keys with explicit `ON DELETE` action on every FK. | MVP |
| DB-06 | `UNIQUE` constraints where the business demands identity. | MVP |
| DB-07 | `NOT NULL` on every business-mandatory column. | MVP |
| DB-08 | `CHECK` constraints encoding business rules. | MVP |
| DB-09 | All timestamps `timestamptz`, stored and compared in UTC. | MVP |
| DB-10 | At least one partial unique index (3 delivered: active refresh token per family, single active system prompt, canonical DM pair). | MVP |
| DB-11 | Purposeful indexes (keyset, FK, GIN full-text, HNSW vector). | MVP |
| DB-12 | Multi-statement operations wrapped in transactions. | MVP |
| DB-13 | SQL functions. | MVP |
| DB-14 | Stored procedures (`CREATE PROCEDURE`). | MVP |
| DB-15 | Views. | MVP |
| DB-16 | Triggers. | MVP |
| DB-17 | Row Level Security policies. | MVP |
| DB-18 | `app.current_user_id` is the transaction-scoped authenticated actor (`SET LOCAL`). | MVP |
| DB-19 | Versioned, repeatable migrations. | MVP |
| DB-20 | DML / seed loading from `database/seeds/seed.json`. | MVP |

## MOD — Data model and normalization

| ID | Requirement | Scope |
|----|-------------|-------|
| MOD-01 | ER model with entities, attributes, PK, FK, cardinalities. | MVP |
| MOD-02 | Documented business rules, including implicit ones. | MVP |
| MOD-03 | Key selection justified (surrogate vs natural). | MVP |
| MOD-04 | `database/seeds/seed.json` doubles as business corpus (entities, attributes, relationships, cardinalities, explicit + implicit rules, normalization decisions) and as loadable seed data. | MVP |
| MOD-05 | Normalization documented 1NF to 2NF to 3NF with justification that the final model is in 3NF. | MVP |
| MOD-06 | ER diagram rendered (Mermaid source + exported image). | MVP |

## OBJ — Required database objects

| ID | Requirement | Scope |
|----|-------------|-------|
| OBJ-01 | Stored procedure: user query. | MVP |
| OBJ-02 | Stored procedure: user edit / delete (logical). | MVP |
| OBJ-03 | View: the authenticated user's conversations. | MVP |
| OBJ-04 | Query: channel message history with **keyset** pagination. | MVP |
| OBJ-05 | Query: message search with the matched term highlighted. | MVP |
| OBJ-06 | Query: permission-aware AI context retrieval. | MVP |
| OBJ-07 | Query: accumulated copilot consumption per user. | MVP |

## MSG — Message rules

| ID | Requirement | Scope |
|----|-------------|-------|
| MSG-01 | Messages are never physically deleted; logical deletion only, enforced by the database. | MVP |
| MSG-02 | Message editing preserves the original state as history. | MVP |
| MSG-03 | No SQL string concatenation anywhere — parameterized statements only. | MVP |
| MSG-04 | No `OFFSET` pagination anywhere — keyset only. | MVP |

## AUTH — Authentication

| ID | Requirement | Scope |
|----|-------------|-------|
| AUTH-01 | Passwords hashed with a memory-hard KDF; never stored in plaintext. | MVP |
| AUTH-02 | Login endpoint issuing a JWT access token. | MVP |
| AUTH-03 | Short access-token lifetime (15 minutes). | MVP |
| AUTH-04 | Refresh token issued and stored hashed (never plaintext at rest). | MVP |
| AUTH-05 | Refresh token rotation on every use. | MVP |
| AUTH-06 | Reuse detection: replaying a rotated token revokes the whole family. | MVP |
| AUTH-07 | Logout / revocation. | MVP |
| AUTH-08 | Refresh token transported in an `HttpOnly`, `SameSite`, `Secure` cookie. | MVP |
| AUTH-09 | The actor id is read **only** from the verified token, never from a request body or parameter. | MVP |

## ARCH — Backend architecture

| ID | Requirement | Scope |
|----|-------------|-------|
| ARCH-01 | Java + Spring Boot + Maven + PostgreSQL. | MVP |
| ARCH-02 | Clean Architecture with `domain`, `application`, `infrastructure`, `presentation`. | MVP |
| ARCH-03 | `domain` has no dependency on Spring, HTTP, the PostgreSQL driver, or JPA/Hibernate APIs. | MVP |
| ARCH-04 | Layer boundaries enforced automatically (ArchUnit). | MVP |
| ARCH-05 | Thin use cases: validate, invoke repository/DB function, map, return result. | MVP |
| ARCH-06 | SOLID demonstrated naturally; any design pattern used is justified in `DECISIONS.md`. | MVP |

## API — REST API

| ID | Requirement | Scope |
|----|-------------|-------|
| API-01 | Correct HTTP status codes per operation. | MVP |
| API-02 | Request validation with field-level errors. | MVP |
| API-03 | Uniform error envelope (RFC 9457 Problem Details). | MVP |
| API-04 | Correlation ID accepted or generated, echoed in responses and logs. | MVP |
| API-05 | Authentication filter on all protected routes. | MVP |
| API-06 | Authorization enforced at DB level for every data-returning route. | MVP |
| API-07 | Keyset pagination contract with opaque cursor. | MVP |
| API-08 | OpenAPI documentation. | MVP |

## RT — Realtime

| ID | Requirement | Scope |
|----|-------------|-------|
| RT-01 | New messages appear in real time for channel members. | MVP |
| RT-02 | Subscription lifecycle (open, close, cleanup). | MVP |
| RT-03 | Duplicate events are idempotent on the client (`clientMessageId`). | MVP |
| RT-04 | Reconnect with gap recovery (`Last-Event-ID` then keyset catch-up). | MVP |
| RT-05 | Failed or dropped events surfaced and recovered, not silently lost. | MVP |
| RT-06 | Realtime fan-out respects channel membership. | MVP |

## SRCH — Search

| ID | Requirement | Scope |
|----|-------------|-------|
| SRCH-01 | Search implemented with PostgreSQL full-text search (`tsvector`/`tsquery`). | MVP |
| SRCH-02 | Authorization enforced by the database inside the search query, never by post-filtering in application code. | MVP |
| SRCH-03 | Matched term highlighted (`ts_headline`). | MVP |
| SRCH-04 | Search results paginated by keyset. | MVP |

## AI — Copilot / RAG

| ID | Requirement | Scope |
|----|-------------|-------|
| AI-01 | RAG pipeline implemented end to end. | MVP |
| AI-02 | `AIProvider` abstraction; no direct coupling to a single vendor. | MVP |
| AI-03 | OpenAI-compatible implementation so providers are interchangeable. MVP chat provider: **Groq**. | MVP |
| AI-16 | Chat and embedding providers configured, credentialed and failing independently (Groq offers no embeddings). | MVP |
| AI-04 | Provider credentials only from environment; never hardcoded, never committed. | MVP |
| AI-05 | Embeddings stored in `pgvector` and associated with messages. | MVP |
| AI-06 | A trigger keeps vector and search data consistent when a message changes. | MVP |
| AI-07 | Retrieval order is: actor, permission-aware SQL, authorized candidates, vector similarity, context, LLM. | MVP |
| AI-08 | Global vector search followed by application filtering is forbidden and provably absent. | MVP |
| AI-09 | Retrieved messages treated as untrusted data; cannot override system instructions. | MVP |
| AI-10 | Explicit negative answers for insufficient context, missing permission, out of scope. | MVP |
| AI-11 | Every grounded answer carries citations to source messages. | MVP |
| AI-12 | Cited message ids validated against the authorized candidate set before the answer is returned. | MVP |
| AI-13 | Actor name and role injected server-side from the authenticated identity, never from the frontend. | MVP |
| AI-14 | System prompt versioned and persisted. | MVP |
| AI-15 | Per-user accumulated copilot token consumption recorded and queryable. | MVP |

## FE — Frontend

| ID | Requirement | Scope |
|----|-------------|-------|
| FE-01 | React + TypeScript + Vite. | MVP |
| FE-02 | Conversation area. | MVP |
| FE-03 | AI copilot panel. | MVP |
| FE-04 | User profile view. | MVP |
| FE-05 | Message states `pending`, `sent`, `failed` with retry. | MVP |
| FE-06 | Optimistic message sending. | MVP |
| FE-07 | Lazy history loading (infinite scroll upward). | MVP |
| FE-08 | Scroll position preserved when older history is prepended. | MVP |
| FE-09 | Loading states. | MVP |
| FE-10 | Empty states. | MVP |
| FE-11 | Error states. | MVP |
| FE-12 | Responsive desktop layout. | MVP |
| FE-13 | Responsive mobile layout. | MVP |
| FE-14 | Spanish locale. | MVP |
| FE-15 | English locale. | MVP |
| FE-16 | No hardcoded user-facing strings inside components; all via i18n. | MVP |

## TEST — Testing

| ID | Requirement | Scope |
|----|-------------|-------|
| TEST-01 | Tests run against a **real** PostgreSQL instance (Testcontainers), not mocks. | MVP |
| TEST-02 | **Mandatory Test 1** — a non-member cannot retrieve messages from a private channel. | MVP |
| TEST-03 | **Mandatory Test 2** — a user cannot retrieve messages from another user's private conversation. | MVP |
| TEST-04 | Vector / RAG retrieval leaks nothing across channels. | MVP |
| TEST-05 | Search leaks nothing across channels. | MVP |
| TEST-06 | Physical `DELETE` on messages is rejected by the database. | MVP |
| TEST-07 | Application role has no `BYPASSRLS` and is not `SUPERUSER`. | MVP |
| TEST-08 | Missing `app.current_user_id` yields zero rows. | MVP |
| TEST-09 | Refresh-token rotation and reuse detection. | MVP |
| TEST-10 | Edit-history and embedding-invalidation triggers. | MVP |
| TEST-11 | ArchUnit layer-dependency test. | MVP |
| TEST-12 | API-level integration tests over the real stack. | POST-MVP |
| TEST-13 | Frontend component tests. | POST-MVP |

## OPS — Docker and operations

| ID | Requirement | Scope |
|----|-------------|-------|
| OPS-01 | `docker compose up` starts database, backend and frontend. | MVP |
| OPS-02 | Documented migration command. | MVP |
| OPS-03 | Documented seed command. | MVP |
| OPS-04 | Documented clean-machine local setup. | MVP |
| OPS-05 | All environment variables documented. | MVP |
| OPS-06 | `.env.example` present; no real secrets committed. | MVP |
| OPS-07 | Healthchecks and correct service start ordering. | MVP |
| OPS-08 | CI workflow running the security test suite. | POST-MVP |

## DOC — Documentation

| ID | Requirement | Scope |
|----|-------------|-------|
| DOC-01 | All documentation in English. | MVP |
| DOC-02 | `README.md`. | MVP |
| DOC-03 | `ARCHITECTURE.md`. | MVP |
| DOC-04 | `DECISIONS.md`. | MVP |
| DOC-05 | ER diagram. | MVP |
| DOC-06 | `database/seeds/seed.json`. | MVP |
| DOC-07 | API documentation. | MVP |
| DOC-08 | Test documentation. | MVP |
| DOC-09 | Evidence instructions (how to reproduce every security claim). | MVP |
| DOC-10 | Deployment instructions. | MVP |
| DOC-11 | `FINAL_AUDIT.md` with per-requirement status, evidence and residual risk. | MVP |

## REPO — Repository hygiene

| ID | Requirement | Scope |
|----|-------------|-------|
| REPO-01 | No secrets, keys, tokens or `.env` committed. | MVP |
| REPO-02 | No personal machine paths or local configuration committed. | MVP |
| REPO-03 | No temporary files, debug logs or assistant-specific artifacts in the delivery. | MVP |
| REPO-04 | Meaningful commits at stable milestones; history not fabricated. | MVP |
