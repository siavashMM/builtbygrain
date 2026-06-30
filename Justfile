# Show all available commands in this Justfile.
help:
    @just --list

# Start the Spring Boot backend with the Maven Wrapper from apps/backend.
backend:
    cd apps/backend && ./mvnw spring-boot:run

# Run backend tests with the Maven Wrapper from apps/backend.
backend-test:
    cd apps/backend && ./mvnw test

# Start the Angular development server from apps/frontend.
frontend:
    cd apps/frontend && npm start

# Run frontend tests once in a headless browser.
frontend-test:
    cd apps/frontend && npm test -- --watch=false --browsers=ChromeHeadless

# Start the local PostgreSQL container using Podman Compose.
db-up:
    podman compose -f infra/podman/compose.yaml up -d

# Stop the local PostgreSQL container using Podman Compose.
db-down:
    podman compose -f infra/podman/compose.yaml down

# Follow logs for the local PostgreSQL container.
db-logs:
    podman compose -f infra/podman/compose.yaml logs -f postgres

# Start the database, then print how to run the app processes separately.
dev: db-up
    @echo "Database is starting or already running."
    @echo "Open one terminal and run: just backend"
    @echo "Open another terminal and run: just frontend"

# Clean backend build artifacts using the Maven Wrapper.
clean:
    cd apps/backend && ./mvnw clean

# Run backend and frontend tests.
check: backend-test frontend-test
