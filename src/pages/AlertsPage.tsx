import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Alert, Ingredient } from '../types'
import { useStore } from '../context/StoreContext'
// DoubleBezelCard not used in AlertsPage
import StateView from '../components/StateView'
import { formatDate } from '../utils/fefo'
import { 
  CalendarBlank, 
  Warning, 
  CheckCircle,
  Bell,
  ClockCounterClockwise
} from '@phosphor-icons/react'

export default function AlertsPage() {
  const { activeStore } = useStore()
  const queryClient = useQueryClient()
  
  // Lọc theo loại cảnh báo: 'ALL' | 'EXPIRY' | 'LOW_STOCK'
  const [filterType, setFilterType] = useState<'ALL' | 'EXPIRY' | 'LOW_STOCK'>('ALL')

  // 1. Tải danh sách nguyên liệu để hiển thị thông tin đi kèm
  const { data: ingResponse } = useQuery({
    queryKey: ['ingredients'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data
    }
  })
  const ingredients = ingResponse?.data ?? []

  // 2. Tải danh sách cảnh báo (Chúng ta gọi API lấy các cảnh báo đang mở của cửa hàng)
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['alerts', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Alert[]>>('/alerts')
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const alerts = response?.data ?? []

  // 3. Mutation giải quyết cảnh báo
  const resolveAlertMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient.post(`/alerts/${id}/resolve`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
    }
  })

  function handleResolve(id: number) {
    resolveAlertMutation.mutate(id)
  }

  // Lọc cảnh báo hiển thị trên giao diện
  const filteredAlerts = alerts.filter((a) => {
    if (filterType === 'ALL') return true
    return a.type === filterType
  })

  return (
    <div className="space-y-6 font-sans">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Bảng cảnh báo hệ thống</h2>
          <p className="text-xs text-ink/60">Danh sách các vấn đề khẩn cấp liên quan đến hết hạn sử dụng và cạn kiệt nguồn hàng.</p>
        </div>

        {/* Bộ lọc */}
        <div className="flex bg-ink/5 p-1 rounded-xl border border-ink/5 shadow-sm">
          <button
            onClick={() => setFilterType('ALL')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              filterType === 'ALL' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            Tất cả ({alerts.length})
          </button>
          <button
            onClick={() => setFilterType('EXPIRY')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              filterType === 'EXPIRY' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            Cận / Quá hạn ({alerts.filter(a => a.type === 'EXPIRY').length})
          </button>
          <button
            onClick={() => setFilterType('LOW_STOCK')}
            className={`px-3 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              filterType === 'LOW_STOCK' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            Dưới ngưỡng tồn ({alerts.filter(a => a.type === 'LOW_STOCK').length})
          </button>
        </div>
      </div>

      <StateView
        isLoading={isLoading}
        isEmpty={filteredAlerts.length === 0}
        isError={isError}
        emptyTitle="Mọi thứ đều ổn!"
        emptySubtitle="Không có cảnh báo khẩn cấp nào tại chi nhánh này."
      >
        <div className="space-y-4 max-w-4xl">
          {filteredAlerts.map((alert) => {
            const ing = ingredients.find((i) => i.id === alert.itemId)
            const isExpiry = alert.type === 'EXPIRY'

            return (
              <div 
                key={alert.id} 
                className="bg-ink/5 p-1 rounded-2xl border border-ink/5 shadow-sm transition-all hover:scale-[1.005]"
              >
                <div className="bg-white rounded-[calc(2rem-1.125rem)] p-4 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
                  
                  {/* Nội dung cảnh báo */}
                  <div className="flex gap-3 items-start">
                    <div className={`w-10 h-10 rounded-xl flex items-center justify-center shrink-0 ${
                      isExpiry ? 'bg-red-500/10 text-red-600' : 'bg-terracotta/15 text-terracotta'
                    }`}>
                      {isExpiry ? <CalendarBlank size={20} /> : <Warning size={20} />}
                    </div>

                    <div>
                      <div className="font-bold text-xs text-ink leading-relaxed">
                        {alert.message}
                      </div>
                      
                      <div className="flex flex-wrap items-center gap-x-3 gap-y-1 text-[10px] text-ink/50 mt-1 font-semibold">
                        <span className="flex items-center gap-0.5">
                          <Bell size={10} />
                          Mã nguyên liệu: {ing?.code || 'N/A'}
                        </span>
                        <span>·</span>
                        <span className="flex items-center gap-0.5">
                          <ClockCounterClockwise size={10} />
                          Thời gian phát hiện: {formatDate(alert.createdAt)}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Nút hành động */}
                  <div className="shrink-0 flex gap-2 w-full sm:w-auto justify-end border-t sm:border-t-0 pt-2.5 sm:pt-0 border-ink/5">
                    {alert.status === 'OPEN' ? (
                      <button
                        onClick={() => handleResolve(alert.id)}
                        disabled={resolveAlertMutation.isPending}
                        className="flex items-center gap-1 bg-sage-dark text-white rounded-lg py-1.5 px-3 text-xs font-semibold hover:bg-sage transition-all shadow-sm"
                      >
                        <CheckCircle size={14} />
                        <span>Đánh dấu đã xử lý</span>
                      </button>
                    ) : (
                      <span className="inline-flex items-center gap-0.5 bg-sage/10 text-sage-dark px-2.5 py-1 rounded-lg text-xs font-bold">
                        Đã xử lý
                      </span>
                    )}
                  </div>

                </div>
              </div>
            )
          })}
        </div>
      </StateView>
    </div>
  )
}
