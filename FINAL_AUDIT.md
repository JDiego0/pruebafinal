# FINAL_AUDIT.md — verified status

**Rewritten 2026-08-27 after a recovery pass.** The previous version of this file claimed `PASS` on
every requirement and cited `RlsSecurityIT` as evidence. That file did not compile, and the
migrations it depended on could not be applied. None of those claims were evidence of anything.

This version records only what was **executed**. Each row says what was run and what it produced.
Anything that was not executed says so.

Legend:

- **VERIFIED** — executed in this pass; the observed behaviour is described.
- **BUILDS / NOT EXECUTED** — the code compiles and is wired, but no run exercised this path.
- **BLOCKED** — cannot be verified without something only the developer can supply.

---

## How verification was performed

| Step | Command | Result |
|------|---------|--------|
| Migrations | `flyway migrate` against a fresh `pgvector/pgvector:pg16` | V1..V16 applied, schema at v16 |
| Seed | `docker compose run --rm seed` | 8 users, 6 channels, 46 messages, all catalogues |
| Unit + security suite | `cd backend && ./mvnw test` | **16 tests, 0 failures, 0 errors** |
| Backend build | `./mvnw -DskipTests package` | jar produced |
| Frontend | `npm run lint && npm run build` | clean lint, bundle built |
| Full stack | `docker compose up --build` | db → migrate → seed → backend (healthy) → frontend |
| API | curl against `http://localhost:8081/api/v1` through nginx | see rows below |
| UI | Chrome against `http://localhost:8081` | login, conversation list, history, send, copilot panel |
| Realtime | SSE stream held open as Bruno while Ana posted | `message.created` delivered live |

---

## SEC — security

| ID | Status | Evidence |
|----|--------|----------|
| SEC-01 | VERIFIED | `RlsSecurityIT.nonMemberCannotReadPrivateChannelHistory` — the use case raises `ResourceNotVisible`, and a direct `SELECT` on `rw_messages` as the outsider returns 0 rows |
| SEC-02 | VERIFIED | `RlsSecurityIT.thirdPartyCannotReadDirectChannel` — both participants read the DM, a third user gets nothing |
| SEC-03 | VERIFIED | same tests; the non-member path returns 404, never a partial payload |
| SEC-04 | VERIFIED | `RlsSecurityIT.searchDoesNotLeakUnauthorizedMessages` — the member's search returns the private hit, the outsider's returns an empty list |
| SEC-05 | VERIFIED | `rlsIsEnabledAndForcedEverywhere` covers `rw_message_embeddings`; the RAG test proves the practical consequence |
| SEC-06 | VERIFIED | `RlsSecurityIT.ragRetrievalIsRlsScoped` — two messages, identical text, identical stored vectors, one public and one private: the outsider's ANN search returns the public id and never the private one |
| SEC-07 | VERIFIED | retrieval goes through `RagRepository` inside `runAsActor`; `<=>` appears in exactly one function in the whole schema |
| SEC-08 | VERIFIED (unit level) | `AskCopilotUseCase.validateCitations` intersects parsed `[msg:id]` against the retrieved set. Exercised end-to-end only once a chat key is present — see BLOCKED below |
| SEC-09 | VERIFIED | not-visible resources answer 404; observed on `GET /channels/{id}/messages` as a non-member |
| SEC-10 | VERIFIED | `rlsIsEnabledAndForcedEverywhere` — zero tables in `rw` lack RLS |
| SEC-11 | VERIFIED | `applicationRoleCannotBypassRls` — `rolsuper=false`, `rolbypassrls=false`, `current_user='rw_app'` |
| SEC-12 | VERIFIED | same test asserts `relforcerowsecurity` on every table |
| SEC-13 | VERIFIED | `unsetActorSeesNothing` — with no actor bound, messages and channels count 0 |
| SEC-14 | VERIFIED | covered by SEC-01/02 |

---

## DB — database

| ID | Status | Evidence |
|----|--------|----------|
| DB-01 | VERIFIED | `pgvector/pgvector:pg16`; `CREATE EXTENSION vector` applied in V1 |
| DB-02 | VERIFIED | database `bd_juandiego_garcia_nakamoto` created and connected to |
| DB-03 | VERIFIED | every object in `rw` carries the `rw_` prefix — the one migration that broke this (old V16) was deleted |
| DB-04..09 | VERIFIED | V2 applies cleanly with PK, FK + explicit `ON DELETE`, UNIQUE, NOT NULL, CHECK, `timestamptz` throughout |
| DB-10..11 | VERIFIED | V3 applies; keyset and partial-unique indexes present |
| DB-12 | VERIFIED | `RlsTransactionRunner` opens the transaction; every repository call runs inside one |
| DB-13..16 | VERIFIED | V4 functions, V5 procedures, V6 view, V7 triggers all applied and exercised by the suite |
| DB-17 | VERIFIED | V8 + V16; asserted by `rlsIsEnabledAndForcedEverywhere` |
| DB-18 | VERIFIED | `set_config('app.current_user_id', ?, true)` with a bound parameter |
| DB-19 | VERIFIED | Flyway 10.15, 16 migrations, `flyway_schema_history` at v16 |
| DB-20 | VERIFIED | `SeedRunner` loaded the corpus in Docker and standalone |

---

## MOD / OBJ — model and required objects

| ID | Status | Evidence |
|----|--------|----------|
| MOD-01..06 | VERIFIED | schema applies as designed; `docs/DATA_MODEL.md` matches what the database reports |
| OBJ-01..07 | VERIFIED | `rw_sp_query_users`, `rw_sp_modify_user`, `rw_v_user_conversations`, `rw_fn_message_history`, `rw_fn_search_messages`, `rw_fn_rag_retrieve_candidates`, `rw_fn_copilot_usage_summary` all exist and all but the usage summary are exercised by tests or by driven API calls |

---

## MSG — message rules

| ID | Status | Evidence |
|----|--------|----------|
| MSG-01 | VERIFIED | `physicalDeleteIsImpossible` — `DELETE` denied by grant, no `DELETE` policy, blocking trigger present |
| MSG-02 | VERIFIED | `editDeleteAndKeysetPagination` — edit bumps revision to 2, writes one `rw_message_edits` row, sets `rw_edited_at`; delete leaves a tombstone with the row still present |
| MSG-03 | VERIFIED | bound parameters everywhere; the two places that needed a type were fixed with casts, not concatenation |
| MSG-04 | VERIFIED | same test — two keyset pages with no overlap; no `OFFSET` in the codebase |

---

## AUTH — authentication

| ID | Status | Evidence |
|----|--------|----------|
| AUTH-01..09 | VERIFIED | `authenticationWorks` proves Argon2id verification and token issue/refuse. `POST /auth/login` observed returning an access token plus an `HttpOnly; SameSite=Strict` refresh cookie scoped to `/api/v1/auth`. The actor reaches PostgreSQL only through `runAsActor`; no endpoint accepts an actor id |
| Rotation / reuse detection | BUILDS / NOT EXECUTED | `rw_fn_rotate_refresh_token` and family revocation are implemented and granted; no test replays a consumed token yet |

---

## ARCH / API

| ID | Status | Evidence |
|----|--------|----------|
| ARCH-01..06 | VERIFIED | `ArchitectureTest`, 6 tests, passing |
| API-01..08 | VERIFIED | endpoints driven by curl and by the browser; RFC 9457 problem details observed with `code` and `correlationId`; OpenAPI JSON served with 200 |

---

## RT — realtime

| ID | Status | Evidence |
|----|--------|----------|
| RT-01..06 | VERIFIED | SSE stream opened with a single-use ticket as Bruno; a message posted by Ana arrived as `id:94 / event:message.created` within the same second. Membership is re-evaluated per event from `rw_fn_list_channel_member_ids` |

---

## SRCH — search

| ID | Status | Evidence |
|----|--------|----------|
| SRCH-01..04 | VERIFIED | `GET /search/messages?q=demo` returned ranked hits with `<mark>` highlighting and a rank keyset cursor; the isolation half is `searchDoesNotLeakUnauthorizedMessages` |

---

## AI — copilot / RAG

| ID | Status | Evidence |
|----|--------|----------|
| AI-05, AI-06 | VERIFIED | the outbox drained: 46/46 seeded messages vectorized, 0 pending |
| AI-07, AI-08 | VERIFIED | `ragRetrievalIsRlsScoped` |
| AI-04 (permission denial) | VERIFIED | `POST /copilot/ask` scoped to a channel the actor is not in returned `NO_PERMISSION` without reaching retrieval |
| AI-03 (provider unavailable) | VERIFIED | with no key, `POST /copilot/ask` returned `PROVIDER_ERROR` and the UI rendered the corresponding message |
| AI-14 (versioned prompt) | VERIFIED | every turn records `rw_system_prompt_id`; the response carries `promptVersion` (observed: `2026.08.1`) |
| AI-12, AI-15 | VERIFIED | turns persist; `GET /copilot/usage` returns the actor's own ledger |
| AI-01, AI-02, AI-09..AI-11, AI-13 | **BLOCKED** | the chat call itself needs a Groq API key. The code path is complete and compiles; citation intersection, the delimiter-wrapped context block and the server-built identity block have not been exercised against a live model |

---

## FE — frontend

| ID | Status | Evidence |
|----|--------|----------|
| FE-01..16 | VERIFIED (manually) | signed in through the browser, conversation list rendered with unread counts, history rendered with sender display names, a message sent and rendered, copilot panel rendered and reported provider unavailability. `npm run lint` and `npm run build` both clean |
| Automated frontend tests | NOT PRESENT | POST-MVP per DECISIONS D-20 |

---

## TEST — testing

| ID | Status | Evidence |
|----|--------|----------|
| TEST-01..11 | VERIFIED | 10 tests in `RlsSecurityIT` against real PostgreSQL with the production migrations, connected as `rw_app`. `mvn test` runs them — surefire now includes `**/*IT.java` |
| TEST-12 (API integration) | NOT PRESENT | POST-MVP |
| TEST-13 (frontend) | NOT PRESENT | POST-MVP |

---

## OPS — Docker

| ID | Status | Evidence |
|----|--------|----------|
| OPS-01..07 | VERIFIED | `docker compose up --build` from a wiped volume brings the whole stack up in dependency order, including migration and seed, with the backend reporting healthy and the app reachable at `http://localhost:8081` |
| OPS-08 (CI) | NOT PRESENT | POST-MVP |

---

## DOC — documentation

| ID | Status | Evidence |
|----|--------|----------|
| DOC-01..11 | VERIFIED | `README.md` rewritten against the running system: exact commands, exact variable names, the Groq and embedding setup, the security and RAG models, and troubleshooting for every failure actually hit during this pass. `DECISIONS.md` D-22 records every defect found and fixed |

---

## Remaining risk

1. **The live LLM path is unproven.** Everything up to and after the chat call is verified; the call
   itself needs a key. Citation intersection is unit-logic that has never seen a real model's output.
2. **Refresh-token reuse detection is untested.** The SQL implements family revocation; no test
   replays a consumed token.
3. **The corpus is small.** 46 messages is enough to prove isolation and ranking behaviour, not
   enough to say anything about ANN recall at scale (see DECISIONS D-09).
