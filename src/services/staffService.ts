import type { Role, SessionUser } from '../types'
import apiClient from '../api/client'
import { apiErrorMessage, unwrapApiData } from '../utils/apiResponse'

interface BackendStaffResponse {
  id: number
  fullName: string
  email: string
  role: 'MANAGER' | 'STAFF'
  status: 'PENDING_ACTIVATION' | 'ACTIVE' | 'DISABLED'
  mustChangePassword: boolean
}

export type InvitationStatus = 'VALID' | 'INVALID' | 'EXPIRED' | 'USED'

export interface InvitationVerification {
  status: InvitationStatus
  valid: boolean
  email: string | null
  storeName: string | null
  role: 'MANAGER' | 'STAFF' | null
}

export const staffRoleLabel: Record<Extract<Role, 'MANAGER' | 'STAFF'>, string> = {
  MANAGER: 'Manager',
  STAFF: 'Staff',
}

function toStaffUser(user: BackendStaffResponse, storeId: number): SessionUser {
  return {
    id: user.id,
    storeId,
    username: user.email,
    email: user.email,
    fullName: user.fullName,
    role: user.role,
    status: user.status,
    mustChangePassword: user.mustChangePassword,
    storeIds: [storeId],
  }
}

export async function listStaffByStore(storeId: number) {
  try {
    const res = await apiClient.get(`/stores/${storeId}/staff`)
    return unwrapApiData<BackendStaffResponse[]>(res.data).map((user) => toStaffUser(user, storeId))
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không tải được danh sách nhân viên.'))
  }
}

export async function inviteStaff(storeId: number, email: string, role: Extract<Role, 'MANAGER' | 'STAFF'>) {
  try {
    const res = await apiClient.post(`/stores/${storeId}/staff/invitations`, { email, role })
    const staff = unwrapApiData<BackendStaffResponse[]>(res.data).map((user) => toStaffUser(user, storeId))
    return {
      user: staff.find((user) => user.email === email.trim().toLowerCase()) ?? staff[staff.length - 1],
      invitation: null,
      message: 'Đã gửi email kích hoạt. Nhân viên sẽ tự đặt mật khẩu qua liên kết trong email.',
    }
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể tạo lời mời.'))
  }
}

export async function verifyInvitation(token: string): Promise<InvitationVerification> {
  try {
    const res = await apiClient.get('/staff/invitations/verify', { params: { token } })
    return unwrapApiData<InvitationVerification>(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể kiểm tra lời mời.'))
  }
}

export async function acceptInvitation(input: { token: string; fullName: string; password: string }) {
  try {
    const res = await apiClient.post('/staff/invitations/accept', input)
    return unwrapApiData<InvitationVerification>(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể chấp nhận lời mời.'))
  }
}

export async function revokeInvitation(userId: number) {
  const storeId = Number(localStorage.getItem('activeStoreId'))
  await apiClient.delete(`/stores/${storeId}/staff/invitations/${userId}`)
  return true
}

export async function disableStaff(userId: number) {
  const storeId = Number(localStorage.getItem('activeStoreId'))
  const res = await apiClient.patch(`/stores/${storeId}/staff/${userId}/disable`)
  const staff = unwrapApiData<BackendStaffResponse[]>(res.data)
  return toStaffUser(staff.find((user) => user.id === userId) ?? staff[0], storeId)
}

export async function enableStaff(userId: number) {
  const storeId = Number(localStorage.getItem('activeStoreId'))
  const res = await apiClient.patch(`/stores/${storeId}/staff/${userId}/enable`)
  const staff = unwrapApiData<BackendStaffResponse[]>(res.data)
  return toStaffUser(staff.find((user) => user.id === userId) ?? staff[0], storeId)
}
