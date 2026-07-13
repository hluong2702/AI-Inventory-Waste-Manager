import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import apiClient from '../api/client'
import type { ApiResponse, Forecast, InventoryInsight, WasteRiskLevel } from '../types'
import { useStore } from '../context/StoreContext'
import { useAuth } from '../context/AuthContext'
import StateView from '../components/StateView'
import { 
  Calculator, 
  ShoppingCart, 
  CalendarCheck,
  CheckCircle,
  WarningCircle,
  Lightning,
  ArrowRight
} from '@phosphor-icons/react'

export default function ForecastPage() {
  const { activeStore } = useStore()
  const { username } = useAuth()
  const queryClient = useQueryClient()
  const navigate = useNavigate()

  // Cấu hình số ngày dự trữ (Days)
  const [days, setDays] = useState<number>(7)
  const [orderSuccess, setOrderSuccess] = useState<string | null>(null)

  // 1. Tải danh sách dự báo nhập hàng từ API
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['forecast', activeStore?.id, days],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Forecast[]>>(`/forecast?days=${days}`)
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const forecastList = response?.data ?? []

  const { data: insightResponse, isLoading: isLoadingInsights, isError: isInsightError } = useQuery({
    queryKey: ['inventory-insights', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<InventoryInsight[]>>('/insights/inventory')
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const insights = insightResponse?.data ?? []

  // 2. Mutation tạo nhanh phiếu nhập kho dựa trên đề xuất
  const quickOrderMutation = useMutation({
    mutationFn: async (itemsToOrder: { ingredientId: number; quantity: number }[]) => {
      const payload = {
        type: 'IMPORT',
        reason: 'IMPORT_NEW',
        recordedBy: username || 'owner',
        items: itemsToOrder.map(item => {
          // Tạo số lô ngẫu nhiên tương ứng
          const randomSuffix = Math.floor(1000 + Math.random() * 9000)
          const lotNum = `LOT-FC-${randomSuffix}`
          
          // Giả lập hạn dùng là +15 ngày đối với sữa và +60 ngày đối với nguyên liệu khô khác
          const expDate = new Date()
          expDate.setDate(expDate.getDate() + (item.ingredientId === 2 ? 15 : 60))
          const expDateStr = expDate.toISOString().split('T')[0]

          // Đơn giá giả lập trung bình: 50.000đ
          const cost = item.ingredientId === 1 ? 120000 : item.ingredientId === 2 ? 28000 : 65000

          return {
            ingredientId: item.ingredientId,
            batchNumber: lotNum,
            quantity: item.quantity,
            expiredDate: expDateStr,
            costPerUnit: cost
          }
        })
      }
      await apiClient.post('/inventory/transactions', payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['forecast'] })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
      setOrderSuccess('Đã tự động lập phiếu Nhập kho và cập nhật lô hàng đề xuất thành công!')
      setTimeout(() => setOrderSuccess(null), 3000)
    }
  })

  // Thực hiện đặt hàng nhanh cho tất cả nguyên liệu có đề xuất > 0
  function handleQuickOrderAll() {
    const itemsToOrder = forecastList
      .filter(item => item.recommendedOrder > 0)
      .map(item => ({
        ingredientId: item.ingredientId,
        quantity: item.recommendedOrder
      }))

    if (itemsToOrder.length === 0) {
      alert('Tất cả nguyên liệu đều đủ dùng trong khoảng thời gian dự trữ này.')
      return
    }

    if (confirm(`Hệ thống sẽ tự động lập phiếu Nhập kho cho ${itemsToOrder.length} nguyên liệu thiếu hàng. Bạn đồng ý chứ?`)) {
      quickOrderMutation.mutate(itemsToOrder)
    }
  }

  // Đặt hàng nhanh cho 1 nguyên liệu duy nhất
  function handleQuickOrderSingle(ingredientId: number, quantity: number) {
    if (confirm(`Lập nhanh phiếu Nhập kho cho nguyên liệu này với số lượng ${quantity}?`)) {
      quickOrderMutation.mutate([{ ingredientId, quantity }])
    }
  }

  return (
    <div className="space-y-6 font-sans">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Dự báo & Đề xuất nhập hàng</h2>
          <p className="text-xs text-ink/60">Thuật toán tự động tính toán khối lượng hàng hóa cần đặt để tối ưu dòng tiền và tránh cạn kho.</p>
        </div>

        {/* Nút đặt nhanh tất cả */}
        <button
          onClick={handleQuickOrderAll}
          disabled={quickOrderMutation.isPending || forecastList.filter(item => item.recommendedOrder > 0).length === 0}
          className="flex items-center gap-1.5 bg-sage-dark text-white rounded-xl py-2.5 px-4 text-xs font-semibold hover:bg-sage transition-all shadow-sm disabled:opacity-50 disabled:cursor-not-allowed shrink-0"
        >
          <ShoppingCart size={16} />
          <span>Đặt tất cả đề xuất</span>
        </button>
      </div>

      {/* Thông tin công thức dự báo & cấu hình ngày */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        
        {/* Hộp giải thích công thức */}
        <div className="lg:col-span-2 bg-white border border-ink/5 rounded-2xl p-5 shadow-sm flex items-start gap-4">
          <div className="w-10 h-10 rounded-xl bg-sage/10 text-sage-dark flex items-center justify-center shrink-0">
            <Calculator size={20} />
          </div>
          <div>
            <h4 className="font-bold text-xs text-ink uppercase tracking-wider">Công thức dự báo thông minh</h4>
            <div className="text-xs text-ink/75 leading-relaxed mt-2 space-y-1">
              <p>Số lượng đề xuất nhập = <span className="font-bold text-terracotta">(Avg × Days)</span> + <span className="font-semibold text-sage-dark">MinStock</span> - <span className="font-semibold text-ink">CurrentStock</span></p>
              <ul className="text-[10px] text-ink/60 list-disc list-inside space-y-0.5">
                <li><span className="font-bold">Avg (Tiêu thụ ngày):</span> Lượng tiêu thụ trung bình hàng ngày của nguyên liệu.</li>
                <li><span className="font-bold">Days (Ngày dự trữ):</span> Số ngày dự kiến bán hàng cần chuẩn bị hàng hóa.</li>
                <li><span className="font-bold">MinStock (Ngưỡng an toàn):</span> Số lượng tối thiểu cần có sẵn để phòng ngừa rủi ro.</li>
                <li><span className="font-bold">CurrentStock (Tồn hiện tại):</span> Tổng tồn kho khả dụng hiện thực của các lô hàng.</li>
              </ul>
            </div>
          </div>
        </div>

        {/* Bộ điều chỉnh Days */}
        <div className="bg-white border border-ink/5 rounded-2xl p-5 shadow-sm flex flex-col justify-between">
          <div>
            <label className="block text-xs font-bold text-ink uppercase tracking-wider mb-2">Số ngày bán hàng cần dự trữ</label>
            <p className="text-[10px] text-ink/50 leading-relaxed mb-3">
              Tùy biến số ngày dự kiến chuẩn bị nguyên liệu (Ví dụ: đặt hàng cho 7 ngày tới, 14 ngày tới).
            </p>
          </div>
          
          <div className="flex bg-ink/5 p-1 rounded-xl border border-ink/5">
            {[7, 14, 30].map((d) => (
              <button
                key={d}
                onClick={() => setDays(d)}
                className={`flex-1 text-center py-1.5 rounded-lg text-xs font-bold transition-all ${
                  days === d ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
                }`}
              >
                {d} ngày
              </button>
            ))}
          </div>
        </div>

      </div>

      {orderSuccess && (
        <div className="bg-sage/10 border border-sage/20 text-sage-dark p-3.5 rounded-xl text-xs font-semibold flex items-center gap-2 animate-pulse">
          <CheckCircle size={16} />
          <span>{orderSuccess}</span>
        </div>
      )}

      <StateView isLoading={isLoadingInsights} isError={isInsightError}>
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
          {insights.slice(0, 3).map((insight) => (
            <InsightCard
              key={insight.ingredientId}
              insight={insight}
              onPrimaryAction={() => {
                if (insight.recommendedOrderQty > 0) {
                  handleQuickOrderSingle(insight.ingredientId, insight.recommendedOrderQty)
                } else {
                  navigate('/inventory')
                }
              }}
            />
          ))}
        </div>
      </StateView>

      {/* Bảng kết quả dự báo đề xuất */}
      <StateView isLoading={isLoading} isError={isError}>
        <div className="bg-white rounded-2xl border border-ink/5 overflow-hidden shadow-sm">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-ink/5 text-ink/80 font-bold border-b border-ink/10">
                <th className="px-6 py-4">Mã</th>
                <th className="px-6 py-4">Nguyên liệu</th>
                <th className="px-6 py-4 text-right">Mức tiêu thụ/ngày</th>
                <th className="px-6 py-4 text-right">Tồn kho hiện tại</th>
                <th className="px-6 py-4 text-right">Tồn tối thiểu (Min)</th>
                <th className="px-6 py-4 text-right bg-sage/5 text-sage-dark font-extrabold">Đề xuất đặt ({days} ngày)</th>
                <th className="px-6 py-4 text-center">Trạng thái đặt hàng</th>
                <th className="px-6 py-4 text-right">Tự động hóa</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-ink/10 text-ink/80">
              {forecastList.map((item) => {
                const needsOrder = item.recommendedOrder > 0
                const isUrgent = item.currentStock < item.minStock && needsOrder

                return (
                  <tr key={item.ingredientId} className="hover:bg-ink/5 transition-colors">
                    <td className="px-6 py-4 font-mono font-bold text-ink">{item.ingredientCode}</td>
                    <td className="px-6 py-4 font-semibold text-ink">{item.ingredientName}</td>
                    <td className="px-6 py-4 text-right font-mono font-semibold">{item.avgDailyUsage} {item.unit}</td>
                    <td className="px-6 py-4 text-right font-mono font-semibold">{item.currentStock} {item.unit}</td>
                    <td className="px-6 py-4 text-right font-mono font-semibold text-ink/60">{item.minStock} {item.unit}</td>
                    <td className={`px-6 py-4 text-right font-mono font-extrabold bg-sage/5 ${
                      needsOrder ? 'text-terracotta' : 'text-sage-dark'
                    }`}>
                      {item.recommendedOrder} {item.unit}
                    </td>
                    <td className="px-6 py-4 text-center">
                      <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full font-bold text-[10px] ${
                        isUrgent 
                          ? 'bg-red-500/10 text-red-600 animate-pulse' 
                          : needsOrder 
                          ? 'bg-terracotta/15 text-terracotta' 
                          : 'bg-sage/15 text-sage-dark'
                      }`}>
                        {isUrgent ? <WarningCircle size={10} /> : null}
                        {isUrgent ? 'Cần nhập gấp' : needsOrder ? 'Chuẩn bị nhập' : 'Đủ dùng'}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-right">
                      <button
                        onClick={() => handleQuickOrderSingle(item.ingredientId, item.recommendedOrder)}
                        disabled={quickOrderMutation.isPending || !needsOrder}
                        className="inline-flex items-center gap-1 bg-white hover:bg-sage hover:text-white border border-sage-dark/25 text-sage-dark px-3 py-1.5 rounded-xl font-bold transition-all disabled:opacity-40 disabled:bg-ink/5 disabled:text-ink/30 disabled:border-ink/10 shadow-sm"
                      >
                        <CalendarCheck size={12} />
                        <span>Đặt nhanh</span>
                      </button>
                    </td>
                  </tr>
                )
              })}
            </tbody>
          </table>
        </div>
      </StateView>
    </div>
  )
}

function InsightCard({
  insight,
  onPrimaryAction
}: {
  insight: InventoryInsight
  onPrimaryAction: () => void
}) {
  const tone = riskTone(insight.wasteRiskLevel)
  const stockoutText = insight.daysUntilStockout === undefined ? 'Chưa rõ' : `${insight.daysUntilStockout} ngày`
  const expiryText = insight.daysUntilExpiry === undefined ? 'Không có lô' : `${insight.daysUntilExpiry} ngày`

  return (
    <div className={`bg-white border ${tone.border} rounded-2xl p-4 shadow-sm space-y-3`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[10px] font-bold uppercase tracking-wider text-ink/50">{insight.ingredientCode}</p>
          <h3 className="font-bold text-sm text-ink truncate mt-0.5">{insight.ingredientName}</h3>
        </div>
        <span className={`shrink-0 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-extrabold ${tone.badge}`}>
          <Lightning size={11} weight="fill" />
          {tone.label}
        </span>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <Metric label="Cạn kho" value={stockoutText} />
        <Metric label="Cận hạn" value={expiryText} />
        <Metric label="Nên nhập" value={`${insight.recommendedOrderQty} ${insight.unit}`} strong={insight.recommendedOrderQty > 0} />
      </div>

      <ul className="space-y-1.5 text-[11px] leading-relaxed text-ink/70">
        {insight.explanationBullets.slice(0, 3).map((bullet) => (
          <li key={bullet} className="flex gap-1.5">
            <span className={`mt-1 h-1.5 w-1.5 rounded-full ${tone.dot}`} />
            <span>{bullet}</span>
          </li>
        ))}
      </ul>

      <button
        onClick={onPrimaryAction}
        className={`w-full inline-flex items-center justify-center gap-1.5 rounded-xl px-3 py-2 text-xs font-bold transition-colors ${tone.button}`}
      >
        {insight.ctaLabel}
        <ArrowRight size={13} />
      </button>
    </div>
  )
}

function Metric({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="rounded-xl bg-offwhite/60 border border-ink/5 px-2 py-2 min-w-0">
      <div className="text-[9px] uppercase font-bold text-ink/45 truncate">{label}</div>
      <div className={`text-[11px] font-extrabold truncate mt-0.5 ${strong ? 'text-terracotta' : 'text-ink'}`}>{value}</div>
    </div>
  )
}

function riskTone(level: WasteRiskLevel) {
  if (level === 'HIGH') {
    return {
      label: 'Rủi ro cao',
      border: 'border-red-500/20',
      badge: 'bg-red-500/10 text-red-600',
      dot: 'bg-red-500',
      button: 'bg-red-600 text-white hover:bg-red-700'
    }
  }
  if (level === 'MEDIUM') {
    return {
      label: 'Cần chú ý',
      border: 'border-terracotta/20',
      badge: 'bg-terracotta/15 text-terracotta',
      dot: 'bg-terracotta',
      button: 'bg-terracotta text-white hover:brightness-95'
    }
  }
  return {
    label: 'Ổn định',
    border: 'border-sage/25',
    badge: 'bg-sage/15 text-sage-dark',
    dot: 'bg-sage-dark',
    button: 'bg-sage-dark text-white hover:bg-sage'
  }
}
