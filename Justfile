set dotenv-load := true

# Show all available commands in this Justfile.
help:
    @just --list

# Start the Spring Boot backend with the Maven Wrapper from apps/backend.
backend:
    cd apps/backend && ./mvnw spring-boot:run

# Run backend tests with the Maven Wrapper from apps/backend.
backend-test:
    cd apps/backend && env -u DEBUG ./mvnw test

# Start the Angular development server from apps/frontend.
frontend:
    cd apps/frontend && npm start

# Run frontend tests once in a headless browser.
frontend-test:
    cd apps/frontend && npm test -- --watch=false --browsers=ChromeHeadless

# Start the local PostgreSQL container using Podman Compose.
db-up:
    podman compose -f infra/podman/compose.yaml up -d postgres

# Start the loopback-only S3-compatible local object store.
object-store-up:
    podman compose -f infra/podman/compose.yaml up -d minio

# Start the local email catcher used by password reset.
mail-up:
    podman compose -f infra/podman/compose.yaml up -d mailpit

# Follow local object-store logs.
object-store-logs:
    podman compose -f infra/podman/compose.yaml logs -f minio

# Stop the local PostgreSQL container using Podman Compose.
db-down:
    podman compose -f infra/podman/compose.yaml down

# Follow logs for the local PostgreSQL container.
db-logs:
    podman compose -f infra/podman/compose.yaml logs -f postgres

# Start the database, then print how to run the app processes separately.
dev: db-up object-store-up mail-up
    @echo "Database is starting or already running."
    @echo "Open one terminal and run: just backend"
    @echo "Open another terminal and run: just frontend"

# Clean backend build artifacts using the Maven Wrapper.
clean:
    cd apps/backend && ./mvnw clean

# Run backend and frontend tests.
check: backend-test frontend-test

# Run the complete local verification suite used before merging a batch.
verify: backend-test frontend-test
    cd apps/frontend && npm run build

# Audit JavaScript and Java dependencies; the first Maven scan populates a large vulnerability database.
security-check:
    cd apps/frontend && npm audit --audit-level=high
    cd apps/backend && env -u DEBUG ./mvnw org.owasp:dependency-check-maven:12.2.2:check -DfailBuildOnCVSS=7

# Build the production HTTPS/reverse-proxy image, including the Angular bundle.
edge-build:
    podman build -f infra/edge/Dockerfile -t builtbygrain-edge .

# Validate the packaged Caddy configuration without starting a listener.
edge-validate: edge-build
    podman run --rm --entrypoint caddy -e APP_DOMAIN=localhost builtbygrain-edge validate --config /etc/caddy/Caddyfile --adapter caddyfile

# Start/stop the production edge. APP_DOMAIN must resolve to this host.
edge-up:
    podman compose -f infra/edge/compose.yaml up -d --build

edge-down:
    podman compose -f infra/edge/compose.yaml down
