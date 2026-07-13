import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../../api/client'
import type { ApiResponse, Store } from '../../types'
import { 
  MagnifyingGlass, 
  Buildings, 
  MapPin, 
  Phone
} from '@phosphor-icons/react'

export default function AdminStoresPage() {
  const [search, setSearch] = useState('')

  // Tải tất cả stores của toàn hệ thống (không filter theo tenant)
  const { data: response, isLoading } = useQuery({
    queryKey: ['admin-stores'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Store[]>>('/admin/stores')
      return res.data
    }
  })
  const stores = response?.data ?? []

  const filtered = stores.filter(s =>
    s.name.toLowerCase().includes(search.toLowerCase()) ||
    s.address.toLowerCase().includes(search.toLowerCase())
  )

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 mb-1.5">
          <Buildings size={14} className="text-white/30" />
          <span className="text-[10px] text-white/30 uppercase font-bold tracking-widest">Admin / Stores</span>
        </div>
        <h2 className="text-2xl font-bold text-white tracking-tight">Danh sách cửa hàng toàn hệ thống</h2>
        <p className="text-xs text-white/40 mt-1">
          Tổng cộng <span className="text-white font-bold">{stores.length}</span> cửa hàng đang hoạt động trên nền tảng.
        </p>
      </div>

      {/* Search */}
      <div className="relative max-w-sm">
        <MagnifyingGlass size={14} className="absolute left-3.5 top-3 text-white/30" />
        <input
          type="text"
          placeholder="Tìm kiếm theo tên, địa chỉ..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="w-full bg-white/5 border border-white/10 rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder:text-white/30 focus:outline-none focus:ring-2 focus:ring-white/20 transition-all"
        />
      </div>

      {/* Table */}
      <div className="bg-white/5 border border-white/10 rounded-2xl overflow-hidden">
        <table className="w-full text-left text-xs border-collapse">
          <thead>
            <tr className="border-b border-white/10 text-white/40 font-bold uppercase tracking-wider text-[9px]">
              <th className="px-6 py-4">ID</th>
              <th className="px-6 py-4">Tên cửa hàng</th>
              <th className="px-6 py-4">Địa chỉ</th>
              <th className="px-6 py-4">Điện thoại</th>
              <th className="px-6 py-4 text-center">Trạng thái</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {isLoading ? (
              Array.from({ length: 4 }).map((_, i) => (
                <tr key={i}>
                  <td colSpan={5} className="px-6 py-4">
                    <div className="h-4 bg-white/5 rounded animate-pulse" />
                  </td>
                </tr>
              ))
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={5} className="px-6 py-10 text-center text-white/30">
                  {search ? 'Không tìm thấy cửa hàng phù hợp.' : 'Chưa có cửa hàng nào.'}
                </td>
              </tr>
            ) : (
              filtered.map((store) => (
                <tr key={store.id} className="hover:bg-white/5 transition-colors group">
                  <td className="px-6 py-4">
                    <span className="font-mono font-bold text-white/60 text-[10px] bg-white/5 px-2 py-0.5 rounded">
                      #{store.id}
                    </span>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-2">
                      <div className="w-7 h-7 rounded-lg bg-white/5 border border-white/10 flex items-center justify-center">
                        <Buildings size={12} className="text-white/50" />
                      </div>
                      <span className="font-semibold text-white group-hover:text-white/90">{store.name}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-1 text-white/50">
                      <MapPin size={10} className="shrink-0" />
                      <span className="truncate max-w-[200px]">{store.address}</span>
                    </div>
                  </td>
                  <td className="px-6 py-4">
                    <div className="flex items-center gap-1 text-white/50">
                      <Phone size={10} />
                      {store.phone}
                    </div>
                  </td>
                  <td className="px-6 py-4 text-center">
                    <span className="inline-flex items-center gap-1 text-[9px] font-bold uppercase px-2 py-0.5 rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                      <span className="w-1 h-1 rounded-full bg-emerald-400 animate-pulse" />
                      Hoạt động
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  )
}
