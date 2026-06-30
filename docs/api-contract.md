# API Contract

This document will describe the REST API between the Angular frontend and Spring Boot backend.

The initial health endpoint is implemented.

## Current Endpoints

```http
GET /api/health
```

Purpose: confirm that the backend application is running.

Example response:

```json
{
  "status": "ok",
  "timestamp": "2026-06-28T12:00:00Z"
}
```

```http
GET /api/public/products
```

Purpose: return active products for public shop browsing.

```http
POST /api/admin/products
PUT /api/admin/products/{id}
PATCH /api/admin/products/{id}/deactivate
```

Purpose: allow admins to create, update, and deactivate products.
These endpoints require the `ADMIN` role.

## Future API Areas

- Products
- Categories
- Cart
- Orders
- Customers
- Admin
- Payments
- Shipping

## Draft Conventions

- Base path: `/api`
- Request and response format: JSON
- Dates and times: ISO 8601
- Errors: consistent JSON error responses
