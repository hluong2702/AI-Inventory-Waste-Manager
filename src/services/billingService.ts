import apiClient from '../api/client'
import type { ApiResponse, BillingEntitlements, SubscriptionPlan } from '../types'

export interface UpgradeSubscriptionResponse {
  subscriptionId: number
  paymentTransactionId: number | null
  subscriptionStatus: 'PENDING_PAYMENT' | 'ACTIVE' | 'CANCELLED' | 'EXPIRED'
  paymentStatus: 'CREATING' | 'CREATION_RECONCILING' | 'PENDING' | 'RECONCILING' | 'SUCCESS' | 'FAILED' | 'CANCELLED' | 'EXPIRED' | 'REVIEW_REQUIRED' | 'REFUNDED' | null
  amount: number
  currency: string
  provider: string
  providerTransactionId: string | null
  paymentUrl: string | null
}

export async function getBillingEntitlements() {
  const res = await apiClient.get<ApiResponse<BillingEntitlements>>('/billing/entitlements')
  return res.data.data
}

export async function upgradeSubscription(plan: SubscriptionPlan, storeId: number) {
  const storageKey = `payment-idempotency:${storeId}:${plan}`
  const idempotencyKey = sessionStorage.getItem(storageKey) ?? crypto.randomUUID()
  sessionStorage.setItem(storageKey, idempotencyKey)
  const res = await apiClient.post<ApiResponse<UpgradeSubscriptionResponse>>(
    '/subscription/upgrade',
    {
      targetPlan: plan,
      paymentProvider: import.meta.env.VITE_PAYMENT_PROVIDER ?? 'PAYOS',
      paymentMethod: import.meta.env.VITE_PAYMENT_METHOD ?? 'BANK_TRANSFER',
    },
    { headers: { 'Idempotency-Key': idempotencyKey } },
  )
  const payment = res.data.data
  if (payment.paymentStatus && ['FAILED', 'CANCELLED', 'EXPIRED', 'REVIEW_REQUIRED', 'REFUNDED'].includes(payment.paymentStatus)) {
    sessionStorage.removeItem(storageKey)
  }
  return payment
}
