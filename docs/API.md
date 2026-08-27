# API.md — REST Contract

Base path `/api/v1` · JSON only · UTF-8 · all timestamps ISO-8601 UTC (`2026-08-27T14:03:11.482Z`).
Live OpenAPI once the backend runs: `/api/v1/openapi.json`, Swagger UI at `/swagger-ui.html`.

---

## 1. Cross-cutting rules

**Authentication.** All routes except `POST /auth/login`, `POST /auth/refresh` and `GET /health` require `Authorization: Bearer <access token>`.

**The actor is never a parameter.** No endpoint accepts a user id identifying the caller — in a path, a query string, or a body. The actor comes from the verified token only (`AUTH-09`). Endpoints that operate on *another* user (admin deactivation) name that user in the path, and the database still decides whether the operation is allowed.

**Correlation id.** Send `X-Correlation-Id`; if absent one is generated. Always echoed in the response and present in every log line for the request.

**Errors** — RFC 9457 Problem Details, `Content-Type: application/problem+json`:

```json
{
  "type": "https://riwi.co/errors/validation",
  "title": "Validation failed",
  "status": 422,
  "detail": "One or more fields are invalid.",
  "instance": "/api/v1/channels/9f1c.../messages",
  "code": "VALIDATION_ERROR",
  "correlationId": "01J8W2N4Q7R5T3",
  "errors": [ { "field": "body", "code": "SIZE", "message": "must be between 1 and 4000 characters" } ]
}
```

Codes: `VALIDATION_ERROR`, `AUTHENTICATION_FAILED`, `TOKEN_EXPIRED`, `TOKEN_REUSE_DETECTED`, `OPERATION_NOT_PERMITTED`, `NOT_FOUND`, `CONFLICT`, `RATE_LIMITED`, `PROVIDER_UNAVAILABLE`, `INTERNAL_ERROR`.

**Status codes.** `200` read / update · `201` created (with `Location`) · `204` no content · `400` malformed · `401` missing or invalid credentials · `403` authenticated, resource visible, operation not allowed · `404` not found **or** not authorized on a private resource (`SEC-14`) · `409` conflict · `422` semantically invalid · `429` rate limited · `503` AI provider unavailable.

> `403` vs `404`: `403` only when the actor can already see the resource but not perform the operation (a `MEMBER` renaming a channel). Anything they should not know exists is `404`.

**Keyset pagination** (`API-07`, `MSG-04`). No `page`, no `offset`, ever.

```
GET /…?limit=50&cursor=eyJ0cyI6IjIwMjYtMDgtMjdUMTQ6MDM6MTEuNDgyWiIsImlkIjo0ODE5MX0
```

```json
{ "items": [ … ], "pageInfo": { "nextCursor": "eyJ0cy…", "hasMore": true } }
```

The cursor is base64url of the ordering tuple (`{"ts":…,"id":…}` for history, `{"rank":…,"id":…}` for search). It is opaque to clients, validated server-side, and carries no authorization meaning — a cursor from another user's page still returns only rows the actor may read. `limit` ∈ [1, 100], default 50.

---

## 2. Authentication

### `POST /auth/login`
```json
{ "email": "ana.torres@riwi.co", "password": "…" }
```
`200` → `{ "accessToken": "eyJ…", "tokenType": "Bearer", "expiresIn": 900, "user": { … } }`
Sets `rw_refresh=<opaque>; HttpOnly; Secure; SameSite=Strict; Path=/api/v1/auth; Max-Age=1209600`.
`401 AUTHENTICATION_FAILED` — identical shape and timing for unknown email and wrong password.
`429` after repeated failures; `rw_locked_until` also applies.

### `POST /auth/refresh`
No body; reads the cookie. `200` → new access token, **new** refresh cookie (rotation, `AUTH-05`).
`401 TOKEN_REUSE_DETECTED` → the whole family is revoked, the cookie cleared, re-login required (`AUTH-06`).

### `POST /auth/logout`
`204`. Revokes the family (`LOGOUT`), clears the cookie.

---

## 3. Profile and users

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/me` | actor's profile from the token subject |
| `PATCH` | `/me` | `{ displayName?, locale?, avatarUrl? }` → `200`; RLS restricts the update to the actor's own row |
| `GET` | `/users?query=&limit=&cursor=` | directory search — **stored procedure `rw_sp_query_users`** (`OBJ-01`); returns active users only |
| `PATCH` | `/users/{userId}` | admin: role change — **`rw_sp_modify_user`** (`OBJ-02`) |
| `DELETE` | `/users/{userId}` | admin: **logical** deactivation via `rw_sp_modify_user` (`BR-12`); `204`. Never a physical delete. |

`GET /me` →
```json
{ "userId": "…", "email": "ana.torres@riwi.co", "displayName": "Ana Torres",
  "role": "MEMBER", "locale": "es", "avatarUrl": null, "createdAt": "…", "lastSeenAt": "…" }
```

---

## 4. Conversations and channels

### `GET /conversations`
Reads the view `rw_v_user_conversations` (`OBJ-03`) — the authenticated user's conversations, with last message preview and unread count, ordered by last activity.

```json
{ "items": [ {
    "channelId": "…", "type": "DIRECT", "name": null,
    "counterpart": { "userId": "…", "displayName": "Bruno Salas" },
    "lastMessage": { "messageId": 48191, "preview": "Confirmo la demo…",
                     "senderDisplayName": "Bruno Salas", "createdAt": "…" },
    "unreadCount": 3, "memberRole": "MEMBER", "isArchived": false
} ], "pageInfo": { "nextCursor": null, "hasMore": false } }
```

The view is membership-scoped; a non-member's channel is not merely hidden from the payload, it is not a row.

| Method | Path | Body / notes | Codes |
|--------|------|--------------|-------|
| `GET` | `/channels?type=PUBLIC&query=` | discoverable public channels only (IBR-02) | 200 |
| `POST` | `/channels` | `{ name, slug, topic?, type: "PUBLIC"\|"PRIVATE", memberIds[] }` — creator becomes `OWNER` in the same transaction | 201, 409 |
| `GET` | `/channels/{channelId}` | 404 if not a member and not public | 200, 404 |
| `PATCH` | `/channels/{channelId}` | `{ name?, topic?, isArchived? }` — `OWNER`/`MODERATOR` | 200, 403, 404 |
| `POST` | `/channels/direct` | `{ counterpartUserId }` → get-or-create the canonical DM (BR-08); returns 200 when it already exists, 201 when created | 200, 201, 404 |
| `GET` | `/channels/{channelId}/members` | members only (`SEC-09`) | 200, 404 |
| `POST` | `/channels/{channelId}/members` | `{ userId, role? }` — `OWNER`/`MODERATOR`, or self-join to a public channel | 201, 403, 404, 409 |
| `DELETE` | `/channels/{channelId}/members/{userId}` | leave (self) or remove; last-owner guard → 409 | 204, 403, 404, 409 |
| `PUT` | `/channels/{channelId}/read-cursor` | `{ lastReadMessageId }` | 204 |

---

## 5. Messages

### `GET /channels/{channelId}/messages?limit=50&cursor=…`
Backward keyset history, newest first (`OBJ-04`). Non-member → `404` with no rows fetched.

```json
{ "items": [ {
    "messageId": 48191, "channelId": "…",
    "sender": { "userId": "…", "displayName": "Bruno Salas", "avatarUrl": null },
    "body": "Confirmo la demo para el jueves.",
    "parentMessageId": null, "clientMessageId": "…",
    "isDeleted": false, "revision": 1,
    "createdAt": "2026-08-27T14:03:11.482Z", "editedAt": null
} ], "pageInfo": { "nextCursor": "eyJ0cy…", "hasMore": true } }
```

A logically deleted message returns `"isDeleted": true` and `"body": null` — the row is preserved (`MSG-01`), the content is not disclosed (`BR-06`).

### `POST /channels/{channelId}/messages`
```json
{ "body": "…", "clientMessageId": "3f1a…", "parentMessageId": null }
```
`201` with the created message. **Idempotent**: the same `clientMessageId` returns `200` with the existing message instead of duplicating (IBR-04) — this is what makes optimistic retry and SSE reconnection safe.
`403` archived channel · `404` not a member · `422` empty or over-long body.

### `PATCH /messages/{messageId}`
`{ "body": "…" }` → `200`. Sender only. Previous body is written to `rw_message_edits` and the embedding is invalidated, both by trigger (`MSG-02`, `AI-06`).

### `DELETE /messages/{messageId}`
`204`. **Logical** delete — sets `rw_is_deleted`; the row survives and no physical `DELETE` is issued or permitted (`MSG-01`).

---

## 6. Search

### `GET /search/messages?q=demo%20jueves&channelId=&limit=&cursor=`

PostgreSQL full-text search executed inside the RLS-filtered relation (`SRCH-01`, `SRCH-02`). `q` is parsed with `websearch_to_tsquery`, always as a bind parameter.

```json
{ "items": [ {
    "messageId": 48191, "channelId": "…", "channelName": "proyecto-atlas",
    "sender": { "userId": "…", "displayName": "Bruno Salas" },
    "highlight": "Confirmo la <mark>demo</mark> para el <mark>jueves</mark>.",
    "rank": 0.6079, "createdAt": "…"
} ], "pageInfo": { "nextCursor": "eyJyYW5r…", "hasMore": false } }
```

`highlight` comes from `ts_headline` with `<mark>` delimiters (`SRCH-03`); the client renders it through a sanitizer that allows only `<mark>`. `channelId` narrows the search and can never widen it. Results from channels the actor does not belong to are not returned, not ranked, and not counted.

---

## 7. Realtime

### `POST /realtime/ticket`
`201` → `{ "ticket": "…", "expiresIn": 60 }` — single-use, bound to the actor. Exists because `EventSource` cannot set headers and an access token in a URL would leak into logs and history.

### `GET /realtime/stream?ticket=…`
`text/event-stream`. Heartbeat comment every 20 s. Events carry ids only — never message bodies — so the stream cannot become a second read path that bypasses RLS.

```
id: 48191
event: message.created
data: {"channelId":"…","messageId":48191,"senderId":"…","clientMessageId":"3f1a…","createdAt":"…"}

event: message.updated
data: {"channelId":"…","messageId":48191,"revision":2}

event: message.deleted
data: {"channelId":"…","messageId":48191}
```

On reconnect the browser sends `Last-Event-ID`; the client additionally runs a keyset catch-up from its newest known message id, so a dropped event cannot leave a permanent gap (`RT-04`).

---

## 8. AI copilot

### `POST /copilot/ask`
```json
{ "question": "¿Qué acordamos sobre la demo del jueves?",
  "channelId": "…",            // optional narrowing scope
  "threadId": null }
```

`200` — grounded answer:
```json
{ "turnId": "…", "threadId": "…", "status": "ANSWERED",
  "answer": "Se acordó mover la demo al jueves a las 10:00 [msg:48191] …",
  "citations": [ {
      "messageId": 48191, "channelId": "…", "channelName": "proyecto-atlas",
      "senderDisplayName": "Bruno Salas", "createdAt": "…",
      "excerpt": "Confirmo la demo para el jueves.", "similarity": 0.83, "rank": 1
  } ],
  "promptVersion": "2026.08.1",
  "usage": { "promptTokens": 1284, "completionTokens": 96, "totalTokens": 1380 } }
```

`200` — honest refusal (`AI-10`), always with `"citations": []`:

| `status` | Meaning |
|----------|---------|
| `INSUFFICIENT_CONTEXT` | nothing authorized was relevant enough, or citation validation failed |
| `NO_PERMISSION` | the requested scope yields no readable conversation — worded identically whether or not the channel exists |
| `OUT_OF_SCOPE` | not a question about the workspace corpus |

`503 PROVIDER_UNAVAILABLE` when no provider is configured or the provider errors; the turn is still recorded with `PROVIDER_ERROR` and usage is still ledgered (`BR-18`).

Every citation is guaranteed readable by the actor: candidates come from the RLS-enforced retrieval function, and returned ids are intersected with that set before the response is built (`AI-12`).

### `GET /copilot/usage`
Accumulated consumption for the authenticated user (`OBJ-07`, `AI-15`).

```json
{ "userId": "…",
  "totals": { "requests": 42, "promptTokens": 51840, "completionTokens": 6120, "totalTokens": 57960 },
  "byOperation": [ { "operation": "CHAT", "requests": 30, "totalTokens": 55210 },
                   { "operation": "EMBEDDING", "requests": 12, "totalTokens": 2750 } ],
  "byModel":     [ { "provider": "groq",  "model": "llama-3.3-70b-versatile", "operation": "CHAT",      "requests": 30, "totalTokens": 55210 },
                   { "provider": "local", "model": "deterministic-1536",      "operation": "EMBEDDING", "requests": 12, "totalTokens": 2750 } ],
  "firstUsedAt": "…", "lastUsedAt": "…" }
```

### `GET /copilot/threads` · `GET /copilot/threads/{threadId}/turns`
Owner-scoped history, keyset paginated.

---

## 9. Operations

| Method | Path | Notes |
|--------|------|-------|
| `GET` | `/health` | liveness, unauthenticated |
| `GET` | `/health/ready` | database + migration state |
| `GET` | `/openapi.json` | contract (`API-08`) |

---

## 10. Rate limits

| Route | Limit | On exceed |
|-------|-------|-----------|
| `POST /auth/login` | 10 / 5 min per IP + email | `429`, `Retry-After` |
| `POST /auth/refresh` | 60 / 5 min per IP | `429` |
| `POST /copilot/ask` | 20 / min per user | `429` |
| everything else | 600 / min per user | `429` |
