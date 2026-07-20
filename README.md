# AI Inventory & Waste Manager

React 19 / TypeScript frontend and Spring Boot 3 / Java 21 backend for multi-tenant inventory and
food-waste management.

## Run locally

Start MySQL 8 and Redis, then run:

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

In another terminal:

```bash
npm install
cp .env.example .env
npm run dev
```

The frontend defaults to `http://localhost:5173` and the API to
`http://localhost:8080/api` in local development.

## Security/session behavior

- Access tokens live only in browser memory.
- Refresh tokens are rotated by the backend and stored in an `HttpOnly` cookie.
- Reloading an existing session obtains a new access token through the refresh endpoint.
- Tenant selection is sent as `x-store-id`, but the backend accepts it only when an active
  database membership exists; the client-side role/store metadata is never authoritative.
- Registration and staff onboarding require one-time email activation links.

Production backend configuration and architecture notes are documented in
[`backend/README.md`](backend/README.md). Never deploy using the `dev` Spring profile or local
environment defaults.

## Verification

```bash
npm run quality:frontend
npm run test:a11y
npm run test:perf
npm run audit:deps
cd backend && ./gradlew quality --no-daemon
cd backend && ./gradlew securityAudit --no-daemon
```

`quality:frontend` runs lint, coverage thresholds, Java/TypeScript API-contract checks, and the
production build. Playwright runs accessibility checks on public authentication routes and enforces
the current frontend budget: load under 3 seconds, transferred resources under 1.5 MB, and no
JavaScript asset over 500 KB. `securityAudit` requires NVD access; configure `NVD_API_KEY` in CI to
avoid the public-feed rate limit.
