# Built by Grain Frontend

Angular and TypeScript frontend for Built by Grain.

## Stack

- Angular
- TypeScript
- Angular dev server proxy for backend API calls

## Run Locally

Install dependencies once:

```bash
npm install
```

Start the backend in another terminal:

```bash
cd ../backend
./mvnw spring-boot:run
```

Start the frontend:

```bash
npm start
```

The frontend starts on `http://localhost:4200`.

The homepage calls `GET /api/health`. During local development, `proxy.conf.json` forwards `/api` requests to `http://localhost:8080`.

## Test

```bash
npm test
```
