import { createContext, useContext, type ReactNode, useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Store } from '../types'
import { useStoreContextStore } from '../stores/storeContextStore'
import { useSubscriptionStore } from '../stores/subscriptionStore'
import { useAuthStore } from '../stores/authStore'

interface StoreContextValue {
  activeStoreId: number
  activeStore: Store | null
  stores: Store[]
  isLoadingStores: boolean
  switchStore: (id: number) => void
}

const StoreContext = createContext<StoreContextValue | undefined>(undefined)

export function StoreProvider({ children }: { children: ReactNode }) {
  const { activeStoreId, activeStore, stores, setStores, switchStore } = useStoreContextStore()
  const setPlan = useSubscriptionStore((state) => state.setPlan)
  const currentStore = useAuthStore((state) => state.currentStore)
  const availableStores = useAuthStore((state) => state.availableStores)
  const setCurrentAuthStore = useAuthStore((state) => state.setCurrentStore)

  // Đọc danh sách cửa hàng từ API
  const { data: response, isLoading: isLoadingStores } = useQuery({
    queryKey: ['stores'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Store[]>>('/stores')
      return res.data
    }
  })

  useEffect(() => {
    if (response?.data) setStores(response.data)
    else if (availableStores.length > 0) setStores(availableStores)
  }, [availableStores, response?.data, setStores])

  // Tự động gán header x-store-id cho mọi request của apiClient
  useEffect(() => {
    apiClient.defaults.headers.common['x-store-id'] = String(activeStoreId)
    apiClient.defaults.headers.common['storeId'] = String(activeStoreId)
  }, [activeStoreId])

  useEffect(() => {
    if (activeStore) setPlan(activeStore.subscriptionPlan, activeStore.subscriptionExpiresAt)
  }, [activeStore, setPlan])

  function handleSwitchStore(id: number) {
    switchStore(id)
    const nextStore = stores.find((store) => store.id === id)
    if (nextStore) setCurrentAuthStore(nextStore)
  }

  const resolvedActiveStore = activeStore ?? currentStore
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
