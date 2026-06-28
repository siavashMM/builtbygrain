# Built by Grain

Built by Grain is an ecommerce platform for selling handcrafted wooden products in Germany.

This repository is a monorepo. It will contain an Angular frontend, a Spring Boot backend, documentation, infrastructure files, and helper scripts.

## Project Structure

```
apps/
  frontend/   # Angular frontend application
  backend/    # Spring Boot backend application
docs/         # Project documentation
infra/        # Future Podman and deployment files
scripts/      # Future developer scripts
```

## Current Status

Initial repository structure with Angular frontend and Spring Boot backend scaffolds.

No payment integration, DHL integration, or database connection has been generated yet.

## Planned Stack

- Frontend: Angular and TypeScript
- Backend: Java and Spring Boot
- Database: PostgreSQL
- Containers: Podman
- Payments: Stripe, later
- Shipping: DHL tracking, later

## Development Approach

- Keep changes small and reviewable.
- Document decisions before implementation.
- Prefer simple architecture until the product needs more.
- Build REST APIs with clear contracts.
- Keep frontend and backend separate inside the monorepo.

## Backend Quick Start

Java 21 should be active on `PATH` or through `JAVA_HOME`. Maven is provided by the backend Maven Wrapper.

```bash
cd apps/backend
./mvnw spring-boot:run
```

Health check:

```bash
curl http://localhost:8080/api/health
```

## Frontend Quick Start

```bash
cd apps/frontend
npm install
npm start
```

The frontend runs on `http://localhost:4200` and proxies `/api` requests to the backend on `http://localhost:8080`.
