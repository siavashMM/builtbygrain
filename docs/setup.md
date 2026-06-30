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

cd apps/backend
./mvnw spring-boot:run
```

Flyway runs automatically when the backend starts and creates the `products` table.

## Product API

Public users can read active products:

```bash
curl http://localhost:8080/api/public/products
```

Admins can create, update, and deactivate products with HTTP Basic authentication:

```bash
curl -u admin:admin \
  -H "Content-Type: application/json" \
  -d '{"name":"Oak Board","description":"Handmade oak serving board","priceCents":4900,"currency":"EUR"}' \
  http://localhost:8080/api/admin/products
```

Product prices are stored by the backend. Checkout code must never trust product prices sent by the frontend.
