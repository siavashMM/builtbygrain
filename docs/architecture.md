# Architecture

Built by Grain will use a simple monorepo structure with separate frontend and backend applications.

## Applications

- `apps/frontend`: Angular customer and admin user interfaces.
- `apps/backend`: Spring Boot REST API for products, orders, customers, and admin workflows.

## Supporting Areas

- `docs`: Architecture, roadmap, API notes, and coding rules.
- `infra`: Podman, database, and deployment configuration.
- `scripts`: Developer automation such as setup, linting, or local checks.

## System Architecture Diagram

Solid arrows show synchronous current runtime paths. Dashed arrows show
asynchronous, optional, or future paths; dashed boxes are optional or planned.

```mermaid
flowchart TB
    subgraph client["Browser"]
        customerUi["Angular customer UI"]
        adminUi["Angular admin UI"]
        localState["Guest cart and wishlist<br/>localStorage"]
        sessionState["HttpOnly session cookie<br/>and CSRF token"]

        customerUi --> localState
        customerUi --- sessionState
        adminUi --- sessionState
    end

    cdn["Optional managed CDN<br/>static and public responses"]
    caddy["Caddy HTTPS edge<br/>TLS, redirects, compression,<br/>traffic limits, proxy headers"]
    angular["Angular production bundle<br/>static files and SPA fallback"]

    client -->|direct HTTPS| caddy
    client -.->|optional CDN path| cdn
    cdn -.-> caddy
    caddy -->|static files| angular
    angular --> client

    subgraph backend["Spring Boot backend"]
        requestPipeline["Spring Security request pipeline<br/>sessions, CSRF, authorization,<br/>API rate limiting, login throttling"]

        publicApi["Public APIs<br/>catalog, products, storefront"]
        uploadApi["Public upload API"]
        accountApi["Customer account and auth APIs"]
        adminApi["Admin APIs"]
        oauthApi["OAuth callbacks"]

        cacheLayer["Spring cache-aside layer<br/>public DTOs only"]
        publicServices["Product, catalog,<br/>storefront services"]
        accountServices["Customer, password,<br/>session services"]
        adminServices["Catalog and storefront<br/>management services"]
        readRouter["Primary / replica<br/>data-source router"]
        primaryAccess["Primary-only data access"]
        storageService["Object-storage service"]
        mailService["Password-reset mail service"]

        requestPipeline --> publicApi
        requestPipeline --> uploadApi
        requestPipeline --> accountApi
        requestPipeline --> adminApi
        requestPipeline --> oauthApi

        publicApi --> cacheLayer
        cacheLayer -->|cache miss| publicServices
        publicServices --> readRouter

        uploadApi --> storageService
        accountApi --> accountServices
        adminApi --> adminServices
        oauthApi --> accountServices

        requestPipeline -->|sessions, limits, throttles| primaryAccess
        accountServices --> primaryAccess
        adminServices -->|transactional writes| primaryAccess
        adminServices --> storageService
        accountServices --> mailService
    end

    caddy -->|/api, /oauth2,<br/>/login/oauth2| requestPipeline

    subgraph data["Data and infrastructure"]
        redis[("Redis<br/>public read cache<br/>5 min default, 30 sec details")]
        primary[("PostgreSQL primary<br/>system of record, sessions,<br/>security and rate-limit state")]
        replica[("Optional PostgreSQL<br/>read replica")]
        objectStorage[("Private S3-compatible storage<br/>MinIO locally")]
        smtp["SMTP provider<br/>Mailpit locally"]
    end

    cacheLayer -->|lookup and populate| redis
    readRouter -->|explicit read-only queries| replica
    readRouter -->|replica disabled or unavailable| primary
    primaryAccess --> primary
    primary -.->|streaming replication| replica
    adminServices -.->|after commit: evict affected caches| redis
    storageService <--> objectStorage
    mailService --> smtp

    subgraph external["External platforms"]
        oauthProviders["Google and Apple OAuth"]
        maps["Google Maps browser API"]
    end

    oauthApi <--> oauthProviders
    customerUi --> maps

    subgraph future["Planned, not implemented"]
        serverCart["Server-side guest cart<br/>and sign-in merge"]
        orders["Orders and checkout<br/>server-side validation"]
        stripe["Stripe payments"]
        dhl["DHL tracking"]

        serverCart --> orders
        orders --> stripe
        orders --> dhl
    end

    customerUi -.->|future| serverCart
    orders -.->|future persistence| primary

    classDef optional stroke-dasharray: 6 4
    classDef planned stroke-dasharray: 3 3
    class cdn,replica optional
    class serverCart,orders,stripe,dhl planned
```

## Production request edge

Production uses a same-origin HTTPS edge in front of both applications.

The edge owns HTTPS, HTTP-to-HTTPS redirects, static compression and caching,
broad per-client traffic limits, forwarding-header sanitation, and API routing.
Spring Boot remains authoritative for authentication and applies shared
PostgreSQL rate limits using account, client-address, reset-token, and
authenticated-principal scopes. A managed CDN may cache only the explicitly
public responses; account, admin, OAuth, and other personalized responses must
not be cached.

## Public read performance

The backend uses cache-aside Redis caching for the public product list, product
details, category/navigation responses, category pages, and the public storefront
aggregate.

The normal public TTL is five minutes. Product details expire after 30 seconds
because they include display stock and price data. Successful product, catalog,
variant, image, and storefront mutations clear all related public caches after the
database transaction completes. Cache read/write failures are logged and fall back
to PostgreSQL, so Redis is not a source of truth or a request-availability
dependency.

Only explicitly marked public, read-only service methods may use the optional
PostgreSQL replica. The router also verifies that the active transaction is
read-only, and replica pool connections are configured as read-only. Writes,
Flyway, authentication, customer accounts, sessions, throttling, password resets,
and admin reads always use the primary. The replica is optional and disabled by
default. If acquiring a replica connection fails, that read falls back to the
primary; failures after a query has started are returned normally and are not
automatically retried.

Redis and the replica can return bounded, eventually consistent storefront data.
Checkout and future order creation must re-read price, stock, customer, and payment
state from the primary inside the write transaction; cached display data must never
authorize a purchase. Production monitoring should alert on replication lag and
Redis error/fallback logs.

## Initial Backend Direction

The backend should expose REST APIs and keep business logic separate from controllers.

Future areas:

- Product catalog
- Shopping cart
- Orders
- Admin management
- Stripe payments
- DHL tracking

Stripe and DHL are intentionally out of scope for the initial setup.

## Initial Frontend Direction

The frontend should be an Angular application with clear feature areas.

Future areas:

- Product listing
- Product detail pages
- Cart
- Checkout
- Admin dashboard

Checkout can be designed later, after product and order basics exist.

## Catalog And Guest Cart

The public Angular catalog uses standalone components for the product list, product detail, shop navigation, and cart page. Public product API calls remain in `ProductService`; guest-cart state and quantity rules remain in `CartService` so components only coordinate presentation and user actions.

The current guest cart is browser-local and persists display data in `localStorage`.
This is a temporary Stage 1 limitation, not the target architecture. Its subtotal is
explicitly an estimate and cached prices are never trusted for payment or an order.
Stage 2 must move the canonical guest cart to the server, identify it with an opaque
secure cookie, expire it after 30 days of inactivity by default, and define a tested
anonymous-to-customer merge rule.

Public product detail lookup uses the existing product service and `findBySlugAndActiveTrue`, keeping the controller thin and preventing inactive products from being exposed.

## Customer Accounts

Customer authentication uses Spring Security's delegating password encoder,
JDBC-backed server sessions, CSRF protection, and HttpOnly cookies. Customer data,
saved addresses, and hashed password-reset tokens are stored in PostgreSQL through
JPA and Flyway. All account resources are resolved from the authenticated principal.

See [customer-accounts.md](customer-accounts.md) for the security model, local mail
setup, environment variables, and checkout integration contract.
