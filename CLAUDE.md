# CLAUDE.md — Working Agreement and Context Map

Riwi Co. S.A.S. internal messaging platform. This file is the entry point for every work session.
Read this, then `PROGRESS.md`, then only the documents relevant to the current task.

---

## Read order

1. `CLAUDE.md` (this file) — conventions and invariants
2. `PROGRESS.md` — what is done, what is next, current phase
3. Task-relevant only:
   - schema / SQL work → `docs/DATA_MODEL.md`
   - anything touching authorization → `docs/SECURITY_MODEL.md` (**mandatory** before editing RLS, retrieval or auth)
   - endpoints → `docs/API.md`
   - "why is it like this?" → `DECISIONS.md`
   - acceptance → `REQUIREMENTS.md`

Do not re-read the whole repository. Inspect the files the current task touches.

---

## Non-negotiable invariants

Violating any of these fails the assessment. They are not preferences.

1. **Authorization lives in PostgreSQL.** Every user-content query runs through RLS with `app.current_user_id` set. Backend filtering is UX, never the control.
2. **The actor comes only from the verified JWT.** No endpoint, use case or SQL function accepts a caller-supplied actor id.
3. **No global vector search followed by application filtering.** The ANN operator appears exactly once, inside a `SECURITY INVOKER` function whose `FROM` clause is already RLS-constrained.
4. **No SQL string concatenation.** Bind parameters only, including `set_config`.
5. **No `OFFSET` pagination.** Keyset only, everywhere.
6. **Messages are never physically deleted.** No `DELETE` grant, no `DELETE` policy, and a `BEFORE DELETE` trigger that raises.
7. **`rw_app` is `NOSUPERUSER NOBYPASSRLS`** and is the only role the runtime uses.
8. **The `domain` layer imports nothing from Spring, JPA, JDBC, servlet or Jackson.**
9. **Secrets come from the environment.** Never a literal in source, never in a committed file, never in a log line.
10. **The AI never receives unauthorized content**, and every cited message id is intersected with the RLS-produced candidate set before responding.

---

## Naming and conventions

| Thing | Convention | Example |
|-------|-----------|---------|
| Database | fixed name | `bd_juandiego_garcia_nakamoto` |
| Schema | `rw` | `rw.rw_messages` |
| Tables | `rw_` + English plural | `rw_channel_members` |
| Columns | `rw_` + English snake_case | `rw_created_at` |
| Functions | `rw_fn_` | `rw_fn_is_channel_member` |
| Procedures | `rw_sp_` | `rw_sp_query_users` |
| Views | `rw_v_` | `rw_v_user_conversations` |
| Triggers | `rw_trg_` | `rw_trg_messages_block_delete` |
| Indexes | `rw_ix_` / `rw_ux_` (unique) | `rw_ix_messages_keyset` |
| Migrations | `V<n>__snake_case.sql` | `V3__functions.sql` |
| Java package | `co.riwi.messaging.<layer>` | `co.riwi.messaging.domain.model` |
| Use cases | `<Verb><Noun>UseCase`, one public method `execute` | `SendMessageUseCase` |
| REST | `/api/v1/...`, plural nouns, no verbs in paths | `/api/v1/channels/{id}/messages` |
| i18n keys | `feature.component.element` | `messages.composer.placeholder` |
| Timestamps | `timestamptz`, UTC, ISO-8601 in JSON | `2026-08-27T14:03:11.482Z` |

All identifiers, code, comments and documentation in **English**. UI strings live in `frontend/src/shared/i18n/locales/{es,en}.json` and nowhere else.

---

## Layer rules

```
presentation ──► application ──► domain ◄── infrastructure
```

- `domain` — pure Java. Entities, value objects, errors, ports. No framework imports.
- `application` — thin use cases: validate → invoke port → map → return. No SQL, no HTTP.
- `infrastructure` — JDBC adapters, JWT, SSE, AI providers, config. Implements domain ports.
- `presentation` — controllers, DTOs, problem-details advice, filters.

ArchUnit enforces this (`TEST-11`). If a test fails here, fix the design, not the test.

---

## Commands

```bash
# full stack
docker compose up --build

# database only, then migrate + seed
docker compose up -d db
docker compose run --rm migrate
docker compose run --rm backend java -jar app.jar --spring.profiles.active=seed

# backend
cd backend && ./mvnw spring-boot:run
cd backend && ./mvnw test                       # includes the Testcontainers security suite
cd backend && ./mvnw test -Dtest=RlsSecurityIT   # the two mandatory tests

# frontend
cd frontend && npm run dev
cd frontend && npm run build && npm run lint
```

---

## Working rules for this project

- Prefer changing SQL over changing Java when the rule is a data rule.
- Any new endpoint returning user content must be accompanied by a negative test proving a non-member gets nothing.
- Any new SQL function that reads user content must be `SECURITY INVOKER`. If you think you need `SECURITY DEFINER`, stop and justify it in `DECISIONS.md` first.
- Update `PROGRESS.md` at the end of every phase; record every scope cut in `DECISIONS.md` §D-20.
- Commit at stable milestones with a message naming the phase and the requirement ids covered.
- Never commit `.env`, keys, local paths, generated artifacts or scratch files.
