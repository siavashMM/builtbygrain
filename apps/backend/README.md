# Built by Grain Backend

Spring Boot REST API for Built by Grain.

## Stack

- Java 21
- Spring Boot
- Maven Wrapper
- REST over JSON

PostgreSQL is planned, but this initial backend does not configure a database connection.

## Run Locally

Prerequisites:

- Java 21 active on `PATH` or through `JAVA_HOME`

Maven does not need to be installed globally. Use the committed Maven Wrapper.

From the backend directory:

```bash
cd apps/backend
./mvnw spring-boot:run
```

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
