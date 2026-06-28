# AI Coding Rules

These rules guide AI-assisted development for Built by Grain.

## General Rules

- Keep the project simple and professional.
- Make small, focused changes.
- Explain important decisions in documentation.
- Do not add frameworks or services before they are needed.
- Prefer clear code over clever code.

## Monorepo Rules

- Put frontend code in `apps/frontend`.
- Put backend code in `apps/backend`.
- Put documentation in `docs`.
- Put infrastructure files in `infra`.
- Put helper scripts in `scripts`.

## Frontend Rules

- Use Angular when the frontend is generated.
- Keep components focused.
- Keep API calls in services.
- Keep customer and admin areas clearly separated.

## Backend Rules

- Use Java and Spring Boot when the backend is generated.
- Use REST APIs.
- Keep controllers thin.
- Put business logic in services.
- Keep persistence details separate from API models where practical.

## Scope Rules

- Do not implement Stripe until the catalog, cart, and order basics exist.
- Do not implement DHL tracking until order management exists.
- Do not add production deployment before local development works.
