# TESTING.md — Security and Testing Strategy

This project prioritizes testing the *real security boundary*, which is inside PostgreSQL.

## 1. Testcontainers and PostgreSQL 16

The integration test suite (`RlsSecurityIT`) uses **Testcontainers** to spin up a genuine `pgvector/pgvector:pg16` Docker container.

### Why not H2 or mock repositories?
The core requirement is that authorization is enforced via Row Level Security (RLS) inside PostgreSQL (`SEC-10`). Testing this with an in-memory database (like H2) or mocking the `MessageRepository` in Java proves nothing about the actual security of the system. 
The only way to prove RLS works is to connect to a real PostgreSQL instance as the real application role (`rw_app`), set `app.current_user_id`, and verify that the database refuses to return rows the user does not own.

## 2. Mandatory Security Tests

The assessment requires two mandatory tests, plus several others to prove the security invariants. These are implemented in `RlsSecurityIT`.

### TEST-02: Non-member cannot read a private channel
- **Test:** `test1_nonMemberCannotReadPrivateChannel`
- **Mechanism:** A user creates a `PRIVATE` channel and sends a message. Another user (not a member) attempts to read the message history.
- **Assertion:** The system throws `ResourceNotVisible` (HTTP 404).

### TEST-03: Third party cannot read an A↔B direct channel
- **Test:** `test2_thirdPartyCannotReadDirectChannel`
- **Mechanism:** User A opens a `DIRECT` channel with User B and sends a message. User C attempts to read the message history.
- **Assertion:** The system throws `ResourceNotVisible`.

### TEST-04: RAG isolation
- **Test:** `test4_ragIsolation`
- **Mechanism:** User A posts in a `PUBLIC` channel and a `PRIVATE` channel. User B (who cannot see the private channel) queries the `RagRepository`.
- **Assertion:** Only the public message is returned. The vector search runs strictly inside the authorized set (AI-08).

### TEST-05: Search isolation
- **Test:** `test5_searchIsolation`
- **Mechanism:** A unique term ("kryptonite") is sent in a `PRIVATE` channel. A non-member attempts to search for it using `rw_fn_search_messages`.
- **Assertion:** Zero results are returned.

### TEST-06: Physical delete rejected
- **Test:** `test6_physicalDeleteRejected`
- **Mechanism:** A direct `DELETE FROM rw_messages` is attempted by `rw_app`.
- **Assertion:** PostgreSQL rejects it with a permission denied error (`rw_app` holds no `DELETE` grants).

### TEST-07: Role flags
- **Test:** `test7_rwAppRoleFlags`
- **Mechanism:** Queries `pg_roles` for `rw_app`.
- **Assertion:** Asserts that `rolsuper = false` and `rolbypassrls = false`.

### TEST-08: Actor unset
- **Test:** `test8_actorUnsetReturnsZeroRows`
- **Mechanism:** Queries `rw_messages` directly without setting `app.current_user_id`.
- **Assertion:** RLS defaults to deny; zero rows are returned.

## 3. Architecture Tests (TEST-11)

`ArchitectureTest.java` uses **ArchUnit** to mechanically enforce the Clean Architecture boundary:
- `domain` depends on nothing (no Spring, JDBC, or Jackson).
- `application` depends only on `domain`.
- `presentation` and `infrastructure` depend on `application` and `domain`.

## 4. How to Run

```bash
cd backend
./mvnw test
```

> **Note on Windows/Docker Desktop:** Testcontainers requires a working Docker environment. If `mvnw test` fails with `Could not find a valid Docker environment` on Windows, ensure Docker Desktop is running and that `DOCKER_HOST` is correctly exposed, or run the tests inside WSL2.
