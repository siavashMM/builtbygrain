# Local setup

## PostgreSQL and object storage

Create an ignored local environment file, replace both credential placeholders, and start the loopback-bound services:

```bash
cp .env.example .env
just dev
```

PostgreSQL listens on `127.0.0.1:5432`; MinIO uses `127.0.0.1:9000` and its console uses `127.0.0.1:9001`. Runtime uploads belong in the private `builtbygrain-media` bucket.

## Backend

The local database defaults are `builtbygrain` for the database, username, and password. Bootstrap a missing admin by setting environment-only credentials:

```bash
export ADMIN_USERNAME=admin@example.com
export ADMIN_PASSWORD='use-a-long-random-password'
just backend
```

Flyway owns catalog, Spring Session, throttling, and integrity schemas. A missing bootstrap username is created with a BCrypt password hash; no account is invented when the variables are absent.

Deployments must activate the `prod` Spring profile. It forces secure admin cookies and disables development CORS origins unless exact origins are explicitly configured.

## Admin API session

The Angular client handles this automatically. Command-line clients must retain cookies and echo the CSRF token. With `jq` installed:

```bash
curl -s -c /tmp/bbg-cookies http://localhost:8080/api/admin/auth/csrf > /tmp/bbg-csrf.json
CSRF_TOKEN=$(jq -r .token /tmp/bbg-csrf.json)
curl -s -b /tmp/bbg-cookies -c /tmp/bbg-cookies \
  -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $CSRF_TOKEN" \
  -d '{"username":"admin@example.com","password":"use-a-long-random-password"}' \
  http://localhost:8080/api/admin/auth/login
```

Fetch `/api/admin/auth/csrf` again after login, then include its token and the cookie jar on every POST, PUT, PATCH, or DELETE. See `backend-api.http` for executable IDE examples.

## Frontend

```bash
just frontend
```

Customer routes include `/`, `/category/**`, `/products/{slug}`, and `/cart`. Admin routes begin at `/admin/login`;
the guarded tree is lazy loaded. Guest cart/wishlist preferences may use `localStorage`; credentials and sessions
are never stored there.

The shopping bag is browser-local and survives refreshes, but is not shared between browsers or devices. Checkout
Account and Delivery onboarding begin at `/checkout/account`; displayed totals remain estimates until server-validated
order and payment processing are implemented. See [checkout-onboarding.md](checkout-onboarding.md) for optional
Google, Apple, and address-recommendation setup.

Run `just verify` for backend tests, frontend headless tests, and a production build. Run `just security-check` for npm audit plus the pinned Maven vulnerability scan.
