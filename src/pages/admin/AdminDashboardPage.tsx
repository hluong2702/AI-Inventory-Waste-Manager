import { useQuery } from '@tanstack/react-query'
import apiClient from '../../api/client'
import type { ApiResponse } from '../../types'
import { 
  Buildings, 
  Users, 
  Storefront, 
  ArrowUp, 
  Database,
  ShieldCheck
} from '@phosphor-icons/react'
import { useNavigate } from 'react-router-dom'

interface AdminStats {
  totalStores: number
  totalUsers: number
  totalIngredients: number
  totalTransactions: number
  totalWasteCost: number
}

interface StoreWithStats {
  id: number
  name: number
  address: string
  phone: string
}

export default function AdminDashboardPage() {
  const navigate = useNavigate()

  // Tải thống kê toàn hệ thống
  const { data: statsResponse, isLoading: isLoadingStats } = useQuery({
    queryKey: ['admin-stats'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<AdminStats>>('/admin/stats')
      return res.data
    }
  })

  // Tải danh sách stores toàn hệ thống
  const { data: storesResponse } = useQuery({
    queryKey: ['admin-stores'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<StoreWithStats[]>>('/admin/stores')
      return res.data
    }
  })

  const stats = statsResponse?.data
  const stores = storesResponse?.data ?? []

  const kpiCards = [
    {
      label: 'Tổng cửa hàng',
      value: stats?.totalStores ?? '—',
      icon: Buildings,
      color: 'from-blue-500/20 to-blue-600/10 border-blue-500/20 text-blue-400',
      onClick: () => navigate('/admin/stores')
    },
    {
      label: 'Tổng người dùng',
      value: stats?.totalUsers ?? '—',
      icon: Users,
      color: 'from-purple-500/20 to-purple-600/10 border-purple-500/20 text-purple-400',
      onClick: () => navigate('/admin/users')
    },
    {
      label: 'Nguyên liệu quản lý',
      value: stats?.totalIngredients ?? '—',
      icon: Database,
      color: 'from-emerald-500/20 to-emerald-600/10 border-emerald-500/20 text-emerald-400',
    },
    {
      label: 'Tổng giao dịch kho',
      value: stats?.totalTransactions ?? '—',
      icon: ArrowUp,
      color: 'from-amber-500/20 to-amber-600/10 border-amber-500/20 text-amber-400',
    },
  ]

  return (
    <div className="space-y-8">
      {/* Page Header */}
      <div>
        <div className="flex items-center gap-2 mb-1.5">
          <ShieldCheck size={16} className="text-white/40" />
          <span className="text-[10px] text-white/40 uppercase font-bold tracking-widest">Admin Dashboard</span>
        </div>
        <h2 className="text-2xl font-bold text-white tracking-tight">Tổng quan hệ thống</h2>
        <p className="text-xs text-white/40 mt-1">
          Thống kê toàn bộ dữ liệu trên nền tảng SaaS AI Inventory.
        </p>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        {kpiCards.map((card) => {
          const Icon = card.icon
          return (
            <div
              key={card.label}
              onClick={card.onClick}
              className={`bg-gradient-to-br ${card.color} border rounded-2xl p-5 ${card.onClick ? 'cursor-pointer hover:scale-[1.01] transition-transform duration-200' : ''}`}
            >
              {isLoadingStats ? (
                <div className="h-12 bg-white/5 rounded-lg animate-pulse" />
              ) : (
                <>
                  <div className="flex items-start justify-between">
                    <Icon size={20} className="opacity-80" />
                    <span className="text-[9px] font-bold uppercase tracking-widest opacity-60">
                      {card.onClick ? 'Chi tiết →' : ''}
                    </span>
                  </div>
                  <div className="mt-3">
                    <div className="text-2xl font-bold font-mono text-white">{String(card.value)}</div>
                    <div className="text-xs text-white/50 mt-0.5 font-medium">{card.label}</div>
                  </div>
                </>
              )}
            </div>
          )
        })}
      </div>

      {/* Recent Stores */}
      <div className="bg-white/5 border border-white/10 rounded-2xl overflow-hidden">
        <div className="px-6 py-4 border-b border-white/10 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <Storefront size={16} className="text-white/60" />
            <h3 className="font-semibold text-sm text-white">Danh sách cửa hàng gần đây</h3>
          </div>
          <button
            onClick={() => navigate('/admin/stores')}
            className="text-xs font-bold text-white/40 hover:text-white transition-colors"
          >
            Xem tất cả →
          </button>
        </div>

        <div className="divide-y divide-white/5">
          {stores.slice(0, 5).map((store: any) => (
            <div key={store.id} className="px-6 py-4 flex items-center justify-between hover:bg-white/5 transition-colors">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center text-[10px] font-bold text-white/60">
                  #{store.id}
                </div>
                <div>
                  <div className="text-sm font-medium text-white">{store.name}</div>
                  <div className="text-[10px] text-white/40 mt-0.5">{store.address}</div>
                </div>
              </div>
              <span className="inline-flex items-center gap-1 text-[9px] font-bold uppercase tracking-wider px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                <span className="w-1 h-1 rounded-full bg-emerald-400" />
                Hoạt động
              </span>
            </div>
          ))}
          {stores.length === 0 && !isLoadingStats && (
            <div className="px-6 py-8 text-center text-sm text-white/30">
              Chưa có dữ liệu stores.
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
