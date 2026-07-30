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
gradlew bootRun
```

Flyway applies `V1__init.sql` and `V2__seed_admin.sql` on startup. The second
migration seeds the first administrator — see [Authentication](#authentication).

| | |
|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI spec | http://localhost:8080/v3/api-docs |
| MinIO console | http://localhost:9001 (`minioadmin` / `minioadmin`) |

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
| `S3_ENDPOINT` | `http://localhost:9000` | |
| `S3_ACCESS_KEY` / `S3_SECRET_KEY` | `minioadmin` / `minioadmin` | |
| `S3_BUCKET` | `files` | |

## Domain

- **User** — `id`, `username`, `password_hash`, `role`, `status`
- **File** — `id`, `name`, `location` (S3 key), `status`, `user_id`
- **Event** — `id`, `user_id`, `file_id`, `status`, `created_at`

MySQL stores metadata and permissions; MinIO stores the bytes. `files.location`
holds the object key, never the file itself.

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

| Method | Path | ADMIN | MODERATOR | USER |
|---|---|---|---|---|
| `POST` | `/auth/register` | — open, no token — |||
| `POST` | `/auth/login` | — open, no token — |||
| `GET` | `/users` | ✅ | ✅ | ❌ |
| `GET` | `/users/{id}` | any | any | self only |
| `POST` | `/users` | ✅ | ❌ | ❌ |
| `PUT` | `/users/{id}` | ✅ | ❌ | ❌ |
| `DELETE` | `/users/{id}` | ✅ | ❌ | ❌ |
| `POST` | `/files` | ✅ | for self | for self |
| `GET` | `/files` | all | all | own |
| `GET` | `/files/{id}` | any | any | own |
| `PUT` | `/files/{id}` | ✅ | ✅ | ❌ |
| `DELETE` | `/files/{id}` | ✅ | ✅ | ❌ |
| `POST` | `/events` | ✅ | ❌ | ❌ |
| `GET` | `/events` | all | all | own |
| `GET` | `/events/{id}` | any | any | own |
| `PUT` | `/events/{id}` | ✅ | ✅ | ❌ |
| `DELETE` | `/events/{id}` | ✅ | ✅ | ❌ |

Registration is open and always produces `role = USER`, `status = ACTIVE` —
accepting a role from the request body would make authorisation meaningless. A
successful registration returns a token immediately, so no second call is needed.

`POST /events` exists only because the requirements ask for full CRUD on Event.
Events are normally created by the application itself on every upload, never by
a client.

### Deletion

`DELETE` is a soft delete: it sets `deleted_at` and keeps the row, because the
data is retained for later analysis. Every read filters on `deleted_at IS NULL`.

```
DELETE /files/{id}              soft — ADMIN, MODERATOR
DELETE /files/{id}?hard=true    hard — ADMIN only, MODERATOR gets 403
```

Hard delete physically removes the row and the object in MinIO. Because the
foreign keys are `RESTRICT`, the service walks the chain bottom-up — events, then
files, then the user — and removes the S3 objects before the rows that hold their
keys.

### Roles vs ownership

Role is checked before the method runs; ownership ("own files only") can only be
checked after the row is loaded and its `user_id` is known. Ownership checks
therefore live in the service layer, not in an annotation.

### Tokens

One access token, no refresh flow. When it expires the client logs in again. TTL
is a day so that a token pasted into Swagger UI survives a working session;
production would use minutes plus a refresh token.

Status is not carried in the token, so blocking a user takes effect only once
their current token expires.

## Authentication

`V2__seed_admin.sql` inserts the first administrator, because registration only
ever grants `USER` and there would otherwise be no way to reach an ADMIN-only
endpoint:

```
admin / admin        development only — change it in any real environment
```

Log in and keep the token:

```bash
curl -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin"}'
```

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "username": "admin", "role": "ADMIN" }
```

Send it on every other call:

```
Authorization: Bearer <token>
```

### How a request is authenticated

`JwtAuthenticationFilter` runs at the `AUTHENTICATION` position of the security
chain. It reads the `Authorization` header, verifies the signature and expiry,
and publishes an `Authentication` into the Reactor Context — the reactive
replacement for `SecurityContextHolder`, which cannot work here because a single
request is handled across several event-loop threads.

The filter never rejects a request. A missing or invalid token simply leaves the
request unauthenticated, and `AuthorizationWebFilter` decides what that means for
the path being called. Rejecting inside the filter would break `/auth/login`,
which is reached without a token by definition.

The database is read once, during login. Afterwards the signed token is the only
proof required, so no query happens per request.

Form login and HTTP Basic are both disabled. They would otherwise answer with
their own login prompt, and an unauthenticated call would get a `302` redirect
instead of the `401` a REST client expects.

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
| 403 | `USER_BLOCKED` | credentials fine, account not `ACTIVE` |
| 403 | `ACCESS_DENIED` | role does not allow the call |
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

Next:

- Swagger `Authorize` button (`@SecurityScheme`)
- Unit tests for the service and mapper layers
- Users, files and events CRUD with `@PreAuthorize`
- S3 upload/download and event recording
- OpenAPI annotations, integration tests, application Dockerfile