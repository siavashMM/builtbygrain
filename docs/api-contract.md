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

## Example Future Endpoint

```http
GET /api/products
```

Purpose: return a list of products for the shop catalog.

This is only a placeholder example and should be refined before implementation.
