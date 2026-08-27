# BACKLOG.md — Implementation Backlog

Ordered by dependency. Each task names the `REQUIREMENTS.md` ids it satisfies.
`PROGRESS.md` tracks live status; this file is the plan.

Model policy per the brief: **Opus** for phases 1 (already done), 3 (RLS/RAG security), 10 (security tests) and 13 (final audit); **Sonnet** for phases 2, 4–9, 11, 12 implementation work.

---

## PHASE 0 — Initialization  ✅ complete
| # | Task | Covers |
|---|------|--------|
| 0.1 | Repository skeleton | — |
| 0.2 | `REQUIREMENTS.md` traceability spine | all |
| 0.3 | `CLAUDE.md` working agreement | — |
| 0.4 | `ARCHITECTURE.md` | ARCH-02, DOC-03 |
| 0.5 | `DECISIONS.md` (19 ADRs + scope) | ARCH-06, DOC-04 |
| 0.6 | `PROGRESS.md` | — |
| 0.7 | `docs/DATA_MODEL.md` — ER, cardinalities, rules, 3NF | MOD-01…MOD-05 |
| 0.8 | `docs/SECURITY_MODEL.md` — RLS + RAG security | SEC-*, DOC-09 |
| 0.9 | `docs/API.md` — contract | API-* |
| 0.10 | This backlog | — |
| 0.11 | `.gitignore`, `.env.example`, ER diagram source | OPS-05, OPS-06, MOD-06 |

## PHASE 1 — Business corpus and seed  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 1.1 | `database/seeds/seed.schema.json` | MOD-04 |
| 1.2 | `seed.json` `model` block — entities, attributes, relationships, cardinalities, explicit + implicit rules, normalization decisions | MOD-01…MOD-05 |
| 1.3 | `seed.json` `data` block — 2 roles, 3 channel types, 3 member roles, 8 users, 6 channels (2 public, 2 private, 2 direct), ~120 realistic bilingual messages, system prompt v1, and `rw_ai_providers` / `rw_embedding_models` rows: `groq` (chat, OpenAI-compatible) + `local` (embeddings, `deterministic-1536`) | DB-20, D-21 |
| 1.4 | Deliberate negative fixtures: a private channel with a term that appears nowhere else, and a DM whose content is semantically near a public message (for TEST-04/05) | TEST-04, TEST-05 |
| 1.5 | Render `docs/diagrams/er.svg` from `er.mmd` | MOD-06, DOC-05 |

## PHASE 2 — Schema  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 2.1 | `V1__extensions_and_roles.sql` — `pgcrypto`, `vector`, roles `rw_migrator` / `rw_app`, schema `rw` | DB-01, SEC-11 |
| 2.2 | `V2__tables.sql` — 20 tables, PK, FK with explicit `ON DELETE`, `NOT NULL`, `CHECK`, `timestamptz` | DB-03…DB-09 |
| 2.3 | `V3__indexes.sql` — keyset, FK, GIN, HNSW, 3 partial unique indexes | DB-10, DB-11 |
| 2.4 | Generated `tsvector` column | SRCH-01, D-12 |
| 2.5 | Schema smoke test: every FK has an `ON DELETE`; every table has a PK | DB-04, DB-05 | ✅

## PHASE 3 — Logic and RLS  *(Opus — security critical)*
| # | Task | Covers |
|---|------|--------|
| 3.1 | `V4__functions.sql` — `rw_fn_current_user_id`, `rw_fn_is_channel_member` (`SECURITY DEFINER`, pinned `search_path`) | DB-13, DB-18 |
| 3.2 | `rw_fn_message_history` — keyset | OBJ-04, MSG-04 |
| 3.3 | `rw_fn_search_messages` — `websearch_to_tsquery` + `ts_headline`, `SECURITY INVOKER` | OBJ-05, SRCH-* |
| 3.4 | `rw_fn_rag_retrieve_candidates` — `SECURITY INVOKER`, membership `EXISTS`, then ANN | OBJ-06, AI-07, AI-08 |
| 3.5 | `rw_fn_copilot_usage_summary` | OBJ-07, AI-15 |
| 3.6 | `rw_fn_send_message`, `rw_fn_edit_message`, `rw_fn_soft_delete_message`, `rw_fn_open_direct_channel` (transactional) | DB-12, MSG-* |
| 3.7 | Auth functions: `rw_fn_verify_credentials`, `rw_fn_issue_refresh_token`, `rw_fn_rotate_refresh_token`, `rw_fn_revoke_family` | AUTH-04…AUTH-07 |
| 3.8 | `V5__procedures.sql` — `rw_sp_query_users`, `rw_sp_modify_user` | OBJ-01, OBJ-02, DB-14 |
| 3.9 | `V6__views.sql` — `rw_v_user_conversations` | OBJ-03, DB-15 |
| 3.10 | `V7__triggers.sql` — `updated_at`; block physical delete; edit history; embedding invalidation + job enqueue; `pg_notify`; last-owner guard; direct-channel membership guard | DB-16, MSG-01, MSG-02, AI-06, BR-07, BR-10 |
| 3.11 | `V8__rls.sql` — `ENABLE` + `FORCE` on all data tables, every policy from `SECURITY_MODEL.md` §4 | DB-17, SEC-10, SEC-12 |
| 3.12 | `V9__grants.sql` — `rw_app` DML-only, no `DELETE`, column-scoped `UPDATE` on `rw_messages`, `EXECUTE` allow-list | SEC-11, D-07 |
| 3.13 | Seed loader (Spring runner, JSON-schema validated, one transaction, idempotent) | DB-20, OPS-03 |

## PHASE 4 — Authentication  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 4.1 | Maven project, Spring Boot 3.3 / Java 21, package skeleton, Flyway wiring | ARCH-01 |
| 4.2 | Domain ports, value objects, errors | ARCH-03 |
| 4.3 | `RlsTransactionRunner` + out-of-transaction guard | DB-18, SEC-13 |
| 4.4 | Argon2id encoder; credential adapter | AUTH-01 |
| 4.5 | JWT issue/verify; `AuthenticatedActor`; auth filter | AUTH-02, AUTH-03, AUTH-09 |
| 4.6 | Refresh rotation + reuse detection; `HttpOnly` cookie | AUTH-04…AUTH-08 |
| 4.7 | `/auth/login`, `/auth/refresh`, `/auth/logout`, `/me` | API-01, API-05 |
| 4.8 | Correlation-id filter, Problem Details advice, validation | API-02, API-03, API-04 |
| 4.9 | Rate limiting on auth routes | §10 of API.md |
| 4.10 | ArchUnit layer test | ARCH-04, TEST-11 |

## PHASE 5 — Messaging API  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 5.1 | Channel + membership repositories and use cases | — |
| 5.2 | `/conversations` over the view | OBJ-03 |
| 5.3 | Channel endpoints incl. `POST /channels/direct` | BR-08 |
| 5.4 | Message history — keyset, opaque cursor | OBJ-04, API-07 |
| 5.5 | Idempotent send | IBR-04 |
| 5.6 | Edit + logical delete | MSG-01, MSG-02 |
| 5.7 | Read cursor | — |
| 5.8 | OpenAPI generation | API-08 |

## PHASE 6 — Realtime  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 6.1 | `PostgresNotificationListener` on a dedicated connection | RT-01 |
| 6.2 | `SseHub` with membership-filtered fan-out | RT-06 |
| 6.3 | `POST /realtime/ticket` (single-use, 60 s) | D-15 |
| 6.4 | `GET /realtime/stream` + heartbeat + lifecycle cleanup | RT-02 |
| 6.5 | `Last-Event-ID` handling and documented catch-up contract | RT-04, RT-05 |

## PHASE 7 — Search  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 7.1 | Search repository over `rw_fn_search_messages` | SRCH-01, SRCH-02 |
| 7.2 | `GET /search/messages` with rank keyset cursor | SRCH-04 |
| 7.3 | `<mark>` highlight contract + sanitizer note | SRCH-03 |

## PHASE 8 — Frontend  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 8.1 | Vite + TS + Tailwind + router + providers | FE-01 |
| 8.2 | Typed API client, silent refresh, problem-details parsing, correlation id | API-03 |
| 8.3 | i18n scaffolding, `es`/`en` catalogues, lint rule against literal JSX strings | FE-14…FE-16 |
| 8.4 | Login and session | AUTH |
| 8.5 | Conversation list | FE-02 |
| 8.6 | Message list: lazy history, scroll anchoring, virtualization | FE-07, FE-08 |
| 8.7 | Composer: optimistic send, `pending`/`sent`/`failed`, retry | FE-05, FE-06 |
| 8.8 | SSE hook + reconciliation by `clientMessageId` + catch-up | RT-03, RT-04 |
| 8.9 | Search UI with highlight | SRCH-03 |
| 8.10 | Copilot panel: question, answer, citation chips, refusal states | FE-03, AI-11 |
| 8.11 | Profile view + locale switch | FE-04 |
| 8.12 | Loading / empty / error states everywhere | FE-09…FE-11 |
| 8.13 | Responsive breakpoints, mobile drawer | FE-12, FE-13 |

## PHASE 9 — AI RAG  *(Sonnet, security review by Opus)*
| # | Task | Covers |
|---|------|--------|
| 9.1 | `AIProvider` / `EmbeddingProvider` ports — separate, independently configured | AI-02, AI-16 |
| 9.2 | `OpenAiCompatibleChatProvider` targeting **Groq** (`https://api.groq.com/openai/v1`, `llama-3.3-70b-versatile`); same adapter serves OpenAI/Ollama | AI-03 |
| 9.3 | `DeterministicLocalEmbeddingProvider` (default, offline) + `OpenAiCompatibleEmbeddingProvider`; startup validation that the model emits 1536 dimensions | D-06, D-21 |
| 9.3b | Independent failure handling: unreachable Groq degrades chat to `PROVIDER_UNAVAILABLE` without stopping the embedding worker, and vice versa | AI-16 |
| 9.4 | Embedding worker draining `rw_embedding_jobs` | AI-05, AI-06 |
| 9.5 | System prompt v1, persisted and versioned | AI-14 |
| 9.6 | `AskCopilotUseCase`: embed → retrieve → assemble → call → validate citations → persist | AI-01, AI-07 |
| 9.7 | Untrusted-context framing + delimiter neutralization | AI-09 |
| 9.8 | Refusal statuses | AI-10 |
| 9.9 | Citation allow-list gate | AI-12 |
| 9.10 | Server-built identity block | AI-13 |
| 9.11 | Usage ledger + `GET /copilot/usage` | AI-15, OBJ-07 |

## PHASE 10 — Security testing  *(Opus)*
| # | Task | Covers |
|---|------|--------|
| 10.1 | Testcontainers base: `pgvector/pgvector:pg16`, real Flyway migrations, connections **as `rw_app`** | TEST-01 |
| 10.2 | Fixture builder mirroring production actor propagation | — |
| 10.3 | **TEST 1** — non-member cannot read a private channel (repository and API) | TEST-02 |
| 10.4 | **TEST 2** — third party cannot read an A↔B direct channel | TEST-03 |
| 10.5 | RAG isolation: near-identical foreign message never retrieved or cited | TEST-04 |
| 10.6 | Search isolation: unique foreign term returns nothing | TEST-05 |
| 10.7 | Physical delete rejected (privilege and trigger) | TEST-06 |
| 10.8 | `rw_app` role flags | TEST-07 |
| 10.9 | Actor unset → zero rows on every RLS table | TEST-08 |
| 10.10 | Refresh rotation, replay, family revocation, partial unique index | TEST-09 |
| 10.11 | Edit history + embedding invalidation triggers | TEST-10 |
| 10.12 | Static checks: no `OFFSET`, no string-concatenated SQL, no `SECURITY DEFINER` on read paths | MSG-03, MSG-04 |

## PHASE 11 — Docker  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 11.1 | `backend/Dockerfile` (multi-stage, non-root) | OPS-01 |
| 11.2 | `frontend/Dockerfile` + nginx config | OPS-01 |
| 11.3 | `docker-compose.yml`: `db` → `migrate` → `backend` → `frontend`, healthchecks | OPS-01, OPS-07 |
| 11.4 | `.env.example` + variable documentation | OPS-05, OPS-06 |
| 11.5 | Clean-machine verification run | OPS-04 |

## PHASE 12 — Documentation  *(Sonnet)*
| # | Task | Covers |
|---|------|--------|
| 12.1 | `README.md` — overview, quick start, commands, structure | DOC-02 |
| 12.2 | `docs/TESTING.md` | DOC-08 |
| 12.3 | `docs/EVIDENCE.md` — reproduce every security claim | DOC-09 |
| 12.4 | `docs/DEPLOYMENT.md` — local, Compose, Supabase | DOC-10 |
| 12.5 | Refresh `PROGRESS.md`, `DECISIONS.md`, ER diagram | — |

## PHASE 13 — Final audit  *(Opus)*
| # | Task | Covers |
|---|------|--------|
| 13.1 | Full suite green; capture output | TEST-* |
| 13.2 | Repository hygiene sweep: secrets, keys, local paths, temp files, assistant artifacts | REPO-01…REPO-03 |
| 13.3 | Independent re-verification of every `SEC-*` claim | SEC-* |
| 13.4 | `FINAL_AUDIT.md` — requirement / status / evidence / residual risk | DOC-11 |
| 13.5 | Final commits | REPO-04 |
