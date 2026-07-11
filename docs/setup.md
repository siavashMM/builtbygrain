# Local Setup

## PostgreSQL

From the repository root, start the local PostgreSQL database with Podman Compose:

```bash
podman compose -f infra/podman/compose.yaml up -d
```

The local database uses:

- Database: `builtbygrain`
- Username: `builtbygrain`
- Password: `builtbygrain`
- Port: `5432`

## Backend

The backend reads database settings from environment variables and falls back to the local Podman values.

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/builtbygrain
export DATABASE_USERNAME=builtbygrain
export DATABASE_PASSWORD=builtbygrain
export ADMIN_USERNAME=admin@example.com
export ADMIN_PASSWORD='use-a-long-random-password'

cd apps/backend
./mvnw spring-boot:run
```

Flyway runs automatically when the backend starts and creates the `products` table.

On first startup, `ADMIN_USERNAME` and `ADMIN_PASSWORD` create a hashed account in
PostgreSQL. Once created, login uses the `admin_accounts` table and those variables
are no longer required for that account. If the table is empty and either variable
is missing, admin login is unavailable.

## Product API

Public users can read active products:

```bash
curl http://localhost:8080/api/public/products
```

Admins can list all products, including inactive products, with HTTP Basic authentication:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" http://localhost:8080/api/admin/products
```

Admins can create, update, and deactivate products with HTTP Basic authentication:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" \
  -H "Content-Type: application/json" \
  -d '{"name":"Oak Board","slug":"oak-board","description":"Handmade oak serving board","priceCents":4900,"currency":"EUR","imageUrl":"https://example.com/oak-board.jpg","inStock":true,"sizes":["S","M"],"active":true}' \
  http://localhost:8080/api/admin/products
```

Read one public active product by slug:

```bash
curl http://localhost:8080/api/public/products/oak-board
```

Update a product:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" \
  -X PUT \
  -H "Content-Type: application/json" \
  -d '{"name":"Walnut Board","slug":"walnut-board","description":"Handmade walnut serving board","priceCents":5900,"currency":"EUR","imageUrl":"https://example.com/walnut-board.jpg","inStock":true,"sizes":["M","L"],"active":true}' \
  http://localhost:8080/api/admin/products/1
```

Deactivate or activate a product without deleting it:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -X PATCH http://localhost:8080/api/admin/products/1/deactivate
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -X PATCH http://localhost:8080/api/admin/products/1/activate
```

Delete a product:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" -X DELETE http://localhost:8080/api/admin/products/1
```

Upload or replace a product image:

```bash
curl -u "$ADMIN_USERNAME:$ADMIN_PASSWORD" \
  -F "image=@/path/to/product.png" \
  http://localhost:8080/api/admin/products/1/image
```

The root `backend-api.http` file contains the same requests for IDE HTTP clients.

If `/api/public/products` returns `[]` after creating a product, check `/api/admin/products`.
Public product browsing only shows products where `active` is `true`.

Product prices are stored by the backend. Checkout code must never trust product prices sent by the frontend.

## Frontend Admin Area

Start the Angular app:

```bash
cd apps/frontend
npm start
```

Customer shop route:

- `http://localhost:4200/`

Admin routes:

- `http://localhost:4200/admin/login`
- `http://localhost:4200/admin/products`

Use the admin credentials configured with `ADMIN_USERNAME` and `ADMIN_PASSWORD`. The normal shop UI does not link to the admin area.
