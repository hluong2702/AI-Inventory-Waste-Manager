import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * ProtectedRoute — Bảo vệ toàn bộ route nội bộ.
 * Nếu user chưa đăng nhập → redirect về /login.
 * Nếu đã đăng nhập là SYSTEM_ADMIN → redirect thẳng về /admin.
 */
export default function ProtectedRoute() {
  const { isAuthenticated, role } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  // SYSTEM_ADMIN không dùng Layout thường → redirect sang /admin
  if (role === 'SYSTEM_ADMIN') {
    return <Navigate to="/admin" replace />
  }

  return <Outlet />
}
