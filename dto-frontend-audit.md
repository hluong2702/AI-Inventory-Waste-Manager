# Audit thủ công DTO backend ↔ type frontend

Ngày audit: 2026-07-13. Phạm vi: toàn bộ Java record/enum trong các package `dto`, các enum được DTO sử dụng, type/interface TypeScript và các payload inline đang gọi API. Không dùng OpenAPI codegen.

## Các cặp response đang được frontend sử dụng

| Backend DTO | Frontend type | Kết quả |
|---|---|---|
| `AdminStatsResponse` | `AdminStats` (`AdminDashboardPage`) | Khớp toàn bộ field và kiểu số. |
| `AdminStoreResponse` | `AdminStore` | Đã sửa: cùng có `id`, `name`, `address`, `phone`, `subscriptionPlan`, `status`, `createdAt`; frontend xử lý `address`/`phone` nullable an toàn. |
| `AdminUserResponse` | `AdminUser` | Đã sửa: đủ field; `storeId` nullable; role thô dùng `BackendRole` (`SYSTEM_ADMIN`, `OWNER`, `MANAGER`, `STAFF`) và UI có fallback role lạ. |
| `AlertResponse` | `Alert` | Đã sửa enum thành đúng `LOW_STOCK`, `EXPIRING_SOON`; các field còn lại khớp. Backend khai báo `status` là `String`, nhưng code hiện chỉ tạo `OPEN`/`RESOLVED`, đúng union frontend. |
| `AuthResponse` | `BackendAuthResponse` (`authService`) | Khớp toàn bộ field, nullability và enum role backend; `OWNER` được chuẩn hoá thành `STORE_OWNER` sau boundary API. |
| `AuthResponse` | `AuthResponse` (`types/index.ts`) | **Lệch/stale:** type này có `username`, `fullName`, `email`, `storeIds`, `activeStoreId` nhưng backend trả `userId`, `storeId`, `mustChangePassword`. Hiện flow login dùng `BackendAuthResponse`, còn type stale chỉ còn trong API cũ `authStore.setAuth`. |
| `BillingEntitlementsResponse` | `BillingEntitlements` | Field khớp. **Nullability lệch:** backend có thể trả `expiresAt: null`, frontend khai báo `expiresAt?: string` (không nhận `null`). **Enum lệch:** xem `SubscriptionPlan` bên dưới. |
| `PlanLimits` | `SubscriptionLimits` | Khớp, ba giới hạn đều hỗ trợ `null`. |
| `BillingUsage` | `SubscriptionUsage` | Khớp. |
| `PlanDefinition` | `PlanDefinition` | Field khớp; chịu mismatch `SubscriptionPlan`. |
| `ForecastResponse` | `Forecast` | Khớp toàn bộ field. Java `BigDecimal` được JSON hoá thành number như frontend đang dùng. |
| `InventoryInsightResponse` | `InventoryInsight` | Field/enum khớp. **Nullability lệch:** `nearestBatchId`, `nearestBatchExpiryDate`, `weekdayAdjustedUsage`, `daysUntilStockout`, `daysUntilExpiry` được backend gửi dưới dạng `null`, frontend dùng optional `?` nhưng không nhận `null`. |
| `IngredientImportResult` | `IngredientImportResult` | Khớp. |
| `IngredientResponse` | `Ingredient` | Field khớp. **Required/optional lệch:** backend luôn trả `unitCost`, frontend khai báo `unitCost?`. Không gây crash nhưng contract frontend lỏng hơn thực tế. |
| `InventoryBatchResponse` | `InventoryBatch` | Khớp field và required/nullability. `Instant`/`LocalDate` đều được frontend nhận dưới dạng string. |
| `StockTransactionResponse` + `Item` | `StockTransaction` + `StockTransactionItem` | **Lệch:** frontend thiếu `items[].batchId`; backend có thể gửi `expiredDate: null`, `costPerUnit: null` trong khi frontend chỉ khai báo optional; frontend giới hạn `reason` vào 4 literal nhưng backend DTO dùng `String`. `type` khớp vì backend chủ động chuyển `IN/OUT` thành `IMPORT/EXPORT` cho endpoint report. |
| `WasteRecordResponse` | `WasteRecord` | **Lệch:** backend có thể gửi `batchId: null` và `recordedBy: null`; frontend dùng `batchId?` và bắt buộc `recordedBy: string`. Frontend giới hạn `reason` vào 4 literal trong khi backend DTO là `String` (flow hiện tại chỉ tạo bốn giá trị đó). |
| `WasteDashboardResponse` + `TopWasteIngredient` | `WasteDashboard` | Nested field khớp. **Enum/string lệch:** frontend chỉ nhận `week | month`, backend nhận/trả request parameter `String`, nên giá trị khác vẫn có thể được trả nếu caller truyền vào. Frontend hiện chỉ gọi `month`. |
| `AuditLogResponse` | `AuditLog` | Khớp theo dữ liệu controller hiện tạo; `action` luôn là enum backend `IN`/`OUT`. |
| `StaffResponse` | `BackendStaffResponse` | Khớp. Backend DTO dùng enum `Role` rộng hơn, nhưng service của endpoint này chỉ trả `MANAGER`/`STAFF`, đúng type frontend. |
| `InvitationVerificationResponse` | `InvitationVerification` | Khớp theo invariant service: response invalid có `email`, `storeName`, `role` là `null`; response valid chỉ có role `MANAGER`/`STAFF`. |
| `StoreResponse` | `Store` | **Lệch:** backend có thể gửi `phone: null`, frontend dùng `phone?: string`; frontend có thêm `subscriptionExpiresAt?` nhưng backend DTO không trả field này. `subscriptionPlan` còn mismatch `ENTERPRISE`. |

## Các cặp request đang được frontend gửi

| Backend DTO | Payload frontend | Kết quả |
|---|---|---|
| `LoginRequest` | `LoginValues` / `loginWithPassword` | Khớp (`email`, `password`). |
| `RegisterRequest` | `RegisterOwnerInput` | Khớp payload gửi đi; `confirmPassword` chỉ tồn tại ở form và không được gửi. |
| `FirstLoginChangePasswordRequest` | `FirstLoginValues` | Field khớp. **Validation lệch:** frontend cho tối thiểu 6 ký tự, backend yêu cầu tối thiểu 8. |
| `ChangePlanRequest` | `changeBillingPlan` | Khớp field; chịu mismatch enum `SubscriptionPlan`. |
| `StoreRequest` | `StoreFormValues` | Field khớp. Frontend bắt buộc `phone` tối thiểu 9 ký tự, backend cho phép `phone: null`; frontend đang chặt hơn. |
| `CreateIngredientRequest` | `IngredientFormValues` | **Lệch:** backend cho phép `code`, `category`, `maxStock`, `unitCost` nullable; frontend bắt buộc ba field đầu và không có `unitCost`. Payload vẫn hợp lệ vì backend mặc định `unitCost = 0`. |
| `CreateInventoryTransactionRequest` + `Item` | payload inline (`TransactionsPage`, `ForecastPage`) | Item chính khớp. Frontend gửi thêm `recordedBy` nhưng DTO backend không có field; payload chưa có type riêng và mutation dùng `any`. Optional import fields được bỏ khỏi JSON khi không dùng, phù hợp nullable backend. |
| `InviteStaffRequest` | `inviteStaff` | Khớp (`email`, role chỉ `MANAGER`/`STAFF`). |
| `AcceptInvitationRequest` | `acceptInvitation` | Khớp payload (`token`, `fullName`, `password`); `confirmPassword` chỉ dùng trong form. |

## Enum dùng chung

| Backend enum | Frontend type/logic | Kết quả |
|---|---|---|
| `AlertType` | `AlertType` | Đã khớp: chỉ `LOW_STOCK`, `EXPIRING_SOON`. |
| `Role` | `BackendRole` + `Role` chuẩn hoá | Đã khớp tại API boundary; backend `OWNER` được đổi thành UI role `STORE_OWNER` cho session. Admin UI dùng role backend thô và fallback an toàn. |
| `SubscriptionPlan` | `SubscriptionPlan` | **Lệch:** backend có `FREE`, `BASIC`, `PRO`, `ENTERPRISE`; frontend thiếu `ENTERPRISE`. Các title/icon trong `BillingPage` cũng chỉ phân nhánh rõ ba gói đầu. |
| `StoreStatus` | `StoreStatus` | Khớp (`ACTIVE`, `SUSPENDED`). |
| `UserStatus` | `UserStatus` | Khớp (`PENDING_ACTIVATION`, `ACTIVE`, `DISABLED`). |
| `WasteRiskLevel` | `WasteRiskLevel` | Khớp (`LOW`, `MEDIUM`, `HIGH`). |
| `InvitationStatus` | `InvitationStatus` | Khớp (`VALID`, `INVALID`, `EXPIRED`, `USED`). |
| `StockTransactionType` | `AuditLog.action` / response transform | Khớp theo từng endpoint: audit dùng `IN`/`OUT`, transaction history được backend đổi thành `IMPORT`/`EXPORT`. |

## Backend DTO hiện chưa có frontend counterpart

Các endpoint/type này chưa được frontend hiện tại gọi hoặc chưa có model TypeScript tương ứng, nên chưa tồn tại “cặp” để so field:

- Admin: `AdminDashboardResponse`, `StoreActivityResponse`, `UpdateStoreStatusRequest`.
- Inventory legacy/direct: `InventoryInRequest`, `InventoryOutRequest`, `InventoryTransactionResponse`.
- Subscription/payment module: `SubscriptionPlanResponse`, `CurrentSubscriptionResponse`, `UpgradeSubscriptionRequest`, `UpgradeSubscriptionResponse`, `CancelSubscriptionRequest`, `PaymentWebhookRequest`, `PaymentWebhookResponse`; cùng các enum `SubscriptionStatus`, `PaymentStatus`, `BillingCycle`.

## Ghi chú contract chung

Backend trả object/list thô. `ApiResponse<T>` (`success`, `data`) là envelope do Axios response interceptor của frontend tự bọc, không phải DTO backend. Error response backend là `ApiError` (`code`, `message`, `timestamp`) và không đi qua nhánh bọc success.
