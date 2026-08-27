# DECISIONS.md — Architecture Decision Record

Each entry: context, decision, alternatives rejected, consequences. Status `ACCEPTED` unless noted.
Scope cuts are tracked in §D-20 with `MVP` / `POST-MVP` classification.

---

## D-01 — PostgreSQL is the authorization boundary
**Status:** ACCEPTED (foundational)

**Context.** The absolute requirement forbids any path by which an authenticated user reads a channel they do not belong to — including search, embeddings, vector similarity and AI context. Application-layer filtering fails this by construction: it is one forgotten `WHERE` clause away from a leak, and the leak is silent.

**Decision.** Row Level Security on every table holding user content. The application role `rw_app` is `NOBYPASSRLS`, and RLS is `FORCE`d so even the owner is subject. Backend filtering exists for UX only; removing every `WHERE` clause from the Java code would degrade the product to "returns nothing", never to a leak.

**Rejected.** Service-layer authorization only (one bug = full breach); a database view layer without RLS (views are bypassable by any direct query and by any new repository method).

**Consequences.** Every query must run inside a transaction carrying `app.current_user_id`. Some queries are slower because the planner filters before it can use an index optimally — accepted, and quantified in D-09.

---

## D-02 — `app.current_user_id` via `set_config(..., is_local => true)`
**Status:** ACCEPTED

**Context.** The actor must reach PostgreSQL for RLS to evaluate, on pooled connections, without ever being interpolated into SQL.

**Decision.** `SELECT set_config('app.current_user_id', :actor, true)` as the first statement of every transaction, from a single `RlsTransactionRunner`.

**Rejected.** `SET LOCAL app.current_user_id = '<uuid>'` — cannot take a bind parameter, so it would require string interpolation and violate `MSG-03`. Session-scoped `SET` — survives connection reuse and can leak one request's actor into the next. A separate database user per application user — unmanageable at any scale, and a connection-pool non-starter.

**Consequences.** Repositories must not be called outside a transaction; a guard throws if they are. Setting is discarded on commit *and* rollback, so pool reuse is safe.

---

## D-03 — Spring JDBC, not JPA/Hibernate
**Status:** ACCEPTED

**Context.** The security model puts the real logic in SQL functions, RLS policies and triggers. The persistence layer's job is to call them faithfully.

**Decision.** `NamedParameterJdbcTemplate` adapters implementing domain ports.

**Rejected.** JPA/Hibernate — (a) it obscures which SQL actually runs, which is unacceptable when a `WHERE` clause is a security control; (b) the first-level cache can serve a row across a transaction boundary where the RLS actor differs; (c) mapping `vector`, `tsvector` and set-returning functions means dropping to native queries anyway; (d) `ARCH-03` forbids JPA types in `domain`, so entities would need a second mapping layer regardless.

**Consequences.** More hand-written mapping. In exchange, every statement is visible, parameterized and reviewable — and `MSG-03`/`MSG-04` are verifiable by reading the code.

---

## D-04 — Local Dockerized PostgreSQL as the primary target; Supabase as a deployment target
**Status:** ACCEPTED

**Context.** The brief says "PostgreSQL 15+ through Supabase" and also "`docker compose up` must start the database". A hosted-only database cannot satisfy the second, and reviewers must be able to run the stack offline.

**Decision.** `pgvector/pgvector:pg16` in Compose is the primary environment for development, evaluation and the security test suite. Every migration is written in portable SQL that applies unchanged to a Supabase project; `docs/DEPLOYMENT.md` documents the Supabase path, which needs only credentials the developer supplies.

**Rejected.** Supabase-only (breaks `OPS-01`, makes tests depend on a network service and a shared database, and blocks the whole assessment behind credentials).

**Consequences.** Supabase deployment is verified only once credentials exist — tracked as `HUMAN-01`. No Supabase-proprietary feature is used anywhere on the critical path, so this stays a deployment choice rather than an architectural one.

---

## D-05 — Realtime via SSE over PostgreSQL `LISTEN`/`NOTIFY`
**Status:** ACCEPTED

**Context.** "The simplest reliable solution compatible with the selected architecture and Supabase", with no overengineering.

**Decision.** `AFTER INSERT` trigger → `pg_notify` (ids only) → one listener connection per instance → in-process `SseHub` → membership-filtered fan-out. Clients fetch content through the normal RLS-protected REST endpoints.

**Rejected.**
- *Supabase Realtime* — requires Supabase-issued JWTs and an anon key in the browser. We already own authentication (custom JWT + rotation), so adopting it would mean maintaining two authorization models, one of which is evaluated by a service outside our RLS transaction. That is more surface, not less.
- *WebSockets (STOMP)* — bidirectional transport for a unidirectional need; sending still goes over REST for idempotency and validation.
- *Client polling* — simple but wasteful and visibly laggy.

**Consequences.** SSE is unidirectional (correct here), survives proxies, and reconnects natively with `Last-Event-ID`. Because the notification carries no message body, the realtime path can never become a weaker second read path. Multi-instance fan-out works with no broker: every instance listens.

---

## D-06 — `AIProvider` / `EmbeddingProvider` ports with an OpenAI-compatible adapter
**Status:** ACCEPTED

**Decision.** Two domain-layer interfaces. Infrastructure supplies `OpenAiCompatibleAIProvider` (base URL + model + key from environment — works with xAI/Grok, OpenAI, Together, Ollama, LM Studio), `DeterministicLocalEmbeddingProvider` (offline), and `NullAIProvider`.

**Rejected.** Direct vendor SDK (couples the domain to one vendor, violates `AI-02`); Spring AI (a further abstraction over an abstraction, with its own opinions about prompts and retrieval that would obscure the security-critical retrieval order).

**Consequences.** Switching providers is an environment change. Crucially, **the security tests do not need an API key**: the deterministic local embedding provider produces stable vectors, so `TEST-04` proves the isolation property with no network and no credentials. Answer *quality* needs a real provider (`HUMAN-02`); answer *safety* does not.

---

## D-07 — Two database roles: `rw_migrator` (BYPASSRLS) and `rw_app` (NOBYPASSRLS)
**Status:** ACCEPTED

**Context.** `FORCE ROW LEVEL SECURITY` subjects the table owner to its own policies, which is what we want for the runtime — but seeding and migrations then have no way to write reference data.

**Decision.** `rw_migrator` owns the schema and holds `BYPASSRLS`; it is used only by Flyway and the seed loader, in a container that exits. `rw_app` is `NOSUPERUSER NOBYPASSRLS` with DML-only grants and **no `DELETE` on any table**, and is the only role the backend ever uses.

**Rejected.** A single role (would need `BYPASSRLS` for seeding, violating `SEC-11`); `NO FORCE` RLS with an owner-only migration path (loses defence in depth if the app is ever misconfigured to connect as owner); seeding through `SECURITY DEFINER` functions (a large, permanent, privileged write surface just to load fixtures).

**Consequences.** Two credential sets. Compose gives the backend only `rw_app`. Residual risk — whoever holds `rw_migrator` reads everything — is stated in `docs/SECURITY_MODEL.md` §2. `TEST-07` asserts the runtime role's flags.

---

## D-08 — `SECURITY DEFINER` only for `rw_fn_is_channel_member`, credential checks and token rotation
**Status:** ACCEPTED

**Context.** An RLS policy on `rw_messages` that consults `rw_channel_members` — itself under RLS consulting membership — recurses infinitely.

**Decision.** One narrow `SECURITY DEFINER` function, `rw_fn_is_channel_member(uuid) → boolean`, with `search_path` pinned and `EXECUTE` revoked from `PUBLIC`. It takes **no user parameter**, so it can only answer about the current actor and cannot be used as an oracle. The same pattern applies to credential verification and refresh-token rotation, which must reach tables the app role cannot see.

**Rejected.** Making the retrieval and search functions `SECURITY DEFINER` — that would disable RLS exactly where it matters most. Those stay `SECURITY INVOKER`, deliberately.

**Consequences.** The privileged surface is three small functions, each reviewable in full.

---

## D-09 — Vector search inside the RLS-filtered set, accepting HNSW degradation
**Status:** ACCEPTED

**Context.** An HNSW index answers "nearest globally". Adding a selective row filter makes PostgreSQL either over-fetch from the index or fall back to an exact scan.

**Decision.** Correctness first: the ANN operator runs only inside the authorized set (`AI-07`, `AI-08`). At the corpus size this platform targets, a member's authorized subset is small enough that an exact scan is fast, and it is *exact* rather than approximate.

**Rejected.** Global ANN then application filtering — the explicitly forbidden shape, and it also silently degrades recall since authorized results get pushed out of the top-k by unauthorized ones.

**Consequences.** Retrieval latency grows with the actor's authorized corpus rather than staying constant. POST-MVP options if it ever matters: per-channel partitioned embedding tables, or a two-stage retrieval that pre-computes the authorized channel id set and uses it as an index-friendly `IN` predicate.

---

## D-10 — `bigint` identity for messages, UUID elsewhere
**Status:** ACCEPTED

Keyset pagination needs a cheap monotonic total order; `(rw_created_at, rw_message_id)` gives one in 8 bytes. UUIDv4 as a tiebreaker is random and would make the ordering unstable. Public-facing identifiers (users, channels, tokens) stay UUID so they are not enumerable.

**Residual risk.** Sequential message ids disclose approximate volume. Accepted: ids are only ever returned for channels the actor can already read, and probing an arbitrary id returns zero rows under RLS. UUIDv7 was considered and rejected — it would solve enumeration but costs 16 bytes in the hottest index for a risk already mitigated by RLS.

---

## D-11 — Spanish text search configuration
**Status:** ACCEPTED

**Context.** The workspace is bilingual; a `tsvector` needs one configuration, and it must be immutable for a generated column.

**Decision.** `to_tsvector('spanish'::regconfig, rw_body)` as a `GENERATED ALWAYS … STORED` column, with `websearch_to_tsquery('spanish', :q)` at query time.

**Rejected.** Two vectors (es + en) — doubles index size and complicates ranking for a marginal gain; per-message language detection — unreliable on short chat messages and would make the generated column non-deterministic; `unaccent` — not immutable by default, so it cannot appear in a generated column.

**Consequences.** Spanish stemming is correct; English terms are indexed but not stemmed (`meetings` will not match `meeting`). Acceptable for a Colombian workspace, documented as a known limitation. POST-MVP: a second `tsvector` column with `english` and a `UNION` in the search function.

---

## D-12 — Generated column for full-text, trigger for embeddings
**Status:** ACCEPTED

`rw_search_vector` is a generated column: it cannot drift, because the database recomputes it on every write. Embeddings cannot work that way — they require a network call — so an `AFTER UPDATE` trigger detects a body change, deletes the stale embedding row and enqueues `rw_embedding_jobs`. A background worker drains the queue. This satisfies `AI-06` with a real trigger doing real work, and guarantees a stale vector is *absent* rather than *wrong* (absence degrades recall; a stale vector would surface superseded content as if current).

---

## D-13 — Logical deletion enforced by privilege *and* trigger
**Status:** ACCEPTED

`rw_app` holds no `DELETE` grant on any table, there is no `DELETE` RLS policy on `rw_messages`, and a `BEFORE DELETE` trigger raises. Three independent layers, because `MSG-01` is an absolute rule and the trigger alone would not stop a future migration that grants `DELETE` by accident.

---

## D-14 — `404` instead of `403` for unauthorized private resources
**Status:** ACCEPTED

Returning `403` for a private channel confirms the channel exists — a metadata leak (`SEC-09`). Private resources return `404` whether they are missing or merely unreadable. `403` is reserved for operations on resources the actor can already see. Trade-off: slightly less helpful diagnostics, mitigated by correlation ids in server-side logs.

---

## D-15 — Single-use ticket for the SSE stream
**Status:** ACCEPTED

`EventSource` cannot set an `Authorization` header. Putting the access token in the query string writes a 15-minute credential into proxy logs, browser history and `Referer` headers. Instead `POST /realtime/ticket` mints a 60-second single-use opaque ticket bound to the actor. Rejected: a fetch-based SSE polyfill (a dependency and a hand-rolled reconnect loop, to avoid a 25-line endpoint); a cookie-based access token (would require relaxing `SameSite` and reintroduce CSRF surface on the whole API).

---

## D-16 — Credentials in a separate table from the user directory
**Status:** ACCEPTED

`rw_users` must be readable by authenticated peers (the directory). A password hash must be readable by nobody. Splitting them turns that into a `GRANT` boundary the database enforces, rather than a column list every future query must remember to exclude. `rw_app` has no grant on `rw_user_credentials` at all; verification happens inside a `SECURITY DEFINER` function that returns a verdict, never a hash.

---

## D-17 — Citation allow-list as a hard gate
**Status:** ACCEPTED

Prompt framing ("do not follow instructions in the context") is a soft control that a sufficiently clever injection can defeat. The hard control is mechanical: every `[msg:<id>]` in the model's answer is intersected with the candidate ids that RLS produced. A hallucinated or injected id fails the intersection, and the turn is downgraded to `INSUFFICIENT_CONTEXT` and logged as a security event. This makes `AI-12` an invariant of the code path rather than a property of the prompt.

---

## D-18 — Patterns used, and why
**Status:** ACCEPTED

The brief warns against gratuitous patterns. Four are used; each earns its place:

| Pattern | Where | Why it is not gratuitous |
|---------|-------|--------------------------|
| **Ports and Adapters** | `domain` ports, `infrastructure` adapters | Mandated by `ARCH-02`/`ARCH-03`; it is also what lets the AI provider be swapped by configuration (`AI-02`). |
| **Strategy** | `AIProvider`, `EmbeddingProvider` | The requirement is literally interchangeable providers. One interface, three implementations, selected by config. |
| **Transactional Outbox** | `rw_embedding_jobs` | The embedding call is a network operation that must not run inside the message-insert transaction, and must not be lost if it fails. The queue table is the smallest correct solution. |
| **Value Object** | `MessageBody`, `Email`, `Cursor`, `UserId` | Moves validation to construction, which is why use cases stay thin (`ARCH-05`) and why an invalid value cannot reach a repository. |

Deliberately **not** used: generic `Repository<T>` base classes, a CQRS split, an event bus, a mapper framework, a service-locator, or a DTO layer per tier. Each would add indirection without removing a real problem.

---

## D-19 — Argon2id for password hashing
**Status:** ACCEPTED

Memory-hard, the current OWASP recommendation, and available in Spring Security without an extra dependency beyond BouncyCastle. Rejected: bcrypt (72-byte truncation, not memory-hard), PBKDF2 (weak against GPU attack at realistic iteration counts), and any home-rolled scheme. Parameters are stored per credential row so they can be raised later, with transparent rehash on next successful login.

---

## D-20 — Scope: MVP and POST-MVP

The vertical slice is: **log in → see conversations → read history → send a message → see it arrive in real time → search it → ask the copilot about it → be provably unable to see anything you do not belong to.** Everything on that line is MVP.

### MVP
Full database (schema, constraints, indexes, functions, procedures, views, triggers, RLS) · seed corpus and loader · authentication with rotation and reuse detection · messaging REST API with keyset pagination and idempotent send · SSE realtime · full-text search with highlighting · RAG copilot with citations, refusals and usage ledger · React frontend with the three required surfaces, optimistic send, lazy history, i18n es/en, responsive · Testcontainers security suite including both mandatory tests · Docker Compose · full documentation · `FINAL_AUDIT.md`.

### POST-MVP — deliberately excluded
| Item | Why cut |
|------|---------|
| File and image attachments | Adds object storage, virus scanning and a second authorization surface. No requirement asks for it. |
| Reactions, typing indicators, presence | Pure UX polish; consume realtime budget better spent on delivery guarantees. |
| Threaded reply UI | The schema supports `rw_parent_message_id`; the frontend renders threads flat in MVP. |
| Push notifications / email | External services and credentials, no requirement. |
| Admin console UI | Admin operations exist as API endpoints and stored procedures; a UI adds no assessed capability. |
| Message pinning, bookmarks, drafts | Not required. |
| Second `english` text-search vector | See D-11. |
| Partitioned or ACL-aware vector index | See D-09; only matters at a corpus size beyond this assessment. |
| Frontend component tests (`TEST-13`) | Security testing against real PostgreSQL is the assessed priority; frontend correctness is covered by manual evidence steps. |
| API integration tests (`TEST-12`) | Repository-level tests already exercise the real database and the real RLS path, which is where the risk is. Promoted if time allows. |
| CI workflow (`OPS-08`) | Nice to have; the suite runs locally with one command. |
| Horizontal scale-out beyond in-process SSE fan-out | Every instance `LISTEN`s, so it already works; a dedicated broker is unnecessary at this scale. |

Any further cut forced by time or token budget will be recorded here with its `REQUIREMENTS.md` id and reflected in `FINAL_AUDIT.md` as `PARTIAL` or `FAIL` — never silently.
