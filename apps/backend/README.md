# Built by Grain Backend

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
