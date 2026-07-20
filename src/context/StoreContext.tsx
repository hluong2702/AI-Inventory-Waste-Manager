import { createContext, useContext, type ReactNode, useEffect } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Store } from '../types'
import { useStoreContextStore } from '../stores/storeContextStore'
import { useSubscriptionStore } from '../stores/subscriptionStore'
import { useAuthStore } from '../stores/authStore'
import { getBillingEntitlements } from '../services/billingService'

interface StoreContextValue {
  activeStoreId: number
  activeStore: Store | null
  stores: Store[]
  isLoadingStores: boolean
  switchStore: (id: number) => Promise<void>
}

const StoreContext = createContext<StoreContextValue | undefined>(undefined)

export function StoreProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { activeStoreId, activeStore, stores, setStores, switchStore } = useStoreContextStore()
  const setPlan = useSubscriptionStore((state) => state.setPlan)
  const setEntitlements = useSubscriptionStore((state) => state.setEntitlements)
  const storedEntitlements = useSubscriptionStore((state) => state.entitlements)
  const currentStore = useAuthStore((state) => state.currentStore)
  const currentUser = useAuthStore((state) => state.currentUser)
  const isAuthenticated = useAuthStore((state) => state.isAuthenticated)
  const availableStores = useAuthStore((state) => state.availableStores)
  const setCurrentAuthStore = useAuthStore((state) => state.setCurrentStore)

  // Đọc danh sách cửa hàng từ API
  const { data: response, isLoading: isLoadingStores } = useQuery({
    queryKey: ['stores', currentUser?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Store[]>>('/stores')
      return res.data
    },
    enabled: isAuthenticated && currentUser !== null && currentUser.role !== 'SYSTEM_ADMIN',
  })

  const canReadEntitlements = currentUser?.role === 'STORE_OWNER' || currentUser?.role === 'MANAGER'
  const activeStoreIsAvailable = stores.some((store) => store.id === activeStoreId)
    || availableStores.some((store) => store.id === activeStoreId)
  const { data: billingEntitlements } = useQuery({
    queryKey: ['billing-entitlements', String(activeStoreId)],
    queryFn: getBillingEntitlements,
    enabled: isAuthenticated && canReadEntitlements && activeStoreId > 0 && activeStoreIsAvailable,
  })

  useEffect(() => {
    if (response?.data) setStores(response.data)
    else if (availableStores.length > 0) setStores(availableStores)
  }, [availableStores, response?.data, setStores])

  // Tự động gán header x-store-id cho mọi request của apiClient
  useEffect(() => {
    if (activeStoreId > 0) {
      apiClient.defaults.headers.common['x-store-id'] = String(activeStoreId)
      apiClient.defaults.headers.common['storeId'] = String(activeStoreId)
    } else {
      delete apiClient.defaults.headers.common['x-store-id']
      delete apiClient.defaults.headers.common.storeId
    }
  }, [activeStoreId])

  useEffect(() => {
    if (activeStore && (!storedEntitlements || storedEntitlements.plan !== activeStore.subscriptionPlan)) {
      setPlan(activeStore.subscriptionPlan, activeStore.subscriptionExpiresAt)
    }
  }, [activeStore, setPlan, storedEntitlements])

  useEffect(() => {
    if (billingEntitlements) setEntitlements(billingEntitlements)
  }, [billingEntitlements, setEntitlements])

  async function handleSwitchStore(id: number) {
    const nextStore = stores.find((store) => store.id === id)
    if (!nextStore) return

    await queryClient.cancelQueries({
      predicate: (query) => query.queryKey[0] !== 'stores',
    })
    queryClient.removeQueries({
      predicate: (query) => query.queryKey[0] !== 'stores',
    })
    useSubscriptionStore.getState().reset()
    switchStore(id)
    setCurrentAuthStore(nextStore)
  }

  const resolvedActiveStore = activeStore && stores.some((store) => store.id === activeStore.id)
    ? activeStore
    : currentStore
  const resolvedStores = stores.length > 0 ? stores : availableStores

  return (
    <StoreContext.Provider value={{ activeStoreId, activeStore: resolvedActiveStore, stores: resolvedStores, isLoadingStores, switchStore: handleSwitchStore }}>
      {children}
    </StoreContext.Provider>
  )
}

export function useStore() {
  const ctx = useContext(StoreContext)
  if (!ctx) throw new Error('useStore phải được sử dụng bên trong StoreProvider')
  return ctx
}
