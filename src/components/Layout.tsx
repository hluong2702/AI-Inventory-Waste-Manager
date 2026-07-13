import { useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useStore } from '../context/StoreContext'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Alert } from '../types'
import { 
  Gauge, 
  Storefront, 
  Cube, 
  Swap, 
  Archive, 
  Warning, 
  ChartBar, 
  TrendUp, 
  CreditCard,
  UsersThree,
  SignOut,
  CaretUpDown,
  X,
  List as ListIcon,
  Check
} from '@phosphor-icons/react'
import { useSubscriptionStore } from '../stores/subscriptionStore'

export default function Layout() {
  const { username, role, fullName, logout } = useAuth()
  const { activeStoreId, activeStore, stores, switchStore, isLoadingStores } = useStore()
  const { current: subscription, limitBanner, setLimitBanner } = useSubscriptionStore()
  const navigate = useNavigate()
  const [mobileOpen, setMobileOpen] = useState(false)
  const [storeSwitcherOpen, setStoreSwitcherOpen] = useState(false)

  // Lấy số cảnh báo chưa xử lý để hiển thị badge trên nav
  const { data: alertsData } = useQuery({
    queryKey: ['alerts', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Alert[]>>('/alerts')
      return res.data
    },
    enabled: !!activeStore?.id,
    refetchInterval: 30000 // Tự động refresh mỗi 30 giây
  })
  const openAlertsCount = alertsData?.data?.filter(a => a.status === 'OPEN').length ?? 0

  // Khai báo menu điều hướng kèm theo điều kiện phân quyền
  const navItems = [
    { to: '/', label: 'Tổng quan', icon: Gauge, roles: ['STORE_OWNER', 'MANAGER', 'STAFF'], badge: 0 },
    { to: '/stores', label: 'Cửa hàng', icon: Storefront, roles: ['STORE_OWNER', 'MANAGER'], badge: 0 },
    { to: '/ingredients', label: 'Nguyên liệu', icon: Cube, roles: ['STORE_OWNER', 'MANAGER'], badge: 0 },
    { to: '/transactions', label: 'Nhập / Xuất', icon: Swap, roles: ['STORE_OWNER', 'MANAGER', 'STAFF'], badge: 0 },
    { to: '/inventory', label: 'Tồn kho', icon: Archive, roles: ['STORE_OWNER', 'MANAGER', 'STAFF'], badge: 0 },
    { to: '/alerts', label: 'Cảnh báo', icon: Warning, roles: ['STORE_OWNER', 'MANAGER', 'STAFF'], badge: openAlertsCount },
    { to: '/reports', label: 'Báo cáo', icon: ChartBar, roles: ['STORE_OWNER', 'MANAGER'], badge: 0 },
    { to: '/forecast', label: 'Dự báo nhập', icon: TrendUp, roles: ['STORE_OWNER', 'MANAGER'], badge: 0 },
    { to: '/settings/staff', label: 'Nhân viên', icon: UsersThree, roles: ['STORE_OWNER', 'MANAGER'], badge: 0 },
    { to: '/billing', label: 'Gói dịch vụ', icon: CreditCard, roles: ['STORE_OWNER'], badge: 0 },
  ]

  // Lọc menu theo vai trò hiện tại
  const allowedNavItems = navItems.filter(
    (item) => !item.roles || item.roles.includes(role || '')
  )

  function handleLogout() {
    logout()
    navigate('/login')
  }

  const SidebarContent = () => (
    <div className="flex flex-col h-full">
      <div className="flex-1">
        {/* Header Sidebar */}
        <div className="px-6 py-8 border-b border-white/10">
          <div className="flex items-center gap-2">
            <Cube size={24} weight="duotone" className="text-terracotta" />
            <div>
              <h1 className="text-base font-bold tracking-tight leading-tight text-white">AI Inventory</h1>
              <p className="text-[9px] text-white/40 tracking-widest uppercase font-semibold">
                & Waste Manager
              </p>
            </div>
          </div>
        </div>

        {/* Store context: Owner được đổi store nếu sở hữu nhiều store; Manager/Staff chỉ xem store được gán. */}
        {role !== 'SYSTEM_ADMIN' && (
          <div className="px-4 py-4 border-b border-white/10">
            <label className="block text-[9px] uppercase tracking-wider text-white/40 font-semibold mb-2 px-2">
              Chi nhánh làm việc
            </label>
            {isLoadingStores ? (
              <div className="h-9 bg-white/10 rounded-xl animate-pulse" />
            ) : role !== 'STORE_OWNER' || stores.length <= 1 ? (
              <div className="flex items-center gap-2 px-3 py-2 bg-white/5 rounded-xl border border-white/10">
                <Storefront size={13} className="text-white/50 shrink-0" />
                <span className="text-xs font-semibold text-white truncate">
                  {activeStore?.name || 'Cửa hàng của bạn'}
                </span>
              </div>
            ) : (
              // Chuyển store là hành vi multi-tenant nhạy cảm, chỉ mở cho Store Owner.
              <div className="relative">
                <button
                  onClick={() => setStoreSwitcherOpen(v => !v)}
                  className="w-full flex items-center justify-between gap-2 px-3 py-2 bg-white/5 hover:bg-white/10 rounded-xl border border-white/10 text-xs font-semibold text-white transition-all"
                >
                  <div className="flex items-center gap-2 truncate">
                    <Storefront size={13} className="text-white/50 shrink-0" />
                    <span className="truncate">{activeStore?.name || 'Chọn cửa hàng'}</span>
                  </div>
                  <CaretUpDown size={12} className="text-white/40 shrink-0" />
                </button>

                {storeSwitcherOpen && (
                  <div className="absolute top-full left-0 right-0 mt-1 bg-sage-dark rounded-xl border border-white/10 shadow-2xl z-50 overflow-hidden">
                    {stores.map((s) => (
                      <button
                        key={s.id}
                        onClick={() => {
                          switchStore(s.id)
                          setStoreSwitcherOpen(false)
                          setMobileOpen(false)
                        }}
                        className="w-full flex items-center justify-between gap-2 px-3 py-2.5 hover:bg-white/10 text-left transition-colors text-xs font-medium text-white/80 hover:text-white"
                      >
                        <span className="truncate">{s.name}</span>
                        {s.id === activeStoreId && <Check size={12} className="text-white shrink-0" />}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}
          </div>
        )}

        {/* Navigation Menu */}
        <nav className="px-3 py-5 space-y-1">
          {allowedNavItems.map((item) => {
            const Icon = item.icon
            return (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.to === '/'}
                onClick={() => setMobileOpen(false)}
                className={({ isActive }) =>
                  `flex items-center justify-between gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                    isActive
                      ? 'bg-white text-sage-dark shadow-md font-semibold'
                      : 'text-white/70 hover:bg-white/10 hover:text-white'
                  }`
                }
              >
                <div className="flex items-center gap-3">
                  <Icon size={17} weight="regular" />
                  <span>{item.label}</span>
                </div>
                {/* Alert Badge */}
                {item.badge > 0 && (
                  <span className="min-w-[18px] h-[18px] px-1 rounded-full bg-red-500 text-white text-[9px] font-extrabold flex items-center justify-center animate-pulse">
                    {item.badge > 9 ? '9+' : item.badge}
                  </span>
                )}
              </NavLink>
            )
          })}
        </nav>
      </div>

      {/* Sidebar Footer — User info & Logout */}
      <div className="p-4 border-t border-white/10 bg-black/10">
        <div className="flex items-center gap-3 px-2 py-2 mb-3">
          <div className="w-9 h-9 rounded-full bg-white/10 border border-white/10 flex items-center justify-center text-xs font-bold text-white">
            {(fullName || username || '?').charAt(0).toUpperCase()}
          </div>
          <div className="truncate">
            <div className="font-semibold text-xs leading-none text-white truncate">
              {fullName || username}
            </div>
            <span className="inline-block mt-1 px-2 py-0.5 rounded text-[9px] font-bold bg-white/10 text-white/70 uppercase tracking-wider">
              {role === 'STORE_OWNER' ? 'Chủ quán' : role === 'MANAGER' ? 'Quản lý' : role === 'STAFF' ? 'Nhân viên' : 'Admin'}
            </span>
          </div>
        </div>

        <button
          onClick={handleLogout}
          className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-xl border border-white/10 bg-white/5 text-xs font-semibold hover:bg-white/15 text-white/80 transition-all duration-200"
        >
          <SignOut size={13} />
          <span>Đăng xuất</span>
        </button>
      </div>
    </div>
  )

  return (
    <div className="flex min-h-screen bg-offwhite text-ink selection:bg-sage/30">
      
      {/* ==========================================
          DESKTOP SIDEBAR (fixed, luôn hiện)
         ========================================== */}
      <aside className="hidden md:flex w-64 shrink-0 bg-sage-dark text-white flex-col shadow-lg z-10">
        <SidebarContent />
      </aside>

      {/* ==========================================
          MOBILE HEADER + OVERLAY SIDEBAR
         ========================================== */}
      {/* Mobile header */}
      <div className="md:hidden fixed top-0 left-0 right-0 z-30 bg-sage-dark text-white px-4 py-3 flex items-center justify-between border-b border-white/10">
        <div className="flex items-center gap-2">
          <Cube size={18} weight="duotone" className="text-terracotta" />
          <span className="font-bold text-sm text-white">AI Inventory</span>
        </div>
        <div className="flex items-center gap-2">
          {/* Alert badge indicator on mobile */}
          {openAlertsCount > 0 && (
            <span className="w-5 h-5 rounded-full bg-red-500 text-white text-[9px] font-extrabold flex items-center justify-center">
              {openAlertsCount}
            </span>
          )}
          <button
            onClick={() => setMobileOpen(v => !v)}
            className="p-2 rounded-lg hover:bg-white/10 transition-colors"
          >
            {mobileOpen ? <X size={20} /> : <ListIcon size={20} />}
          </button>
        </div>
      </div>

      {/* Mobile Overlay */}
      {mobileOpen && (
        <div
          className="md:hidden fixed inset-0 z-20 bg-black/60 backdrop-blur-sm"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Mobile Drawer */}
      <aside
        className={`md:hidden fixed top-0 left-0 bottom-0 z-30 w-72 bg-sage-dark text-white flex flex-col shadow-2xl transition-transform duration-300 ease-[cubic-bezier(0.32,0.72,0,1)] ${
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <SidebarContent />
      </aside>

      {/* ==========================================
          MAIN CONTENT
         ========================================== */}
      <main className="flex-1 p-6 md:p-8 overflow-y-auto mt-12 md:mt-0 max-w-[1400px] mx-auto w-full">
        <div className="mb-4 flex flex-wrap items-center justify-end gap-3">
          {limitBanner && (
            <div className="mr-auto rounded-xl border border-terracotta/25 bg-terracotta/10 px-3 py-2 text-xs font-semibold text-terracotta">
              {limitBanner}
              <button onClick={() => setLimitBanner(null)} className="ml-3 underline">Ẩn</button>
            </div>
          )}
          <button
            onClick={() => navigate('/billing')}
            className="rounded-full border border-ink/10 bg-white px-3 py-1.5 text-[11px] font-bold text-ink shadow-sm"
          >
            Gói {subscription.plan}
            {subscription.expiresAt ? ` · hết hạn ${subscription.expiresAt}` : ''}
          </button>
        </div>
        <Outlet />
      </main>
    </div>
  )
}
