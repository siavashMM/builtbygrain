# Built by Grain

Built by Grain is an ecommerce platform for selling handcrafted wooden products in Germany.

This repository is a monorepo. It will contain an Angular frontend, a Spring Boot backend, documentation, infrastructure files, and helper scripts.

## Project Structure

```
apps/
  frontend/   # Future Angular application
  backend/    # Future Spring Boot application
docs/         # Project documentation
infra/        # Future Podman and deployment files
scripts/      # Future developer scripts
```

## Current Status

Initial repository structure only.

No Angular app, Spring Boot app, payment integration, or DHL integration has been generated yet.

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
