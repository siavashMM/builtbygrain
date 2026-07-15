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

Purpose: return active product cards for public list views.

The response is an array of compact product-card objects in ascending name order. Inactive products are never included. Card-image fallback rules and the cheapest active-variant price are resolved by the backend.

```json
{
  "id": 1,
  "name": "Oak Serving Board",
  "slug": "oak-serving-board",
  "currency": "EUR",
  "fromPriceCents": 4900,
  "primaryImageUrl": "/api/public/uploads/products/oak-front.webp",
  "hoverImageUrl": "/api/public/uploads/products/oak-side.webp",
  "categoryId": 2,
  "categoryName": "Serving boards",
  "categorySlug": "serving-boards",
  "colorSwatches": [{
    "id": 12,
    "label": "Oak",
    "swatchHex": "#B78B5E",
    "swatchImageUrl": null,
    "primaryImageUrl": "/api/public/uploads/products/oak-front.webp",
    "hoverImageUrl": "/api/public/uploads/products/oak-side.webp"
  }]
}
```

```http
GET /api/public/products/{slug}
```

Purpose: return one active product by slug for public shop browsing.

Returns `404 Not Found` when the slug does not exist or belongs to an inactive product. This keeps inactive catalog data out of the public API.

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
  "imageUrls": ["https://example.com/oak-serving-board.jpg"],
  "inStock": true,
  "sizes": ["S", "M", "L"],
  "active": true
}
```

`priceCents` and `currency` are backend-provided display values. The guest cart may cache them for rendering an estimated subtotal, but they are not authoritative checkout input. A future checkout request must contain only product IDs, quantities, and any supported option identifiers; the backend will reload active products and calculate the authoritative total.

```http
POST /api/admin/auth/login
GET /api/admin/products
POST /api/admin/products
PUT /api/admin/products/{id}
PATCH /api/admin/products/{id}/deactivate
PATCH /api/admin/products/{id}/activate
DELETE /api/admin/products/{id}
POST /api/admin/products/{id}/images
GET /api/admin/products/{id}/images
GET /api/admin/products/{id}/listing-images
PUT /api/admin/products/{id}/listing-images/{primary|hover}
```

Purpose: allow admins to verify login, list all products, create, update, deactivate, activate, delete, and upload product images.
These endpoints require the `ADMIN` role.

The listing-image update body is `{ "imageId": 123 }`; use `null` to clear a role. The image must be an active `product_images` record owned by the same product. Image-list responses include filenames and usage labels so the editor can show gallery, variant, and card assignments.

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
POST /api/admin/products/{id}/images
Content-Type: multipart/form-data
```

Form field:

- `images`: one or more JPEG, PNG, WebP, or GIF files within the configured size limit.

## Frontend-only Guest Cart

The current shopping cart has no backend endpoint. It is stored in browser `localStorage` under `builtbygrain.guest-cart` and contains product IDs, slugs, display data, backend-provided display prices, and quantities. It stores neither credentials nor authentication tokens.

## Future API Areas

- Products
- Categories
- Server-validated checkout
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
