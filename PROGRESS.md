# PROGRESS.md

Living state of the project. Updated at the end of every phase.
Plan: `docs/BACKLOG.md` · Acceptance: `REQUIREMENTS.md` · Rationale: `DECISIONS.md`

**Current phase:** 2 — complete. Beginning Phase 3 (functions, procedures, views, triggers, RLS).
**Last updated:** 2026-08-27

> **Amendment (2026-08-27).** MVP chat provider is **Groq**, not xAI/Grok. Groq is OpenAI-compatible, so the adapter is unchanged — but Groq publishes **no embedding models**, so chat and embeddings are now configured as two independent providers. Recorded as `DECISIONS.md` D-21 and requirement `AI-16`. No change to the schema, the RLS model, the retrieval order or any security property.

---

## Phase status

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| 0 | Initialization | ✅ done | Requirements, architecture, ER + 3NF, security model, API contract, backlog |
| 1 | Business corpus, seed, ER render | ✅ done | seed.json validated, ER embedded in DATA_MODEL.md; standalone SVG blocked locally (see note) |
| 2 | PostgreSQL schema | ✅ done | 20 tables, migrations verified against a live pgvector/pg16 container |
| 3 | Functions, procedures, views, triggers, RLS | ⏸ not started | Opus — security critical |
| 4 | Authentication | ⏸ not started | |
| 5 | Messaging REST API | ⏸ not started | |
| 6 | Realtime (SSE + LISTEN/NOTIFY) | ⏸ not started | |
| 7 | Search | ⏸ not started | |
| 8 | Frontend | ⏸ not started | |
| 9 | AI RAG and embeddings | ⏸ not started | |
| 10 | Security testing | ⏸ not started | Opus — the two mandatory tests |
| 11 | Docker | ⏸ not started | |
| 12 | Documentation | ⏸ not started | |
| 13 | Final audit | ⏸ not started | Opus — produces `FINAL_AUDIT.md` |

---

## Delivered in Phase 0

```
REQUIREMENTS.md          113 traceable requirement ids across 14 groups
CLAUDE.md                conventions, 10 non-negotiable invariants, read order
ARCHITECTURE.md          system view, Clean Architecture layers, data flows, realtime, RAG, frontend
DECISIONS.md             19 ADRs + MVP / POST-MVP scope
PROGRESS.md              this file
docs/DATA_MODEL.md       20 relations, ER diagram, cardinalities, 18 explicit + 9 implicit rules, 1NF→3NF
docs/SECURITY_MODEL.md   14 threats, roles, actor propagation, every RLS policy, RAG pipeline, evidence map
docs/API.md              full REST contract, error model, keyset cursors, SSE, copilot
docs/BACKLOG.md          14 phases, task-level, each mapped to requirement ids
docs/diagrams/er.mmd     ER source
.env.example .gitignore  environment contract, hygiene
```

Directory skeleton created for `database/`, `backend/`, `frontend/`, `docs/`.

---

## Delivered in Phase 1

```
database/seeds/seed.schema.json   JSON Schema validating seed.json's shape
database/seeds/seed.json          business corpus (model) + loadable data
```

Data: 2 roles, 3 channel types, 3 member roles, 2 AI providers (`groq` chat,
`local` embeddings) + 1 embedding model, 8 users (mixed es/en locale, 2
ADMIN), 6 channels (2 public, 2 private, 2 direct), 23 memberships, 46
messages, 1 active system prompt (v2026.08.1).

Negative fixtures verified programmatically (`node` referential-integrity +
fixture check, all assertions passed):
- **TEST-05 fixture** — the term `kryptonite` appears only inside the
  `incident-response` private channel (5 of 8 users are non-members).
- **TEST-04 fixture** — `proyecto-atlas` (public) and `incident-response`
  (private) each contain a message about "the Thursday 10am demo",
  semantically close but in channels with disjoint membership, so retrieval
  isolation is provable once Phase 9/10 wire up the RAG pipeline and tests.

**Known local-environment limitation:** `docs/diagrams/er.svg` was not
generated — `mermaid-cli`'s headless Chrome fails to launch on this machine
(`STATUS_DLL_NOT_FOUND`, a missing Windows runtime dependency unrelated to
the project). Not a scope gap: the same Mermaid source (`docs/diagrams/er.mmd`)
is already embedded as a live diagram in `docs/DATA_MODEL.md` §2, which
GitHub/GitLab render natively. Revisit only if a reviewer specifically needs
a standalone image file.

---

## Environment verified on this machine

| Tool | Version |
|------|---------|
| JDK (`JAVA_HOME`) | Temurin 21.0.10 |
| Maven | 3.9.14 |
| Node | 24.19.0 / npm 11.17.0 |
| Docker | 29.2.1 |
| Docker Compose | v5.0.2 |
| psql client | 17.9 |

Targets: Java 21, Spring Boot 3.3, PostgreSQL 16 + pgvector, React 19 + Vite.

---

## Open human actions

| id | Blocking? | Item |
|----|-----------|------|
| HUMAN-01 | No — only Supabase deployment | Supabase project URL, database password, connection string |
| HUMAN-02 | No — only AI answer quality | **Groq** API key (`AI_API_KEY`; base URL and model pre-filled) |
| HUMAN-02b | No — only retrieval relevance | Embedding provider base URL + model + key (`EMBEDDING_*`). Defaults to the offline local provider; Groq has no embeddings |
| HUMAN-03 | No — only when pushing | GitHub repository URL and push authorization |
| HUMAN-04 | No — generated locally for dev | Production `JWT_SECRET` (256-bit) |

None blocks Phase 1 through 13 locally. Details in `README.md` § Human actions.

---

## Risks being tracked

| # | Risk | Mitigation |
|---|------|-----------|
| R1 | RLS recursion between `rw_messages` and `rw_channel_members` policies | `SECURITY DEFINER` `rw_fn_is_channel_member` with pinned `search_path`, no user parameter (D-08) |
| R2 | Connection pool leaking `app.current_user_id` across requests | `set_config(..., is_local => true)` + out-of-transaction guard + `DISCARD ALL` on return (D-02) |
| R3 | HNSW index degradation under RLS filtering | Accepted; correctness over latency, alternatives recorded (D-09) |
| R4 | Prompt injection from message bodies | Citation allow-list as a hard gate, plus framing, escaping, no tool access (D-17) |
| R5 | Assessment expects Supabase; Compose must run offline | Portable SQL, local Postgres primary, Supabase as documented deployment target (D-04) |
| R6 | Scope breadth vs. token budget | Vertical MVP first; cuts recorded in `DECISIONS.md` §D-20 and surfaced in `FINAL_AUDIT.md` |
| R7 | `rw_migrator` holds `BYPASSRLS` | Migration-only, separate credentials, container exits, asserted by `TEST-07` (D-07) |
| R8 | Groq offers no embeddings, so RAG depends on a second provider | Deterministic local provider is the default and needs no credentials; the 1536-dimension contract is validated at startup (D-21) |

---

## Next action

Begin **Phase 3** — the security-critical phase: `rw_fn_current_user_id`/`rw_fn_is_channel_member`, message/search/RAG/usage functions, auth functions, procedures, the conversations view, triggers (delete-block, edit-history, embedding-invalidation, last-owner guard, direct-channel guard), full RLS policy set, and grants (`docs/BACKLOG.md` Phase 3).
