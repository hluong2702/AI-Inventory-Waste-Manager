import { create } from 'zustand'
import type { AuthResponse, AuthSession, Role, SessionUser, Store } from '../types'

const AUTH_SESSION_KEY = 'inventoryai_auth_session_v1'

interface AuthState {
  currentUser: SessionUser | null
  currentStore: Store | null
  availableStores: Store[]
  username: string | null
  email: string | null
  role: Role | null
  fullName: string | null
  storeIds: number[]
  mustChangePassword: boolean
  isAuthenticated: boolean
  hydrate: () => void
  setAuth: (auth: AuthResponse) => void
  setSession: (session: AuthSession) => void
  setCurrentStore: (store: Store) => void
  clearAuth: () => void
}

function readSession(): AuthSession | null {
  const raw = localStorage.getItem(AUTH_SESSION_KEY)
  return raw ? (JSON.parse(raw) as AuthSession) : null
}

const savedSession = readSession()

export const useAuthStore = create<AuthState>((set) => ({
  currentUser: savedSession?.currentUser ?? null,
  currentStore: savedSession?.currentStore ?? null,
  availableStores: savedSession?.stores ?? [],
  username: savedSession?.currentUser.username ?? localStorage.getItem('username'),
  email: savedSession?.currentUser.email ?? localStorage.getItem('email'),
  role: savedSession?.currentUser.role ?? (localStorage.getItem('role') as Role | null),
  fullName: savedSession?.currentUser.fullName ?? localStorage.getItem('fullName'),
  storeIds: savedSession?.currentUser.storeIds ?? (JSON.parse(localStorage.getItem('storeIds') ?? '[]') as number[]),
  mustChangePassword: savedSession?.currentUser.mustChangePassword ?? false,
  isAuthenticated: !!savedSession || !!localStorage.getItem('accessToken'),
  hydrate: () => {
    const session = readSession()
    set({
      currentUser: session?.currentUser ?? null,
      currentStore: session?.currentStore ?? null,
      availableStores: session?.stores ?? [],
      username: session?.currentUser.username ?? localStorage.getItem('username'),
      email: session?.currentUser.email ?? localStorage.getItem('email'),
      role: session?.currentUser.role ?? (localStorage.getItem('role') as Role | null),
      fullName: session?.currentUser.fullName ?? localStorage.getItem('fullName'),
      storeIds: session?.currentUser.storeIds ?? (JSON.parse(localStorage.getItem('storeIds') ?? '[]') as number[]),
      mustChangePassword: session?.currentUser.mustChangePassword ?? false,
      isAuthenticated: !!session || !!localStorage.getItem('accessToken'),
    })
  },
  setAuth: (auth) => {
    localStorage.setItem('accessToken', auth.accessToken)
    localStorage.setItem('refreshToken', auth.refreshToken)
    localStorage.setItem('username', auth.username)
    localStorage.setItem('email', auth.email)
    localStorage.setItem('role', auth.role)
    localStorage.setItem('fullName', auth.fullName)
    localStorage.setItem('storeIds', JSON.stringify(auth.storeIds))
    if (auth.activeStoreId) {
      localStorage.setItem('activeStoreId', String(auth.activeStoreId))
    }
    set({
      username: auth.username,
      email: auth.email,
      role: auth.role,
      fullName: auth.fullName,
      storeIds: auth.storeIds,
      mustChangePassword: false,
      isAuthenticated: true,
    })
  },
  setSession: (session) => {
    localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(session))
    localStorage.setItem('accessToken', session.accessToken)
    localStorage.setItem('username', session.currentUser.username)
    localStorage.setItem('email', session.currentUser.email)
    localStorage.setItem('role', session.currentUser.role)
    localStorage.setItem('fullName', session.currentUser.fullName)
    localStorage.setItem('storeIds', JSON.stringify(session.currentUser.storeIds))
    localStorage.setItem('activeStoreId', String(session.currentStore.id))
    set({
      currentUser: session.currentUser,
      currentStore: session.currentStore,
      availableStores: session.stores,
      username: session.currentUser.username,
      email: session.currentUser.email,
      role: session.currentUser.role,
      fullName: session.currentUser.fullName,
      storeIds: session.currentUser.storeIds,
      mustChangePassword: !!session.currentUser.mustChangePassword,
      isAuthenticated: true,
    })
  },
  setCurrentStore: (store) => {
    const session = readSession()
    if (session) {
      const nextSession = { ...session, currentStore: store }
      localStorage.setItem(AUTH_SESSION_KEY, JSON.stringify(nextSession))
    }
    localStorage.setItem('activeStoreId', String(store.id))
    set({ currentStore: store })
  },
  clearAuth: () => {
    localStorage.removeItem(AUTH_SESSION_KEY)
    localStorage.removeItem('accessToken')
    localStorage.removeItem('refreshToken')
    localStorage.removeItem('username')
    localStorage.removeItem('email')
    localStorage.removeItem('role')
    localStorage.removeItem('fullName')
    localStorage.removeItem('storeIds')
    localStorage.removeItem('activeStoreId')
    set({
      currentUser: null,
      currentStore: null,
      availableStores: [],
      username: null,
      email: null,
      role: null,
      fullName: null,
      storeIds: [],
      mustChangePassword: false,
      isAuthenticated: false,
    })
  },
}))
