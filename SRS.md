# TÀI LIỆU ĐẶC TẢ YÊU CẦU PHẦN MỀM (SRS)
## DỰ ÁN: AI Inventory & Waste Manager
### Chuẩn IEEE 830 - Phiên bản Tiếng Việt

---

## 1. Giới thiệu (Introduction)

### 1.1 Mục đích (Purpose)
Tài liệu này đặc tả toàn bộ các yêu cầu nghiệp vụ, chức năng và phi chức năng cho hệ thống **AI Inventory & Waste Manager** — nền tảng SaaS (Software-as-a-Service) hỗ trợ quản lý tồn kho, tối ưu hóa quy trình nhập/xuất kho theo nguyên tắc FEFO, dự báo nhu cầu đặt hàng và giảm lãng phí thực phẩm cho các doanh nghiệp ngành F&B (Nhà hàng, Quán cafe, Bếp ăn công nghiệp). Tài liệu này được biên soạn nhằm định hướng cho đội ngũ phát triển, kiểm thử, quản trị dự án và làm cơ sở nghiệm thu sản phẩm.

### 1.2 Phạm vi (Scope)
Hệ thống **AI Inventory & Waste Manager** là một nền tảng Web Application chạy trên mô hình đa người thuê (Multi-tenant) chia sẻ chung cơ sở dữ liệu (Shared Database). 
*   **Hệ thống cung cấp:**
    *   Cơ chế phân tách dữ liệu tuyệt đối giữa các cửa hàng (Tenants) qua bộ lọc Tenant Context ở tầng bảo mật.
    *   Tự động hóa luồng xuất kho tối ưu FEFO (First Expired, First Out - Hết hạn trước, Xuất trước).
    *   Quản lý vòng đời tồn kho của từng lô hàng cụ thể kèm theo đơn giá chi tiết.
    *   Công cụ phân tích lãng phí, cảnh báo sớm về hạn sử dụng và lượng tồn kho tối thiểu.
    *   Thuật toán dự báo đặt hàng dựa trên lượng tiêu hao thực tế để tối ưu chi phí nguyên liệu.
    *   Quy trình tuyển dụng và phân quyền nhân viên thông qua email tự động.
    *   Hệ thống Billing tích hợp cổng thanh toán để nâng cấp/gia hạn các gói dịch vụ (`FREE`, `BASIC`, `PRO`, `ENTERPRISE`).
*   **Phạm vi ngoài hệ thống (Out of Scope):** Hệ thống không trực tiếp thực hiện việc giao nhận hàng vật lý, thanh toán lương nhân sự, hay quản lý chuỗi cung ứng bên ngoài F&B.

### 1.3 Định nghĩa, Thuật ngữ và Từ viết tắt (Definitions, Acronyms, Abbreviations)
*   **SaaS**: Software-as-a-Service (Phần mềm dưới dạng dịch vụ).
*   **Multi-tenant**: Kiến trúc đa người thuê, nhiều doanh nghiệp dùng chung một hạ tầng/cơ sở dữ liệu nhưng dữ liệu của họ hoàn toàn cô lập.
*   **FEFO**: First Expired, First Out (Lô nguyên liệu có hạn sử dụng ngắn nhất sẽ được ưu tiên xuất kho trước).
*   **JWT**: JSON Web Token (Cơ chế mã hóa dùng để xác thực quyền truy cập không lưu trạng thái phiên - Stateless).
*   **POS**: Point of Sale (Hệ thống phần mềm bán hàng tại quầy).
*   **RBAC**: Role-Based Access Control (Kiểm soát truy cập dựa trên vai trò).
*   **Tenant**: Đại diện cho một doanh nghiệp hoặc một hệ thống cửa hàng (Store).

### 1.4 Tài liệu tham khảo (References)
*   IEEE Std 830-1998, *IEEE Recommended Practice for Software Requirements Specifications*.
*   Tài liệu thiết kế cấu trúc thư mục và cấu trúc DB hiện tại của dự án `AI Inventory & Waste Manager`.

---

## 2. Mô tả tổng quan (Overall Description)

### 2.1 Góc nhìn sản phẩm (Product Perspective)
Hệ thống hoạt động theo mô hình Client-Server độc lập:
*   **Client (Frontend)**: Ứng dụng Single Page Application (SPA) phát triển bằng React 19, TypeScript và TailwindCSS 4, được triển khai trên môi trường trình duyệt web của người dùng cuối.
*   **Server (Backend)**: Hệ thống RESTful API phát triển bằng Spring Boot 3.3.7 (Java 21), kết nối cơ sở dữ liệu MySQL để lưu trữ thông tin có cấu trúc và Redis để lưu trữ cache/phiên làm việc/rate limit.

```
+--------------------+       HTTPS       +------------------------------------+
|  React Frontend    | <===============> |  Spring Boot Backend (Port 8080)   |
|  (Vite, Zustand,   |  (JWT Auth, CORS) |  - Security Filter Chain           |
|   Tailwind CSS 4)  |                   |  - TenantContext (ThreadLocal)     |
+--------------------+                   +------------------------------------+
                                                           |
                                           +---------------+---------------+
                                           |                               |
                                           v                               v
                                 +------------------+            +------------------+
                                 |   MySQL (DB)     |            |   Redis (Cache/  |
                                 |   (Flyway Mig.)  |            |   Rate Limiter)  |
                                 +------------------+            +------------------+
```

### 2.2 Các chức năng chính (Product Functions)
*   **Quản lý Tenant & Phân tách dữ liệu:** Tách biệt tuyệt đối dữ liệu giữa các store, hỗ trợ tài khoản Owner sở hữu và chuyển đổi qua lại giữa nhiều store (`Multi-store`).
*   **Xác thực và phân quyền:** Đăng ký, đăng nhập stateless qua JWT, bắt buộc đổi mật khẩu tạm thời đối với nhân viên mới được mời khi đăng nhập lần đầu.
*   **Quản lý kho FEFO:** Nhập kho theo lô có HSD và đơn giá. Xuất kho tự động trừ dần từ lô cận date nhất.
*   **Cảnh báo thông minh:** Tự động phát hiện nguyên liệu sắp hết hạn hoặc dưới định mức an toàn vào ban đêm (`AlertJob` chạy lúc 02:15).
*   **Dự báo và phân tích lãng phí:** Phân tích hao hụt thực phẩm, tính toán lượng hàng cần đặt tối ưu dựa trên tần suất tiêu hao thực tế.
*   **Quản lý nhân viên**: Luồng gửi lời mời qua Email kèm mật khẩu tạm, giới hạn số lượng lời mời gửi đi để tránh spam.
*   **Quản lý Subscription & Thanh toán**: Cung cấp các hạn mức về store, nhân viên, nguyên liệu ứng với từng gói dịch vụ. Webhook tự kích hoạt gói khi có giao dịch thanh toán thành công.

### 2.3 Vai trò người dùng và đặc điểm (User Classes and Characteristics)
Hệ thống định nghĩa 4 cấp vai trò thực tế như sau:

| Mã Vai trò | Tên Vai trò (Mã nguồn) | Mô tả & Đặc điểm quyền hạn |
| :--- | :--- | :--- |
| **UR-01** | `SYSTEM_ADMIN` | **Quản trị viên hệ thống (Super Admin)**: Không thuộc về store cụ thể nào. Có toàn quyền truy cập trang Admin để giám sát toàn bộ hệ thống, quản lý danh sách Stores, xem lịch sử giao dịch thanh toán, và thay đổi trạng thái kích hoạt/khóa của các store. |
| **UR-02** | `OWNER` | **Chủ doanh nghiệp (Store Owner)**: Chủ sở hữu một hoặc nhiều store. Có quyền quản lý cấu hình store, nâng cấp/gia hạn gói cước dịch vụ, quản trị nhân sự (Manager/Staff), tạo danh mục nguyên liệu, kiểm kho và xem tất cả báo cáo lãng phí/dự báo đặt hàng. |
| **UR-03** | `MANAGER` | **Quản lý chi nhánh (Store Manager)**: Do Owner bổ nhiệm vào một store cụ thể. Có quyền quản lý nhân sự cấp `STAFF`, quản lý nguyên liệu, thực hiện nhập/xuất kho, lập biên bản hao hụt, xem các báo cáo lãng phí và nhận dự báo tồn kho trong phạm vi store của mình. |
| **UR-04** | `STAFF` | **Nhân viên kho/Nhân viên vận hành (Store Staff)**: Nhân viên thuộc store. Chỉ có quyền xem danh mục nguyên liệu, thực hiện giao dịch Nhập kho, Xuất kho tiêu thụ, Ghi nhận lãng phí, xem danh sách cảnh báo. Không có quyền mời nhân viên hoặc cấu hình hệ thống/billing. |

### 2.4 Môi trường vận hành (Operating Environment)
*   **Môi trường Server**:
    *   Hệ điều hành: Linux (Ubuntu 22.04 LTS trở lên) hoặc macOS.
    *   Java Runtime Environment: JRE 21.
    *   Hệ quản trị CSDL: MySQL 8.0+.
    *   In-memory Cache: Redis 6.x+.
*   **Môi trường Client (Trình duyệt)**:
    *   Google Chrome (phiên bản 100+), Mozilla Firefox (phiên bản 100+), Apple Safari (phiên bản 15+), Microsoft Edge (phiên bản 100+).
    *   Yêu cầu màn hình: Giao diện hỗ trợ Responsive hoàn toàn (Mobile, Tablet, Desktop).

### 2.5 Ràng buộc về Thiết kế và Triển khai (Design and Implementation Constraints)
*   **Ràng buộc bảo mật**: Token JWT sử dụng thuật toán HMAC-SHA256 để ký tên, có thời gian hết hạn cố định (Access token: 30 phút, Refresh token: 14 ngày).
*   **Ràng buộc dữ liệu**: Định dạng số của số lượng nguyên liệu trong cơ sở dữ liệu phải lưu dưới dạng `DECIMAL(14,3)` (Hỗ trợ tối đa 3 chữ số thập phân cho đơn vị như kg, lít).
*   **Ràng buộc Multi-tenant**: Tuyệt đối không lưu store_id cứng trong phần thân (Request Body) của các API nghiệp vụ. Client truyền qua Header `x-store-id` và backend đối chiếu với Store Ownership của UserPrincipal trước khi gán vào `TenantContext`.

### 2.6 Giả định và Phụ thuộc (Assumptions and Dependencies)
*   **Giả định**: Người dùng hệ thống có thiết bị kết nối mạng Internet ổn định.
*   **Phụ thuộc**: 
    *   Hệ thống gửi email phụ thuộc vào tính sẵn sàng của dịch vụ SMTP của Google (Gmail SMTP).
    *   Hệ thống thanh toán phụ thuộc vào Webhook phản hồi từ dịch vụ thanh toán bên thứ ba gửi về chính xác cấu trúc dữ liệu.

---

## 3. Các yêu cầu chức năng (System Features / Functional Requirements)

### 3.1 Module 1: Quản lý Tenant / Multi-tenancy
*   **Mô tả**: Hỗ trợ việc khởi tạo doanh nghiệp (Store) và bảo mật cách ly dữ liệu. Cho phép tài khoản `OWNER` sở hữu nhiều cửa hàng và chuyển đổi nhanh giữa các cửa hàng thông qua giao diện chọn store.
*   **Business Rules (Luật nghiệp vụ)**:
    *   Khi đăng ký tài khoản `OWNER` mới, hệ thống tự động tạo ra một `Store` và đính kèm gói `FREE` có thời hạn kích hoạt 1 tháng.
    *   Khi truy cập API, nếu client gửi Header `x-store-id`, backend kiểm tra xem store đó có thuộc quyền sở hữu của `OWNER` (hoặc user có thuộc store đó) hay không. Nếu không, trả về lỗi `403 Forbidden`.
    *   Dữ liệu trả về từ bất kỳ bảng nghiệp vụ nào (`ingredients`, `inventory_batches`, `stock_transactions`, `waste_records`, `alerts`) bắt buộc phải lọc theo `store_id` hoạt động hiện tại.

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-01**: Cho phép `OWNER` đăng ký tài khoản đồng thời khởi tạo store mới trong cùng một transaction.
*   **FR-02**: Cho phép `OWNER` chuyển đổi cửa hàng đang thao tác (`activeStoreId`) trên giao diện nếu sở hữu nhiều store. Dữ liệu hiển thị phải ngay lập tức chuyển sang store được chọn.
*   **FR-03**: Hệ thống tự động gán store_id cho tất cả các giao dịch phát sinh từ phía Client dựa trên `TenantContext` mà không dựa vào Request Body gửi lên.

---

### 3.2 Module 2: Authentication & Authorization (Xác thực & Phân quyền)
*   **Mô tả**: Quản lý đăng nhập, cấp phát JWT, xác thực quyền hạn dựa trên vai trò (RBAC) và kiểm soát đổi mật khẩu bắt buộc cho nhân sự mới.
*   **Business Rules (Luật nghiệp vụ)**:
    *   Hỗ trợ cơ chế token đôi: Access Token (hạn 30 phút) và Refresh Token (hạn 14 ngày lưu trong Redis để chống trùng lặp/thu hồi).
    *   Đối với tài khoản nhân viên được mời có cờ `must_change_password = true`: Mọi yêu cầu gọi API nghiệp vụ (có tiền tố `/api/`) đều bị chặn tại `MustChangePasswordFilter` và trả về lỗi `MUST_CHANGE_PASSWORD` (HttpStatus 403), bắt buộc client điều hướng người dùng tới trang `/first-login` để thiết lập mật khẩu mới.

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-04**: Hệ thống phải xác thực thông tin đăng nhập của người dùng qua email và mật khẩu (sử dụng BCrypt băm mật khẩu). Trả về JWT Access Token & Refresh Token.
*   **FR-05**: Cho phép kiểm soát quyền hạn bằng `@PreAuthorize` trên từng API dựa vào danh sách Role thực tế (`SYSTEM_ADMIN`, `OWNER`, `MANAGER`, `STAFF`).
*   **FR-06**: Hệ thống phải chặn toàn bộ API nghiệp vụ và yêu cầu người dùng thay đổi mật khẩu nếu thuộc tính `mustChangePassword` trong JWT giải mã là `true`.

---

### 3.3 Module 3: Quản lý tồn kho (Inventory Management)
*   **Mô tả**: Quản lý danh mục nguyên liệu của từng Store, quản lý các lô hàng nhập kho có HSD và kiểm soát quá trình xuất kho tiêu thụ hoặc hao hụt.
*   **Quy trình nghiệp vụ Nhập/Xuất kho (FEFO)**:
    1.  **Nhập kho**: Tạo mới bản ghi lô hàng (`inventory_batches`) lưu `quantity`, `expiry_date`, `cost_per_unit` và `batch_number`. Hệ thống tạo giao dịch `IN` tương ứng.
    2.  **Xuất kho**: Nhập mã nguyên liệu (`ingredientId`) và số lượng xuất (`quantity`).
        *   Hệ thống tính tổng số lượng tồn của nguyên liệu đó trên tất cả các lô chưa hết hạn và còn số lượng lớn hơn 0.
        *   Nếu tổng số lượng tồn nhỏ hơn lượng cần xuất, hệ thống từ chối giao dịch và báo lỗi `INSUFFICIENT_STOCK`.
        *   Nếu đủ, hệ thống nạp các lô hàng lên sắp xếp theo thứ tự ưu tiên: hạn sử dụng tăng dần (`expiry_date ASC`), lô nào nhập trước xếp trước (`received_at ASC`) kèm theo khóa Pessimistic Write Lock (`SELECT FOR UPDATE`) để tránh Race Condition.
        *   Hệ thống trừ dần số lượng từ lô cận date nhất đến khi đủ lượng cần xuất. Mỗi lần trừ lô nào sẽ tạo ra một bản ghi giao dịch `OUT` tương ứng cho lô đó.

```
                  +----------------------------------------------+
                  |  Người dùng yêu cầu xuất kho:                |
                  |  Ingredient ID + Số lượng xuất (Q)           |
                  +----------------------------------------------+
                                         |
                                         v
                  +----------------------------------------------+
                  |  Tính tổng số lượng khả dụng của nguyên liệu |
                  |  trong các lô hàng chưa hết hạn (HSD >= nay) |
                  +----------------------------------------------+
                                         |
                       +-----------------+-----------------+
                       |                                   |
                       v [Tổng tồn < Q]                    v [Tổng tồn >= Q]
         +----------------------------+     +----------------------------+
         |  Báo lỗi:                  |     |  Khóa các lô hàng hợp lệ   |
         |  INSUFFICIENT_STOCK        |     |  bằng Pessimistic Write    |
         |  (Từ chối giao dịch)       |     |  Lock (SELECT FOR UPDATE)  |
         +----------------------------+     +----------------------------+
                                                           |
                                                           v
                                            +----------------------------+
                                            |  Sắp xếp lô hàng theo:     |
                                            |  - Hạn sử dụng tăng dần    |
                                            |  - Ngày nhận tăng dần      |
                                            +----------------------------+
                                                           |
                                                           v
                                            +----------------------------+
                                            |  Trừ dần lượng tồn ở các   |
                                            |  lô hàng theo thứ tự trên. |
                                            |  Tạo 1 giao dịch OUT/lô.   |
                                            +----------------------------+
```

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-07**: Cho phép thêm mới, chỉnh sửa, xem danh sách và xóa mềm (`is_deleted = true`) nguyên liệu kèm theo định mức tồn kho tối thiểu (`min_stock`) và đơn giá cơ sở (`unit_cost`).
*   **FR-08**: Hỗ trợ nhập kho theo lô hàng, bắt buộc cung cấp Số lô (`batch_number`), Hạn sử dụng (`expiry_date`) và Giá nhập mỗi đơn vị (`cost_per_unit`).
*   **FR-09**: Tự động khấu trừ tồn kho theo nguyên lý FEFO khi có yêu cầu xuất kho tiêu thụ hoặc ghi nhận hao hụt thực phẩm.
*   **FR-10**: Cho phép ghi nhận biên bản hao hụt thực phẩm (`waste_records`) kèm lý do cụ thể (`EXPIRED`, `DAMAGED`, `PREP_ERROR`, `OTHER`) và tự động tính toán chi phí thiệt hại ước tính (`estimated_cost`).

---

### 3.4 Module 4: Cảnh báo sớm & Dự báo AI (Early Alerts & Forecasting)
*   **Mô tả**: Tự động phát hiện rủi ro kho hàng để gửi cảnh báo và cung cấp các phân tích dự báo lượng nguyên liệu tối ưu để giảm thiểu lãng phí.
*   **Business Rules (Luật nghiệp vụ)**:
    *   **Quét cảnh báo hàng ngày**: Chạy ngầm định kỳ vào lúc `02:15` mỗi đêm. Hệ thống quét qua toàn bộ store:
        *   Tạo cảnh báo `LOW_STOCK` (Tồn kho thấp) nếu tổng lượng tồn khả dụng thấp hơn định mức `min_stock` của nguyên liệu đó.
        *   Tạo cảnh báo `EXPIRING_SOON` (Sắp hết hạn) nếu có lô hàng của nguyên liệu có HSD nằm trong khoảng số ngày cấu hình (`app.alerts.expiring-days` - mặc định là 3 ngày).
    *   **Công thức dự báo đặt hàng**: 
        $$\text{Recommended Order Qty} = (\text{AvgDailyUsage} \times \text{Days}) + \text{MinStock} - \text{CurrentStock}$$
        *Trong đó: $\text{AvgDailyUsage}$ được tính bằng trung bình lượng xuất kho thực tế trong khoảng thời gian đã qua (ví dụ: 7 ngày).*

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-11**: Hệ thống chạy tác vụ quét tự động hàng đêm để phát hiện các nguyên liệu rơi vào trạng thái tồn kho thấp hoặc sắp hết hạn sử dụng.
*   **FR-12**: Cung cấp giao diện xem danh sách cảnh báo chưa giải quyết (Open) và cho phép người dùng click "Giải quyết" (`Resolve`) để đóng cảnh báo.
*   **FR-13**: Hệ thống tính toán và đưa ra lượng nguyên liệu khuyến nghị đặt hàng (`Forecast`) cho chu kỳ tiếp theo dựa trên lượng tiêu thụ thực tế lịch sử.
*   **FR-14**: Phân tích mức độ rủi ro lãng phí của từng nguyên liệu (`Waste Risk Level`) thành 3 cấp độ: `LOW`, `MEDIUM`, `HIGH` dựa trên thời gian hết hạn còn lại và tốc độ tiêu thụ hàng ngày.

---

### 3.5 Module 5: Quản lý Subscription & Billing
*   **Mô tả**: Kiểm soát các giới hạn tài nguyên của từng cửa hàng dựa theo gói cước dịch vụ đăng ký, và xử lý nâng cấp gói qua giao dịch thanh toán.
*   **Hạn mức các gói cước dịch vụ (Subscription Plans)**:

| Chỉ số giới hạn | Gói FREE | Gói BASIC | Gói PRO | Gói ENTERPRISE |
| :--- | :--- | :--- | :--- | :--- |
| **Giá cước (VND/tháng)** | 0 VND | 299,000 VND | 699,000 VND | Thỏa thuận |
| **Số lượng Store tối đa** | 1 Store | 1 Store | Không giới hạn | Không giới hạn |
| **Số nhân viên tối đa** | 2 Staff | 10 Staff | Không giới hạn | Không giới hạn |
| **Số nguyên liệu tối đa** | 30 Ingredients | 500 Ingredients | Không giới hạn | Không giới hạn |
| **Tính năng khả dụng** | `BASIC_ALERTS`<br>`BASIC_REPORTS` | + `BASIC_FORECAST` | + `ADVANCED_FORECAST`<br>`EXPORT_REPORTS`<br>`MULTI_STORE` | + `PRIORITY_SUPPORT` |

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-15**: Chặn các thao tác tạo mới nhân viên hoặc thêm nguyên liệu vượt quá giới hạn cấu hình của gói cước đang áp dụng cho Store đó.
*   **FR-16**: Cho phép tạo giao dịch nâng cấp gói cước (`payment_transactions`) chứa trạng thái `PENDING` và trả về thông tin thanh toán cho khách hàng.
*   **FR-17**: Cung cấp webhook tiếp nhận thông tin thanh toán thành công để tự động chuyển trạng thái giao dịch sang `SUCCESS` và nâng cấp cấu hình giới hạn của store ngay lập tức.

---

### 3.6 Module 6: Quản lý và mời nhân sự qua Email
*   **Mô tả**: Hỗ trợ tuyển dụng nhân sự vào store. Người quản trị gửi lời mời bằng email, hệ thống gửi thông tin đăng nhập tạm thời, giới hạn tần suất gửi email.
*   **Business Rules (Luật nghiệp vụ)**:
    *   Chỉ `OWNER` và `MANAGER` mới có quyền mời nhân sự.
    *   `OWNER` có thể mời `MANAGER` và `STAFF`.
    *   `MANAGER` chỉ có quyền mời `STAFF`, không được mời `MANAGER` khác.
    *   **Giới hạn chống spam (Rate Limit)**: Mỗi tài khoản người dùng chỉ được phép gửi tối đa 5 lời mời trong vòng 60 phút. Hệ thống sử dụng Redis để ghi nhận, nếu Redis gặp sự cố sẽ sử dụng bộ đếm trong bộ nhớ tạm (In-memory Fallback Counter).
    *   Mật khẩu tạm thời phải được tự động sinh ngẫu nhiên (sử dụng thuật toán mật mã an toàn `SecureRandom`), mã hóa BCrypt lưu trong cơ sở dữ liệu và gửi dưới dạng văn bản gốc duy nhất 1 lần qua email của nhân sự được mời.

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-18**: Cho phép gửi lời mời nhân sự bằng cách nhập Email và chọn Role (`MANAGER` hoặc `STAFF`).
*   **FR-19**: Hệ thống tự động giới hạn tối đa 5 lời mời được gửi đi từ một tài khoản người dùng trong vòng 60 phút nhằm bảo vệ tài nguyên hệ thống.
*   **FR-20**: Tự động sinh mật khẩu tạm ngẫu nhiên, tạo tài khoản ở trạng thái `PENDING_ACTIVATION`, gửi thư điện tử chứa liên kết kích hoạt và thông tin mật khẩu tới email đích.
*   **FR-21**: Cho phép nhân sự xác thực Token mời trên giao diện Frontend để truy cập trang đổi mật khẩu lần đầu và chính thức kích hoạt tài khoản.

---

### 3.7 Module 7: Báo cáo & Dashboard lãng phí
*   **Mô tả**: Cung cấp các thống kê trực quan về chi phí lãng phí thực phẩm, lịch sử giao dịch tồn kho và cho phép kết xuất dữ liệu để lưu trữ ngoài.
*   **Business Rules (Luật nghiệp vụ)**:
    *   Chỉ có vai trò `OWNER` và `MANAGER` mới được truy cập các báo cáo tài chính lãng phí và tải file kết xuất CSV.
    *   Dashboard tính toán tổng chi phí lãng phí của chu kỳ hiện tại và so sánh với chu kỳ trước (theo tuần hoặc theo tháng) để tính toán phần trăm tăng/giảm lãng phí.

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-22**: Hiển thị biểu đồ thống kê xu hướng lãng phí, top các nguyên liệu bị hao hụt nhiều nhất theo khối lượng và ước tính giá trị thiệt hại.
*   **FR-23**: Cho phép người dùng kết xuất toàn bộ nhật ký hao hụt (`waste_records`) và nhật ký giao dịch kho (`stock_transactions`) trong khoảng thời gian tùy chọn ra file định dạng `.csv`.
*   **FR-24**: Cung cấp giao diện xem lịch sử giao dịch tồn kho chi tiết (`Audit Log`) có phân trang, hỗ trợ tìm kiếm và lọc theo loại giao dịch (`IN` / `OUT`).

---

### 3.8 Module 8: Tích hợp POS (POS Integration)
*   **Mô tả**: Cho phép import file dữ liệu bán hàng từ hệ thống POS để tự động trừ kho nguyên liệu tương ứng.
*   **Business Rules (Luật nghiệp vụ)**:
    *   Hỗ trợ đọc file CSV chứa danh sách mặt hàng bán được và số lượng tương ứng.
    *   Hệ thống ánh xạ các mặt hàng bán được với định lượng nguyên liệu cấu hình sẵn để tự động thực hiện giao dịch xuất kho theo FEFO.

#### Danh sách Yêu cầu Chức năng (Requirements):
*   **FR-25**: Cho phép tải lên file CSV doanh số bán hàng từ POS để hệ thống phân tích và trừ kho tự động.
*   **FR-26**: Hiển thị kết quả import chi tiết bao gồm số lượng bản ghi thành công, số lượng bản ghi bị bỏ qua và danh sách các lỗi phát sinh (nếu có).

---

## 4. Yêu cầu giao tiếp bên ngoài (External Interface Requirements)

### 4.1 Giao diện người dùng (User Interfaces)
*   **Giao diện ứng dụng**: Giao diện Dashboard sử dụng cấu trúc lưới (Bento Grid) kết hợp Glassmorphism để mang lại cảm giác hiện đại và cao cấp.
*   **Tương thích màn hình**: 
    *   Giao diện responsive tự thích ứng theo độ phân giải màn hình từ Mobile (chiều rộng tối thiểu 320px) đến Desktop Ultra-wide (tối đa 2560px).
    *   Cung cấp chế độ nền tối (Dark Mode) và nền sáng (Light Mode).
*   **Cảnh báo trực quan**: Các trường hợp tồn kho sắp hết hạn hoặc dưới mức tối thiểu phải được làm nổi bật bằng tông màu cảnh báo chuyên biệt (Màu cam/đỏ, độ tương phản cao).

### 4.2 Giao diện phần mềm (Software Interfaces)
*   **Giao diện API**: Giao tiếp Client-Server qua các RESTful API sử dụng định dạng dữ liệu `JSON`. Đầu ra dữ liệu được chuẩn hóa dưới dạng:
    ```json
    {
      "success": true,
      "data": { ... },
      "errorCode": null,
      "message": "Success"
    }
    ```
*   **Cơ sở dữ liệu**: Kết nối MySQL 8.0+ sử dụng JDBC Connection Pool thông qua HikariCP. Cấu hình Hibernate tự động kiểm tra tính hợp lệ của lược đồ cơ sở dữ liệu (`ddl-auto: validate`).
*   **Dịch vụ Email**: Tương tác với dịch vụ SMTP của Google (smtp.gmail.com) trên cổng `587` hỗ trợ bảo mật mã hóa TLS/StartTLS.
*   **Hệ thống Cache**: Kết nối Redis cục bộ thông qua cổng mặc định `6379` để thực hiện khóa phân tán và đếm lượt gửi yêu cầu (Rate Limiter).

### 4.3 Giao diện truyền thông (Communication Interfaces)
*   Mọi yêu cầu giao tiếp giữa Frontend và Backend bắt buộc phải sử dụng giao thức bảo mật **HTTPS** (cổng `443`) để mã hóa dữ liệu truyền đi trên đường truyền mạng.
*   Thiết lập chính sách chia sẻ tài nguyên nguồn gốc chéo (CORS) nghiêm ngặt tại Backend, chỉ cho phép các tên miền được cấu hình cụ thể trong `application.yml` gửi request.

---

## 5. Yêu cầu phi chức năng (Non-Functional Requirements)

Các ngưỡng dưới đây là **mục tiêu nghiệm thu**, không phải số liệu production đã đạt. Mỗi lần công
bố kết quả phải đính kèm môi trường, tập dữ liệu, cấu hình tải, thời điểm đo và artifact báo cáo.

### 5.1 Hiệu năng hệ thống (Performance)
*   **NFR-01 (Thời gian phản hồi)**: 95% số request API nghiệp vụ cơ bản (Xem danh sách nguyên liệu, lịch sử giao dịch) phải có thời gian phản hồi dưới **200ms**. Các API liên quan đến thuật toán phức tạp như Dự báo đặt hàng hoặc Phân tích lãng phí sâu phải phản hồi dưới **800ms** trong điều kiện tải thường.
*   **NFR-02 (Thời gian tải trang)**: Giao diện người dùng phải hiển thị khung bố cục chính (First Contentful Paint) trong vòng dưới **1.5 giây**.
*   **NFR-03 (Khả năng xử lý đồng thời)**: Backend phải đáp ứng tối thiểu **1000 requests/giây** mà không làm tăng tỷ lệ lỗi quá 0.1%.
*   **Bằng chứng nghiệm thu NFR-01..03**: chạy production build trên staging; Playwright kiểm tra
    budget phía client, công cụ load test kiểm tra p95/throughput/error rate phía API. Chủ sở hữu:
    Backend/Platform; tần suất: trước mỗi production release có thay đổi đường dữ liệu chính.

### 5.2 An toàn bảo mật (Security)
*   **NFR-04 (Mã hóa mật khẩu)**: 100% mật khẩu lưu trữ trong CSDL phải được mã hóa bằng thuật toán **BCrypt** với độ phức tạp (Strength) là 10. Tuyệt đối không lưu mật khẩu dạng văn bản thô.
*   **NFR-05 (Mã hóa đường truyền)**: Toàn bộ kết nối API phải thông qua chứng chỉ mã hóa lớp truyền tải **TLS 1.2 hoặc 1.3**.
*   **NFR-06 (Bảo mật JWT)**: Chuỗi ký JWT Secret Key phải có độ dài tối thiểu 256-bit (32 bytes) và được lưu trữ an toàn trong biến môi trường của máy chủ sản xuất.

### 5.3 Khả năng mở rộng (Scalability)
*   **NFR-07 (Khả năng mở rộng đa người thuê)**: Hệ thống phải có khả năng hỗ trợ quy mô tăng trưởng lên đến **10,000 cửa hàng hoạt động đồng thời** mà không cần thay đổi cấu trúc bảng cơ sở dữ liệu cốt lõi nhờ giải pháp chia chỉ mục (Indexing) theo trường `store_id`.
*   **NFR-08 (Thiết kế phi trạng thái - Stateless)**: Tầng ứng dụng của backend phải hoàn toàn phi trạng thái (Stateless), cho phép triển khai nhân bản dịch vụ (Scale-out) ra nhiều máy chủ nằm sau bộ cân bằng tải (Load Balancer).

### 5.4 Tính tin cậy và khả dụng (Reliability & Availability)
*   **NFR-09 (Độ khả dụng hệ thống)**: Hệ thống phải đảm bảo thời gian hoạt động liên tục (Uptime) đạt tối thiểu **99.9%** mỗi năm (tương đương tổng thời gian gián đoạn hệ thống không mong muốn không vượt quá 8.76 giờ/năm).
*   **NFR-10 (Tính toàn vẹn dữ liệu)**: Các giao dịch nhập/xuất kho phải sử dụng cơ chế transaction của JPA/Hibernate để đảm bảo tính toàn vẹn dữ liệu (ACID). Nếu xảy ra lỗi giữa chừng, toàn bộ các bước trong giao dịch xuất kho FEFO phải được rollback về trạng thái ban đầu.
*   **Bằng chứng nghiệm thu NFR-09..10**: uptime được tính từ health probe/monitoring production;
    toàn vẹn dữ liệu được xác nhận bằng integration test MySQL/Flyway và test rollback. Không suy
    diễn uptime từ unit test hoặc một lần chạy local.

### 5.5 Khả năng sử dụng (Usability)
*   **NFR-11 (Đơn giản và trực quan)**: Người dùng phổ thông (Nhân viên kho) phải có khả năng hoàn thành một quy trình nhập kho hoặc xuất kho trong vòng dưới 3 lượt click chuột.
*   **NFR-12 (Thông báo lỗi rõ ràng)**: Hệ thống không được hiển thị mã lỗi lập trình (Stack Trace) ra ngoài màn hình client. Mọi thông báo lỗi phải được chuyển đổi thành thông điệp tường minh bằng ngôn ngữ tự nhiên thân thiện với người dùng cuối.

---

## 6. Các yêu cầu khác (Other Requirements)

### 6.1 Luật pháp và Tuân thủ dữ liệu
*   Hệ thống phải tuân thủ Luật An ninh mạng Việt Nam về việc lưu trữ thông tin tài khoản người dùng tại máy chủ đặt trong lãnh thổ Việt Nam.
*   Tuân thủ chính sách bảo vệ dữ liệu cá nhân của người dùng, không cung cấp thông tin liên hệ hay số điện thoại của các cửa hàng cho bên thứ ba khi chưa được sự cho phép bằng văn bản từ Owner.

---
*(Tài liệu được kết thúc tại đây)*
