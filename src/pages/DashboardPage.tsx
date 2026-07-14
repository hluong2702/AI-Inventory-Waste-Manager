import { useQuery } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Alert, InventoryBatch, WasteRecord, Ingredient, InventoryInsight, WasteRiskLevel, WasteDashboard, PageResponse } from '../types'
import { useStore } from '../context/StoreContext'
import DoubleBezelCard from '../components/DoubleBezelCard'
import StateView from '../components/StateView'
import { formatVND, formatDate, getDaysUntilExpiry } from '../utils/fefo'
import { 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer 
} from 'recharts'
import { 
  WarningCircle, 
  Trash, 
  Package, 
  CalendarBlank, 
  ArrowRight,
  TrendDown,
  Lightning
} from '@phosphor-icons/react'
import { useNavigate } from 'react-router-dom'

export default function DashboardPage() {
  const { activeStore } = useStore()
  const navigate = useNavigate()

  // 1. Tải danh sách nguyên liệu
  const { data: ingResponse, isLoading: isLoadingIngs } = useQuery({
    queryKey: ['ingredients'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data
    }
  })

  // 2. Tải danh sách cảnh báo (OPEN)
  const { data: alertsResponse, isLoading: isLoadingAlerts } = useQuery({
    queryKey: ['alerts', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Alert[]>>('/alerts')
      return res.data
    },
    enabled: !!activeStore?.id
  })

  // 3. Tải danh sách lô hàng trong kho
  const { data: batchesResponse, isLoading: isLoadingBatches } = useQuery({
    queryKey: ['batches'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<InventoryBatch>>>('/inventory/batches?page=0&size=100&sort=expiryDate,asc')
      return res.data
    }
  })

  // 4. Tải danh sách báo cáo lãng phí (tháng này)
  const { data: wasteResponse, isLoading: isLoadingWaste } = useQuery({
    queryKey: ['waste-logs', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<WasteRecord>>>('/reports/waste?page=0&size=100&sort=createdAt,desc')
      return res.data
    },
    enabled: !!activeStore?.id
  })

  // 5. Tải recommendation engine tồn kho
  const { data: insightResponse, isLoading: isLoadingInsights } = useQuery({
    queryKey: ['inventory-insights', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<InventoryInsight[]>>('/insights/inventory')
      return res.data
    },
    enabled: !!activeStore?.id
  })

  const { data: wasteDashboardResponse } = useQuery({
    queryKey: ['waste-dashboard', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<WasteDashboard>>('/reports/waste/dashboard?period=month')
      return res.data
    },
    enabled: !!activeStore?.id
  })

  const ingredients = ingResponse?.data ?? []
  const alerts = alertsResponse?.data ?? []
  const batches = batchesResponse?.data.content ?? []
  const wasteRecords = wasteResponse?.data.content ?? []
  const insights = insightResponse?.data ?? []
  const wasteDashboard = wasteDashboardResponse?.data

  const isLoading = isLoadingIngs || isLoadingAlerts || isLoadingBatches || isLoadingWaste || isLoadingInsights

  // ==========================================
  // XỬ LÝ SỐ LIỆU KPI
  // ==========================================

  // Tổng số lượng nguyên liệu đang hoạt động
  const totalIngredientsCount = ingredients.length

  // Tổng chi phí lãng phí tháng này
  const totalWasteCost = wasteDashboard?.currentWasteCost ?? wasteRecords.reduce((sum, w) => sum + w.estimatedCost, 0)

  // Số lượng cảnh báo chưa xử lý
  const activeAlertsCount = alerts.filter(a => a.status === 'OPEN').length

  // ==========================================
  // CHUẨN BỊ DỮ LIỆU BIỂU ĐỒ LÃNG PHÍ HÀNG NGÀY
  // ==========================================
  
  const chartData = Object.entries(
    wasteRecords.reduce<Record<string, number>>((acc, record) => {
      const date = record.createdAt.slice(0, 10)
      acc[date] = (acc[date] ?? 0) + record.estimatedCost
      return acc
    }, {}),
  )
    .sort(([a], [b]) => a.localeCompare(b))
    .map(([date, dailyCost]) => {
      const parsed = new Date(date)
      return {
        name: Number.isNaN(parsed.getTime()) ? date : `${parsed.getUTCDate()}/${parsed.getUTCMonth() + 1}`,
        'Chi phí lãng phí': dailyCost,
      }
    })

  // ==========================================
  // XỬ LÝ LÔ HÀNG SẮP HẾT HẠN TRONG KHO
  // ==========================================
  const nearExpiryBatches = batches
    .filter((b) => b.quantity > 0)
    .map((b) => {
      const ing = ingredients.find((i) => i.id === b.ingredientId)
      return {
        ...b,
        ingredientName: ing?.name ?? 'Nguyên liệu ẩn',
        unit: ing?.unit ?? '',
        daysLeft: getDaysUntilExpiry(b.expiredDate)
      }
    })
    // Sắp xếp các lô có hạn dùng gần nhất (hoặc đã hết hạn) lên đầu
    .sort((a, b) => a.daysLeft - b.daysLeft)
    .slice(0, 5)

  return (
    <div className="space-y-8 font-sans">
      
      {/* Welcome Title */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-2">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">
            {activeStore ? activeStore.name : 'Hệ thống Quản lý'}
          </h2>
          <p className="text-xs text-ink/60">
            Tổng quan tình hình tồn kho và cảnh báo thất thoát hôm nay.
          </p>
        </div>
        <div className="text-xs text-ink/50 bg-white border border-ink/5 px-3 py-1.5 rounded-xl font-medium shadow-sm">
          Ngày giả lập hệ thống: <span className="font-bold text-terracotta">09/07/2026</span>
        </div>
      </div>

      <StateView isLoading={isLoading} loadingCount={2}>
        {/* KPI Cards Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
          <KpiCard 
            label="Cảnh báo chưa xử lý" 
            value={activeAlertsCount} 
            color="terracotta" 
            icon={WarningCircle}
            onClick={() => navigate('/alerts')}
          />
          <KpiCard 
            label="Chi phí lãng phí tháng này" 
            value={formatVND(totalWasteCost)} 
            color="sage" 
            icon={Trash}
            onClick={() => navigate('/reports')}
          />
          <KpiCard 
            label="Nguyên liệu quản lý" 
            value={`${totalIngredientsCount} mặt hàng`} 
            color="ink" 
            icon={Package}
            onClick={() => navigate('/ingredients')}
          />
        </div>

        <DoubleBezelCard
          title="Đề xuất tồn kho hôm nay"
          subtitle="Recommendation engine giải thích bằng dữ liệu tiêu thụ, tồn kho và hạn dùng"
          action={
            <button 
              onClick={() => navigate('/forecast')}
              className="text-xs text-sage-dark font-bold hover:underline flex items-center gap-0.5"
            >
              Xem dự báo <ArrowRight size={12} />
            </button>
          }
        >
          <div className="grid grid-cols-1 md:grid-cols-3 gap-3">
            {insights.slice(0, 3).map((insight) => (
              <DashboardInsightCard
                key={insight.ingredientId}
                insight={insight}
                onClick={() => navigate(insight.recommendedOrderQty > 0 ? '/forecast' : '/inventory')}
              />
            ))}
            {insights.length === 0 && (
              <div className="md:col-span-3 text-center py-6 text-xs text-ink/50 bg-offwhite/20 rounded-xl border border-dashed border-ink/10">
                Chưa có đủ dữ liệu để tạo đề xuất tồn kho.
              </div>
            )}
          </div>
        </DoubleBezelCard>

        {wasteDashboard && (
          <DoubleBezelCard
            title="Dashboard tiền thất thoát"
            subtitle="Estimated waste cost theo tháng, so sánh kỳ trước và top nguyên liệu cần kiểm soát"
            action={
              <button 
                onClick={() => navigate('/reports')}
                className="text-xs text-sage-dark font-bold hover:underline flex items-center gap-0.5"
              >
                Xem chi tiết <ArrowRight size={12} />
              </button>
            }
          >
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              <div className="rounded-xl border border-terracotta/15 bg-terracotta/5 p-4">
                <div className="text-[10px] font-bold uppercase text-ink/45">Thất thoát tháng này</div>
                <div className="mt-1 text-2xl font-mono font-bold text-ink">{formatVND(wasteDashboard.currentWasteCost)}</div>
                <div className="mt-2 text-xs text-ink/60">
                  Kỳ trước {formatVND(wasteDashboard.previousWasteCost)}
                  <span className={`ml-2 font-bold ${wasteDashboard.changePercent > 0 ? 'text-terracotta' : 'text-sage-dark'}`}>
                    {wasteDashboard.changePercent > 0 ? '+' : ''}{wasteDashboard.changePercent}%
                  </span>
                </div>
              </div>
              <div className="lg:col-span-2 grid grid-cols-1 sm:grid-cols-2 gap-2">
                {wasteDashboard.topWasteIngredients.slice(0, 4).map((item) => (
                  <div key={item.ingredientId} className="rounded-xl border border-ink/5 bg-white px-3 py-2">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs font-bold text-ink truncate">{item.ingredientName}</span>
                      <span className="text-xs font-mono font-bold text-terracotta">{formatVND(item.estimatedCost)}</span>
                    </div>
                    <div className="mt-0.5 text-[10px] text-ink/50">Xuất hủy {item.quantity} {item.unit}</div>
                  </div>
                ))}
              </div>
            </div>
          </DoubleBezelCard>
        )}

        {/* Row 2: Biểu đồ lãng phí & Lô hàng sắp hết hạn */}
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* Biểu đồ lãng phí */}
          <div className="lg:col-span-8">
            <DoubleBezelCard 
              title="Biến động chi phí lãng phí" 
              subtitle="Tổng chi phí nguyên liệu bị hủy/hết hạn theo ngày (Từ 01/07 - 15/07/2026)"
              action={
                <span className="flex items-center gap-1 text-[11px] font-bold text-terracotta uppercase bg-terracotta/10 px-2 py-0.5 rounded-full">
                  <TrendDown size={12} />
                  Thất thoát cao
                </span>
              }
            >
              <div className="h-72 w-full mt-2">
                <ResponsiveContainer width="100%" height="100%">
                  <AreaChart
                    data={chartData}
                    margin={{ top: 10, right: 10, left: -10, bottom: 0 }}
                  >
                    <defs>
                      <linearGradient id="colorWaste" x1="0" y1="0" x2="0" y2="1">
                        <stop offset="5%" stopColor="var(--color-terracotta)" stopOpacity={0.2}/>
                        <stop offset="95%" stopColor="var(--color-terracotta)" stopOpacity={0.0}/>
                      </linearGradient>
                    </defs>
                    <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.03)" />
                    <XAxis 
                      dataKey="name" 
                      tick={{ fontSize: 10, fill: 'var(--color-ink)', opacity: 0.6 }} 
                      axisLine={false}
                      tickLine={false}
                    />
                    <YAxis 
                      tickFormatter={(val) => `${val / 1000}k`}
                      tick={{ fontSize: 10, fill: 'var(--color-ink)', opacity: 0.6 }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip 
                      formatter={(value: any) => [formatVND(value), 'Lãng phí']}
                      contentStyle={{ 
                        backgroundColor: '#fff', 
                        border: '1px solid rgba(0,0,0,0.08)', 
                        borderRadius: '12px',
                        fontSize: '12px'
                      }}
                    />
                    <Area 
                      type="monotone" 
                      dataKey="Chi phí lãng phí" 
                      stroke="var(--color-terracotta)" 
                      strokeWidth={2}
                      fillOpacity={1} 
                      fill="url(#colorWaste)" 
                    />
                  </AreaChart>
                </ResponsiveContainer>
              </div>
            </DoubleBezelCard>
          </div>

          {/* Lô hàng sắp hết hạn */}
          <div className="lg:col-span-4">
            <DoubleBezelCard 
              title="Lô hàng cận hạn sử dụng" 
              subtitle="Cần ưu tiên xuất trước theo FEFO"
              action={
                <button 
                  onClick={() => navigate('/inventory')}
                  className="text-xs text-sage-dark font-bold hover:underline flex items-center gap-0.5"
                >
                  Chi tiết <ArrowRight size={12} />
                </button>
              }
            >
              <div className="space-y-4">
                {nearExpiryBatches.length === 0 ? (
                  <div className="text-center py-6 text-xs text-ink/50">
                    Không có lô hàng cận hạn nào trong kho.
                  </div>
                ) : (
                  nearExpiryBatches.map((batch) => {
                    const isExpired = batch.daysLeft < 0
                    const isUrgent = batch.daysLeft <= 3

                    return (
                      <div 
                        key={batch.id} 
                        className="flex items-center justify-between p-3 rounded-xl border border-ink/5 bg-offwhite/40"
                      >
                        <div className="truncate pr-2">
                          <div className="font-semibold text-xs text-ink truncate">
                            {batch.ingredientName}
                          </div>
                          <div className="text-[10px] text-ink/50 mt-0.5">
                            Lô: <span className="font-mono">{batch.batchNumber}</span> · SL: {batch.quantity} {batch.unit}
                          </div>
                        </div>

                        <span className={`shrink-0 text-[10px] font-bold px-2 py-0.5 rounded-full ${
                          isExpired 
                            ? 'bg-red-500/10 text-red-600' 
                            : isUrgent 
                            ? 'bg-terracotta/15 text-terracotta' 
                            : 'bg-sage/15 text-sage-dark'
                        }`}>
                          {isExpired 
                            ? 'Đã hết hạn' 
                            : isUrgent 
                            ? `Còn ${batch.daysLeft} ngày` 
                            : `Còn ${batch.daysLeft} ngày`
                          }
                        </span>
                      </div>
                    )
                  })
                )}
              </div>
            </DoubleBezelCard>
          </div>
        </div>

        {/* Row 3: Cảnh báo khẩn cấp */}
        <DoubleBezelCard 
          title="Cảnh báo khẩn cấp chưa xử lý" 
          subtitle="Các sự cố tồn kho thấp hoặc lô hàng hết hạn cần phản hồi ngay"
          action={
            <button 
              onClick={() => navigate('/alerts')}
              className="text-xs text-sage-dark font-bold hover:underline flex items-center gap-0.5"
            >
              Đến trang Cảnh báo <ArrowRight size={12} />
            </button>
          }
        >
          <div className="space-y-3">
            {alerts.filter(a => a.status === 'OPEN').length === 0 ? (
              <div className="text-center py-8 text-xs text-ink/50 bg-offwhite/20 rounded-xl border border-dashed border-ink/10">
                Chúc mừng! Không có cảnh báo khẩn cấp nào chưa xử lý.
              </div>
            ) : (
              alerts.filter(a => a.status === 'OPEN').slice(0, 3).map((alert) => {
                const isExpiry = alert.type === 'EXPIRING_SOON'
                const AlertIcon = isExpiry ? CalendarBlank : WarningCircle
                return (
                <div
                  key={alert.id}
                  className={`flex items-start justify-between gap-4 p-4 rounded-xl border ${
                    isExpiry ? 'bg-red-500/5 border-red-500/10' : 'bg-terracotta/5 border-terracotta/10'
                  }`}
                >
                  <div className="flex gap-2.5 items-start">
                    <AlertIcon
                      size={18}
                      className={`${isExpiry ? 'text-red-600' : 'text-terracotta'} shrink-0 mt-0.5`}
                    />
                    <div>
                      <p className="text-xs font-semibold text-ink leading-normal">
                        {alert.message}
                      </p>
                      <span className="text-[10px] text-ink/50 block mt-1">
                        Ngày tạo: {formatDate(alert.createdAt)} · Phân loại: {isExpiry ? 'Cận / Quá hạn' : 'Tồn kho thấp'}
                      </span>
                    </div>
                  </div>
                  
                  <button 
                    onClick={async () => {
                      await apiClient.post(`/alerts/${alert.id}/resolve`)
                      window.location.reload()
                    }}
                    className="shrink-0 text-[10px] font-bold text-sage-dark bg-white border border-sage-dark/20 hover:bg-sage-dark hover:text-white px-2.5 py-1 rounded-lg transition-colors shadow-sm"
                  >
                    Đã xử lý
                  </button>
                </div>
                )
              })
            )}
          </div>
        </DoubleBezelCard>
      </StateView>
    </div>
  )
}

function KpiCard({ 
  label, 
  value, 
  color, 
  icon: Icon,
  onClick
}: { 
  label: string
  value: string | number
  color: 'terracotta' | 'sage' | 'ink'
  icon: any
  onClick?: () => void
}) {
  const colorMap = {
    terracotta: {
      border: 'border-l-terracotta',
      bg: 'bg-terracotta/5',
      icon: 'text-terracotta',
    },
    sage: {
      border: 'border-l-sage-dark',
      bg: 'bg-sage/5',
      icon: 'text-sage-dark',
    },
    ink: {
      border: 'border-l-ink',
      bg: 'bg-ink/5',
      icon: 'text-ink',
    }
  }

  const selected = colorMap[color]

  return (
    <div 
      onClick={onClick}
      className={`bg-white rounded-2xl p-6 shadow-sm border-l-4 ${selected.border} flex items-center justify-between cursor-pointer hover:shadow-md transition-all group duration-300 transform hover:-translate-y-0.5`}
    >
      <div>
        <div className="text-xs font-medium text-ink/60">{label}</div>
        <div className="text-xl md:text-2xl font-bold text-ink mt-1.5">{value}</div>
      </div>
      <div className={`w-12 h-12 rounded-xl ${selected.bg} flex items-center justify-center group-hover:scale-110 transition-transform`}>
        <Icon size={22} className={selected.icon} />
      </div>
    </div>
  )
}

function DashboardInsightCard({
  insight,
  onClick
}: {
  insight: InventoryInsight
  onClick: () => void
}) {
  const tone = dashboardRiskTone(insight.wasteRiskLevel)
  const stockout = insight.daysUntilStockout === undefined ? 'chưa rõ' : `${insight.daysUntilStockout} ngày`
  const expiry = insight.daysUntilExpiry === undefined ? 'không có lô' : `${insight.daysUntilExpiry} ngày`

  return (
    <button
      onClick={onClick}
      className={`text-left rounded-xl border ${tone.border} bg-white p-4 hover:shadow-md transition-all min-w-0`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <div className="text-[10px] font-bold text-ink/45 uppercase tracking-wider truncate">{insight.ingredientCode}</div>
          <div className="font-bold text-sm text-ink truncate mt-0.5">{insight.ingredientName}</div>
        </div>
        <span className={`shrink-0 inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-extrabold ${tone.badge}`}>
          <Lightning size={11} weight="fill" />
          {tone.label}
        </span>
      </div>

      <div className="mt-3 grid grid-cols-3 gap-2 text-[10px]">
        <div>
          <div className="font-bold text-ink/45 uppercase">Tồn</div>
          <div className="font-extrabold text-ink mt-0.5 truncate">{insight.currentStock} {insight.unit}</div>
        </div>
        <div>
          <div className="font-bold text-ink/45 uppercase">Cạn</div>
          <div className="font-extrabold text-ink mt-0.5 truncate">{stockout}</div>
        </div>
        <div>
          <div className="font-bold text-ink/45 uppercase">Hạn</div>
          <div className="font-extrabold text-ink mt-0.5 truncate">{expiry}</div>
        </div>
      </div>

      <p className="mt-3 text-[11px] text-ink/65 leading-relaxed line-clamp-2">
        {insight.explanationBullets[0]}
      </p>

      <div className={`mt-3 inline-flex items-center gap-1 text-[11px] font-bold ${tone.link}`}>
        {insight.ctaLabel}
        <ArrowRight size={12} />
      </div>
    </button>
  )
}

function dashboardRiskTone(level: WasteRiskLevel) {
  if (level === 'HIGH') {
    return {
      label: 'Cao',
      border: 'border-red-500/20',
      badge: 'bg-red-500/10 text-red-600',
      link: 'text-red-600'
    }
  }
  if (level === 'MEDIUM') {
    return {
      label: 'Vừa',
      border: 'border-terracotta/20',
      badge: 'bg-terracotta/15 text-terracotta',
      link: 'text-terracotta'
    }
  }
  return {
    label: 'Ổn',
    border: 'border-sage/25',
    badge: 'bg-sage/15 text-sage-dark',
    link: 'text-sage-dark'
  }
}
