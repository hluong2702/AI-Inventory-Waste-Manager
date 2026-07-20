import type { AuthSession, SessionUser, Store } from '../types'
import apiClient from '../api/client'
import { apiErrorMessage, unwrapApiData } from '../utils/apiResponse'
import { setAccessToken } from '../auth/tokenStore'

interface RegisterOwnerInput {
  storeName: string
  email: string
  password: string
}

export interface RegistrationResult {
  verificationRequired: boolean
  email: string
  expiresInHours: number
}

interface BackendAuthResponse {
  accessToken: string
  refreshToken: string | null
  userId: number
  storeId: number | null
  role: 'SYSTEM_ADMIN' | 'OWNER' | 'MANAGER' | 'STAFF'
  mustChangePassword: boolean
}

function mapBackendRole(role: BackendAuthResponse['role']) {
  return role === 'OWNER' ? 'STORE_OWNER' : role
}

function buildBackendSession(auth: BackendAuthResponse, email: string, storeName?: string): AuthSession {
  setAccessToken(auth.accessToken)
  const mappedRole = mapBackendRole(auth.role)
  const currentStore: Store = {
    id: auth.storeId ?? 0,
    name: auth.storeId ? (storeName ?? `Store #${auth.storeId}`) : 'System Admin',
    address: '',
    phone: null,
    subscriptionPlan: 'FREE',
    status: 'ACTIVE',
  }
  const currentUser: SessionUser = {
    id: auth.userId,
    storeId: auth.storeId ?? undefined,
    username: email,
    email,
    fullName: email.split('@')[0],
    role: mappedRole,
    status: 'ACTIVE',
    mustChangePassword: auth.mustChangePassword,
    storeIds: auth.storeId ? [auth.storeId] : [],
  }
  return {
    currentUser,
    currentStore,
    stores: auth.storeId ? [currentStore] : [],
  }
}

export async function logoutSession() {
  await apiClient.post('/auth/logout', {})
}

export async function registerOwner(input: RegisterOwnerInput) {
  try {
    const res = await apiClient.post('/auth/register', {
      storeName: input.storeName,
      email: input.email,
      password: input.password,
    })
    return unwrapApiData<RegistrationResult>(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể đăng ký tài khoản.'))
  }
}

export async function loginWithPassword(email: string, password: string) {
  try {
    const res = await apiClient.post('/auth/login', { email, password })
    return buildBackendSession(unwrapApiData<BackendAuthResponse>(res.data), email)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Email hoặc mật khẩu không chính xác.'))
  }
}

export async function changeFirstPassword(_userId: number, newPassword: string) {
  try {
    const email = localStorage.getItem('email') ?? ''
    const res = await apiClient.post('/auth/first-login-change-password', { newPassword })
    return buildBackendSession(unwrapApiData<BackendAuthResponse>(res.data), email)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể đổi mật khẩu.'))
  }
}
