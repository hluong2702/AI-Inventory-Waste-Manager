import { useState, type ComponentType } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowRight,
  CalendarBlank,
  ChartLineUp,
  CheckCircle,
  ClockCountdown,
  Package,
  ShieldCheck,
  Sparkle,
  WarningCircle,
  Eye,
  Prohibit,
  Check,
  X,
  Spinner,
} from '@phosphor-icons/react'
import apiClient from '../api/client'
import type { ApiResponse, BackendDailyAction, DashboardData, PageResponse } from '../types'
import { formatVND } from '../utils/fefo'

interface DailyActionCenterProps {
  storeName?: string
  insightsAvailable: boolean
  insights: DashboardData['insights']
  openAlerts: number
  monthlyWasteCost: number | null
  canViewForecast: boolean
  onNavigate: (route: string) => void
}

export default function DailyActionCenter({
  storeName,
  insightsAvailable,
  insights,
  openAlerts,
  monthlyWasteCost,
  canViewForecast,
  onNavigate,
}: DailyActionCenterProps) {
  const queryClient = useQueryClient()

  // ── States for Dismiss Modal ──
  const [dismissingActionId, setDismissingActionId] = useState<number | null>(null)
  const [dismissReason, setDismissReason] = useState('')

  // ── 1. Fetch live open actions from backend ──
  const { data: actionsPage, isLoading: isLoadingActions } = useQuery({
    queryKey: ['daily-actions', insightsAvailable],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<BackendDailyAction>>>(
        '/daily-actions?page=0&size=5'
      )
      return res.data.data
    },
    enabled: insightsAvailable,
  })

  const dailyActions = actionsPage?.content ?? []

  // ── 2. Live counts for metrics ──
  const { data: countData } = useQuery({
    queryKey: ['daily-actions-count', insightsAvailable],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<{ openCount: number }>>('/daily-actions/count')
      return res.data.data
    },
    enabled: insightsAvailable,
  })

  const openCount = countData?.openCount ?? 0
  const highRiskCount = insights?.highRiskCount ?? 0
  const suggestedOrderCount = insights?.suggestedOrderCount ?? 0
  const healthyCount = insights?.healthyCount ?? 0
  const totalIngredientCount = insights?.totalCount ?? 0

  // ── 3. Mutations for actions state transitions ──
  const acknowledgeMutation = useMutation({
    mutationFn: async (actionId: number) => {
      await apiClient.post(`/daily-actions/${actionId}/acknowledge`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['daily-actions'] })
      queryClient.invalidateQueries({ queryKey: ['daily-actions-count'] })
    },
  })

  const resolveMutation = useMutation({
    mutationFn: async (actionId: number) => {
      await apiClient.post(`/daily-actions/${actionId}/resolve`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['daily-actions'] })
      queryClient.invalidateQueries({ queryKey: ['daily-actions-count'] })
    },
  })

  const dismissMutation = useMutation({
    mutationFn: async ({ actionId, reason }: { actionId: number; reason: string }) => {
      await apiClient.post(`/daily-actions/${actionId}/dismiss`, { reason })
    },
    onSuccess: () => {
      setDismissingActionId(null)
      setDismissReason('')
      queryClient.invalidateQueries({ queryKey: ['daily-actions'] })
      queryClient.invalidateQueries({ queryKey: ['daily-actions-count'] })
    },
  })

  const today = new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date())

  // Trình điều khiển bỏ qua (dismiss)
  const handleOpenDismissModal = (actionId: number) => {
    setDismissingActionId(actionId)
    setDismissReason('Lô hàng đã được xử lý thủ công')
  }

  const handleConfirmDismiss = () => {
    if (dismissingActionId) {
      dismissMutation.mutate({ actionId: dismissingActionId, reason: dismissReason })
    }
  }

  return (
    <section
      aria-labelledby="daily-action-title"
      className="overflow-hidden rounded-2xl border border-ink/10 bg-white shadow-[0_18px_50px_rgba(58,58,52,0.07)]"
    >
      <div className="grid gap-0 lg:grid-cols-[minmax(0,1.55fr)_minmax(280px,0.65fr)]">
        {/* Left Side: Actions list */}
        <div className="p-5 sm:p-7">
          <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:justify-between">
            <div className="max-w-2xl">
              <div className="mb-2 flex items-center gap-2 text-xs font-bold text-sage-dark">
                <Sparkle size={15} weight="fill" aria-hidden="true" />
                AI Daily Action Center
              </div>
              <h2 id="daily-action-title" className="text-2xl font-extrabold tracking-tight text-ink sm:text-3xl">
                Việc cần xử lý hôm nay
              </h2>
              <p className="mt-2 max-w-xl text-sm leading-relaxed text-ink/60">
                Hệ thống ưu tiên hành động từ tốc độ tiêu thụ, tồn theo lô và hạn sử dụng tại {storeName ?? 'cửa hàng'}.
              </p>
            </div>
            <time
              className="shrink-0 text-xs font-semibold capitalize text-ink/50"
              dateTime={new Date().toISOString().slice(0, 10)}
            >
              {today}
            </time>
          </div>

          {!insightsAvailable ? (
            <div className="mt-7 flex min-h-44 flex-col items-center justify-center rounded-xl border border-dashed border-terracotta/30 bg-terracotta/5 px-6 text-center">
              <WarningCircle size={30} className="text-terracotta" weight="duotone" aria-hidden="true" />
              <h3 className="mt-3 text-sm font-bold text-ink">Gói hiện tại chưa có phân tích nâng cao</h3>
              <p className="mt-1 max-w-md text-xs leading-relaxed text-ink/55">
                Dashboard không suy diễn trạng thái an toàn khi chưa có quyền chạy phân tích tồn kho.
              </p>
              <button
                type="button"
                onClick={() => onNavigate('/inventory')}
                className="mt-4 inline-flex items-center gap-1.5 rounded-lg border border-ink/10 bg-white px-3 py-2 text-xs font-bold text-ink transition-colors hover:border-sage-dark/30 hover:text-sage-dark"
              >
                Xem tồn kho thực tế <ArrowRight size={13} aria-hidden="true" />
              </button>
            </div>
          ) : isLoadingActions ? (
            <div className="mt-7 flex min-h-44 items-center justify-center">
              <Spinner size={24} className="animate-spin text-sage-dark" />
            </div>
          ) : dailyActions.length > 0 ? (
            <div className="mt-7 space-y-3">
              {dailyActions.map((action, index) => (
                <ActionRow
                  key={action.id}
                  action={action}
                  rank={index + 1}
                  onAcknowledge={() => acknowledgeMutation.mutate(action.id)}
                  onResolve={() => resolveMutation.mutate(action.id)}
                  onDismiss={() => handleOpenDismissModal(action.id)}
                  onNavigate={onNavigate}
                  canViewForecast={canViewForecast}
                />
              ))}
            </div>
          ) : (
            <div className="mt-7 flex min-h-44 flex-col items-center justify-center rounded-xl border border-dashed border-sage/35 bg-sage/5 px-6 text-center">
              <ShieldCheck size={30} className="text-sage-dark" weight="duotone" aria-hidden="true" />
              <h3 className="mt-3 text-sm font-bold text-ink">Kho đang trong vùng an toàn</h3>
              <p className="mt-1 max-w-md text-xs leading-relaxed text-ink/55">
                Chưa phát hiện hành động khẩn cấp hoặc các lô sắp hết hạn cần xử lý ngay.
              </p>
              <button
                type="button"
                onClick={() => onNavigate('/inventory')}
                className="mt-4 inline-flex items-center gap-1.5 rounded-lg border border-sage-dark/25 bg-white px-3 py-2 text-xs font-bold text-sage-dark transition-colors hover:bg-sage/10 active:translate-y-px"
              >
                Kiểm tra tồn kho <ArrowRight size={13} aria-hidden="true" />
              </button>
            </div>
          )}
        </div>

        {/* Right Side: Metrics panel */}
        <aside className="border-t border-ink/10 bg-offwhite/55 p-5 sm:p-7 lg:border-l lg:border-t-0" aria-label="Tóm tắt vận hành hôm nay">
          <h3 className="text-sm font-bold text-ink">Tình trạng hôm nay</h3>
          <div className="mt-5 grid grid-cols-2 gap-x-5 gap-y-6 lg:grid-cols-1">
            <SummaryMetric
              icon={WarningCircle}
              label="Việc cần xử lý"
              value={insightsAvailable ? String(openCount) : '—'}
              detail={`${openAlerts} cảnh báo mở`}
              tone="danger"
            />
            <SummaryMetric
              icon={Package}
              label="Cần lập đơn nhập"
              value={insightsAvailable ? String(suggestedOrderCount) : '—'}
              detail="Theo đề xuất AI & MA"
              tone="warning"
            />
            <SummaryMetric
              icon={ShieldCheck}
              label="Đang an toàn"
              value={insightsAvailable ? String(healthyCount) : '—'}
              detail={insightsAvailable ? `Trên tổng ${totalIngredientCount} nguyên liệu` : 'Chưa có quyền phân tích'}
              tone="safe"
            />
            <SummaryMetric
              icon={ChartLineUp}
              label="Lãng phí tháng"
              value={monthlyWasteCost === null ? '—' : formatVND(monthlyWasteCost)}
              detail={monthlyWasteCost === null ? 'Chỉ Owner/Manager được xem' : 'Chi phí đã hủy hàng'}
              tone="neutral"
              compact
            />
          </div>

          <button
            type="button"
            onClick={() => onNavigate(canViewForecast && insightsAvailable ? '/forecast' : '/inventory')}
            className="mt-7 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-ink px-4 py-3 text-xs font-bold text-offwhite transition-colors hover:bg-ink/90 active:translate-y-px"
          >
            {canViewForecast && insightsAvailable ? 'Xem toàn bộ dự báo' : 'Xem tồn kho thực tế'}{' '}
            <ArrowRight size={14} aria-hidden="true" />
          </button>
        </aside>
      </div>

      {/* ── Modal Bỏ qua (Dismiss Modal) ── */}
      {dismissingActionId !== null && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-4 backdrop-blur-xs">
          <div className="w-full max-w-md rounded-2xl bg-white p-6 shadow-xl border border-ink/5">
            <div className="flex items-center justify-between pb-3 border-b border-ink/5">
              <h3 className="font-extrabold text-sm text-ink uppercase tracking-wide flex items-center gap-1.5">
                <Prohibit size={18} className="text-terracotta" />
                Bỏ qua hành động khẩn cấp
              </h3>
              <button
                onClick={() => setDismissingActionId(null)}
                className="text-ink/40 hover:text-ink transition-colors"
              >
                <X size={18} />
              </button>
            </div>

            <div className="mt-4 space-y-4">
              <p className="text-xs text-ink/60 leading-relaxed">
                Khi bỏ qua, hành động này sẽ tạm ẩn khỏi bảng xử lý hàng ngày. Hãy chọn hoặc nhập lý do cụ thể:
              </p>
              <select
                value={dismissReason}
                onChange={(e) => setDismissReason(e.target.value)}
                className="w-full bg-white border border-ink/10 rounded-xl px-3 py-2.5 text-xs font-semibold text-ink focus:outline-none focus:ring-2 focus:ring-violet-500/20"
              >
                <option value="Lô hàng đã được xử lý thủ công">Lô hàng đã được xử lý thủ công</option>
                <option value="Sai sót dữ liệu tồn kho">Sai sót dữ liệu tồn kho</option>
                <option value="Đã đặt đơn mua hàng ngoài hệ thống">Đã đặt đơn mua hàng ngoài hệ thống</option>
                <option value="Nguyên liệu ngừng sử dụng tạm thời">Nguyên liệu ngừng sử dụng tạm thời</option>
                <option value="Khác">Lý do khác...</option>
              </select>

              {dismissReason === 'Khác' && (
                <textarea
                  placeholder="Nhập lý do chi tiết..."
                  rows={3}
                  value={dismissReason === 'Khác' ? '' : dismissReason}
                  onChange={(e) => setDismissReason(e.target.value)}
                  className="w-full bg-white border border-ink/10 rounded-xl p-3 text-xs font-medium text-ink focus:outline-none focus:ring-2 focus:ring-violet-500/20"
                />
              )}
            </div>

            <div className="mt-6 flex justify-end gap-2 border-t border-ink/5 pt-4">
              <button
                type="button"
                onClick={() => setDismissingActionId(null)}
                className="px-4 py-2 border border-ink/10 text-xs font-bold text-ink rounded-xl bg-white hover:bg-ink/5 transition-colors"
              >
                Hủy bỏ
              </button>
              <button
                type="button"
                onClick={handleConfirmDismiss}
                disabled={dismissMutation.isPending}
                className="px-4 py-2 bg-terracotta hover:bg-terracotta/95 text-xs font-bold text-white rounded-xl shadow-sm transition-all disabled:opacity-40"
              >
                {dismissMutation.isPending ? 'Đang lưu...' : 'Xác nhận bỏ qua'}
              </button>
            </div>
          </div>
        </div>
      )}
    </section>
  )
}

// ─── ActionRow Component ───

function ActionRow({
  action,
  rank,
  onAcknowledge,
  onResolve,
  onDismiss,
  onNavigate,
  canViewForecast,
}: {
  action: BackendDailyAction
  rank: number
  onAcknowledge: () => void
  onResolve: () => void
  onDismiss: () => void
  onNavigate: (route: string) => void
  canViewForecast: boolean
}) {
  const tone = getActionTone(action.actionType)
  const Icon = tone.icon

  // Trực quan hóa giá trị rủi ro
  const riskValueText =
    action.riskValueEstimate && action.riskValueEstimate > 0
      ? `Trị giá rủi ro: ~${formatVND(action.riskValueEstimate)}`
      : ''

  const handlePrimaryNavigation = () => {
    if (action.actionType === 'EXPIRY_RISK') {
      onNavigate('/inventory')
    } else if (action.actionType === 'REORDER') {
      onNavigate(canViewForecast ? '/forecast' : '/inventory')
    } else {
      onNavigate('/inventory')
    }
  }

  return (
    <article
      className={`group grid gap-3 rounded-xl border p-4 transition-all hover:shadow-md sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center ${tone.surface}`}
    >
      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${tone.iconSurface}`}>
        <Icon size={19} weight="duotone" aria-hidden="true" />
      </div>

      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-[10px] font-extrabold text-ink/40">Ưu tiên {rank}</span>
          <span className={`text-[10px] font-extrabold ${tone.label}`}>
            {tone.name} · Score: {action.priorityScore.toFixed(0)}
          </span>
          {action.status === 'ACKNOWLEDGED' && (
            <span className="bg-ink/10 text-ink/75 text-[9px] font-bold px-1.5 py-0.5 rounded-full">
              Đã ghi nhận
            </span>
          )}
        </div>
        <h3 className="mt-1 text-sm font-extrabold text-ink leading-tight">
          {action.productName}: {action.title}
        </h3>
        <p className="mt-1 text-xs leading-relaxed text-ink/65">{action.description}</p>
        {riskValueText && <p className={`mt-1.5 text-xs font-bold ${tone.label}`}>{riskValueText}</p>}
      </div>

      {/* Action CTA Buttons */}
      <div className="flex flex-col sm:flex-row gap-1.5 shrink-0 self-center">
        {action.status === 'OPEN' && (
          <button
            type="button"
            onClick={onAcknowledge}
            title="Đánh dấu đã ghi nhận thông tin"
            className="inline-flex items-center justify-center gap-1 rounded-lg border border-ink/10 bg-white hover:bg-ink/5 px-2.5 py-2 text-xs font-bold text-ink transition-all active:translate-y-px"
          >
            <Eye size={13} />
            <span>Ghi nhận</span>
          </button>
        )}

        <button
          type="button"
          onClick={onResolve}
          title="Xác nhận đã giải quyết xong"
          className="inline-flex items-center justify-center gap-1 rounded-lg border border-sage-dark/20 bg-sage text-white hover:bg-sage-dark px-2.5 py-2 text-xs font-bold transition-all active:translate-y-px"
        >
          <Check size={13} />
          <span>Giải quyết</span>
        </button>

        <button
          type="button"
          onClick={onDismiss}
          title="Bỏ qua cảnh báo"
          className="inline-flex items-center justify-center gap-1 rounded-lg border border-ink/10 bg-white hover:text-terracotta hover:border-terracotta/20 px-2.5 py-2 text-xs font-semibold text-ink/60 transition-all active:translate-y-px"
        >
          <Prohibit size={13} />
          <span>Bỏ qua</span>
        </button>

        <button
          type="button"
          onClick={handlePrimaryNavigation}
          className="inline-flex items-center justify-center gap-1 rounded-lg bg-ink hover:bg-ink/90 px-3 py-2 text-xs font-bold text-white transition-all active:translate-y-px"
        >
          <span>Chi tiết</span>
          <ArrowRight size={13} />
        </button>
      </div>
    </article>
  )
}

// ─── Tone configuration by ActionType ───

function getActionTone(type: 'EXPIRY_RISK' | 'REORDER' | 'ANOMALY') {
  if (type === 'EXPIRY_RISK') {
    return {
      name: 'Nguy cơ hết hạn',
      label: 'text-red-600',
      surface: 'border-red-500/15 bg-red-500/[0.02]',
      iconSurface: 'bg-red-500/10 text-red-600',
      icon: CalendarBlank,
    }
  }
  if (type === 'REORDER') {
    return {
      name: 'Đề xuất đặt hàng',
      label: 'text-terracotta',
      surface: 'border-terracotta/15 bg-terracotta/[0.02]',
      iconSurface: 'bg-terracotta/10 text-terracotta',
      icon: ClockCountdown,
    }
  }
  return {
    name: 'Bất thường',
    label: 'text-indigo-600',
    surface: 'border-indigo-500/15 bg-indigo-500/[0.02]',
    iconSurface: 'bg-indigo-500/10 text-indigo-600',
    icon: WarningCircle,
  }
}

// ─── Summary Metric Component ───

function SummaryMetric({
  icon: Icon,
  label,
  value,
  detail,
  tone,
  compact = false,
}: {
  icon: ComponentType<{ size?: number; className?: string; weight?: 'duotone' }>
  label: string
  value: string
  detail: string
  tone: 'danger' | 'warning' | 'safe' | 'neutral'
  compact?: boolean
}) {
  const colors = {
    danger: 'text-red-600 bg-red-500/10',
    warning: 'text-terracotta bg-terracotta/10',
    safe: 'text-sage-dark bg-sage/15',
    neutral: 'text-ink bg-ink/5',
  }[tone]

  return (
    <div className="flex items-start gap-3">
      <div className={`mt-0.5 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${colors}`}>
        <Icon size={16} weight="duotone" aria-hidden="true" />
      </div>
      <div className="min-w-0">
        <div className="text-[11px] font-semibold text-ink/50">{label}</div>
        <div className={`${compact ? 'text-base' : 'text-xl'} mt-0.5 truncate font-extrabold text-ink`}>
          {value}
        </div>
        <div className="mt-0.5 text-[10px] leading-relaxed text-ink/45">{detail}</div>
      </div>
    </div>
  )
}
