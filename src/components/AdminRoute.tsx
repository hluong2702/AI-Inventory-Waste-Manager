import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'

/**
 * AdminRoute — Chỉ cho phép role SYSTEM_ADMIN truy cập các route /admin/*.
 * Người dùng không đăng nhập → về /login.
 * Người dùng không phải ADMIN → về / (Dashboard).
 */
export default function AdminRoute() {
  const { isAuthenticated, role } = useAuth()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (role !== 'SYSTEM_ADMIN') {
    return <Navigate to="/" replace />
  }

  return <Outlet />
}
