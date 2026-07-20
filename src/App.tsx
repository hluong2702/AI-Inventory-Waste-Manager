import { lazy, Suspense } from 'react'
import { Routes, Route, Navigate } from 'react-router-dom'

// Layouts & Guards
import AdminRoute from './components/AdminRoute'
import { FirstLoginGuard, RequireAuth, RoleGuard } from './guards/RouteGuards'
import { rolesForRoute } from './services/routeManifest'

const Layout = lazy(() => import('./components/Layout'))
const AdminLayout = lazy(() => import('./components/AdminLayout'))
const LoginPage = lazy(() => import('./pages/LoginPage'))
const RegisterPage = lazy(() => import('./pages/RegisterPage'))
const FirstLoginPage = lazy(() => import('./pages/FirstLoginPage'))
const AcceptInvitePage = lazy(() => import('./pages/AcceptInvitePage'))
const StoreSelectPage = lazy(() => import('./pages/StoreSelectPage'))
const DashboardPage = lazy(() => import('./pages/DashboardPage'))
const StoresPage = lazy(() => import('./pages/StoresPage'))
const IngredientsPage = lazy(() => import('./pages/IngredientsPage'))
const TransactionsPage = lazy(() => import('./pages/TransactionsPage'))
const InventoryPage = lazy(() => import('./pages/InventoryPage'))
const AlertsPage = lazy(() => import('./pages/AlertsPage'))
const ReportsPage = lazy(() => import('./pages/ReportsPage'))
const ForecastPage = lazy(() => import('./pages/ForecastPage'))
const BillingPage = lazy(() => import('./pages/BillingPage'))
const StaffManagementPage = lazy(() => import('./pages/StaffManagementPage'))
const RecipesPage = lazy(() => import('./pages/RecipesPage'))
const PosIntegrationPage = lazy(() => import('./pages/PosIntegrationPage'))
const AdminDashboardPage = lazy(() => import('./pages/admin/AdminDashboardPage'))
const AdminStoresPage = lazy(() => import('./pages/admin/AdminStoresPage'))
const AdminUsersPage = lazy(() => import('./pages/admin/AdminUsersPage'))

function RouteLoading() {
  return (
    <div className="grid min-h-[100dvh] place-items-center bg-offwhite text-sm font-semibold text-ink/60">
      Đang tải trang…
    </div>
  )
}

export default function App() {
  return (
    <Suspense fallback={<RouteLoading />}>
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
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/inventory" element={<InventoryPage />} />
          <Route path="/alerts" element={<AlertsPage />} />

          <Route element={<RoleGuard allowedRoles={rolesForRoute('/stores')} />}>
            <Route path="/stores" element={<StoresPage />} />
            <Route path="/ingredients" element={<IngredientsPage />} />
            <Route path="/reports" element={<ReportsPage />} />
            <Route path="/forecast" element={<ForecastPage />} />
            <Route path="/settings/staff" element={<StaffManagementPage />} />
          </Route>

          <Route element={<RoleGuard allowedRoles={rolesForRoute('/recipes')} />}>
            <Route path="/recipes" element={<RecipesPage />} />
          </Route>

          <Route element={<RoleGuard allowedRoles={rolesForRoute('/integrations/pos')} />}>
            <Route path="/integrations/pos" element={<PosIntegrationPage />} />
          </Route>

          <Route element={<RoleGuard allowedRoles={rolesForRoute('/billing')} />}>
            <Route path="/billing" element={<BillingPage />} />
          </Route>
        </Route>
      </Route>

      {/* Fallback — redirect về Dashboard */}
      <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
