# EVIDENCE.md — How to reproduce every security claim

This document explains exactly how a reviewer can manually reproduce and prove every security claim (`SEC-*`) using the provided Docker Compose environment and the seeded data.

## Environment Setup
1. Start the stack: `docker compose up --build`
2. Wait for `db`, `migrate`, `backend`, and `frontend` to be healthy.
3. The seed loader automatically loads the fixture data (users, channels, memberships, messages).

## Seeded Users
The passwords for all seeded users are `RiwiDemo#2026`.
- `alice@riwi.co` (MEMBER)
- `bob@riwi.co` (MEMBER)
- `admin@riwi.co` (ADMIN)

## 1. Non-member cannot read a private channel (SEC-01 / TEST-02)

1. Log in to the frontend as `alice@riwi.co`.
2. Create a new PRIVATE channel named "Alice Secret".
3. Send a message: "This is a private message".
4. Log out and log in as `bob@riwi.co`.
5. Observe that "Alice Secret" does NOT appear in the channel list.
6. Attempt to directly access the channel via the API:
   ```bash
   curl -i -H "Authorization: Bearer <bob_token>" http://localhost:8080/api/v1/channels/<alice_secret_channel_id>/messages
   ```
7. **Expected Result:** `404 Not Found` (ResourceNotVisible). It does not return 403, preventing metadata leakage (SEC-09, SEC-14).

## 2. Third party cannot read an A↔B direct channel (SEC-02 / TEST-03)

1. Log in as `alice@riwi.co`.
2. Open a direct message with `bob@riwi.co` and send "Hello Bob".
3. Log in as `admin@riwi.co` (who is an ADMIN, but not a member of that DM).
4. Try to fetch the messages of that DM via the API.
5. **Expected Result:** `404 Not Found`. Admin privileges do not bypass RLS for content.

## 3. Search isolation (SEC-04 / TEST-05)

1. The seed data includes a PRIVATE channel `incident-response` containing a message with the word "kryptonite".
2. Log in as `hugo@riwi.co` (who is a member of `incident-response`).
3. Search for "kryptonite".
4. **Expected Result:** 3 messages found, with highlighting.
5. Log in as `frank@riwi.co` (who is NOT a member of `incident-response`).
6. Search for "kryptonite".
7. **Expected Result:** 0 results. The database filters out the rows *inside* `rw_fn_search_messages`.

## 4. RAG and Vector isolation (SEC-06, SEC-07, SEC-08 / TEST-04)

1. Ensure the `AI_API_KEY` is configured (so you get a real response) and `EMBEDDING_PROVIDER_CODE=local` (the default).
2. The seed data has a message about "the Thursday 10am demo" in `proyecto-atlas` (PUBLIC) and `incident-response` (PRIVATE).
3. Log in as `frank@riwi.co` (non-member of `incident-response`).
4. Ask the Copilot: "What time is the demo?".
5. **Expected Result:** The copilot answers "Thursday 10am" and cites ONLY the message from `proyecto-atlas`. It is mathematically impossible for the vector search to return the `incident-response` message because `rw_fn_rag_retrieve_candidates` applies RLS filtering before vector similarity.

## 5. Physical delete rejected (SEC-10, MSG-01 / TEST-06)

1. Connect to the database as `rw_app`:
   ```bash
   docker exec -it riwi-messaging-db psql -U rw_app -d bd_juandiego_garcia_nakamoto
   ```
2. Run: `DELETE FROM rw.rw_messages;`
3. **Expected Result:** `ERROR: permission denied for table rw_messages`.

## 6. No `BYPASSRLS` for runtime role (SEC-11 / TEST-07)

1. Connect to the database.
2. Run: `SELECT rolname, rolsuper, rolbypassrls FROM pg_roles WHERE rolname = 'rw_app';`
3. **Expected Result:** `rolsuper = f`, `rolbypassrls = f`.
