FROM docker.io/library/node:22-alpine AS frontend

WORKDIR /workspace/apps/frontend
COPY apps/frontend/package.json apps/frontend/package-lock.json ./
RUN npm ci
COPY apps/frontend/ ./
RUN npm run build

FROM docker.io/library/caddy:2.11.4-builder-alpine AS caddy-builder

# Pinned because edge rate limiting is supplied by a Caddy module rather than core.
RUN xcaddy build \
    --with github.com/mholt/caddy-ratelimit@5625512f24f6f59d6f64fb3aafe5eecff0b286db

FROM docker.io/library/caddy:2.11.4-alpine

COPY --from=caddy-builder /usr/bin/caddy /usr/bin/caddy
COPY --from=frontend /workspace/apps/frontend/dist/builtbygrain-frontend/browser /srv/frontend
COPY infra/edge/Caddyfile /etc/caddy/Caddyfile

EXPOSE 80 443 443/udp
