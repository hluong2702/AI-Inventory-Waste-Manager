import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { clearAccessToken, getAccessToken, setAccessToken } from '../auth/tokenStore'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? 'http://localhost:8080/api'

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
})

type RetryableRequest = InternalAxiosRequestConfig & { _retry?: boolean }
let refreshPromise: Promise<string> | null = null

function isPublicEndpoint(url: string) {
  return url.includes('/auth/login')
    || url.includes('/auth/register')
    || url.includes('/auth/refresh')
    || url.includes('/staff/invitations/verify')
    || url.includes('/staff/invitations/accept')
}

function refreshAccessToken() {
  refreshPromise ??= axios
    .post(`${API_BASE_URL}/auth/refresh`, {}, { withCredentials: true })
    .then((res) => {
      const auth = res.data?.data ?? res.data
      setAccessToken(auth.accessToken)
      return auth.accessToken as string
    })
    .finally(() => { refreshPromise = null })
  return refreshPromise
}

function clearSession() {
  clearAccessToken()
  for (const key of ['inventoryai_auth_session_v1', 'accessToken', 'refreshToken', 'username', 'email', 'role', 'fullName', 'storeIds', 'activeStoreId', 'subscriptionPlan', 'subscriptionExpiresAt', 'pendingUpgradePlan']) {
    localStorage.removeItem(key)
  }
  for (const key of Object.keys(sessionStorage)) {
    if (key.startsWith('payment-idempotency:')) sessionStorage.removeItem(key)
  }
}

apiClient.interceptors.request.use(async (config) => {
  let token = getAccessToken()
  const url = config.url ?? ''
  const isPublic = isPublicEndpoint(url)
  if (!token && localStorage.getItem('inventoryai_auth_session_v1') && !isPublic) {
    try {
      token = await refreshAccessToken()
    } catch {
      clearSession()
      window.location.href = '/login'
      throw new Error('Session refresh failed')
    }
  }
  if (token && config.headers && !isPublic) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const activeStoreId = localStorage.getItem('activeStoreId')
  if (activeStoreId && config.headers && !isPublic) {
    config.headers['x-store-id'] = activeStoreId
    config.headers.storeId = activeStoreId
  }
  return config
})

apiClient.interceptors.response.use(
  (response) => {
    const isBinaryResponse = response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer'
    if (!isBinaryResponse && response.data && typeof response.data === 'object' && !('success' in response.data)) {
      response.data = { success: true, data: response.data }
    }
    return response
  },
  async (error: AxiosError) => {
    const request = error.config as RetryableRequest | undefined
    const url = request?.url ?? ''
    const isPublicAuth = isPublicEndpoint(url)
    if (error.response?.status === 401 && request && !request._retry && !isPublicAuth) {
      request._retry = true
      try {
        request.headers.Authorization = `Bearer ${await refreshAccessToken()}`
        return apiClient(request)
      } catch {
        clearSession()
        window.location.href = '/login'
      }
    } else if (error.response?.status === 401 && !isPublicAuth) {
      clearSession()
      window.location.href = '/login'
    }
    const errorData = error.response?.data as { code?: string } | undefined
    if (error.response?.status === 403 && errorData?.code === 'MUST_CHANGE_PASSWORD') {
      window.location.href = '/first-login'
    }
    return Promise.reject(error)
  },
)

export default apiClient
