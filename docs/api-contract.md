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
GET /api/public/products/{slug}
```

Purpose: return one active product by slug for public shop browsing.

Example product response:

```json
{
  "id": 1,
  "name": "Oak Serving Board",
  "slug": "oak-serving-board",
  "description": "Handmade oak board",
  "priceCents": 4900,
  "currency": "EUR",
  "imageUrl": "https://example.com/oak-serving-board.jpg",
  "inStock": true,
  "sizes": ["S", "M", "L"],
  "active": true
}
```

```http
POST /api/admin/auth/login
GET /api/admin/products
POST /api/admin/products
PUT /api/admin/products/{id}
PATCH /api/admin/products/{id}/deactivate
PATCH /api/admin/products/{id}/activate
DELETE /api/admin/products/{id}
POST /api/admin/products/{id}/image
```

Purpose: allow admins to verify login, list all products, create, update, deactivate, activate, delete, and upload product images.
These endpoints require the `ADMIN` role.

Admin login uses HTTP Basic credentials and returns:

```json
{
  "username": "admin@example.com",
  "admin": true
}
```

Admin product request body:

```json
{
  "name": "Oak Serving Board",
  "slug": "oak-serving-board",
  "description": "Handmade oak board",
  "priceCents": 4900,
  "currency": "EUR",
  "imageUrl": "https://example.com/oak-serving-board.jpg",
  "inStock": true,
  "sizes": ["S", "M", "L"],
  "active": true
}
```

Validation rules:

- `name` is required.
- `slug` is required and unique.
- `priceCents` must be greater than `0`.
- `imageUrl` is optional.
- `description` is optional.
- `inStock` is optional and defaults to `true`.
- `sizes` is optional, supports up to 20 values, and each value is limited to 40 characters.

Product image upload uses multipart form data:

```http
POST /api/admin/products/{id}/image
Content-Type: multipart/form-data
```

Form field:

- `image`: JPEG, PNG, WebP, or GIF within the configured size limit.

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
