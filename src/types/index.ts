export interface ApiResponse<T> {
  success: boolean
  data: T
  errorCode?: number
  message?: string
}

export type Role = 'SYSTEM_ADMIN' | 'STORE_OWNER' | 'MANAGER' | 'STAFF'
export type SubscriptionPlan = 'FREE' | 'BASIC' | 'PRO'
export type StoreStatus = 'ACTIVE' | 'SUSPENDED'
export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED'

export interface SubscriptionLimits {
  stores: number | null
  staff: number | null
  ingredients: number | null
}

export interface SubscriptionUsage {
  stores: number
  staff: number
  ingredients: number
}

export interface PlanDefinition {
  plan: SubscriptionPlan
  monthlyPrice: number
  limits: SubscriptionLimits
  features: string[]
}

export interface BillingEntitlements {
  plan: SubscriptionPlan
  expiresAt?: string
  active: boolean
  limits: SubscriptionLimits
  usage: SubscriptionUsage
  enabledFeatures: string[]
  availablePlans: PlanDefinition[]
}

export interface Subscription {
  plan: SubscriptionPlan
  expiresAt?: string
  limits: SubscriptionLimits
}

export interface User {
  id: number
  storeId?: number
  username: string
  email: string
  role: Role
  fullName: string
  status: UserStatus
  mustChangePassword?: boolean
}

export interface SessionUser extends User {
  storeIds: number[]
  ownedStoreIds?: number[]
}

export interface AuthSession {
  currentUser: SessionUser
  currentStore: Store
  stores: Store[]
  accessToken: string
}

export interface AuthResponse {
  accessToken: string
  refreshToken: string
  username: string
  role: Role
  fullName: string
  email: string
  storeIds: number[]
  activeStoreId?: number
}

export interface Store {
  id: number
  name: string
  address: string
  phone?: string
  subscriptionPlan: SubscriptionPlan
  subscriptionExpiresAt?: string
  status: StoreStatus
}

export interface Ingredient {
  id: number
  storeId: number
  code: string
  name: string
  unit: string
  category: string
  minStock: number
  maxStock: number
  unitCost?: number
  active: boolean
}

export interface InventoryBatch {
  id: number
  storeId: number
  ingredientId: number
  batchNumber: string
  quantity: number
  expiredDate: string // YYYY-MM-DD
  importDate: string  // YYYY-MM-DD
  costPerUnit: number
}

export interface StockTransactionItem {
  ingredientId: number
  batchNumber?: string // Tự động tạo khi nhập, hoặc chọn khi xuất
  quantity: number
  expiredDate?: string  // Chỉ dùng khi nhập
  costPerUnit?: number // Chỉ dùng khi nhập
}

export interface StockTransaction {
  id: number
  storeId: number
  type: 'IMPORT' | 'EXPORT'
  reason: 'IMPORT_NEW' | 'EXPORT_CONSUME' | 'EXPORT_WASTE' | 'EXPORT_ADJUST'
  createdAt: string
  recordedBy: string
  items: StockTransactionItem[]
}

export interface WasteRecord {
  id: number
  storeId: number
  ingredientId: number
  batchId?: number
  quantity: number
  reason: 'EXPIRED' | 'DAMAGED' | 'PREP_ERROR' | 'OTHER'
  estimatedCost: number
  recordedBy: string
  createdAt: string
}

export interface WasteDashboard {
  period: 'week' | 'month'
  currentWasteCost: number
  previousWasteCost: number
  changePercent: number
  topWasteIngredients: {
    ingredientId: number
    ingredientName: string
    unit: string
    quantity: number
    estimatedCost: number
  }[]
}

export interface AuditLog {
  id: number
  createdAt: string
  storeId: number
  storeName: string
  ingredientId: number
  ingredientName: string
  batchNumber: string
  action: 'IN' | 'OUT'
  reason: string
  quantity: number
  unit: string
  actorEmail: string
}

export interface IngredientImportResult {
  imported: number
  skipped: number
  errors: string[]
}

export interface Alert {
  id: number
  storeId: number
  type: 'EXPIRY' | 'LOW_STOCK'
  itemId: number // Ingredient ID
  message: string
  status: 'OPEN' | 'RESOLVED'
  createdAt: string
}

export interface Forecast {
  storeId: number
  ingredientId: number
  ingredientName: string
  ingredientCode: string
  unit: string
  avgDailyUsage: number
  currentStock: number
  minStock: number
  recommendedOrder: number
}

export type WasteRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface InventoryInsight {
  storeId: number
  ingredientId: number
  ingredientName: string
  ingredientCode: string
  unit: string
  nearestBatchId?: number
  nearestBatchExpiryDate?: string
  avgDailyUsage7d: number
  avgDailyUsage28d: number
  weekdayAdjustedUsage?: number
  currentStock: number
  daysUntilStockout?: number
  daysUntilExpiry?: number
  wasteRiskLevel: WasteRiskLevel
  recommendedOrderQty: number
  explanationBullets: string[]
  ctaLabel: string
}
