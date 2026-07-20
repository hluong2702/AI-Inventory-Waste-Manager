import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ArrowRight, CalendarBlank, LockKey, TrendDown, TrendUp, WarningCircle } from '@phosphor-icons/react'
import { useNavigate } from 'react-router-dom'
import {
  Area,
  AreaChart,
  CartesianGrid,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts'
import apiClient from '../api/client'
import DailyActionCenter from '../components/DailyActionCenter'
import DoubleBezelCard from '../components/DoubleBezelCard'
import StateView from '../components/StateView'
import { useStore } from '../context/StoreContext'
import type { ApiResponse, DashboardData } from '../types'
import { apiErrorMessage } from '../utils/apiResponse'
import { formatDate, formatVND } from '../utils/fefo'

export default function DashboardPage() {
  const { activeStore, isLoadingStores } = useStore()
  const navigate = useNavigate()
  const queryClient = useQueryClient()

  const dashboardQuery = useQuery({
    queryKey: ['dashboard', activeStore?.id],
    queryFn: async () => {
      const response = await apiClient.get<ApiResponse<DashboardData>>('/dashboard')
      return response.data.data
    },
    enabled: Boolean(activeStore?.id),
  })

  const resolveAlertMutation = useMutation({
    mutationFn: async (alertId: number) => {
      await apiClient.post(`/alerts/${alertId}/resolve`)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['dashboard', activeStore?.id] })
      await queryClient.invalidateQueries({ queryKey: ['alerts', activeStore?.id] })
    },
  })

  const dashboard = dashboardQuery.data
  const waste = dashboard?.waste ?? null
  const chartData = waste?.dailyCosts.map((point) => ({
    name: shortDate(point.date),
    'Chi phí lãng phí': point.estimatedCost,
  })) ?? []

  return (
    <div className="space-y-8 font-sans">
      <StateView
        isLoading={dashboardQuery.isLoading || isLoadingStores}
        isError={dashboardQuery.isError || (!activeStore && !isLoadingStores)}
        errorMessage={!activeStore
          ? 'Không tìm thấy cửa hàng đang hoạt động.'
          : apiErrorMessage(dashboardQuery.error, 'Không thể tải dữ liệu Dashboard. Dữ liệu cũ hoặc giá trị 0 sẽ không được dùng thay thế.')}
        loadingCount={3}
        onRetry={() => void dashboardQuery.refetch()}
      >
        {dashboard && (
          <>
            <DailyActionCenter
              storeName={activeStore?.name}
              insightsAvailable={dashboard.insightsAvailable}
              insights={dashboard.insights}
              openAlerts={dashboard.openAlertCount}
              monthlyWasteCost={waste?.currentWasteCost ?? null}
              canViewForecast={dashboard.canViewForecast}
              onNavigate={navigate}
            />

            {dashboard.reportsAvailable && waste ? (
              <WasteOverview waste={waste} onOpenReports={() => navigate('/reports')} />
            ) : (
              <RestrictedReportNotice />
            )}

            <div className="grid grid-cols-1 gap-8 lg:grid-cols-12">
              <div className="lg:col-span-8">
                {dashboard.reportsAvailable && waste ? (
                  <DoubleBezelCard
                    title="Biến động chi phí lãng phí"
                    subtitle={`Tổng chi phí nguyên liệu bị hủy/hết hạn từ ${displayDate(dashboard.periodStart)} đến ${displayDate(dashboard.periodEnd)}`}
                    action={<WasteTrend changePercent={waste.changePercent} />}
                  >
                    {chartData.length > 0 ? (
                      <div className="mt-2 h-72 w-full">
                        <ResponsiveContainer width="100%" height="100%">
                          <AreaChart data={chartData} margin={{ top: 10, right: 10, left: -10, bottom: 0 }}>
                            <defs>
                              <linearGradient id="colorWaste" x1="0" y1="0" x2="0" y2="1">
                                <stop offset="5%" stopColor="var(--color-terracotta)" stopOpacity={0.2} />
                                <stop offset="95%" stopColor="var(--color-terracotta)" stopOpacity={0} />
                              </linearGradient>
                            </defs>
                            <CartesianGrid strokeDasharray="3 3" stroke="rgba(0,0,0,0.03)" />
                            <XAxis dataKey="name" tick={{ fontSize: 10, fill: 'var(--color-ink)', opacity: 0.6 }} axisLine={false} tickLine={false} />
                            <YAxis tickFormatter={(value) => `${Number(value) / 1000}k`} tick={{ fontSize: 10, fill: 'var(--color-ink)', opacity: 0.6 }} axisLine={false} tickLine={false} />
                            <Tooltip
                              formatter={(value) => [formatVND(Number(value)), 'Lãng phí']}
                              contentStyle={{ backgroundColor: '#fff', border: '1px solid rgba(0,0,0,0.08)', borderRadius: '12px', fontSize: '12px' }}
                            />
                            <Area type="monotone" dataKey="Chi phí lãng phí" stroke="var(--color-terracotta)" strokeWidth={2} fillOpacity={1} fill="url(#colorWaste)" />
                          </AreaChart>
                        </ResponsiveContainer>
                      </div>
                    ) : (
                      <div className="flex h-72 items-center justify-center text-xs text-ink/50">Chưa ghi nhận chi phí lãng phí trong kỳ này.</div>
                    )}
                  </DoubleBezelCard>
                ) : (
                  <RestrictedChartNotice />
                )}
              </div>

              <div className="lg:col-span-4">
                <DoubleBezelCard
                  title="Lô hàng cận hạn sử dụng"
                  subtitle="Các lô hết hạn hoặc còn tối đa 7 ngày, ưu tiên theo FEFO"
                  action={(
                    <button onClick={() => navigate('/inventory')} className="flex items-center gap-0.5 text-xs font-bold text-sage-dark hover:underline">
                      Chi tiết <ArrowRight size={12} />
                    </button>
                  )}
                >
                  <NearExpiryList batches={dashboard.nearExpiryBatches} />
                </DoubleBezelCard>
              </div>
            </div>

            <DoubleBezelCard
              title="Cảnh báo khẩn cấp chưa xử lý"
              subtitle={`${dashboard.openAlertCount} cảnh báo đang mở; hiển thị 3 cảnh báo mới nhất`}
              action={(
                <button onClick={() => navigate('/alerts')} className="flex items-center gap-0.5 text-xs font-bold text-sage-dark hover:underline">
                  Đến trang Cảnh báo <ArrowRight size={12} />
                </button>
              )}
            >
              <OpenAlerts
                dashboard={dashboard}
                resolvingId={resolveAlertMutation.isPending ? resolveAlertMutation.variables : undefined}
                resolveError={resolveAlertMutation.isError ? apiErrorMessage(resolveAlertMutation.error, 'Không thể xử lý cảnh báo.') : null}
                onResolve={(id) => resolveAlertMutation.mutate(id)}
              />
            </DoubleBezelCard>
          </>
        )}
      </StateView>
    </div>
  )
}

function WasteOverview({ waste, onOpenReports }: { waste: NonNullable<DashboardData['waste']>; onOpenReports: () => void }) {
  return (
    <DoubleBezelCard
      title="Dashboard tiền thất thoát"
      subtitle="Chi phí lãng phí tháng hiện tại, kỳ trước và nhóm nguyên liệu cần kiểm soát"
      action={<button onClick={onOpenReports} className="flex items-center gap-0.5 text-xs font-bold text-sage-dark hover:underline">Xem chi tiết <ArrowRight size={12} /></button>}
    >
      <div className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-xl border border-terracotta/15 bg-terracotta/5 p-4">
          <div className="text-[10px] font-bold uppercase text-ink/45">Thất thoát tháng này</div>
          <div className="mt-1 font-mono text-2xl font-bold text-ink">{formatVND(waste.currentWasteCost)}</div>
          <div className="mt-2 text-xs text-ink/60">
            Kỳ trước {formatVND(waste.previousWasteCost)}
            <span className={`ml-2 font-bold ${waste.changePercent > 0 ? 'text-terracotta' : 'text-sage-dark'}`}>
              {waste.changePercent > 0 ? '+' : ''}{waste.changePercent}%
            </span>
          </div>
        </div>
        <div className="grid grid-cols-1 gap-2 sm:grid-cols-2 lg:col-span-2">
          {waste.topWasteIngredients.length > 0 ? waste.topWasteIngredients.map((item) => (
            <div key={item.ingredientId} className="rounded-xl border border-ink/5 bg-white px-3 py-2">
              <div className="flex items-center justify-between gap-3">
                <span className="truncate text-xs font-bold text-ink">{item.ingredientName}</span>
                <span className="font-mono text-xs font-bold text-terracotta">{formatVND(item.estimatedCost)}</span>
              </div>
              <div className="mt-0.5 text-[10px] text-ink/50">Xuất hủy {item.quantity} {item.unit}</div>
            </div>
          )) : <div className="flex items-center text-xs text-ink/50">Chưa có bản ghi thất thoát trong tháng.</div>}
        </div>
      </div>
    </DoubleBezelCard>
  )
}

function NearExpiryList({ batches }: { batches: DashboardData['nearExpiryBatches'] }) {
  if (batches.length === 0) {
    return <div className="py-6 text-center text-xs text-ink/50">Không có lô hàng hết hạn hoặc cận hạn trong 7 ngày tới.</div>
  }
  return (
    <div className="space-y-4">
      {batches.map((batch) => {
        const isExpired = batch.daysUntilExpiry < 0
        const isUrgent = batch.daysUntilExpiry <= 3
        return (
          <div key={batch.id} className="flex items-center justify-between rounded-xl border border-ink/5 bg-offwhite/40 p-3">
            <div className="truncate pr-2">
              <div className="truncate text-xs font-semibold text-ink">{batch.ingredientName}</div>
              <div className="mt-0.5 text-[10px] text-ink/50">Lô: <span className="font-mono">{batch.batchNumber}</span> · SL: {batch.quantity} {batch.ingredientUnit}</div>
            </div>
            <span className={`shrink-0 rounded-full px-2 py-0.5 text-[10px] font-bold ${isExpired ? 'bg-red-500/10 text-red-600' : isUrgent ? 'bg-terracotta/15 text-terracotta' : 'bg-sage/15 text-sage-dark'}`}>
              {isExpired ? 'Đã hết hạn' : `Còn ${batch.daysUntilExpiry} ngày`}
            </span>
          </div>
        )
      })}
    </div>
  )
}

function OpenAlerts({ dashboard, resolvingId, resolveError, onResolve }: {
  dashboard: DashboardData
  resolvingId?: number
  resolveError: string | null
  onResolve: (id: number) => void
}) {
  if (dashboard.openAlerts.length === 0) {
    return <div className="rounded-xl border border-dashed border-ink/10 bg-offwhite/20 py-8 text-center text-xs text-ink/50">Không có cảnh báo khẩn cấp chưa xử lý.</div>
  }
  return (
    <div className="space-y-3">
      {resolveError && <div className="rounded-lg border border-terracotta/20 bg-terracotta/10 px-3 py-2 text-xs font-semibold text-terracotta">{resolveError}</div>}
      {dashboard.openAlerts.map((alert) => {
        const isExpiry = alert.type === 'EXPIRING_SOON'
        const AlertIcon = isExpiry ? CalendarBlank : WarningCircle
        return (
          <div key={alert.id} className={`flex items-start justify-between gap-4 rounded-xl border p-4 ${isExpiry ? 'border-red-500/10 bg-red-500/5' : 'border-terracotta/10 bg-terracotta/5'}`}>
            <div className="flex items-start gap-2.5">
              <AlertIcon size={18} className={`${isExpiry ? 'text-red-600' : 'text-terracotta'} mt-0.5 shrink-0`} />
              <div>
                <p className="text-xs font-semibold leading-normal text-ink">{alert.message}</p>
                <span className="mt-1 block text-[10px] text-ink/50">Ngày tạo: {formatDate(alert.createdAt)} · {isExpiry ? 'Cận / Quá hạn' : 'Tồn kho thấp'}</span>
              </div>
            </div>
            {dashboard.canResolveAlerts && (
              <button
                type="button"
                disabled={resolvingId === alert.id}
                onClick={() => onResolve(alert.id)}
                className="shrink-0 rounded-lg border border-sage-dark/20 bg-white px-2.5 py-1 text-[10px] font-bold text-sage-dark transition-colors hover:bg-sage-dark hover:text-white disabled:cursor-wait disabled:opacity-50"
              >
                {resolvingId === alert.id ? 'Đang xử lý...' : 'Đã xử lý'}
              </button>
            )}
          </div>
        )
      })}
    </div>
  )
}

function WasteTrend({ changePercent }: { changePercent: number }) {
  const increased = changePercent > 0
  const Icon = increased ? TrendUp : TrendDown
  const label = changePercent === 0 ? 'Ổn định' : `${increased ? 'Tăng' : 'Giảm'} ${Math.abs(changePercent)}%`
  return <span className={`flex items-center gap-1 rounded-full px-2 py-0.5 text-[11px] font-bold uppercase ${increased ? 'bg-terracotta/10 text-terracotta' : 'bg-sage/15 text-sage-dark'}`}><Icon size={12} />{label}</span>
}

function RestrictedReportNotice() {
  return <div className="flex items-center gap-3 rounded-2xl border border-ink/10 bg-white p-5 text-sm text-ink/60"><LockKey size={22} className="shrink-0 text-ink/40" /><div><div className="font-bold text-ink">Số liệu tài chính được giới hạn</div><div className="mt-0.5 text-xs">Chỉ Owner và Manager được xem chi phí thất thoát.</div></div></div>
}

function RestrictedChartNotice() {
  return <div className="flex h-full min-h-80 items-center justify-center rounded-2xl border border-dashed border-ink/15 bg-white/50 p-8 text-center text-xs text-ink/50">Biểu đồ chi phí không khả dụng với vai trò hiện tại.</div>
}

function shortDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return year && month && day ? `${day}/${month}` : value
}

function displayDate(value: string) {
  const [year, month, day] = value.split('-').map(Number)
  return year && month && day ? `${day.toString().padStart(2, '0')}/${month.toString().padStart(2, '0')}/${year}` : value
}
