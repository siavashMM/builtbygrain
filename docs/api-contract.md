# API Contract

This document will describe the REST API between the Angular frontend and Spring Boot backend.

Rate-limited endpoints return `429 Too Many Requests` with a whole-second
`Retry-After` header and a generic JSON error. Clients must stop automatic
retries until that delay has passed. Rate-limit responses are never cacheable.

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
  "categoryPath": "kitchen/serving-boards",
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
GET /api/public/categories
GET /api/public/category?path=office/desks/standing-desks
GET /api/navigation/categories
```

The public category list contains every active category whose ancestry is active, in configured sibling order. Each category includes its canonical ancestry-derived `path`, for example `/category/office/shelves`; the storefront homepage uses active entries with `parentId: null`.

The public category-page endpoint resolves the complete slug path and returns the selected category, its ordered breadcrumb ancestry, and active product cards assigned directly to it or to any active descendant. A branch in recursive navigation is included only when every category in the branch is active and the category or one of its descendants has an active, non-archived product. Product stock does not affect category visibility.

## Storefront configuration

```http
GET /api/public/storefront
```

Returns the homepage settings and active navigation groups as one aggregate. An empty `navigationGroups` array and a settings object without a hero image are valid. Groups, assigned categories, and featured products retain their administrator-defined order. Public categories are references to the real category tree and include their canonical path; inactive categories or categories beneath an inactive ancestor are omitted. Featured products use the same public product-card shape as `GET /api/public/products`; inactive, draft, and archived products are omitted even when their assignment remains visible to administrators.

```json
{
  "settings": {
    "heroImageUrl": "/api/public/uploads/storefront/hero.webp",
    "heroImageAltText": "Oak shelves in a bright workshop",
    "heroHeading": "Handcrafted wooden goods, shaped for everyday use.",
    "heroSupportingText": "Thoughtfully made pieces for calmer desks, organised homes and durable everyday rituals.",
    "updatedAt": "2026-07-18T12:00:00Z"
  },
  "navigationGroups": [{
    "id": 4,
    "label": "Living",
    "displayOrder": 0,
    "categories": [],
    "featuredProducts": []
  }]
}
```

Storefront configuration administration requires the `ADMIN` role:

```http
GET    /api/admin/storefront/settings
PUT    /api/admin/storefront/settings
POST   /api/admin/storefront/settings/hero-image
GET    /api/admin/storefront/navigation-groups
POST   /api/admin/storefront/navigation-groups
PUT    /api/admin/storefront/navigation-groups/{groupId}
DELETE /api/admin/storefront/navigation-groups/{groupId}
PATCH  /api/admin/storefront/navigation-groups/{groupId}/activate
PATCH  /api/admin/storefront/navigation-groups/{groupId}/deactivate
PUT    /api/admin/storefront/navigation-groups/reorder
POST   /api/admin/storefront/navigation-groups/{groupId}/categories
DELETE /api/admin/storefront/navigation-groups/{groupId}/categories/{categoryId}
PUT    /api/admin/storefront/navigation-groups/{groupId}/categories/reorder
POST   /api/admin/storefront/navigation-groups/{groupId}/featured-products
DELETE /api/admin/storefront/navigation-groups/{groupId}/featured-products/{productId}
PUT    /api/admin/storefront/navigation-groups/{groupId}/featured-products/reorder
```

The settings update body contains `heroImageAltText`, `heroHeading`, and `heroSupportingText`. Heading and supporting text may be `null`; the initial record preserves the current homepage copy shown above. When a hero image is configured its alt text must be nonblank. The hero upload is multipart form data with one `image` and an `altText` field, using the product-image MIME type and size rules. A successful replacement deletes the prior locally managed hero upload.

Navigation group create/update bodies are `{ "label": "Living", "active": true }`. Assignment bodies are `{ "id": 123 }`. Every reorder body is `{ "ids": [3, 1, 2] }` and must list every currently contained ID exactly once. A category may be assigned from any tree depth without moving or renaming it. Duplicate category/product assignments return `409 Conflict`, missing references return `404 Not Found`, malformed reorder sets return `400 Bad Request`, and assigning a fourth featured product returns `409 Conflict`.

```http
POST /api/admin/auth/login
GET  /api/admin/auth/session
GET  /api/admin/auth/csrf
POST /api/admin/auth/password
POST /api/admin/auth/logout
GET /api/admin/products
POST /api/admin/products
PUT /api/admin/products/{id}
PATCH /api/admin/products/{id}/deactivate
PATCH /api/admin/products/{id}/activate
DELETE /api/admin/products/{id}
POST /api/admin/products/{id}/images
GET /api/admin/products/{id}/images
DELETE /api/admin/products/{id}/catalog-images/{imageId}
GET /api/admin/products/{id}/listing-images
PUT /api/admin/products/{id}/listing-images/{primary|hover}
GET /api/admin/categories/tree
GET /api/admin/categories/options
POST /api/admin/categories?simple=true
PATCH /api/admin/categories/{id}/name
PATCH /api/admin/categories/{id}/parent
PATCH /api/admin/categories/{id}/status
PATCH /api/admin/categories/{id}/position
DELETE /api/admin/categories/{id}
```

Purpose: allow admins to verify login, list all products, create, update, deactivate, activate, delete, and upload product images.
These endpoints require the `ADMIN` role.

The category create request contains only `name` and optional `parentId`. The backend generates a normalized slug and appends a sibling-safe numeric suffix when necessary. Renaming preserves the existing slug; moving rejects cycles; deletion is allowed only for categories with no child categories and no assigned products.

The listing-image update body is `{ "imageId": 123 }`; use `null` to clear a role. The image must be an active `product_images` record owned by the same product. Image-list responses include filenames and usage labels so the editor can show gallery, variant, and card assignments.

Deleting a catalog image removes it from the product gallery, clears both product-card roles, removes all variant assignments, compacts the remaining gallery order, and deletes the stored upload.

Admin login accepts JSON credentials and returns:

```json
{ "username": "admin@example.com", "password": "use-a-long-random-password" }
```

The success response is:

```json
{
  "username": "admin@example.com",
  "admin": true
}
```

The login response creates an HttpOnly server-session cookie. Mutating admin requests must include the token from the `XSRF-TOKEN` cookie as `X-XSRF-TOKEN`. `GET /api/admin/auth/session` returns the same identity shape or 401. `POST /api/admin/auth/password` accepts `{ "currentPassword": "...", "newPassword": "..." }`, requires at least 12 characters for the new password, and returns 204 after invalidating all of that administrator's sessions. CSRF-protected logout invalidates the current session and returns 204. Throttled logins return 429 with `Retry-After`; other authentication failures return a generic 401.

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
