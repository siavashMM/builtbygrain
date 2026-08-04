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

## Production HTTPS edge

The production edge is defined in `infra/edge`. Its image builds the Angular
production bundle, obtains and renews HTTPS certificates through Caddy, serves
the SPA, and forwards `/api`, `/oauth2`, and `/login/oauth2` to Spring Boot.
The backend port must remain private; only the edge should be publicly reachable.

Before starting it:

1. Point the storefront domain's DNS records at the host.
2. Allow inbound TCP 80/443 and UDP 443.
3. Run Spring Boot with the `prod` profile and production secrets.
4. Set `APP_DOMAIN` and, when necessary, `BACKEND_UPSTREAM`.

```bash
export APP_DOMAIN=shop.example.com
export BACKEND_UPSTREAM=host.containers.internal:8080
just edge-up
```

The `builtbygrain-caddy-data` volume contains certificate state and must be
preserved. Run `just edge-validate` after changing the edge configuration.

### Managed CDN in front of the edge

The edge works directly or behind a managed CDN. When a CDN is enabled:

- Use strict HTTPS from the CDN to Caddy; never use an HTTP-only origin mode.
- Configure the CDN to respect origin `Cache-Control`. Angular fingerprinted
  JavaScript/CSS and public uploads are long-lived; public catalog responses
  use a five-minute shared-cache lifetime. Never cache `/api/account/**`,
  `/api/admin/**`, `/oauth2/**`, or `/login/oauth2/**`.
- Mirror the broad API, authentication, and upload limits from the Caddyfile at
  the CDN so abusive traffic is rejected before reaching the origin.
- Set `TRUSTED_PROXY_CIDRS` to only the CDN's published egress ranges and set
  `CLIENT_IP_HEADERS` to its documented client-address header. Do not use
  `0.0.0.0/0` or trust arbitrary forwarded headers.

Without a CDN, leave the reserved non-routable default unchanged; direct public
clients are then identified by their connection address. Do not broadly trust
all private ranges because a container port-forwarder may itself use a private
source address.

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
