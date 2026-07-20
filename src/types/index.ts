export interface ApiResponse<T> {
  success: boolean
  data: T
  message?: string
}

export interface ApiErrorResponse {
  code: string
  message: string
  timestamp: string
}

export interface PageResponse<T> {
  content: T[]
  number: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export type Role = 'SYSTEM_ADMIN' | 'STORE_OWNER' | 'MANAGER' | 'STAFF'
export type BackendRole = 'SYSTEM_ADMIN' | 'OWNER' | 'MANAGER' | 'STAFF'
export type SubscriptionPlan = 'FREE' | 'BASIC' | 'PRO' | 'ENTERPRISE'
export type StoreStatus = 'ACTIVE' | 'SUSPENDED'
export type UserStatus = 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED'
export type AlertType = 'LOW_STOCK' | 'EXPIRING_SOON'

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
  expiresAt: string | null
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
}

export interface Store {
  id: number
  name: string
  address: string | null
  phone: string | null
  subscriptionPlan: SubscriptionPlan
  subscriptionExpiresAt?: string
  status: StoreStatus
}

export interface AdminStore {
  id: number
  name: string
  address: string | null
  phone: string | null
  subscriptionPlan: SubscriptionPlan
  status: StoreStatus
  createdAt: string
}

export interface AdminUser {
  id: number
  storeId: number | null
  username: string
  email: string
  fullName: string
  // Keep the open string branch so unexpected future backend roles render via fallback.
  role: BackendRole | (string & {})
  status: UserStatus
  memberships: {
    storeId: number
    storeName: string
    role: Exclude<BackendRole, 'SYSTEM_ADMIN'>
    status: UserStatus
  }[]
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
  ingredientName: string
  ingredientUnit: string
  ingredientCategory: string
  batchNumber: string
  quantity: number
  expiredDate: string // YYYY-MM-DD
  importDate: string  // YYYY-MM-DD
  costPerUnit: number
}

export interface InventorySummary {
  ingredientId: number
  code: string
  name: string
  unit: string
  category: string
  minStock: number
  maxStock: number
  totalQuantity: number
  sellableQuantity: number
  activeBatchesCount: number
  expiredBatchesCount: number
  expiringSoonBatchesCount: number
}

export interface InventoryTransactionRequestItem {
  ingredientId: number
  batchNumber?: string // Tự động tạo khi nhập, hoặc chọn khi xuất
  quantity: number
  expiredDate?: string  // Chỉ dùng khi nhập
  costPerUnit?: number // Chỉ dùng khi nhập
}

export interface StockTransactionItem {
  ingredientId: number
  batchNumber: string
  batchId: number | null
  quantity: number
  expiredDate: string | null
  costPerUnit: number | null
}

export type InventoryTransactionType = 'IMPORT' | 'EXPORT'
export type InventoryTransactionReason = 'IMPORT_NEW' | 'EXPORT_CONSUME' | 'EXPORT_WASTE' | 'EXPORT_ADJUST'
export type WasteReason = 'EXPIRED' | 'DAMAGED' | 'PREP_ERROR' | 'OTHER'

export interface CreateInventoryTransactionRequest {
  type: InventoryTransactionType
  reason: InventoryTransactionReason
  wasteReason?: WasteReason
  items: InventoryTransactionRequestItem[]
}

export interface StockTransaction {
  id: number
  storeId: number
  type: 'IMPORT' | 'EXPORT'
  reason: 'IMPORT_NEW' | 'EXPORT_CONSUME' | 'EXPORT_WASTE' | 'EXPORT_ADJUST'
  createdAt: string
  recordedBy: string | null
  items: StockTransactionItem[]
}

export interface WasteRecord {
  id: number
  storeId: number
  ingredientId: number
  ingredientName: string
  ingredientUnit: string
  batchId: number | null
  quantity: number
  reason: WasteReason
  estimatedCost: number
  recordedBy: string | null
  createdAt: string
}

export interface WasteReportSummary {
  startDate: string
  endDate: string
  totalWasteCost: number
  totalQuantity: number
  recordCount: number
  affectedIngredientCount: number
  reasonBreakdown: {
    reason: string
    estimatedCost: number
    quantity: number
    recordCount: number
  }[]
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
  type: AlertType
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

export interface DailyForecastPoint {
  date: string
  predictedDemand: number
  lowerBound: number
  upperBound: number
  weatherCondition: string
  temperatureMax: number | null
  rainMm: number | null
  weatherFactor: number
  isHoliday: boolean
  isWeekend: boolean
}

export interface AiForecast {
  storeId: number
  ingredientId: number
  ingredientName: string
  ingredientCode: string
  unit: string
  totalPredictedDemand: number
  avgDailyPredicted: number
  currentStock: number
  minStock: number
  aiRecommendedOrder: number
  dailyBreakdown: DailyForecastPoint[]
  modelUsed: 'prophet' | 'moving_average'
  historyDaysUsed: number
  modelAccuracyMape: number | null
  confidenceNote: string
  isJavaFallback: boolean
}

export type WasteRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface InventoryInsight {
  storeId: number
  ingredientId: number
  ingredientName: string
  ingredientCode: string
  unit: string
  nearestBatchId: number | null
  nearestBatchExpiryDate: string | null
  avgDailyUsage7d: number
  avgDailyUsage28d: number
  weekdayAdjustedUsage: number | null
  currentStock: number
  daysUntilStockout: number | null
  daysUntilExpiry: number | null
  wasteRiskLevel: WasteRiskLevel
  recommendedOrderQty: number
  explanationBullets: string[]
  ctaLabel: string
}

export interface DashboardData {
  periodStart: string
  periodEnd: string
  ingredientCount: number
  openAlertCount: number
  canResolveAlerts: boolean
  canViewForecast: boolean
  reportsAvailable: boolean
  waste: {
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
    dailyCosts: {
      date: string
      estimatedCost: number
    }[]
  } | null
  insightsAvailable: boolean
  insights: {
    totalCount: number
    highRiskCount: number
    suggestedOrderCount: number
    healthyCount: number
    topInsights: InventoryInsight[]
  } | null
  nearExpiryBatches: {
    id: number
    ingredientId: number
    ingredientName: string
    ingredientUnit: string
    batchNumber: string
    quantity: number
    expiryDate: string
    daysUntilExpiry: number
    costPerUnit: number
  }[]
  openAlerts: {
    id: number
    type: AlertType
    ingredientId: number
    ingredientName: string
    message: string
    createdAt: string
  }[]
}

export interface RecipeIngredientItem {
  ingredientId: number
  ingredientCode: string
  ingredientName: string
  unit: string
  quantity: number
}

export interface Recipe {
  id: number
  storeId: number
  code: string
  name: string
  price: number
  active: boolean
  ingredients: RecipeIngredientItem[]
  createdAt: string
}

export interface CreateRecipeRequest {
  code: string
  name: string
  price: number
  active: boolean
  ingredients: {
    ingredientId: number
    quantity: number
  }[]
}

export type DailyActionType = 'EXPIRY_RISK' | 'REORDER' | 'ANOMALY'
export type DailyActionStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED'

export interface BackendDailyAction {
  id: number
  tenantId: number
  actionType: DailyActionType
  productId: number
  productName: string
  productCode: string
  productUnit: string
  batchId: number | null
  batchNumber: string | null
  expiryDate: string | null
  title: string
  description: string
  riskQtyMin: number | null
  riskQtyMax: number | null
  riskValueEstimate: number | null
  priorityScore: number
  status: DailyActionStatus
  computedAt: string
  expiresAt: string | null
  acknowledgedAt: string | null
  resolvedAt: string | null
  dismissedAt: string | null
  dismissReason: string | null
  createdAt: string
}

