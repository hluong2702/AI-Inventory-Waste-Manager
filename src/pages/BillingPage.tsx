import { CheckCircle, Crown, Sparkle, WarningCircle } from '@phosphor-icons/react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { useSearchParams } from 'react-router-dom'
import DoubleBezelCard from '../components/DoubleBezelCard'
import StateView from '../components/StateView'
import { useSubscriptionStore } from '../stores/subscriptionStore'
import { getBillingEntitlements, upgradeSubscription } from '../services/billingService'
import type { BillingEntitlements, SubscriptionLimits } from '../types'

function formatLimit(value: number | null, label: string) {
  return value === null ? `${label} không giới hạn` : `${value} ${label}`
}

function formatPrice(plan: BillingEntitlements['availablePlans'][number]) {
  if (plan.plan === 'ENTERPRISE') return 'Liên hệ'
  if (plan.monthlyPrice === 0) return '0đ'
  return `${Math.round(plan.monthlyPrice / 1000)}k/tháng`
}

function featureLabel(feature: string) {
  const labels: Record<string, string> = {
    BASIC_ALERTS: 'Cảnh báo tồn kho cơ bản',
    BASIC_REPORTS: 'Báo cáo thất thoát cơ bản',
    BASIC_FORECAST: 'Dự báo nhập hàng cơ bản',
    ADVANCED_FORECAST: 'AI insight nâng cao',
    EXPORT_REPORTS: 'Xuất báo cáo Excel',
    MULTI_STORE: 'Quản lý nhiều chi nhánh',
  }
  return labels[feature] ?? feature
}

function isOverLimit(limits: SubscriptionLimits, usage: BillingEntitlements['usage']) {
  return (
    (limits.stores !== null && usage.stores > limits.stores) ||
    (limits.staff !== null && usage.staff > limits.staff) ||
    (limits.ingredients !== null && usage.ingredients > limits.ingredients)
  )
}

export default function BillingPage() {
  const { current, setEntitlements } = useSubscriptionStore()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const paymentResult = searchParams.get('payment')
  const activeStoreId = localStorage.getItem('activeStoreId')
  const pendingUpgradePlan = localStorage.getItem('pendingUpgradePlan') as BillingEntitlements['plan'] | null

  const { data: entitlements, isLoading, isError } = useQuery<BillingEntitlements>({
    queryKey: ['billing-entitlements', activeStoreId],
    queryFn: getBillingEntitlements,
    refetchInterval: (query) => {
      const latest = query.state.data
      return paymentResult === 'success' && pendingUpgradePlan && latest?.plan !== pendingUpgradePlan ? 2_000 : false
    },
  })

  useEffect(() => {
    if (entitlements) {
      setEntitlements(entitlements)
      if (pendingUpgradePlan === entitlements.plan) {
        sessionStorage.removeItem(`payment-idempotency:${activeStoreId}:${pendingUpgradePlan}`)
        localStorage.removeItem('pendingUpgradePlan')
      }
    }
  }, [activeStoreId, entitlements, pendingUpgradePlan, setEntitlements])

  useEffect(() => {
    if (paymentResult === 'failed' || paymentResult === 'invalid') {
      if (pendingUpgradePlan) {
        sessionStorage.removeItem(`payment-idempotency:${activeStoreId}:${pendingUpgradePlan}`)
      }
      localStorage.removeItem('pendingUpgradePlan')
    }
  }, [activeStoreId, paymentResult, pendingUpgradePlan])

  const changePlanMutation = useMutation({
    mutationFn: (plan: BillingEntitlements['plan']) => {
      const storeId = Number(activeStoreId)
      if (!Number.isSafeInteger(storeId) || storeId <= 0) {
        throw new Error('Không xác định được cửa hàng đang thanh toán.')
      }
      return upgradeSubscription(plan, storeId)
    },
    onSuccess: (result, requestedPlan) => {
      queryClient.invalidateQueries({ queryKey: ['billing-entitlements', activeStoreId] })
      queryClient.invalidateQueries({ queryKey: ['stores'] })
      if (result.paymentUrl) {
        localStorage.setItem('pendingUpgradePlan', requestedPlan)
        window.location.assign(result.paymentUrl)
      }
    },
  })

  const availablePlans = entitlements?.availablePlans ?? []
  const usage = entitlements?.usage

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-2 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Gói dịch vụ</h2>
          <p className="text-xs text-ink/60">
            Gói hiện tại quyết định số lượng cửa hàng, nhân sự và nguyên liệu có thể quản lý.
          </p>
        </div>
        {entitlements && (
          <div className="rounded-xl border border-sage/20 bg-sage/10 px-4 py-3 text-xs font-semibold text-sage-dark">
            Gói hiện tại: {current.plan}
            {current.expiresAt ? ` · hết hạn ${current.expiresAt}` : ''}
          </div>
        )}
      </div>

      {paymentResult === 'success' && (
        <div className="rounded-2xl border border-sage/25 bg-sage/10 p-4 text-sm font-semibold text-sage-dark">
          {pendingUpgradePlan && entitlements?.plan !== pendingUpgradePlan
            ? 'payOS đã chuyển hướng thanh toán thành công. Hệ thống đang chờ webhook xác thực để kích hoạt gói...'
            : 'Thanh toán thành công và gói dịch vụ đã được kích hoạt.'}
        </div>
      )}
      {paymentResult === 'failed' && (
        <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm text-terracotta">
          Thanh toán không thành công hoặc đã bị hủy. Gói hiện tại không thay đổi.
        </div>
      )}
      {paymentResult === 'invalid' && (
        <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm text-terracotta">
          Kết quả thanh toán có chữ ký không hợp lệ. Không có thay đổi nào được áp dụng.
        </div>
      )}

      <StateView
        isLoading={isLoading}
        isError={isError}
        isEmpty={!entitlements || availablePlans.length === 0 || !usage}
        errorMessage="Không tải được thông tin gói từ backend."
        emptyTitle="Chưa có cấu hình gói"
        emptySubtitle="Backend chưa trả về danh sách gói dịch vụ."
      >
        {usage && (
          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            {[
              ['Cửa hàng', usage.stores, current.limits.stores],
              ['Nhân sự', usage.staff, current.limits.staff],
              ['Nguyên liệu', usage.ingredients, current.limits.ingredients],
            ].map(([label, used, limit]) => (
              <div key={label} className="rounded-xl border border-ink/10 bg-white px-4 py-3">
                <p className="text-[11px] font-semibold uppercase tracking-wide text-ink/45">{label}</p>
                <p className="mt-1 text-lg font-bold text-ink">
                  {used} <span className="text-sm font-semibold text-ink/45">/ {limit === null ? 'không giới hạn' : limit}</span>
                </p>
              </div>
            ))}
          </div>
        )}

        <div className="grid grid-cols-1 gap-5 lg:grid-cols-3">
          {availablePlans.map((planDef) => {
            const plan = planDef.plan
            const limits = planDef.limits
            const selected = current.plan === plan
            const currentPrice = availablePlans.find((item) => item.plan === current.plan)?.monthlyPrice ?? 0
            const unsupportedPaidDowngrade = plan !== 'FREE' && planDef.monthlyPrice < currentPrice
            const contactSales = plan === 'ENTERPRISE'
            const blockedByUsage = entitlements ? isOverLimit(limits, entitlements.usage) : false
            const isPending = changePlanMutation.isPending && changePlanMutation.variables === plan
            return (
              <DoubleBezelCard
                key={plan}
                title={plan === 'FREE' ? 'Free' : plan === 'BASIC' ? 'Basic' : plan === 'PRO' ? 'Pro' : 'Enterprise'}
                subtitle={formatPrice(planDef)}
                action={selected ? <CheckCircle size={18} weight="fill" className="text-sage-dark" /> : plan === 'PRO' ? <Crown size={18} className="text-terracotta" /> : null}
              >
                <div className="space-y-4">
                  <ul className="space-y-3 text-sm text-ink/75">
                    <li className="flex items-center gap-2"><CheckCircle size={16} className="text-sage-dark" />{formatLimit(limits.stores, 'Store')}</li>
                    <li className="flex items-center gap-2"><CheckCircle size={16} className="text-sage-dark" />{formatLimit(limits.staff, 'Staff')}</li>
                    <li className="flex items-center gap-2"><CheckCircle size={16} className="text-sage-dark" />{formatLimit(limits.ingredients, 'Ingredients')}</li>
                  </ul>
                  <div className="space-y-2 border-t border-ink/10 pt-3">
                    {planDef.features.map((feature) => (
                      <div key={feature} className="flex items-center gap-2 text-xs font-medium text-ink/60">
                        <Sparkle size={14} className="text-sage-dark" />
                        <span>{featureLabel(feature)}</span>
                      </div>
                    ))}
                  </div>
                  {blockedByUsage && !selected && (
                    <p className="rounded-lg bg-terracotta/10 px-3 py-2 text-xs font-semibold text-terracotta">
                      Usage hiện tại vượt giới hạn gói này. Hãy giảm dữ liệu trước khi hạ gói.
                    </p>
                  )}
                  {unsupportedPaidDowngrade && !selected && (
                    <p className="rounded-lg bg-terracotta/10 px-3 py-2 text-xs font-semibold text-terracotta">
                      Hạ xuống gói trả phí thấp hơn cần checkout mới khi kỳ hiện tại kết thúc.
                    </p>
                  )}
                  <button
                    onClick={() => {
                      if (contactSales) {
                        window.location.assign('mailto:sales@inventoryai.vn?subject=Enterprise%20plan')
                        return
                      }
                      changePlanMutation.mutate(plan)
                    }}
                    disabled={selected || blockedByUsage || unsupportedPaidDowngrade || isLoading || changePlanMutation.isPending}
                    className={`w-full rounded-xl px-4 py-3 text-sm font-bold transition-all active:translate-y-px ${
                      selected || blockedByUsage
                        ? 'border border-ink/10 bg-ink/5 text-ink/40'
                        : 'bg-sage-dark text-white hover:bg-sage'
                    }`}
                  >
                    {selected ? 'Đang sử dụng' : isPending ? 'Đang tạo thanh toán...' : contactSales ? 'Liên hệ tư vấn' : plan === 'FREE' ? 'Chuyển về Free khi hết kỳ' : `Nâng cấp ${plan}`}
                  </button>
                </div>
              </DoubleBezelCard>
            )
          })}
        </div>
      </StateView>

      {changePlanMutation.isError && (
        <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm text-terracotta">
          Không thể khởi tạo thay đổi gói lúc này. Gói hiện tại chỉ được nâng cấp sau khi payment provider xác nhận thanh toán.
        </div>
      )}

      <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm text-terracotta">
        <div className="flex items-start gap-3">
          <WarningCircle size={20} className="mt-0.5 shrink-0" />
          <p>
            Khi chạm giới hạn gói, các màn hình thêm Staff hoặc Ingredient sẽ khóa thao tác tạo mới và hiển thị banner nâng cấp.
          </p>
        </div>
      </div>
    </div>
  )
}
