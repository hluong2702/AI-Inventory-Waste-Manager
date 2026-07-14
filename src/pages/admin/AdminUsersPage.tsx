import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../../api/client'
import type { AdminUser, ApiResponse, BackendRole, PageResponse } from '../../types'
import Pagination from '../../components/Pagination'
import { 
  MagnifyingGlass, 
  Users, 
  User as UserIcon,
  ShieldCheck,
  UserCircle
} from '@phosphor-icons/react'

const roleLabel: Record<BackendRole, string> = {
  SYSTEM_ADMIN: 'Admin',
  OWNER: 'Chủ quán',
  MANAGER: 'Quản lý',
  STAFF: 'Nhân viên'
}

const roleBadgeClass: Record<BackendRole, string> = {
  SYSTEM_ADMIN: 'bg-red-500/10 text-red-400 border-red-500/20',
  OWNER: 'bg-blue-500/10 text-blue-400 border-blue-500/20',
  MANAGER: 'bg-emerald-500/10 text-emerald-400 border-emerald-500/20',
  STAFF: 'bg-white/5 text-white/40 border-white/10'
}

const roleIcon: Record<BackendRole, typeof ShieldCheck> = {
  SYSTEM_ADMIN: ShieldCheck,
  OWNER: UserCircle,
  MANAGER: UserCircle,
  STAFF: UserIcon
}

const unknownRoleBadgeClass = 'bg-white/5 text-white/40 border-white/10'

function isBackendRole(role: string): role is BackendRole {
  return role === 'SYSTEM_ADMIN' || role === 'OWNER' || role === 'MANAGER' || role === 'STAFF'
}

export default function AdminUsersPage() {
  const [search, setSearch] = useState('')
  const [filterRole, setFilterRole] = useState<'ALL' | BackendRole>('ALL')
  const [page, setPage] = useState(0)

  // Tải tất cả users toàn hệ thống
  const { data: response, isLoading } = useQuery({
    queryKey: ['admin-users', page],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<AdminUser>>>(`/admin/users?page=${page}&size=10&sort=id,desc`)
      return res.data
    }
  })
  const users = response?.data.content ?? []

  const filtered = users.filter(u => {
    const matchSearch = 
      u.username.toLowerCase().includes(search.toLowerCase()) ||
      u.fullName.toLowerCase().includes(search.toLowerCase())
    const matchRole = filterRole === 'ALL' || u.role === filterRole
    return matchSearch && matchRole
  })

  return (
    <div className="space-y-6">
      {/* Header */}
      <div>
        <div className="flex items-center gap-2 mb-1.5">
          <Users size={14} className="text-white/30" />
          <span className="text-[10px] text-white/30 uppercase font-bold tracking-widest">Admin / Users</span>
        </div>
        <h2 className="text-2xl font-bold text-white tracking-tight">Quản lý người dùng hệ thống</h2>
        <p className="text-xs text-white/40 mt-1">
          Tổng cộng <span className="text-white font-bold">{response?.data.totalElements ?? 0}</span> tài khoản đã đăng ký.
        </p>
      </div>

      {/* Filters */}
      <div className="flex flex-wrap gap-3">
        <div className="relative">
          <MagnifyingGlass size={14} className="absolute left-3.5 top-3 text-white/30" />
          <input
            type="text"
            placeholder="Tìm username, tên..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="bg-white/5 border border-white/10 rounded-xl pl-10 pr-4 py-2 text-xs text-white placeholder:text-white/30 focus:outline-none focus:ring-2 focus:ring-white/20 transition-all"
          />
        </div>

        <div className="flex bg-white/5 border border-white/10 p-1 rounded-xl gap-1">
          {(['ALL', 'SYSTEM_ADMIN', 'OWNER', 'MANAGER', 'STAFF'] as const).map((r) => (
            <button
              key={r}
              onClick={() => setFilterRole(r)}
              className={`px-3 py-1 rounded-lg text-[10px] font-bold transition-all ${
                filterRole === r
                  ? 'bg-white text-black'
                  : 'text-white/40 hover:text-white'
              }`}
            >
              {r === 'ALL' ? 'Tất cả' : roleLabel[r]}
            </button>
          ))}
        </div>
      </div>

      {/* Users Table */}
      <div className="bg-white/5 border border-white/10 rounded-2xl overflow-hidden">
        <table className="w-full text-left text-xs border-collapse">
          <thead>
            <tr className="border-b border-white/10 text-white/40 font-bold uppercase tracking-wider text-[9px]">
              <th className="px-6 py-4">ID</th>
              <th className="px-6 py-4">Thông tin người dùng</th>
              <th className="px-6 py-4">Username</th>
              <th className="px-6 py-4 text-center">Vai trò</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-white/5">
            {isLoading ? (
              Array.from({ length: 3 }).map((_, i) => (
                <tr key={i}>
                  <td colSpan={4} className="px-6 py-4">
                    <div className="h-4 bg-white/5 rounded animate-pulse" />
                  </td>
                </tr>
              ))
            ) : filtered.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-6 py-10 text-center text-white/30">
                  Không tìm thấy người dùng.
                </td>
              </tr>
            ) : (
              filtered.map((user) => {
                const knownRole = isBackendRole(user.role) ? user.role : null
                const Icon = knownRole ? roleIcon[knownRole] : UserIcon
                const label = knownRole ? roleLabel[knownRole] : `Không xác định (${user.role || 'N/A'})`
                const badgeClass = knownRole ? roleBadgeClass[knownRole] : unknownRoleBadgeClass
                return (
                  <tr key={user.id} className="hover:bg-white/5 transition-colors">
                    <td className="px-6 py-4">
                      <span className="font-mono font-bold text-white/40 text-[10px]">#{user.id}</span>
                    </td>
                    <td className="px-6 py-4">
                      <div className="flex items-center gap-3">
                        <div className="w-8 h-8 rounded-full bg-white/10 border border-white/10 flex items-center justify-center text-xs font-bold text-white/60">
                          {user.fullName.charAt(0)}
                        </div>
                        <span className="font-semibold text-white">{user.fullName}</span>
                      </div>
                    </td>
                    <td className="px-6 py-4 font-mono text-white/50">@{user.username}</td>
                    <td className="px-6 py-4 text-center">
                      <span className={`inline-flex items-center gap-1 text-[9px] font-bold uppercase px-2 py-0.5 rounded-full border ${badgeClass}`}>
                        <Icon size={9} />
                        {label}
                      </span>
                    </td>
                  </tr>
                )
              })
            )}
          </tbody>
        </table>
      </div>
      <Pagination dark page={page} totalPages={response?.data.totalPages ?? 0} totalElements={response?.data.totalElements ?? 0} onPageChange={setPage} />
    </div>
  )
}
