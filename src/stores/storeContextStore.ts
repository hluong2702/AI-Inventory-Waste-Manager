import { create } from 'zustand'
import type { Store } from '../types'

interface StoreContextState {
  activeStoreId: number
  activeStore: Store | null
  stores: Store[]
  setStores: (stores: Store[]) => void
  switchStore: (id: number) => void
  reset: () => void
}

export const useStoreContextStore = create<StoreContextState>((set, get) => ({
  activeStoreId: Number(localStorage.getItem('activeStoreId') ?? 1),
  activeStore: null,
  stores: [],
  setStores: (stores) => {
    if (stores.length === 0) {
      localStorage.removeItem('activeStoreId')
      set({ stores: [], activeStoreId: 0, activeStore: null })
      return
    }

    const savedId = Number(localStorage.getItem('activeStoreId') ?? 0)
    const activeStoreId = stores.some((store) => store.id === savedId) ? savedId : stores[0].id
    localStorage.setItem('activeStoreId', String(activeStoreId))
    set({
      stores,
      activeStoreId,
      activeStore: stores.find((store) => store.id === activeStoreId) ?? stores[0],
    })
  },
  switchStore: (id) => {
    const nextStore = get().stores.find((store) => store.id === id)
    if (!nextStore) return

    localStorage.setItem('activeStoreId', String(id))
    set({
      activeStoreId: id,
      activeStore: nextStore,
    })
  },
  reset: () => {
    localStorage.removeItem('activeStoreId')
    set({ activeStoreId: 0, activeStore: null, stores: [] })
  },
}))
