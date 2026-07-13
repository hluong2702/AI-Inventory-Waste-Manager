import { Routes, Route, Navigate } from 'react-router-dom'

// Layouts & Guards
import Layout from './components/Layout'
import AdminLayout from './components/AdminLayout'
import AdminRoute from './components/AdminRoute'
import { FirstLoginGuard, RequireAuth, RoleGuard } from './guards/RouteGuards'

// Auth & Onboarding
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import FirstLoginPage from './pages/FirstLoginPage'
import AcceptInvitePage from './pages/AcceptInvitePage'
import StoreSelectPage from './pages/StoreSelectPage'

// User Pages
import DashboardPage from './pages/DashboardPage'
import StoresPage from './pages/StoresPage'
import IngredientsPage from './pages/IngredientsPage'
import TransactionsPage from './pages/TransactionsPage'
import InventoryPage from './pages/InventoryPage'
import AlertsPage from './pages/AlertsPage'
import ReportsPage from './pages/ReportsPage'
import ForecastPage from './pages/ForecastPage'
import BillingPage from './pages/BillingPage'
import StaffManagementPage from './pages/StaffManagementPage'

// Admin Pages
import AdminDashboardPage from './pages/admin/AdminDashboardPage'
import AdminStoresPage from './pages/admin/AdminStoresPage'
import AdminUsersPage from './pages/admin/AdminUsersPage'

export default function App() {
  return (
    <Routes>
      {/* ==========================================
          PUBLIC ROUTES
         ========================================== */}
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route path="/accept-invite" element={<AcceptInvitePage />} />

      <Route element={<FirstLoginGuard />}>
        <Route path="/first-login" element={<FirstLoginPage />} />
      </Route>

      {/* ==========================================
          ADMIN ROUTES — Layout + Guard riêng biệt
          Tuyệt đối không chung với user layout
         ========================================== */}
      <Route element={<AdminRoute />}>
        <Route element={<AdminLayout />}>
          <Route path="/admin" element={<AdminDashboardPage />} />
          <Route path="/admin/stores" element={<AdminStoresPage />} />
          <Route path="/admin/users" element={<AdminUsersPage />} />
        </Route>
      </Route>

      {/* ==========================================
          PROTECTED USER ROUTES
          ProtectedRoute: guard + redirect ADMIN sang /admin
         ========================================== */}
      <Route element={<RequireAuth />}>
        {/* Store Selector — Hiển thị khi owner có nhiều store */}
        <Route path="/store-select" element={<StoreSelectPage />} />

        {/* Main App Layout */}
        <Route element={<Layout />}>
          <Route path="/" element={<DashboardPage />} />
          <Route path="/stores" element={<StoresPage />} />
          <Route path="/ingredients" element={<IngredientsPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route path="/alerts" element={<AlertsPage />} />
          <Route path="/reports" element={<ReportsPage />} />
          <Route path="/forecast" element={<ForecastPage />} />
          <Route path="/billing" element={<BillingPage />} />
          <Route element={<RoleGuard allowedRoles={['STORE_OWNER', 'MANAGER']} />}>
            <Route path="/settings/staff" element={<StaffManagementPage />} />
          </Route>
        </Route>
      </Route>

      {/* Fallback — redirect về Dashboard */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
