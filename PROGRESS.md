# PROGRESS.md

Living state of the project. Updated at the end of every phase.
Plan: `docs/BACKLOG.md` · Acceptance: `REQUIREMENTS.md` · Rationale: `DECISIONS.md`

**Current phase:** 4 — complete. Beginning Phase 5 (messaging REST API).
**Last updated:** 2026-08-27

> **Amendment (2026-08-27).** MVP chat provider is **Groq**, not xAI/Grok. Groq is OpenAI-compatible, so the adapter is unchanged — but Groq publishes **no embedding models**, so chat and embeddings are now configured as two independent providers. Recorded as `DECISIONS.md` D-21 and requirement `AI-16`. No change to the schema, the RLS model, the retrieval order or any security property.

---

## Phase status

| Phase | Name | Status | Notes |
|-------|------|--------|-------|
| 0 | Initialization | ✅ done | Requirements, architecture, ER + 3NF, security model, API contract, backlog |
| 1 | Business corpus, seed, ER render | ✅ done | seed.json validated, ER embedded in DATA_MODEL.md; standalone SVG blocked locally (see note) |
| 2 | PostgreSQL schema | ✅ done | 20 tables, migrations verified against a live pgvector/pg16 container |
| 3 | Functions, procedures, views, triggers, RLS | ✅ done | 6 migration files, verified live as rw_app; 3 real bugs found and fixed |
| 4 | Authentication | ✅ done | full backend skeleton, auth + users endpoints verified live end-to-end |
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

## Delivered in Phase 3

```
database/migrations/V4__functions.sql    ~25 functions: actor, RLS recursion
                                          breakers, message history/search/RAG/
                                          usage-summary, send/edit/soft-delete,
                                          channel join/leave/remove/update/
                                          create, direct-channel open, auth
                                          (credential, refresh rotation), the
                                          embedding-worker's 3 functions
database/migrations/V5__procedures.sql   rw_sp_query_users (OBJ-01),
                                          rw_sp_modify_user (OBJ-02)
database/migrations/V6__views.sql        rw_v_user_conversations (OBJ-03)
database/migrations/V7__triggers.sql     updated_at, block-delete, edit-history,
                                          embedding invalidation + pg_notify,
                                          last-owner guard, direct-channel guard
database/migrations/V8__rls.sql          ENABLE + FORCE on every content table,
                                          every policy from SECURITY_MODEL.md §4
database/migrations/V9__grants.sql       rw_app's complete privilege surface:
                                          column-scoped UPDATEs, zero DELETE
                                          grants anywhere, EXECUTE allow-list
```

**Verified against a live `pgvector/pg16` container as `rw_app`** (not just
applied — behaviorally tested with real actor switches), not merely reviewed
on paper:

- TEST-08 equivalent: actor unset → 0 rows on every RLS table
- SEC-01/TEST-02 equivalent: non-member reads a private channel directly and
  via `rw_fn_message_history` → 0 rows both ways
- SEC-02/TEST-03 equivalent: third party reads someone else's DM → 0 rows
- SEC-04/SRCH-02 equivalent: non-member searches a term unique to a private
  channel ("kryptonite") → 0 rows; the member who owns it gets 3 results with
  correct `<mark>` highlighting
- **IBR-01, the load-bearing one**: an ADMIN-role user (hugo) still gets 0
  rows reading a channel he does not belong to — no admin content bypass,
  proven live, not just absent from the SQL
- IBR-02: a non-member discovers a PUBLIC channel exists, cannot read its
  messages, self-joins via `rw_fn_join_channel`, then can
- `rw_v_user_conversations`: returns exactly the actor's own channels, not
  merely-discoverable ones
- BR-07: DIRECT channel membership is immutable once established — leaving
  or adding a third member both raise, even via a `SECURITY DEFINER` path
- BR-08: re-opening an existing DM is idempotent (same channel, no duplicate)
- BR-10: the sole owner of a channel cannot leave it
- MSG-01: a raw `DELETE` on `rw_messages` as `rw_app` → permission denied
- Both stored procedures exercised inside an explicit transaction (matching
  real JDBC usage): directory search, self-service profile edit, admin-only
  role promotion, and a non-admin's role-change attempt correctly rejected

**Three real bugs found only by testing against a live database**, all now
fixed and re-verified:
1. `rw_v_user_conversations` had no `GRANT SELECT` for `rw_app` — a view is
   a distinct grantable object, not inherited from its underlying tables.
2. Five functions declared their `ROW_COUNT` capture variable as `boolean`
   instead of `integer`, which silently miscompiled the following `> 0`
   comparison. Latent until a call actually reached its `RETURN` statement.
3. `rw_channel_members`'s `SELECT` policy didn't let a user see their own
   (non-existent) row in a channel they hadn't joined yet — which broke
   `rw_fn_join_channel`'s `ON CONFLICT DO NOTHING`, because PostgreSQL must
   evaluate the `SELECT` policy to resolve the conflict arbiter even for
   `DO NOTHING`. Documented as an implementation finding in
   `docs/SECURITY_MODEL.md`.

All three are the kind of defect that a design review reading the SQL would
not have caught — this is why Phase 3 verified everything live rather than
trusting the migrations to be correct because they applied without error.

`DECISIONS.md` D-08 rewritten with the complete, honest `SECURITY DEFINER`
function inventory (grew from an estimated 1 to ~20, each individually
justified in a table) once every structural reason for elevation — RLS
recursion, zero table grants on credentials/tokens, the blanket no-`DELETE`
rule, and the embedding worker having no authenticated actor — was worked
through against the real schema.

The Phase 3 backlog's seed-loader task (3.13) is deferred to Phase 4, since
it needs the Maven/Spring Boot skeleton that phase creates.

---

## Delivered in Phase 4

```
backend/pom.xml                    Spring Boot 3.3 / Java 21, Spring JDBC
                                    (no JPA), JJWT, Argon2 (Spring Security
                                    Crypto), bucket4j, springdoc, ArchUnit,
                                    Testcontainers
backend/src/main/java/co/riwi/messaging/
  domain/model/    ~15 value objects + AuthenticatedActor, UserProfile, etc.
  domain/error/    DomainError hierarchy — ValidationError, AuthenticationFailed,
                    OperationNotPermitted, ResourceNotVisible, ConflictError,
                    ProviderUnavailable
  domain/port/     TransactionRunner, Clock, TokenHasher, PasswordHasher,
                    AccessTokenPort, CredentialPort, RefreshTokenPort,
                    UserRepository, AdminUserPort — zero framework imports
  application/     LoginUseCase, RefreshSessionUseCase, LogoutUseCase,
                    GetProfileUseCase, UpdateProfileUseCase, QueryUsersUseCase,
                    ModifyUserUseCase — each validate/invoke/map/return
  infrastructure/  RlsTransactionRunner (D-02), JDBC adapters for every port,
                    JwtService, Argon2PasswordHasher, Sha256TokenHasher,
                    CorrelationIdFilter, JwtAuthenticationFilter,
                    ActorArgumentResolver (@CurrentActor), RateLimitFilter,
                    two-DataSource config (rw_app primary, rw_migrator
                    seed-only), SeedRunner (real seed.json loader)
  presentation/    AuthController, ProfileController, UsersController,
                    HealthController, GlobalExceptionHandler (RFC 9457 +
                    PostgreSQL SQLSTATE translation)
backend/src/test/java/.../ArchitectureTest.java   5 ArchUnit rules (TEST-11)
database/migrations/V10..V12                       3 new migrations, see below
```

**Verified against a live pgvector/pg16 database end to end** — not just
compiled: the real `mvn spring-boot:run` app, hit with real `curl` requests,
through the real 9 (now 12) migrations and the real seed data:

- Login with the seeded Argon2id hash (regenerated for real — the Phase 1
  placeholder hash was fabricated and could never have verified)
- Wrong password and unknown email return the **identical** 401 shape
  (no account enumeration)
- `GET /me`, `PATCH /me` (self-service, RLS-scoped)
- `GET /users` (OBJ-01, the REFCURSOR-returning stored procedure) and
  `PATCH /users/{id}` (OBJ-02): an ADMIN can promote/deactivate; a MEMBER
  attempting the same gets a proper 403, not a raw 500
- `POST /auth/refresh`: rotation issues a genuinely different token;
  replaying the old (already-rotated) token returns `TOKEN_REUSE_DETECTED`
  and **the whole family dies** — even the legitimately-current token from
  the same family stops working, exactly as AUTH-06 requires
- `POST /auth/logout` revokes the family; a subsequent refresh with that
  cookie fails; logout with no cookie at all is a silent no-op (204)
- No token / garbage token on a protected route → 401, never a stack trace
- `ArchitectureTest`: all 5 layering rules pass

**Five real bugs found only by running the actual stack, all fixed and
re-verified live** (none would have been caught by reading the code or by
unit tests against mocks — this is the point of testing against a real
database):

1. **Spring `@Primary` beats by-name autowiring.** `SeedRunner`'s
   constructor parameter was literally named `rwMigratorDataSource`, but
   Spring resolved it to the `@Primary` `rwAppDataSource` anyway — `@Primary`
   short-circuits before parameter-name matching is even considered. Fixed
   with an explicit `@Qualifier`. Without this, the seed loader would have
   silently tried to seed as `rw_app`, which has no `INSERT` grant on lookup
   tables — it failed loudly, but for the wrong reason if you didn't know
   this quirk existed.
2. **JDBC binds a UUID-shaped `String` as `varchar`, not `uuid`.** A literal
   UUID string in raw SQL text gets Postgres's implicit assignment cast; a
   JDBC bind *parameter* carrying the same string does not, and fails with
   "column is of type uuid but expression is of type character varying."
   Fixed in `SeedRunner` by detecting UUID-shaped strings and binding
   `java.util.UUID` instead.
3. **`SELECT fn()` for a void-returning function still yields a result
   set**, which `JdbcTemplate.update()` (→ `executeUpdate()`) rejects.
   Broke `RlsTransactionRunner`'s `set_config` call (i.e. **every**
   authenticated request would have failed), plus two more call sites
   (`recordLoginAttempt`, `revokeFamily`). Fixed by switching all three to
   `jdbc.query(sql, rs -> null, args)` (→ `executeQuery()`).
4. **The JDBC `{call ...}` escape mis-translates a real `PROCEDURE`.**
   PGJDBC turns `{call rw_sp_query_users(...)}` with a registered OUT
   parameter into a function-style invocation, which PostgreSQL rejects for
   an actual `CREATE PROCEDURE` ("... is a procedure, use CALL"). Fixed by
   issuing literal `CALL rw.rw_sp_query_users(...)` SQL text instead of the
   escape syntax.
5. **PostgreSQL SQLSTATEs from a `RAISE EXCEPTION` weren't translated to
   domain errors at all** — `rw_sp_modify_user`'s internal "only an admin"
   check correctly raised `42501`, but the application had no mapping from
   that to `OperationNotPermitted`, so it surfaced as an opaque 500 instead
   of the 403 it actually is. Fixed with a `DataAccessException` handler in
   `GlobalExceptionHandler` that maps `42501`→403, `P0002`→404,
   `23514`/`23505`→409, `28000`→401, `22023`→422, documented in
   `ARCHITECTURE.md` §3.5.

Two more fixes landed in the SQL layer itself while chasing the above (both
new migrations, since V4/V7 are already-applied and committed):

- **V11** — the `BEFORE INSERT` direct-channel guard trigger (V7) counted
  existing rows without checking whether the incoming row was itself one of
  them, so re-running the seed loader against an already-seeded database hit
  "a DIRECT channel may never have more than two members" on the harmless
  idempotent re-insert of an existing pair. `ON CONFLICT DO NOTHING` never
  got the chance to resolve it, because `BEFORE INSERT` triggers fire first.
- **V12** — `rw_fn_rotate_refresh_token` inserted the successor token
  *before* revoking the presented one, so for one instant two "live" rows
  existed in the same family — exactly what PU-1
  (`rw_ux_refresh_tokens_family_active`) exists to forbid, so every rotation
  failed with a unique-violation. Fixed the statement order: revoke, insert,
  link `replaced_by`. (Also fixed, in the same migration before it was ever
  committed: the same `RETURNS TABLE`/column-name collision bug as
  `rw_fn_open_direct_channel` — `rw_family_id` in a bare `WHERE` clause was
  ambiguous against the function's own OUT parameter.)

The real Argon2id hash for the seed password (`RiwiDemo#2026`) was generated
with the actual `Argon2PasswordHasher` and written into `seed.json`,
replacing the earlier fabricated placeholder from Phase 1 — seeded users can
now genuinely log in.

`database/migrations/V10__logout.sql`, `V11__fix_direct_channel_guard_idempotency.sql`,
`V12__fix_refresh_rotation_order.sql` are additive-only: none of them touch
an already-applied migration file, matching the project's Flyway-checksum
convention (documented first when `V10` was created for the logout gap).

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

Begin **Phase 5** — messaging REST API: channel/membership use cases and controllers over the already-built V4 functions (`rw_fn_message_history`, `rw_fn_send_message`, `rw_fn_edit_message`, `rw_fn_soft_delete_message`, `rw_fn_join_channel`, `rw_fn_leave_channel`, `rw_fn_create_channel`, `rw_fn_open_direct_channel`), the `rw_v_user_conversations` view, keyset-cursor encoding, and OpenAPI generation (`docs/BACKLOG.md` Phase 5). Every message-writing endpoint gets a live negative test (non-member send attempt) before being called done, per `CLAUDE.md`.
