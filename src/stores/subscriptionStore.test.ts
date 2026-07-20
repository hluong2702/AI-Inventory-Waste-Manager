import { beforeEach, describe, expect, it } from 'vitest'
import type { BillingEntitlements } from '../types'
import { useSubscriptionStore } from './subscriptionStore'

const entitlements: BillingEntitlements = {
  plan: 'PRO',
  expiresAt: null,
  active: true,
  limits: { stores: null, staff: 10, ingredients: 500 },
  usage: { stores: 2, staff: 10, ingredients: 125 },
  enabledFeatures: ['EXPORT_REPORTS'],
  availablePlans: [],
}

describe('subscription store', () => {
  beforeEach(() => {
    localStorage.clear()
    useSubscriptionStore.getState().reset()
  })

  it('normalizes nullable expiry without persisting a stale value', () => {
    localStorage.setItem('subscriptionExpiresAt', '2026-08-01')

    useSubscriptionStore.getState().setEntitlements(entitlements)

    expect(useSubscriptionStore.getState().current.expiresAt).toBeUndefined()
    expect(localStorage.getItem('subscriptionExpiresAt')).toBeNull()
  })

  it('enforces finite limits and treats null as unlimited', () => {
    useSubscriptionStore.getState().setEntitlements(entitlements)

    expect(useSubscriptionStore.getState().isAtLimit('staff', 10)).toBe(true)
    expect(useSubscriptionStore.getState().isAtLimit('stores', 999)).toBe(false)
  })
})
