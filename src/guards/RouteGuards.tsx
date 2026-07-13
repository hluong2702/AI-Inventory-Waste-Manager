import { Navigate, Outlet, useLocation } from 'react-router-dom'
import type { Role } from '../types'
import { useAuthStore } from '../stores/authStore'

export function RequireAuth() {
  const { isAuthenticated, mustChangePassword, role } = useAuthStore()
  const location = useLocation()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (role === 'SYSTEM_ADMIN') {
    return <Navigate to="/admin" replace />
  }

  // Logic quan trọng: mọi trang nghiệp vụ bị khóa cho đến khi user đổi mật khẩu tạm.
  if (mustChangePassword && location.pathname !== '/first-login') {
    return <Navigate to="/first-login" replace />
  }

  return <Outlet />
}

export function FirstLoginGuard() {
  const { isAuthenticated, mustChangePassword } = useAuthStore()

  if (!isAuthenticated) return <Navigate to="/login" replace />
  if (!mustChangePassword) return <Navigate to="/" replace />

  return <Outlet />
}

export function RoleGuard({ allowedRoles }: { allowedRoles: Role[] }) {
  const role = useAuthStore((state) => state.role)

  if (!role || !allowedRoles.includes(role)) {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
