import type { ComponentType } from 'react'
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
} from '@phosphor-icons/react'
import type { DashboardData, InventoryInsight } from '../types'
import { formatVND } from '../utils/fefo'

type ActionKind = 'EXPIRY' | 'STOCKOUT' | 'OVERSTOCK' | 'MONITOR'

interface DailyAction {
  insight: InventoryInsight
  kind: ActionKind
  title: string
  reason: string
  impact: string
  route: string
  actionLabel: string
  urgency: number
}

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
  const topInsights = insights?.topInsights ?? []
  const actions = topInsights
    .map((insight) => buildAction(insight, canViewForecast))
    .sort((a, b) => b.urgency - a.urgency)
  const priorityActions = actions.filter((action) => action.kind !== 'MONITOR').slice(0, 4)
  const highRiskCount = insights?.highRiskCount ?? 0
  const suggestedOrderCount = insights?.suggestedOrderCount ?? 0
  const healthyCount = insights?.healthyCount ?? 0
  const totalInsightCount = insights?.totalCount ?? 0
  const today = new Intl.DateTimeFormat('vi-VN', {
    weekday: 'long',
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
  }).format(new Date())

  return (
    <section aria-labelledby="daily-action-title" className="overflow-hidden rounded-2xl border border-ink/10 bg-white shadow-[0_18px_50px_rgba(58,58,52,0.07)]">
      <div className="grid gap-0 lg:grid-cols-[minmax(0,1.55fr)_minmax(280px,0.65fr)]">
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
            <time className="shrink-0 text-xs font-semibold capitalize text-ink/50" dateTime={new Date().toISOString().slice(0, 10)}>
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
          ) : totalInsightCount === 0 ? (
            <div className="mt-7 flex min-h-44 flex-col items-center justify-center rounded-xl border border-dashed border-ink/15 bg-offwhite/40 px-6 text-center">
              <Package size={30} className="text-ink/45" weight="duotone" aria-hidden="true" />
              <h3 className="mt-3 text-sm font-bold text-ink">Chưa có nguyên liệu để phân tích</h3>
              <p className="mt-1 max-w-md text-xs leading-relaxed text-ink/55">Thêm nguyên liệu và giao dịch kho để hệ thống tạo đề xuất.</p>
            </div>
          ) : priorityActions.length > 0 ? (
            <div className="mt-7 space-y-3">
              {priorityActions.map((action, index) => (
                <ActionRow
                  key={action.insight.ingredientId}
                  action={action}
                  rank={index + 1}
                  onClick={() => onNavigate(action.route)}
                />
              ))}
            </div>
          ) : (
            <div className="mt-7 flex min-h-44 flex-col items-center justify-center rounded-xl border border-dashed border-sage/35 bg-sage/5 px-6 text-center">
              <ShieldCheck size={30} className="text-sage-dark" weight="duotone" aria-hidden="true" />
              <h3 className="mt-3 text-sm font-bold text-ink">Kho đang trong vùng an toàn</h3>
              <p className="mt-1 max-w-md text-xs leading-relaxed text-ink/55">
                Chưa có hành động khẩn cấp. Hệ thống vẫn tiếp tục theo dõi mức tiêu thụ và các lô gần hạn.
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

        <aside className="border-t border-ink/10 bg-offwhite/55 p-5 sm:p-7 lg:border-l lg:border-t-0" aria-label="Tóm tắt vận hành hôm nay">
          <h3 className="text-sm font-bold text-ink">Tình trạng hôm nay</h3>
          <div className="mt-5 grid grid-cols-2 gap-x-5 gap-y-6 lg:grid-cols-1">
            <SummaryMetric icon={WarningCircle} label="Rủi ro cao" value={insightsAvailable ? String(highRiskCount) : '—'} detail={`${openAlerts} cảnh báo đang mở`} tone="danger" />
            <SummaryMetric icon={Package} label="Cần lập đơn nhập" value={insightsAvailable ? String(suggestedOrderCount) : '—'} detail="Theo mục tiêu đủ 7 ngày" tone="warning" />
            <SummaryMetric icon={ShieldCheck} label="Đang an toàn" value={insightsAvailable ? String(healthyCount) : '—'} detail={insightsAvailable ? `Trên tổng ${totalInsightCount} nguyên liệu` : 'Chưa có quyền phân tích'} tone="safe" />
            <SummaryMetric icon={ChartLineUp} label="Lãng phí tháng" value={monthlyWasteCost === null ? '—' : formatVND(monthlyWasteCost)} detail={monthlyWasteCost === null ? 'Chỉ Owner/Manager được xem' : 'Chi phí đã ghi nhận'} tone="neutral" compact />
          </div>

          <button
            type="button"
            onClick={() => onNavigate(canViewForecast && insightsAvailable ? '/forecast' : '/inventory')}
            className="mt-7 inline-flex w-full items-center justify-center gap-2 rounded-xl bg-ink px-4 py-3 text-xs font-bold text-offwhite transition-colors hover:bg-ink/90 active:translate-y-px"
          >
            {canViewForecast && insightsAvailable ? 'Xem toàn bộ dự báo' : 'Xem tồn kho thực tế'} <ArrowRight size={14} aria-hidden="true" />
          </button>
        </aside>
      </div>
    </section>
  )
}

function ActionRow({ action, rank, onClick }: { action: DailyAction; rank: number; onClick: () => void }) {
  const tone = actionTone(action.kind)
  const Icon = tone.icon

  return (
    <article className={`group grid gap-3 rounded-xl border p-4 transition-shadow hover:shadow-sm sm:grid-cols-[auto_minmax(0,1fr)_auto] sm:items-center ${tone.surface}`}>
      <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-lg ${tone.iconSurface}`}>
        <Icon size={19} weight="duotone" aria-hidden="true" />
      </div>
      <div className="min-w-0">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <span className="text-[10px] font-extrabold text-ink/40">Ưu tiên {rank}</span>
          <span className={`text-[10px] font-extrabold ${tone.label}`}>{tone.name}</span>
        </div>
        <h3 className="mt-1 text-sm font-bold text-ink">{action.insight.ingredientName}: {action.title}</h3>
        <p className="mt-1 text-xs leading-relaxed text-ink/60">{action.reason}</p>
        <p className={`mt-1.5 text-xs font-bold ${tone.label}`}>{action.impact}</p>
      </div>
      <button
        type="button"
        onClick={onClick}
        aria-label={`${action.actionLabel}: ${action.insight.ingredientName}`}
        className="inline-flex w-full items-center justify-center gap-1.5 rounded-lg border border-ink/10 bg-white px-3 py-2 text-xs font-bold text-ink transition-colors hover:border-sage-dark/30 hover:text-sage-dark active:translate-y-px sm:w-auto"
      >
        {action.actionLabel} <ArrowRight size={13} aria-hidden="true" />
      </button>
    </article>
  )
}

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
        <div className={`${compact ? 'text-base' : 'text-xl'} mt-0.5 truncate font-extrabold text-ink`}>{value}</div>
        <div className="mt-0.5 text-[10px] leading-relaxed text-ink/45">{detail}</div>
      </div>
    </div>
  )
}

function buildAction(insight: InventoryInsight, canViewForecast: boolean): DailyAction {
  if (insight.daysUntilExpiry !== null && insight.daysUntilExpiry <= 7) {
    return {
      insight,
      kind: 'EXPIRY',
      title: insight.daysUntilExpiry < 0 ? 'có lô đã hết hạn' : `có lô hết hạn sau ${insight.daysUntilExpiry} ngày`,
      reason: `Tồn ${formatNumber(insight.currentStock)} ${insight.unit}, mức dùng gần đây ${formatNumber(Math.max(insight.avgDailyUsage7d, insight.avgDailyUsage28d))} ${insight.unit}/ngày.`,
      impact: 'Ưu tiên dùng lô gần hạn trước để giảm nguy cơ lãng phí.',
      route: '/inventory',
      actionLabel: 'Xem lô hàng',
      urgency: 300 - insight.daysUntilExpiry,
    }
  }

  if (insight.recommendedOrderQty > 0 || (insight.daysUntilStockout !== null && insight.daysUntilStockout <= 7)) {
    return {
      insight,
      kind: 'STOCKOUT',
      title: insight.daysUntilStockout !== null ? `có thể cạn sau ${formatNumber(insight.daysUntilStockout)} ngày` : 'đang thấp hơn mức an toàn',
      reason: `Tồn ${formatNumber(insight.currentStock)} ${insight.unit}, đề xuất bổ sung ${formatNumber(insight.recommendedOrderQty)} ${insight.unit}.`,
      impact: 'Lập đề nghị nhập để tránh gián đoạn vận hành.',
      route: canViewForecast ? '/forecast' : '/inventory',
      actionLabel: canViewForecast ? 'Xem đề xuất' : 'Xem tồn kho',
      urgency: 200 - (insight.daysUntilStockout ?? 7),
    }
  }

  if (insight.wasteRiskLevel === 'HIGH') {
    return {
      insight,
      kind: 'OVERSTOCK',
      title: 'có dấu hiệu tồn dư kéo dài',
      reason: `Tồn hiện tại ${formatNumber(insight.currentStock)} ${insight.unit}, cao hơn nhu cầu tiêu thụ quan sát được.`,
      impact: 'Tạm dừng nhập và kiểm tra kế hoạch sử dụng.',
      route: '/inventory',
      actionLabel: 'Kiểm tra tồn',
      urgency: 100,
    }
  }

  return {
    insight,
    kind: 'MONITOR',
    title: 'đang trong vùng an toàn',
    reason: insight.explanationBullets[0] ?? 'Chưa phát hiện rủi ro cần xử lý.',
    impact: 'Tiếp tục theo dõi.',
    route: '/inventory',
    actionLabel: 'Xem tồn kho',
    urgency: 0,
  }
}

function actionTone(kind: ActionKind) {
  if (kind === 'EXPIRY') {
    return { name: 'Cận hạn', label: 'text-red-600', surface: 'border-red-500/15 bg-red-500/[0.035]', iconSurface: 'bg-red-500/10 text-red-600', icon: CalendarBlank }
  }
  if (kind === 'STOCKOUT') {
    return { name: 'Thiếu hàng', label: 'text-terracotta', surface: 'border-terracotta/20 bg-terracotta/[0.04]', iconSurface: 'bg-terracotta/10 text-terracotta', icon: ClockCountdown }
  }
  if (kind === 'OVERSTOCK') {
    return { name: 'Tồn dư', label: 'text-terracotta', surface: 'border-terracotta/20 bg-terracotta/[0.04]', iconSurface: 'bg-terracotta/10 text-terracotta', icon: Package }
  }
  return { name: 'Ổn định', label: 'text-sage-dark', surface: 'border-sage/20 bg-sage/[0.04]', iconSurface: 'bg-sage/15 text-sage-dark', icon: CheckCircle }
}

function formatNumber(value: number) {
  return new Intl.NumberFormat('vi-VN', { maximumFractionDigits: 1 }).format(value)
}
