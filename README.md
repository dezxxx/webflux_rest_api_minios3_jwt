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
| Build | Gradle 9.5.1 with a version catalog |
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

Flyway applies `V1__init.sql` on startup.

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

## Project status

Done:

- Gradle build, dependencies, version catalog
- Docker Compose: MySQL, MinIO, bucket provisioning
- Flyway baseline schema

Next:

- Entities and repositories
- JWT authentication and authorization
- S3 upload/download and event recording
- OpenAPI annotations, integration tests, application Dockerfile