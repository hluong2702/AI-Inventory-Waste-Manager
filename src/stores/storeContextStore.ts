import { create } from 'zustand'
import type { Store } from '../types'

interface StoreContextState {
  activeStoreId: number
  activeStore: Store | null
  stores: Store[]
  setStores: (stores: Store[]) => void
  switchStore: (id: number) => void
}

export const useStoreContextStore = create<StoreContextState>((set, get) => ({
  activeStoreId: Number(localStorage.getItem('activeStoreId') ?? 1),
  activeStore: null,
  stores: [],
  setStores: (stores) => {
    const savedId = Number(localStorage.getItem('activeStoreId') ?? 0)
    const activeStoreId = stores.some((store) => store.id === savedId) ? savedId : stores[0]?.id ?? get().activeStoreId
    if (activeStoreId) localStorage.setItem('activeStoreId', String(activeStoreId))
    set({
      stores,
      activeStoreId,
      activeStore: stores.find((store) => store.id === activeStoreId) ?? stores[0] ?? null,
    })
  },
  switchStore: (id) => {
    localStorage.setItem('activeStoreId', String(id))
    set((state) => ({
      activeStoreId: id,
      activeStore: state.stores.find((store) => store.id === id) ?? null,
    }))
  },
}))
