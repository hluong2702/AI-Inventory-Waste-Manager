import type { Role } from '../types'

export interface RouteManifestItem {
  path: string
  area: 'public' | 'app' | 'admin'
  label: string
  roles: Role[] | 'guest'
}

export const routeManifest: RouteManifestItem[] = [
  { path: '/login', area: 'public', label: 'Đăng nhập / đăng ký / quên mật khẩu', roles: 'guest' },
  { path: '/register', area: 'public', label: 'Đăng ký Owner và tạo Store mới', roles: 'guest' },
  { path: '/first-login', area: 'app', label: 'Đổi mật khẩu lần đầu cho tài khoản được mời', roles: ['MANAGER', 'STAFF'] },
  { path: '/store-select', area: 'app', label: 'Chọn cửa hàng làm việc', roles: ['STORE_OWNER', 'MANAGER', 'STAFF'] },
  { path: '/', area: 'app', label: 'Dashboard theo store hiện tại', roles: ['STORE_OWNER', 'MANAGER', 'STAFF'] },
  { path: '/stores', area: 'app', label: 'Quản lý cửa hàng và nhân sự', roles: ['STORE_OWNER', 'MANAGER'] },
  { path: '/ingredients', area: 'app', label: 'Quản lý nguyên liệu', roles: ['STORE_OWNER', 'MANAGER'] },
  { path: '/transactions', area: 'app', label: 'Nhập / xuất kho FEFO', roles: ['STORE_OWNER', 'MANAGER', 'STAFF'] },
  { path: '/inventory', area: 'app', label: 'Tồn kho theo lô', roles: ['STORE_OWNER', 'MANAGER', 'STAFF'] },
  { path: '/alerts', area: 'app', label: 'Cảnh báo tồn kho và hạn dùng', roles: ['STORE_OWNER', 'MANAGER', 'STAFF'] },
  { path: '/reports', area: 'app', label: 'Báo cáo thất thoát và giao dịch', roles: ['STORE_OWNER', 'MANAGER'] },
  { path: '/forecast', area: 'app', label: 'Dự báo nhập hàng', roles: ['STORE_OWNER', 'MANAGER'] },
  { path: '/billing', area: 'app', label: 'Billing / Upgrade', roles: ['STORE_OWNER'] },
  { path: '/settings/staff', area: 'app', label: 'Quản lý nhân viên và lời mời', roles: ['STORE_OWNER', 'MANAGER'] },
  { path: '/admin', area: 'admin', label: 'Admin Dashboard hệ thống', roles: ['SYSTEM_ADMIN'] },
  { path: '/admin/stores', area: 'admin', label: 'Quản trị toàn bộ Store/Tenant', roles: ['SYSTEM_ADMIN'] },
  { path: '/admin/users', area: 'admin', label: 'Quản trị toàn bộ User', roles: ['SYSTEM_ADMIN'] },
]
