import { NavLink, Outlet, Navigate, useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import {
  Gauge,
  Storefront,
  Users,
  SignOut,
  ShieldCheck,
  ArrowLeft,
  Buildings
} from '@phosphor-icons/react'

export default function AdminLayout() {
  const { isAuthenticated, role, fullName, username, logout } = useAuth()
  const navigate = useNavigate()

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />
  }

  if (role !== 'SYSTEM_ADMIN') {
    return <Navigate to="/" replace />
  }

  const adminNavItems = [
    { to: '/admin', label: 'Tổng quan hệ thống', icon: Gauge, end: true },
    { to: '/admin/stores', label: 'Danh sách Stores', icon: Buildings, end: false },
    { to: '/admin/users', label: 'Danh sách Users', icon: Users, end: false },
  ]

  return (
    <div className="flex min-h-screen bg-[#0A0A0A] text-white font-sans selection:bg-white/10">
      {/* Admin Sidebar — Dark OLED style */}
      <aside className="w-64 shrink-0 bg-[#111111] flex flex-col justify-between border-r border-white/5 z-10">
        <div>
          {/* Logo */}
          <div className="px-6 py-8 border-b border-white/5">
            <div className="flex items-center gap-2.5">
              <div className="w-9 h-9 rounded-xl bg-white/10 border border-white/10 flex items-center justify-center">
                <ShieldCheck size={18} weight="fill" className="text-white" />
              </div>
              <div>
                <div className="font-bold text-sm tracking-tight text-white">System Admin</div>
                <div className="text-[9px] text-white/40 tracking-widest uppercase font-semibold mt-0.5">
                  AI Inventory SaaS
                </div>
              </div>
            </div>
          </div>

          {/* System badge */}
          <div className="px-6 py-3 border-b border-white/5">
            <span className="inline-flex items-center gap-1.5 text-[9px] font-extrabold uppercase tracking-widest px-2.5 py-1 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
              <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse" />
              Hệ thống hoạt động
            </span>
          </div>

          {/* Nav items */}
          <nav className="px-3 py-5 space-y-1">
            {adminNavItems.map((item) => {
              const Icon = item.icon
              return (
                <NavLink
                  key={item.to}
                  to={item.to}
                  end={item.end}
                  className={({ isActive }) =>
                    `flex items-center gap-3 px-4 py-2.5 rounded-xl text-sm font-medium transition-all duration-200 ${
                      isActive
                        ? 'bg-white/10 text-white border border-white/10'
                        : 'text-white/50 hover:bg-white/5 hover:text-white/80'
                    }`
                  }
                >
                  <Icon size={16} weight="regular" />
                  <span>{item.label}</span>
                </NavLink>
              )
            })}
          </nav>

          {/* Separator + Back to app */}
          <div className="px-3 mt-2 border-t border-white/5 pt-3">
            <button
              onClick={() => navigate('/')}
              className="w-full flex items-center gap-2 px-4 py-2 rounded-xl text-xs font-medium text-white/40 hover:text-white/70 hover:bg-white/5 transition-all"
            >
              <ArrowLeft size={14} />
              <span>Trở về giao diện người dùng</span>
            </button>
          </div>
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/5">
          <div className="flex items-center gap-3 px-2 py-2 mb-3">
            <div className="w-9 h-9 rounded-full bg-white/10 border border-white/10 flex items-center justify-center text-xs font-bold text-white">
              {(fullName || username || 'A').charAt(0).toUpperCase()}
            </div>
            <div className="truncate">
              <div className="font-semibold text-xs leading-none text-white truncate">
                {fullName || username}
              </div>
              <span className="inline-block mt-1 px-2 py-0.5 rounded text-[9px] font-bold bg-white/10 text-white/80 uppercase tracking-wider">
                SYSTEM_ADMIN
              </span>
            </div>
          </div>

          <button
            onClick={() => {
              logout()
              navigate('/login')
            }}
            className="w-full flex items-center justify-center gap-2 py-2 px-3 rounded-xl border border-white/10 bg-white/5 text-xs font-semibold hover:bg-white/10 text-white/80 transition-all duration-200"
          >
            <SignOut size={13} />
            <span>Đăng xuất</span>
          </button>
        </div>
      </aside>

      {/* Main content area */}
      <main className="flex-1 overflow-y-auto">
        {/* Top bar */}
        <div className="sticky top-0 z-20 border-b border-white/5 bg-[#0A0A0A]/80 backdrop-blur-xl px-8 py-4 flex items-center gap-3">
          <Storefront size={14} className="text-white/30" />
          <span className="text-xs text-white/30 font-medium">Tổng quan quản trị hệ thống SaaS</span>
        </div>

        <div className="p-8 max-w-[1400px] mx-auto">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
