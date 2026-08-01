# Built by Grain Backend

Customer account setup, password-reset mail, and checkout integration details are
documented in [`docs/customer-accounts.md`](../../docs/customer-accounts.md).

Spring Boot REST API for Built by Grain.

## Stack

- Java 21
- Spring Boot
- Maven Wrapper
- PostgreSQL
- REST over JSON

PostgreSQL is used for local development. Flyway manages database migrations.

## Run Locally

Prerequisites:

- Java 21 active on `PATH` or through `JAVA_HOME`
- PostgreSQL and a private S3-compatible object store (the Podman services are documented in `../../docs/setup.md`)

Maven does not need to be installed globally. Use the committed Maven Wrapper.
Start PostgreSQL first; see `../../docs/setup.md`.

From the backend directory:

```bash
cd apps/backend
export ADMIN_USERNAME=admin
export ADMIN_PASSWORD='choose-a-strong-password'
./mvnw spring-boot:run
```

The first startup stores this admin account in PostgreSQL with a hashed password.
Later startups authenticate from the `admin_accounts` table; the environment values
are only used to create a missing username.

An authenticated administrator can change the password from **Admin → Settings**.
The current password is required, the replacement must contain at least 12 characters,
and a successful change invalidates every active session for that administrator.

Copy `../../.env.example` to the ignored `.env`, replace its placeholders, and run
`just dev` from the repository root to start PostgreSQL and loopback-only MinIO.

The API starts on `http://localhost:8080`.

Health check:

```bash
curl http://localhost:8080/api/health
```

Expected response shape:

```json
{
  "status": "ok",
  "timestamp": "2026-06-28T12:00:00Z"
}
```

## Test

```bash
cd apps/backend
./mvnw test
```
