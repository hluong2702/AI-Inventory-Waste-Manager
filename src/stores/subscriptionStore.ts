import { create } from 'zustand'
import type { BillingEntitlements, Subscription, SubscriptionLimits, SubscriptionPlan } from '../types'

const UNKNOWN_LIMITS: SubscriptionLimits = { stores: null, staff: null, ingredients: null }

interface SubscriptionState {
  current: Subscription
  entitlements: BillingEntitlements | null
  limitBanner: string | null
  setPlan: (plan: SubscriptionPlan, expiresAt?: string) => void
  setEntitlements: (entitlements: BillingEntitlements) => void
  setLimitBanner: (message: string | null) => void
  isAtLimit: (kind: keyof SubscriptionLimits, currentCount: number) => boolean
  reset: () => void
}

export const useSubscriptionStore = create<SubscriptionState>((set, get) => ({
  current: {
    plan: (localStorage.getItem('subscriptionPlan') as SubscriptionPlan | null) ?? 'FREE',
    expiresAt: localStorage.getItem('subscriptionExpiresAt') ?? undefined,
    limits: UNKNOWN_LIMITS,
  },
  entitlements: null,
  limitBanner: null,
  setPlan: (plan, expiresAt) => {
    localStorage.setItem('subscriptionPlan', plan)
    if (expiresAt) localStorage.setItem('subscriptionExpiresAt', expiresAt)
    else localStorage.removeItem('subscriptionExpiresAt')
    set({
      current: { plan, expiresAt, limits: UNKNOWN_LIMITS },
      entitlements: null,
      limitBanner: null,
    })
  },
  setEntitlements: (entitlements) => {
    const expiresAt = entitlements.expiresAt ?? undefined
    localStorage.setItem('subscriptionPlan', entitlements.plan)
    if (expiresAt) localStorage.setItem('subscriptionExpiresAt', expiresAt)
    else localStorage.removeItem('subscriptionExpiresAt')
    set({
      entitlements,
      current: {
        plan: entitlements.plan,
        expiresAt,
        limits: entitlements.limits,
      },
    })
  },
  setLimitBanner: (message) => set({ limitBanner: message }),
  isAtLimit: (kind, currentCount) => {
    const limit = get().current.limits[kind]
    return typeof limit === 'number' && currentCount >= limit
  },
  reset: () => {
    localStorage.removeItem('subscriptionPlan')
    localStorage.removeItem('subscriptionExpiresAt')
    localStorage.removeItem('pendingUpgradePlan')
    set({
      current: { plan: 'FREE', limits: UNKNOWN_LIMITS },
      entitlements: null,
      limitBanner: null,
    })
  },
}))
