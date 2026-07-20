# AI Inventory & Waste Manager Backend

Spring Boot 3 / Java 21 backend for a shared-schema, multi-tenant F&B SaaS.

## Local development

Requirements: JDK 21, MySQL 8, Redis, and Node.js for the frontend.

```bash
cd backend
SPRING_PROFILES_ACTIVE=dev ./gradlew bootRun
```

The `dev` profile provides loopback-only defaults. Production has no default database password,
Redis URL, JWT secret, CORS origin, or frontend URL and therefore fails fast when required
configuration is absent.

`BUSINESS_TIME_ZONE` defines inventory expiry, forecasts, reports, admin day metrics, and
subscription period boundaries consistently. It defaults to `Asia/Ho_Chi_Minh`; database
timestamps remain UTC.

Run the backend tests with:

```bash
./gradlew quality --no-daemon
```

`quality` runs unit/integration tests plus Checkstyle. `securityAudit` runs OWASP Dependency-Check
and fails on known vulnerabilities with CVSS 7 or higher; CI should provide `NVD_API_KEY`.

## Operations and observability

- Every HTTP response includes `X-Correlation-ID`; a safe caller-provided value is preserved and
  added to the logging MDC, otherwise the backend generates a UUID.
- `/actuator/health` is public for infrastructure probes. Other actuator endpoints require a
  `SYSTEM_ADMIN` token, and only `health`, `info`, and `metrics` are exposed over HTTP.
- Bounded-tag counters cover payment reconciliation, invitation-email delivery, and per-store
  alert generation outcomes under the `inventoryai.*` metric namespace.

## Tenant and authorization model

- `stores` is the tenant root.
- Every inventory, waste, alert, subscription, and payment row carries a tenant/store key.
- `tenant_memberships` is the authoritative source for a user's tenant-specific role and status.
- JWTs contain only identity metadata (`sub`, `iss`, `aud`, `jti`, timestamps). Role and tenant
  are resolved from the database for every authenticated request.
- `x-store-id` may select only an active membership owned by the authenticated user.
- `SYSTEM_ADMIN` cannot impersonate a tenant through `x-store-id`.
- Composite foreign keys added by Flyway prevent cross-tenant ingredient, batch, transaction,
  waste, alert, daily-action, and payment references at the database layer.

Roles are `SYSTEM_ADMIN`, `OWNER`, `MANAGER`, and `STAFF`. Method-level authorization is enabled;
tenant-bound path parameters are additionally checked against the resolved tenant context.

## Authentication

- Access token TTL: 15 minutes.
- Refresh token TTL: 14 days.
- Refresh tokens are opaque, SHA-256 indexed in Redis, rotated atomically, and organized into
  token families. Reuse revokes the whole family.
- The refresh token is sent only as an `HttpOnly`, `Secure`, scoped cookie. It is not returned to
  browser JavaScript or persisted in local storage.
- Refresh/logout cookie requests require a trusted `Origin`; credentialed CORS accepts only
  explicit origins.
- Logout revokes the refresh session and deny-lists the current access-token `jti` until expiry.
- Login, registration, refresh, and invitation endpoints have Redis-backed rate limiting with a
  bounded in-process fallback.
- Registration creates a pending owner and sends a one-time activation link; no authenticated
  session is issued before activation.

Required production values include `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`,
`FRONTEND_LOGIN_URL`, and `FRONTEND_INVITE_URL`. Use a unique high-entropy JWT secret of at least
32 bytes. See `../.env.backend.example` for the complete list.

## Invitation email

Staff and owner invitations use 256-bit random tokens. Only a SHA-256 token digest is retained in
`invite_tokens`; links put the raw token in the URL fragment so it is not sent in HTTP referrers or
server access logs. Acceptance locks the token row and consumes it once.

Email delivery uses a transactional outbox with leased, bounded retries. The raw invitation URL is
cleared after successful delivery, terminal failure, expiry, revocation, or activation. Email
headers and HTML are sanitized. Production fails at startup when invitation delivery is disabled
or Gmail SMTP credentials are missing/placeholders.

Configure Gmail with an app password:

```text
INVITATION_EMAIL_ENABLED=true
MAIL_USERNAME=service-account@gmail.com
MAIL_APP_PASSWORD=<gmail-app-password>
```

## Inventory integrity and scale

- Stock-out locks candidate batches before checking availability and allocates FEFO in stable
  `expiry_date`, `received_at`, `id` order.
- Expired stock is excluded from consumption and forecasting; waste recording can consume it.
- Database checks prevent negative batch stock, negative costs, invalid transaction quantities,
  and invalid transaction reason/type combinations.
- Ingredient imports enforce subscription limits under a subscription-row lock and cap file size,
  rows, columns, cell length, ZIP expansion, and reported errors. XLSX parsing disables DTD and
  external entities.
- Forecasts, insights, reports, inventory summaries, alerts, and admin KPIs use bounded pagination
  or aggregate/set-based queries instead of loading complete tenant tables.
- Report CSV exports use bounded date ranges, keyset/chunked reads, and spreadsheet-formula escaping.

## Subscription and payOS

Plan limits come from the active tenant subscription. Checkout requires an `Idempotency-Key` and
serializes creation per tenant. Provider calls happen outside database transactions. Ambiguous
creation is reconciled with the same deterministic payOS order code; late success for a closed
local payment is moved to `REVIEW_REQUIRED` instead of silently activating a plan.

Only a verified server-to-server payOS webhook can activate a paid subscription. Signature,
provider order code, amount, currency, local payment state, and current pending subscription are
validated. Return/cancel URLs only control browser navigation.

```text
PAYOS_ENABLED=true
PAYOS_CLIENT_ID=<client-id>
PAYOS_API_KEY=<api-key>
PAYOS_CHECKSUM_KEY=<checksum-key>
PAYOS_RETURN_URL=https://app.example.com/billing?payment=success
PAYOS_CANCEL_URL=https://app.example.com/billing?payment=failed
```

Webhook URL:

```text
https://api.example.com/api/webhook/payment/payos
```

## Flyway

Flyway migrations currently run from `V1` through `V15`. Apply them to a MySQL 8 staging clone
before production deployment because `V8`-`V15` add data repairs, checks, generated columns,
unique constraints, composite foreign keys, query indexes, tenant-scoped invitation tokens,
and active ingredient-code uniqueness.

`V2` historically inserted demo data; `V6` removes those users and all dependent demo records.
There are no supported default or demo credentials. Create the first real owner through the
verified registration flow and provision `SYSTEM_ADMIN` through an audited operational process.
