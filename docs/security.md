# Security

This document captures the first security foundation for Built by Grain.

## Access Rules

- `GET /api/health` is public.
- Public shop endpoints live under `/api/public/**`.
- User endpoints live under `/api/user/**` and require `USER` or `ADMIN`.
- Admin endpoints live under `/api/admin/**` and require `ADMIN`.

The current backend uses HTTP Basic with temporary in-memory users:

- `user` / `password` with role `USER`
- `admin` / `admin` with roles `USER` and `ADMIN`

These accounts are for local development only and must be replaced before production.

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

Product create, update, and deactivate operations are implemented under `/api/admin/**` and require the `ADMIN` role.

Public product browsing should stay under `/api/public/**` so customers can view products without signing in.
