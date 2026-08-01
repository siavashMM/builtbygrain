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

All mutating requests use the `XSRF-TOKEN` cookie and `X-XSRF-TOKEN` header. Fetch `/api/admin/auth/csrf` before login and after login/logout. Authentication failures are generic. PostgreSQL-backed throttling blocks five failures per known account/IP or twenty per IP within 15 minutes for 15 minutes.

Production is same-origin. `CORS_ALLOWED_ORIGINS` accepts a comma-separated set of exact development origins and defaults to `http://localhost:4200`; wildcard credentialed CORS is not enabled.

## Product images

Uploads are stored in a private S3-compatible bucket and are served only through `/api/public/uploads/{products|storefront}/{uuid}.{jpg|png|webp|gif}`. Configure the S3 endpoint, region, bucket, credentials, and path-style mode with the `OBJECT_STORAGE_*` variables shown in `.env.example`.

The backend validates JPEG, PNG, GIF, and WebP byte signatures, verifies any declared media type, enforces the 5 MB limit on actual bytes, and generates object keys. Newly stored objects are deleted after database rollback; replacements and deletions are removed only after commit. Public proxy responses use strict key validation, immutable cache headers, ETag/Last-Modified, GET/HEAD, and byte ranges.

## General safeguards

The backend is authoritative for prices. A future checkout must reload catalog data rather than trusting browser
values.
