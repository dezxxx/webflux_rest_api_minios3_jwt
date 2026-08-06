# File Storage S3 API

Reactive REST API over MinIO S3 object storage. Users upload and manage files,
every upload is recorded as an event, and access is restricted by JWT-based roles.

## Stack

| | |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 |
| Web layer | Spring WebFlux (reactive) |
| Persistence | Spring Data R2DBC + MySQL |
| Migrations | Flyway (over JDBC) |
| Object storage | MinIO, accessed through AWS SDK v2 `S3AsyncClient` |
| Security | Spring Security + JJWT |
| Docs | springdoc-openapi (Swagger UI) |
| Build | Gradle 9.5.1, dependency versions in `gradle.properties` |
| Tests | JUnit 5, Mockito, Testcontainers |

### Why R2DBC and not JPA

The specification asks for both WebFlux and JPA/Hibernate. These are mutually
exclusive: JPA is blocking, and a blocking call on a WebFlux event loop stalls
every other request served by that thread. The reactive stack was kept, so
persistence goes through R2DBC.

Flyway is the one deliberate exception — it has no reactive driver and runs its
migrations over plain JDBC at startup. That is why both an R2DBC and a JDBC URL
point at the same database, and why `mysql-connector-j` is still on the runtime
classpath alongside `r2dbc-mysql`.

## Requirements

- JDK 21+
- Docker (for MySQL and MinIO)

## Quick start

Start the infrastructure:

```bash
docker compose up -d
```

This brings up MySQL on host port **3307** (3306 is often taken by a locally
installed MySQL service), MinIO on **9000**, its web console on **9001**, and a
one-shot job that creates the `files` bucket. MinIO does not autocreate buckets,
so without that job the first upload fails with `NoSuchBucket`.

Build and run:

```bash
gradlew build
SPRING_PROFILES_ACTIVE=dev gradlew bootRun
```

**The `dev` profile is required locally.** Without it the application refuses to
start — see [Development defaults](#development-defaults).

Flyway applies `V1__init.sql` and `V2__seed_admin.sql` on startup. The second
migration seeds the first administrator — see [Authentication](#authentication).

| | |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui/index.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| Front end demo | http://localhost:8080/ |
| MinIO console | http://localhost:9001 (`minioadmin` / `minioadmin`) |

The first three exist **only under the `dev` profile** — see
[Public endpoints](#public-endpoints).

## Configuration

Every setting has a local-development default and is overridable through the
environment. No real credentials belong in `application.yaml`.

| Variable | Default | |
|---|---|---|
| `DB_HOST` | `localhost` | |
| `DB_PORT` | `3307` | host port; inside the Docker network use `3306` |
| `DB_NAME` | `minios3` | |
| `DB_USER` / `DB_PASSWORD` | `root` / `root` | |
| `JWT_SECRET` | dev placeholder | **must** be overridden; HS256 needs 256+ bits |
| `JWT_ACCESS_TTL` | `15m` | raise it locally if re-authorising Swagger gets tedious |
| `JWT_REFRESH_TTL` | `30d` | |
| `S3_ENDPOINT` | `http://localhost:9000` | |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `minioadmin` / `minioadmin` | |
| `S3_BUCKET` | `files` | |

### Development defaults

`InsecureDefaultsGuard` checks three values at startup and **throws if any of them
still holds its development default**:

```
app.jwt.secret          JWT_SECRET
spring.r2dbc.password   DB_PASSWORD
app.s3.secret-key       S3_SECRET_KEY
```

The check is skipped only while the `dev` profile is active, and `dev` has to be
switched on deliberately — there is no default profile. That direction is chosen
on purpose: if strictness depended on naming a `prod` profile instead, then
forgetting to name it would silently leave every default in place, which is the
accident the class exists to prevent. Forgetting it now costs a failed startup
with a message naming the variable.

`JWT_SECRET` is the one that matters. Whoever holds it can sign a token claiming
any user name and the `ADMIN` role; the signature verifies, so the password is
never consulted and no amount of care elsewhere helps.

The `dev` profile also raises the access-token TTL to 8 hours, so a token pasted
into Swagger UI lasts a working session instead of 15 minutes.

`V2__seed_admin.sql` still runs in every profile — `admin` / `admin` exists
wherever the migrations do. Making it dev-only would leave a fresh deployment
with no administrator and no way to create one, since registration only ever
grants `USER`. Bootstrapping the first administrator from the environment is the
proper fix and is not implemented.

### Public endpoints

`SecurityConfig` keeps a whitelist: anything not on it falls into
`anyExchange().authenticated()` and needs a token.

```
/api/v1/auth/login      always
/api/v1/auth/register   always
/api/v1/auth/refresh    always
/api/v1/auth/logout     always

/v3/api-docs/**         dev profile only
/swagger-ui/**          dev profile only
/  and  /index.html     dev profile only
```

The four authentication paths are listed one by one rather than matched with
`/api/v1/auth/**`. A wildcard would also publish whatever is added to
`AuthControllerV1` later — a password reset written next month would be reachable
without a token, and nobody would have had to touch this file for that to happen.
Probing for a path that does not exist now answers `401` rather than `404`, so
guessing reveals nothing either.

The OpenAPI document is behind two locks: the paths above are not public outside
`dev`, and `springdoc.api-docs.enabled` / `springdoc.swagger-ui.enabled` default
to `false`, so the document is not generated at all. It lists every path, every
field and the role each endpoint requires — precisely the reconnaissance an
attacker would otherwise have to guess at.

`/index.html` is a small static page that drives this API the way a front end
would: register, log in, hold the token, send it as a `Bearer` header, and show
each request beside its response. It exists to make the front-end/back-end
boundary visible and is not part of the deliverable.

## Domain

- **User** — `id`, `username`, `password_hash`, `role`, `status`
- **File** — `id`, `name`, `location` (S3 key), `status`, `user_id`
- **Event** — `id`, `user_id`, `file_id`, `status`, `created_at`

MySQL stores metadata and permissions; MinIO stores the bytes. `files.location`
holds the object key, never the file itself.

### How a file is stored

An upload arrives as `multipart/form-data`, so there is no request DTO for it —
the only thing the client sends is the file, and its name travels inside the part
rather than in a JSON body.

```
FileControllerV1 → FileService → S3Storage → MinIO      bytes
                              → FileRepository → MySQL  row
                              → EventService   → MySQL  CREATED
```

Bytes first, row second. A break in between leaves an unreferenced object in the
bucket: invisible, harmless, cheap to sweep up. The opposite order would leave a
row pointing at nothing, and that one is visible to the client as a broken file.

`location` is the **object key**, a fresh UUID with the original extension —
never a URL. A URL would nail the storage host into every row, so moving to
another MinIO or to a real S3 would invalidate the whole table; the host is
configuration and is read from `S3_ENDPOINT` when needed. The UUID is what keeps
two people uploading `passport.pdf` from overwriting each other, since `putObject`
replaces a duplicate key without complaining. The name the user chose is kept
separately in `files.name`.

The whole file is held in memory for the length of the request. That is the price
of `putObject`, which needs the content length before it accepts the first byte;
streaming it instead means either a temporary file or a multipart upload, neither
of which is worth it at this size.

`FileResponseDto` reports the owner as a **name**, not an id, which is why the
three read queries in `FileRepository` are hand-written joins onto `users` rather
than derived from method names. A `MODERATOR` listing every file gets something
readable in one request instead of an id to resolve afterwards; the cost is that
`deleted_at IS NULL` is now ours to remember in each of those queries.

Events are written by `EventService` on create, update and delete. Their `user_id`
is whoever performed the action, not whoever owns the file — for an upload the two
are the same, and when a moderator edits somebody else's file the moderator is the
answer worth keeping.

### Deviations from the specification DDL

Three columns were added because the functional requirements cannot be met
without them:

- `users.password_hash` and `users.role` — the spec requires JWT authentication
  and ADMIN / MODERATOR / USER access levels, but its DDL has neither a
  credential nor a role column.
- `files.user_id` — a USER may only read *their own* data, and ownership is not
  otherwise derivable without joining through `events`.
- `deleted_at` on every table — soft delete.

`events.timestamp` was renamed to `created_at`: `timestamp` is a type name in
MySQL and needs quoting wherever it appears.

## Access levels

| Role | |
|---|---|
| `ADMIN` | full access to all data and operations |
| `MODERATOR` | USER rights, plus read of all users and read/update/delete of all events and files |
| `USER` | read own data, upload files for self |

`role` and `status` are separate columns on purpose: `ACTIVE`/`BLOCKED` decides
whether a user is let in at all, `ADMIN`/`MODERATOR`/`USER` decides what they may
do. Merging them would make a blocked administrator inexpressible.

## API

The specification's OpenAPI section describes only two endpoints while its
functional requirements ask for CRUD over all three entities, so the rest of the
surface is derived from those requirements.

Every path is versioned — `/api/v1/...` — and each controller carries the same
version in its name (`AuthControllerV1`, `UserControllerV1`). A breaking change
to a payload then arrives as a new prefix and a new class beside the old one,
instead of silently breaking clients already on the current contract.

| Method | Path | ADMIN | MODERATOR | USER |
|---|---|---|---|---|
| `POST` | `/api/v1/auth/register` | — open, no token — |||
| `POST` | `/api/v1/auth/login` | — open, no token — |||
| `POST` | `/api/v1/auth/refresh` | — open, the refresh token is the credential — |||
| `POST` | `/api/v1/auth/logout` | — open, does nothing, see [Tokens](#tokens) — |||
| `GET` | `/api/v1/users/me` | — any authenticated caller, own row — |||
| `GET` | `/api/v1/users` | ✅ | ✅ | ❌ |
| `GET` | `/api/v1/users/{id}` | ✅ | ✅ | ❌ — use `/me` |
| `POST` | `/api/v1/users` | ✅ | ❌ | ❌ |
| `PUT` | `/api/v1/users/{id}` | ✅ | ❌ | ❌ |
| `DELETE` | `/api/v1/users/{id}` | ✅ | ❌ | ❌ |
| `POST` | `/api/v1/files` | ✅ | for self | for self |
| `GET` | `/api/v1/files` | all | all | own |
| `GET` | `/api/v1/files/{id}` | any | any | own |
| `PUT` | `/api/v1/files/{id}` | ✅ | ✅ | ❌ |
| `DELETE` | `/api/v1/files/{id}` | ✅ | ✅ | ❌ |
| `POST` | `/api/v1/events` | ✅ | ❌ | ❌ |
| `GET` | `/api/v1/events` | all | all | own |
| `GET` | `/api/v1/events/{id}` | any | any | own |
| `PUT` | `/api/v1/events/{id}` | ✅ | ✅ | ❌ |
| `DELETE` | `/api/v1/events/{id}` | ✅ | ✅ | ❌ |

Registration is open and always produces `role = USER`, `status = ACTIVE` —
accepting a role from the request body would make authorisation meaningless. A
successful registration returns tokens immediately, so no second call is needed.

That leaves `PUT /api/v1/users/{id}` as the only way a `MODERATOR` or a blocked
account can come to exist: an administrator sets the role and the status there.
Nothing else in the application writes either column apart from
`V2__seed_admin.sql` — registration always produces an active `USER`. The new
role reaches the user's own token at their next `/api/v1/auth/refresh`.

A `USER` reads their own row through `/api/v1/users/me`, not through
`/api/v1/users/{id}`: the caller is identified by the token, so no id is needed
and no ownership check has to be repeated.

`POST /api/v1/events` exists only because the requirements ask for full CRUD on Event.
Events are normally created by the application itself on every upload, never by
a client.

### Deletion

`DELETE` is a soft delete everywhere: it sets `deleted_at`, keeps the row, and
every read filters on `deleted_at IS NULL`. Nothing is ever removed physically —
the data is retained for later analysis, and a history with holes in it cannot be
replayed.

For a file that means the object stays in the bucket too. Only the way to reach
it is withdrawn: the row answers `404`, so no request can name the key any more.
An orphan object costs storage and nothing else, while discarding it would make
the retained row point at a file that no longer exists.

### Roles vs ownership

Role is checked before the method runs; ownership ("own files only") can only be
checked after the row is loaded and its `user_id` is known. Ownership checks
therefore live in the service layer, not in an annotation.

### Tokens

Two tokens, **neither stored on the server**:

| | TTL | Claims | Accepted by |
|---|---|---|---|
| access | 15 min | `sub`, `role`, `type=access` | every endpoint |
| refresh | 30 days | `sub`, `type=refresh` | `/api/v1/auth/refresh` only |

The client sends the access token; when it expires it posts the refresh token to
`/api/v1/auth/refresh` and gets a fresh pair without retyping a password.

`/api/v1/auth/refresh` re-reads the user row, and that is the only reason it exists. A
changed role or a blocked account therefore takes effect within one access-token
lifetime — 15 minutes — rather than lasting until the original token expires. The
refresh token deliberately carries no `role`: a copy stored there would go stale
and defeat the re-read.

Both tokens are signed with the same key, so the `type` claim is what keeps them
apart. Without it a 30-day refresh token would be accepted as a `Bearer` on every
endpoint, and an access token could renew itself forever. `JwtAuthenticationFilter`
accepts only `type=access`; `AuthService.refresh` only `type=refresh`.

**There is no revocation.** A signed token is valid until `exp` by construction,
and this application stores nothing that could mark one as dead. `POST /api/v1/auth/logout`
returns `204` and does nothing — logging out is the client discarding both tokens.
A stolen token works until it expires. That is what stateless JWT means, and it is
the trade accepted here in exchange for never touching the database on a request.

## Authentication

`V2__seed_admin.sql` inserts the first administrator, because registration only
ever grants `USER` and there would otherwise be no way to reach an ADMIN-only
endpoint:

```
admin / admin        development only — change it in any real environment
```

Log in and keep both tokens:

```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

```json
{
  "accessToken": "<signed JWT>",
  "refreshToken": "<signed JWT>",
  "username": "admin",
  "role": "ADMIN"
}
```

Send the access token on every other call:

```
Authorization: Bearer <accessToken>
```

When it expires, trade the refresh token for a new pair:

```bash
curl -X POST http://localhost:8080/api/v1/auth/refresh \
  -H 'Content-Type: application/json' \
  -d '{"refreshToken":"<signed JWT>"}'
```

A client normally does this on any `401`: refresh once, retry the original
request, and fall back to the login screen if the refresh itself fails.

### How a request is authenticated

`JwtAuthenticationFilter` runs at the `AUTHENTICATION` position of the security
chain. It reads the `Authorization` header, verifies the signature and expiry,
and publishes an `Authentication` into the Reactor Context — the reactive
replacement for `SecurityContextHolder`, which cannot work here because a single
request is handled across several event-loop threads.

The filter never rejects a request. A missing or invalid token simply leaves the
request unauthenticated, and `AuthorizationWebFilter` decides what that means for
the path being called. Rejecting inside the filter would break `/api/v1/auth/login`,
which is reached without a token by definition.

The database is read only at `/api/v1/auth/login` and `/api/v1/auth/refresh`. Every other
request is authenticated from the signature alone, so no query happens per
request — roughly one read per fifteen minutes instead of one per call.

Form login and HTTP Basic are both disabled. They would otherwise answer with
their own login prompt, and an unauthenticated call would get a `302` redirect
instead of the `401` a REST client expects.

Disabling them is not quite enough on its own. Spring's default entry point still
answered `401` with an empty body and a `WWW-Authenticate: Basic` header, which
made browsers show a credentials prompt for a mechanism this application does not
implement — typing the correct password into it returned `401` all the same,
because nothing reads a `Basic` header here. `JwtAuthenticationEntryPoint`
replaces it: no `WWW-Authenticate`, and the same `ErrorResponseDto` body as every
other failure. It writes that body by hand, since an entry point runs before a
controller is chosen and `GlobalExceptionHandler` never sees it.

### Error format

Every failure returns the same shape, with a machine-readable `code` that stays
stable even when the human message is reworded:

```json
{
  "timestamp": "2026-07-31T01:15:17.673",
  "status": 401,
  "error": "Unauthorized",
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid user name or password"
}
```

| Status | `code` | |
|---|---|---|
| 400 | `VALIDATION_FAILED` | incoming DTO failed its constraints |
| 401 | `INVALID_CREDENTIALS` | unknown username **or** wrong password |
| 401 | `EXPIRED_TOKEN` | refresh token ran out — log in again |
| 401 | `INVALID_TOKEN` | malformed, forged, or the wrong token type |
| 403 | `USER_BLOCKED` | credentials fine, account not `ACTIVE` |
| 403 | `ACCESS_DENIED` | role does not allow the call |
| 404 | `USER_NOT_FOUND` | no such id, or the account was deleted |
| 404 | `FILE_NOT_FOUND` | no such id, deleted, **or owned by somebody else** |
| 404 | `REQUEST_FAILED` | status chosen by Spring itself |
| 409 | `USERNAME_TAKEN` | duplicate caught before the insert |
| 409 | `RESOURCE_CONFLICT` | duplicate caught by the unique constraint |
| 500 | `INTERNAL_ERROR` | logged with a stack trace, never returned to the client |

`401` deliberately does not distinguish an unknown username from a wrong
password. Saying which one failed would let an attacker enumerate valid
usernames.

## Project status

Done:

- Gradle build and dependencies
- Docker Compose: MySQL, MinIO, bucket provisioning
- Flyway schema and seeded administrator
- Entities, enums and repositories
- JWT authentication: login, registration, security chain, global error handling
- Access/refresh token pair, `/api/v1/auth/refresh`, `/api/v1/auth/logout`
- User CRUD with `@PreAuthorize`, soft delete, and the guards that stop an
  administrator demoting, blocking or deleting their own account
- Versioned paths and controllers (`/api/v1`, `AuthControllerV1`)
- Swagger: `Authorize` button, tags, per-operation descriptions and examples
- Named public endpoints, Swagger and the demo page restricted to `dev`
- `JwtAuthenticationEntryPoint`: `401` carries the standard error body
- Static demo page showing how a front end drives the API
- `S3AsyncClient` wired to MinIO, and `S3Storage` as the only class that touches
  the AWS SDK
- `FileService`: upload, ownership-aware reads, update, soft delete, and an event
  recorded on every change
- `EventService` writing the audit trail the specification asks for
- `FileControllerV1`: upload as `multipart/form-data`, ownership-aware reads,
  update and delete closed to a plain `USER` by role
- `EventControllerV1`: the audit trail as an endpoint, with the same
  own-versus-all split as files

Next:

- Downloading the bytes back (the specification's `GET /files/{id}` returns JSON
  only, so this is an addition rather than a requirement)
- Unit tests for the service and mapper layers
- Integration tests, application Dockerfile