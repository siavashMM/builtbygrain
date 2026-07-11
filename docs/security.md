# Security

This document captures the first security foundation for Built by Grain.

## Access Rules

- `GET /api/health` is public.
- Public shop endpoints live under `/api/public/**`.
- User endpoints live under `/api/user/**` and require `USER` or `ADMIN`.
- Admin endpoints live under `/api/admin/**` and require `ADMIN`.

The current backend uses HTTP Basic with temporary in-memory users:

- `user` / `password` with role `USER`
- Admin users with role `ADMIN`, loaded from PostgreSQL

Configure the first admin account by setting environment variables before starting the backend:

```bash
export ADMIN_USERNAME=admin@example.com
export ADMIN_PASSWORD='use-a-long-random-password'
```

These values bootstrap a missing username into `admin_accounts`; passwords are
stored as hashes and subsequent authentication is database-backed.

No production admin password is hardcoded in application code. Replace the temporary in-memory setup with database-backed users before adding customer accounts or production SSO.

## CORS Is Not Authentication

CORS controls which browser origins are allowed to call the API from frontend JavaScript.
It does not prove who the user is and it does not protect private data by itself.

The backend still enforces authentication and roles for `/api/user/**` and `/api/admin/**`.

For local development, the backend allows requests from `http://localhost:4200`.

## Product Prices

The backend must never trust product prices sent from the frontend.

When checkout is implemented, the frontend may send product IDs, quantities, or selected options.
The backend must load trusted prices from its own catalog data before creating payment sessions or orders.

## Stripe Checkout And Card Data

Stripe Checkout will handle card and banking details later.

Banking and card data must never touch the Built by Grain backend. The backend should create Stripe Checkout sessions and let Stripe collect sensitive payment data directly.

## Admin Protection

Admin login and product management operations are implemented under `/api/admin/**` and require the `ADMIN` role.

Current admin endpoints include login verification, list, create, update, activate, deactivate, delete, and product image upload.

Public product browsing should stay under `/api/public/**` so customers can view products without signing in.

The public Angular shop does not link to admin pages. Admin pages are separate routes under `/admin/login` and `/admin/products`, protected by an Angular guard and backed by Spring Security authorization.

Public endpoints only return products where `active` is `true`. Admins can also mark `inStock` separately to show availability without hiding a product.

## Product Image Uploads

Admin image uploads are stored on the backend filesystem under `UPLOADS_ROOT`, which defaults to `uploads`.

The API stores the resulting URL path, such as `/uploads/products/{filename}`, on the product record. Uploaded files are validated by content type and file size. The default maximum size is 5 MB and can be changed with `PRODUCT_IMAGE_MAX_FILE_SIZE_BYTES`.
