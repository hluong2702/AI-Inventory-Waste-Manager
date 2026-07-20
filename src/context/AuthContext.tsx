import { createContext, useContext, type ReactNode } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import type { Role } from '../types'
import { loginWithPassword, logoutSession, registerOwner } from '../services/authService'
import type { RegistrationResult } from '../services/authService'
import { useAuthStore } from '../stores/authStore'
import { useStoreContextStore } from '../stores/storeContextStore'
import { useSubscriptionStore } from '../stores/subscriptionStore'

interface AuthContextValue {
  username: string | null
  role: Role | null
  fullName: string | null
  isAuthenticated: boolean
  login: (email: string, password: string) => Promise<void>
  register: (data: {
    storeName: string
    email: string
    password: string
  }) => Promise<RegistrationResult>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const { username, role, fullName, isAuthenticated, setSession, clearAuth } = useAuthStore()

  async function replaceSession(session: Awaited<ReturnType<typeof loginWithPassword>>) {
    // A request from the previous account must never populate the next account's cache.
    await queryClient.cancelQueries()
    useStoreContextStore.getState().reset()
    useSubscriptionStore.getState().reset()
    setSession(session)
    useStoreContextStore.getState().setStores(session.stores)
    queryClient.clear()
  }

  async function login(email: string, password: string) {
    const session = await loginWithPassword(email, password)
    await replaceSession(session)
  }

  async function register(registerData: {
    storeName: string
    email: string
    password: string
  }) {
    return registerOwner(registerData)
  }

  async function logout() {
    try {
      await logoutSession()
    } catch {
      // Local logout must still complete if the token has already expired.
    }
    clearAuth()
    useStoreContextStore.getState().reset()
    useSubscriptionStore.getState().reset()
    queryClient.clear()
  }

  return (
    <AuthContext.Provider
      value={{
        username,
        role,
        fullName,
        isAuthenticated,
        login,
        register,
        logout,
      }}
    >
      {children}
    </AuthContext.Provider>
  )
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth phải được dùng bên trong AuthProvider')
  return ctx
}
