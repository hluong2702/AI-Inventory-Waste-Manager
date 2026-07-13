# AI Inventory & Waste Manager Backend

Spring Boot 3.x / Java 21 backend scaffold for a shared-database multi-tenant SaaS.

## 1. Schema DB

Migrations:

- `src/main/resources/db/migration/V1__init_multi_tenant_schema.sql`
- `src/main/resources/db/migration/V2__seed_demo_data.sql`

Tenant separation:

- `stores`: tenant root.
- `users.store_id`: nullable only for `SYSTEM_ADMIN`.
- Business tables with mandatory `store_id`: `ingredients`, `inventory_batches`, `stock_transactions`, `waste_records`, `alerts`, `subscriptions`.
- Repository methods always query by `storeId` for business data, for example `findByIdAndStoreIdAndDeletedFalse`.

## 2. Security Config

Security files:

- `common/security/SecurityConfig.java`
- `common/security/JwtAuthenticationFilter.java`
- `common/security/MustChangePasswordFilter.java`
- `common/security/JwtUtil.java`
- `common/security/TenantContext.java`
- `common/security/StoreAccessService.java`

JWT claims:

- `userId`
- `storeId`
- `role`
- `mustChangePassword`

Business endpoints do not trust `store_id` from request body. They read `storeId` from JWT through `SecurityUtils.principal()`. For endpoints that contain `{storeId}`, `StoreAccessService` rejects mismatches to prevent IDOR.

## 3. Auth

Files:

- `auth/AuthController.java`
- `auth/AuthService.java`

Endpoints:

- `POST /api/auth/register`: creates Store + FREE Subscription + OWNER in one transaction.
- `POST /api/auth/login`: blocks disabled users and returns access + refresh token.
- `POST /api/auth/first-login-change-password`: only valid when `must_change_password=true`, activates invited users.

## 4. Staff Invitations

Files:

- `staff/StaffInvitationController.java`
- `staff/StaffInvitationService.java`
- `staff/EmailService.java`
- `staff/ConsoleEmailService.java`

Endpoint:

- `POST /api/stores/{storeId}/staff/invitations`

Rules:

- OWNER and MANAGER can invite STAFF.
- OWNER can invite MANAGER.
- MANAGER cannot invite another MANAGER.
- `{storeId}` must match JWT `storeId`.
- Staff limit is checked against `subscriptions.max_staff`.
- Temporary password is generated, BCrypt-hashed, and sent through `EmailService`.

## 5. Inventory FEFO

Files:

- `inventory/InventoryController.java`
- `inventory/InventoryService.java`

Endpoints:

- `POST /api/inventory/in`
- `POST /api/inventory/out`

FEFO logic:

1. Sum available stock by `storeId + ingredientId`.
2. Reject with `INSUFFICIENT_STOCK` if total quantity is not enough.
3. Load batches ordered by `expiryDate ASC, receivedAt ASC` with pessimistic write lock.
4. Deduct earliest-expiring batches first.
5. Create one OUT transaction per deducted batch.

## 6. Scheduled Alerts

File:

- `alert/AlertJob.java`

Runs daily at `02:15`, creates unresolved alerts for:

- batches expiring within `app.alerts.expiring-days`
- ingredients whose current stock is below `min_stock`

## 7. Forecast

Files:

- `forecast/ForecastController.java`
- `forecast/ForecastService.java`

Endpoint:

- `GET /api/forecast?ingredientId=xxx&days=7`

Formula:

`Recommended = AvgDailyUsage * Days + MinStock - CurrentStock`

`AvgDailyUsage` comes from OUT transactions in the recent window.

## 8. Admin APIs

Files:

- `admin/AdminController.java`
- `admin/AdminService.java`

Endpoints:

- `GET /api/admin/dashboard`
- `GET /api/admin/stores?plan=FREE&status=ACTIVE`
- `PATCH /api/admin/stores/{id}/status`

Admin endpoints require `ROLE_SYSTEM_ADMIN`.

## Demo Seed Accounts

Flyway migration `V2__seed_demo_data.sql` creates these accounts:

| Role | Email | Password |
| --- | --- | --- |
| SYSTEM_ADMIN | `admin@inventoryai.vn` | `admin123` |
| OWNER | `owner@coffee.vn` | `owner123` |
| MANAGER | `manager@coffee.vn` | `manager123` |
| STAFF | `staff@coffee.vn` | `staff123` |
| OWNER | `ownerb@coffee.vn` | `owner123` |

Seed data also creates 3 stores, subscriptions, ingredients, inventory batches, stock transactions, waste records, and open alerts.

## Local Requirements

- JDK 21
- Working Gradle installation or Gradle wrapper
- MySQL database `inventory_ai`
- Redis

This backend pins Gradle to the installed JDK 21 through `gradle.properties`:

```properties
org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home
```

If your shell still reports Java 17 because SDKMAN is first in `PATH`, either update SDKMAN's active Java or run Gradle with:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-21.jdk/Contents/Home gradle bootRun
```

Current machine note: JDK 21 is installed and `gradle build` succeeds when Gradle is allowed to write to `~/.gradle`.
