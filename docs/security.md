# Security

## Access and admin sessions

- Health and `/api/public/**` endpoints are public.
- `/api/admin/**` requires a PostgreSQL-backed `ADMIN` session, except the CSRF and login endpoints.
- There is no fallback customer identity or placeholder `USER` surface.

Bootstrap a missing admin account with environment-only credentials:

```bash
export ADMIN_USERNAME=admin@example.com
export ADMIN_PASSWORD='use-a-long-random-password'
```

Passwords are adaptive Spring Security hashes in `admin_accounts`. Admin login creates a 30-minute server-side
JDBC session. The `BBG_ADMIN_SESSION` cookie is `HttpOnly`
and `SameSite=Lax`; the `prod` profile forces it to `Secure`. The frontend stores no credential or authentication
token in web storage.

Authenticated administrators change their password in **Settings**. `POST /api/admin/auth/password`
requires the current password, accepts a new password of at least 12 characters, updates the
stored BCrypt hash, and invalidates every active session for that administrator.

All mutating requests use the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header. Fetch `/api/admin/auth/csrf` before login and after login/logout. Authentication failures are generic.

Production is same-origin. `CORS_ALLOWED_ORIGINS` accepts a comma-separated set of exact development origins and defaults to `http://localhost:4200`; wildcard credentialed CORS is not enabled.

## Rate limiting and edge protection

Rate limiting is layered. Caddy rejects broad per-client API spikes before they
reach Spring Boot. The application uses atomic fixed-window counters in the
Flyway-owned `rate_limit_buckets` table, so limits remain shared across backend
instances. Subjects are SHA-256 hashed before storage; passwords and reset
tokens are never stored in rate-limit rows.

Default application policies are:

| Operation | Scope and window |
| --- | --- |
| Customer login | 5 failed attempts/account/15 min and 20/IP/15 min |
| Admin login | 5 failed attempts/account/15 min and 10/IP/15 min |
| Registration | 5/IP/hour |
| Password-reset request | 3/account/hour and 10/IP/hour |
| Password reset | 5/token/15 min and 10/IP/15 min |
| Authenticated admin writes | 30/admin/min |
| Admin multipart uploads | 10/admin/10 min, in addition to the write limit |
| Authenticated customer writes | 30/customer/min |

Account and IP login buckets are independent. A successful login clears only
that account's failure bucket and never clears the IP bucket. Unknown account
names consume the same hashed account policy so response status and storage
behavior do not reveal whether an identity exists. Rejected requests return a
generic `429 Too Many Requests`, `Retry-After`, and `Cache-Control: no-store`.

Every default is configurable through the `RATE_LIMIT_*` variables mapped in
`application.properties`. Changes should be based on observed blocked-request
metrics and customer impact rather than disabling a policy.

Spring uses `request.getRemoteAddr()` only after the trusted edge has replaced
forwarding headers. The production native forwarded-header strategy must be
paired with a private backend listener and an edge/CDN trust list; never expose
the backend publicly while accepting client-supplied forwarding headers.

## Product images

Uploads are stored in a private S3-compatible bucket and are served only through `/api/public/uploads/{products|storefront}/{uuid}.{jpg|png|webp|gif}`. Configure the S3 endpoint, region, bucket, credentials, and path-style mode with the `OBJECT_STORAGE_*` variables shown in `.env.example`.

The backend validates JPEG, PNG, GIF, and WebP byte signatures, verifies any declared media type, enforces the 5 MB limit on actual bytes, and generates object keys. Newly stored objects are deleted after database rollback; replacements and deletions are removed only after commit. Public proxy responses use strict key validation, immutable cache headers, ETag/Last-Modified, GET/HEAD, and byte ranges.

## General safeguards

The backend is authoritative for prices. A future checkout must reload catalog data rather than trusting browser
values.
