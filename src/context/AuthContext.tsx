import { createContext, useContext, type ReactNode } from 'react'
import type { Role } from '../types'
import { loginWithPassword, registerOwner } from '../services/authService'
import { useAuthStore } from '../stores/authStore'

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
  }) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

export function AuthProvider({ children }: { children: ReactNode }) {
  const { username, role, fullName, isAuthenticated, setSession, clearAuth } = useAuthStore()

  async function login(email: string, password: string) {
    const session = await loginWithPassword(email, password)
    setSession(session)
  }

  async function register(registerData: {
    storeName: string
    email: string
    password: string
  }) {
    const session = await registerOwner(registerData)
    setSession(session)
  }

  function logout() {
    clearAuth()
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
