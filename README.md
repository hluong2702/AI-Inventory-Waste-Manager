# AI Inventory & Waste Manager - Frontend

React 19 + TypeScript + Vite, Tailwind CSS v4, React Router, TanStack Query.

## Cấu trúc

```
src/
├── api/client.ts       -> axios instance, tự đính JWT vào header, tự logout khi 401
├── context/AuthContext  -> quản lý trạng thái đăng nhập (localStorage)
├── components/Layout    -> sidebar navigation, bảo vệ route (redirect /login nếu chưa đăng nhập)
├── pages/
│   ├── LoginPage
│   ├── DashboardPage    -> KPI card + danh sách cảnh báo
│   ├── ItemsPage        -> CRUD nguyên liệu (list + form tạo mới)
│   ├── WasteLogsPage    -> bảng nhật ký lãng phí
│   └── AlertsPage       -> danh sách cảnh báo
└── types/index.ts       -> type khớp với DTO backend
```

## Chạy local với Spring Boot backend thật

```bash
npm install
cp .env.example .env
npm run dev
```

Frontend chạy tại `http://localhost:5173` và gọi backend tại `http://localhost:8080/api`.

Backend nằm trong `backend/`:

```bash
cd backend
gradle bootRun
```

Yêu cầu backend: JDK 21, Gradle hoạt động, MySQL `inventory_ai`, Redis. Backend đã mở CORS cho `localhost:5173` và `127.0.0.1:5173`.

Lưu ý: máy hiện tại đang có Java 17 và Gradle local lỗi native library, nên cần nâng JDK/Gradle trước khi chạy backend thật.

## Theme

Màu sắc và font định nghĩa qua Tailwind v4 `@theme` trong `src/index.css`:
sage green (`--color-sage`), terracotta (`--color-terracotta`), off-white nền, font Be Vietnam Pro.
Đổi trực tiếp trong file này nếu muốn tách bộ nhận diện riêng cho dự án (khác với SaveBit).

## Việc còn thiếu (tự làm tiếp theo mẫu có sẵn)

- Trang quản lý Inventory (stock-in/stock-out) và Supplier/PO - hiện BE đã có entity nhưng chưa có Service/Controller đầy đủ.
- Trang biểu đồ Forecast - gọi `/api/forecast/{itemId}` và `/api/forecast/reorder-suggestion` (đã có sẵn ở BE).
- Refresh token flow khi access token hết hạn (hiện chỉ tự logout khi gặp 401).
