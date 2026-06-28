# Architecture

Built by Grain will use a simple monorepo structure with separate frontend and backend applications.

## Applications

- `apps/frontend`: Angular customer and admin user interfaces.
- `apps/backend`: Spring Boot REST API for products, orders, customers, and admin workflows.

## Supporting Areas

- `docs`: Architecture, roadmap, API notes, and coding rules.
- `infra`: Podman, database, and deployment configuration.
- `scripts`: Developer automation such as setup, linting, or local checks.

## Initial Backend Direction

The backend should expose REST APIs and keep business logic separate from controllers.

Future areas:

- Product catalog
- Shopping cart
- Orders
- Admin management
- Stripe payments
- DHL tracking

Stripe and DHL are intentionally out of scope for the initial setup.

## Initial Frontend Direction

The frontend should be an Angular application with clear feature areas.

Future areas:

- Product listing
- Product detail pages
- Cart
- Checkout
- Admin dashboard

Checkout can be designed later, after product and order basics exist.
