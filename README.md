# telemed-ia-identity-and-access-api

Identity and Access API for the TeleMed IA project, Group 2.

Governance repository:  
[https://github.com/code-corhuila/telemed-ia-docs](https://github.com/code-corhuila/telemed-ia-docs)

## Modules

- `identity-and-access-core`: domain model, inbound and outbound ports, and application use cases. This module must remain independent from Spring, JPA, HTTP, and other infrastructure frameworks.
- `identity-and-access-adapters`: inbound HTTP adapters and outbound persistence and security adapters.
- `identity-and-access-app`: Spring Boot application, composition root, configuration, runtime resources, and executable artifact.

The base package remains:

```text
com.telemed.identityaccess
```

The application bootstrap class remains in that package so Spring Boot can discover the components, entities, repositories, filters, and configuration classes provided by the dependent modules.

## Architecture

The service follows Hexagonal Architecture.

Dependencies point inward:

```text
HTTP / Persistence / Security
            ↓
          Ports
            ↓
        Use Cases
            ↓
          Domain
```

The `identity-and-access-core` module does not depend on Spring Boot, Spring Data JPA, HTTP libraries, database drivers, or other infrastructure technologies.

Database schema ownership belongs exclusively to:

```text
telemed-ia-identity-and-access-db
```

This API does not contain or execute database migrations.

Hibernate validates the existing schema and must not create or modify database structure.

## Technology Baseline

The current technical baseline is:

- Java 21
- Spring Boot 3.5
- Maven 3.9
- PostgreSQL
- Spring Data JPA
- BCrypt
- JWT authentication
- Docker

Development, CI, and Docker use Java 21.

## Verification

From the repository root:

```sh
mvn -B clean verify
```

To confirm the configured Java release:

```sh
mvn help:evaluate -Dexpression=maven.compiler.release -q -DforceStdout
```

Expected result:

```text
21
```

The CI workflow executes:

```sh
mvn -B verify
```

using Java 21.

## Running Locally

Configure the environment variables documented in `.env.example`.

Spring Boot does not automatically load `.env` files. Variables must be exported by the execution environment, IDE, Docker Compose, or infrastructure layer.

The PostgreSQL database must already exist and must have been provisioned by the migrations from:

```text
telemed-ia-identity-and-access-db
```

Build the service:

```sh
mvn -B clean package
```

Run the application:

```sh
java -jar identity-and-access-app/target/identity-and-access-app.jar
```

Default application port:

```text
8081
```

Default management port:

```text
9081
```

Current authentication endpoints include:

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
```

Additional Identity and Access endpoints are introduced incrementally through the corresponding User Story Pull Requests.

## Runtime Limits

Runtime limits are explicitly configured in the application composition layer.

The current baseline includes:

- HTTP connection timeout
- HTTP keep-alive timeout
- Maximum keep-alive requests
- Database connection pool size
- Database connection acquisition timeout
- Database validation timeout
- PostgreSQL statement timeout
- Graceful shutdown timeout

These values can be configured through environment variables documented in `.env.example`.

## Docker

Build the image from the repository root:

```sh
docker build -f deploy/Dockerfile -t telemed-identity-api .
```

Run using Docker Compose:

```sh
docker compose --env-file .env -f deploy/compose.yml up --build
```

The service joins the external `platform` Docker network, where the API gateway reaches it as `identity-api:8081`. Create the network once if it does not exist:

```sh
docker network create platform
```

The API image does not create database schemas and does not execute migrations.

The database connection configured through `DB_URL` must be reachable from inside the container.

When the service is composed from the infrastructure repository, the database hostname must correspond to the service name available in the Docker network rather than `localhost`.

## Security

Authentication and authorization are enforced by the backend.

The service uses the following technical roles:

```text
PATIENT
PROFESSIONAL
ADMIN
```

Passwords are never stored in plain text and are hashed using BCrypt.

### Bootstrap administrator

Public registration only creates patients, so the first `ADMIN` comes from the environment: `BOOTSTRAP_ADMIN_FULL_NAME`, `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_IDENTITY_DOCUMENT` and `BOOTSTRAP_ADMIN_PASSWORD` (see `.env.example`). At startup:

| Situation | Result |
|---|---|
| No variable set | Nothing is created |
| An `ADMIN` already exists | Nothing changes; logs `bootstrap admin already present` |
| No `ADMIN` and all four set | Creates an active, verified `ADMIN`; logs `bootstrap admin created` |
| The email or document belongs to another account | That account is never promoted; logs a warning |
| Only some variables set, or a password outside the policy | The service refuses to start |

It is a one-time bootstrap, not a way to manage the password: changing the variables later does not update the existing admin, and they may be emptied once it exists. Each environment uses its own values, which are never committed or logged. A forced password change on the first login is a future improvement that needs a new column in the `-db` repository.

Authentication credentials, tokens, password hashes, private keys, and other secrets must never be committed to the repository or written to application logs.

The final authentication design uses RS256 for JWT access tokens.

The Identity and Access service is responsible for token issuance and therefore receives its private key through runtime secret configuration.

Other services validate access tokens using the corresponding public key.

The current migration from the previous HS256 implementation to RS256 is performed through a dedicated User Story Pull Request.

### Account administration (FR-012)

Administrators view the registered accounts under `/api/v1/auth/admin/accounts`, which the gateway already routes. The service answers `401 UNAUTHORIZED` without a valid access token and `403 FORBIDDEN` for any role but `ADMIN`.

- `GET /api/v1/auth/admin/accounts`: `q` (part of the name, email or identity document), `role` (`PATIENT`, `PROFESSIONAL` or `ADMIN`), `active` (`true` or `false`), `page` (from 1) and `limit` (1 to 100, default 20). Newest registrations come first. The response is `{"data": [...], "meta": {"page", "limit", "total", "totalPages"}}`; an invalid parameter gets `400 VALIDATION_ERROR` with `details`.
- `GET /api/v1/auth/admin/accounts/{id}`: one account, or `404 NOT_FOUND`.

Each account has `id`, `fullName`, `email`, `identityDocument`, `role`, `active`, `registrationDate` and `lastAccess` (RFC 3339 UTC; `null` before the first sign-in). Every successful login stamps `lastAccess`. The password hash is never returned.

`PUT /api/v1/auth/admin/accounts/{id}/status` with `{"active": false}` blocks an account and `{"active": true}` restores it, answering the updated account. Repeating the current value changes nothing.

- A blocked account cannot sign in or refresh its session: all its refresh tokens are revoked in the same transaction. An access token it already holds stays valid until it expires (`JWT_ACCESS_TOKEN_TTL`, one hour by default), because every service validates tokens locally.
- `409 ACCOUNT_STATUS_CONFLICT` when an administrator tries to block their own account or the last active administrator. The active administrators are counted with `FOR UPDATE`, so two administrators blocking each other cannot both succeed.
- `403 FORBIDDEN` when the requester's own account is no longer an active administrator, even with a token that has not expired.
- This is the account status, not the professional profile status that professional management owns (HU-05 AC3).

### Professional accounts (internal)

Professionals never register themselves: professional management creates their account with `POST /internal/accounts`, inside the `platform` network only (the gateway does not route `/internal`).

- **Caller:** a service token (`role: SERVICE`) of a client listed in `ACCOUNT_PROVISIONING_CLIENTS` (default `professional-management`). No token or an invalid one gets `401 UNAUTHORIZED`; a user token or another service gets `403 FORBIDDEN`. Both are checked before the body is read.
- **`Idempotency-Key` header** (8 to 128 characters), stored with the account in `identity_idempotency_key` in the same transaction.
- **Body:** `{"fullName", "email", "identityDocument", "password", "role": "PROFESSIONAL"}`, with the same rules as a patient registration. Only `PROFESSIONAL` is accepted.

| Result | Status |
|---|---|
| Account created | `201 {"id": "<uuid>"}` |
| Same key and same body again | `200` with the same `id`; nothing new is created |
| Same key with a different body | `422 IDEMPOTENCY_KEY_REUSED` |
| Email or identity document already registered | `409 REGISTRATION_CONFLICT` |
| Invalid field or `Idempotency-Key` | `400 VALIDATION_ERROR` with `details` |
| Password outside the policy | `400 INVALID_REGISTRATION` |

`GET /internal/accounts?ids=<uuid>,<uuid>` (1 to 100 ids, same callers) answers `{"data": [{"id", "fullName", "email"}]}` so professional management can show the names of the professionals it lists. Only `PROFESSIONAL` accounts are answered: unknown ids and accounts of any other role are left out, so this endpoint never reveals patients or administrators. Ids that are not UUIDs get `400 VALIDATION_ERROR`.

### Notification recipients (internal)

Notifications e-mails patients about their appointments (HU-17); identity owns the address.
`GET /internal/notification-recipients/{id}` answers `{"id", "email"}` of an **active patient**.

- **Caller:** a service token of a client listed in `NOTIFICATION_RECIPIENTS_CLIENTS` (default `notifications`); `401` and `403` as above. These clients may not use `/internal/accounts`, and the clients of `/internal/accounts` may not use this endpoint.
- Any other account (unknown, blocked, professional or administrator) gets `404 NOT_FOUND`; an id that is not a UUID gets `400 VALIDATION_ERROR`.

## Error Handling and Correlation

The public error contract uses the following structure:

```json
{
  "error": "ERROR_CODE",
  "message": "Human-readable message",
  "details": [],
  "traceId": "correlation-id"
}
```

The service uses `X-Correlation-Id` for request tracing.

A valid incoming correlation ID is reused. If the header is missing or invalid, the API generates a new identifier.

The same value is:

- Returned in the response header.
- Stored in MDC as `traceId`.
- Included in the error response.
- Used for structured request tracing.

## Database Integration

The API consumes the Identity and Access PostgreSQL database but does not own its migrations.

The active identifier for users is UUID.

New application operations must not depend on legacy identifiers such as:

```text
legacy_id
legacy_user_id
```

Persistence integration tests must run against PostgreSQL behavior rather than H2.

When integration tests require an externally provisioned database, the connection is provided through:

```text
TEST_DATABASE_URL
```

## User Story Scope

This repository implements the Identity and Access responsibilities associated with the following User Stories.

### HU-01 — Patient Registration

Includes:

- Patient account registration.
- Unique email validation.
- Unique identity document validation.
- Secure password hashing using BCrypt.
- Technical `PATIENT` role.
- UUID-based user identifiers.

### HU-02 — Login and Logout

Includes:

- Authentication with generic invalid-credential responses.
- Inactive-account rejection.
- Resistance to email enumeration.
- JWT access token issuance.
- Refresh token lifecycle.
- Refresh token rotation.
- Logout and refresh token revocation.
- Role-based authorization.

### HU-03 — Password Recovery

Includes:

- Password recovery requests.
- Time-limited reset tokens.
- Hashed reset-token persistence.
- Token supersession.
- Expired, used, and superseded token rejection.
- Secure password reset.
- Active session revocation after password change.
- Delivery through the transactional outbox: the request records `PasswordRecoveryRequested` in
  `identity_outbox_event`, and the relay publishes it to the RabbitMQ topic exchange `identity.events`
  (routing key `password-recovery-requested`, ADR-011 envelope) for notifications. An event counts as
  published only when the broker confirmed it and a queue took it; then its `resetUrl` is removed from the
  table. The relay runs every `OUTBOX_RELAY_INTERVAL` when `OUTBOX_RELAY_ENABLED=true` (off by default).

## Development Workflow

All changes are developed in child branches and integrated through Pull Requests.

No direct commits are made to permanent branches:

```text
develop
qa
main
```

Work starts from `develop` using one task per branch.

Examples:

```text
feat/hu-02-refresh-logout
fix/hu-01-uuid-db-alignment
chore/identity-api-normative-baseline
```

Promotion between permanent branches is performed by reapplying commits with:

```sh
git cherry-pick -x <commit-sha>
```

Permanent branches are never merged into each other.

Published branch history must not be rewritten.

`CODEOWNERS` must not be modified or removed.

## Pull Request Rules

Each Pull Request must:

- Reference the corresponding User Story or task.
- Remain within the 400-line production-code limit.
- Include tests for new behavior.
- Pass `mvn -B verify`.
- Contain no secrets.
- Contain no database migrations.
- Preserve the public API contract.
- Keep architectural boundaries intact.

Tests and generated files are excluded from the 400-line limit according to the project repository rules.

## Testing Strategy

New behavior is developed incrementally following TDD.

The expected development sequence is:

```text
test → implementation → refactor
```

Tests cover the appropriate level:

- Core unit tests for business rules.
- HTTP tests for request validation, authentication, errors, and correlation.
- Persistence integration tests against PostgreSQL.
- End-to-end tests for complete authentication flows.

Legacy tests are not removed until equivalent or stronger coverage exists.

## Current Implementation Roadmap

The repository is being completed incrementally without replacing already validated work.

The remaining implementation sequence includes:

1. UUID alignment with the final database model.
2. RS256 access-token issuance and validation.
3. One-hour access-token lifetime.
4. Refresh-token generation, hashing, rotation, and revocation.
5. Idempotent logout.
6. Password recovery and password reset.
7. PostgreSQL integration coverage.
8. End-to-end HTTP and PostgreSQL coverage.
9. Final security and OpenAPI contract verification.
10. Registration idempotency after the corresponding database storage is introduced in the `-db` repository.

Registration idempotency will be completed only after the database provides durable storage for the `Idempotency-Key`, because the resource and its idempotency key must be persisted atomically.

## Repository Boundaries

This repository owns:

- Authentication.
- Authorization.
- Credentials.
- Access tokens.
- Refresh tokens.
- Password recovery.
- Technical identity roles.

It does not own:

- Patient clinical information.
- Appointments.
- Medical consultations.
- Professional clinical data.
- Document generation.
- Database migrations.

Those responsibilities belong to their corresponding bounded contexts and repositories.