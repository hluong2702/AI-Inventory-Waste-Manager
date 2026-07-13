import apiClient from '../api/client'
import type { ApiResponse, BillingEntitlements, SubscriptionPlan } from '../types'

export async function getBillingEntitlements() {
  const res = await apiClient.get<ApiResponse<BillingEntitlements>>('/billing/entitlements')
  return res.data.data
}

export async function changeBillingPlan(plan: SubscriptionPlan) {
  const res = await apiClient.patch<ApiResponse<BillingEntitlements>>('/billing/plan', { plan })
  return res.data.data
}
