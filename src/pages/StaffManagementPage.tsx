import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowCounterClockwise, CheckCircle, EnvelopeSimple, Plus, Prohibit, UserMinus, X } from '@phosphor-icons/react'
import { useCallback, useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import * as zod from 'zod'
import { disableStaff, enableStaff, inviteStaff, listStaffByStore, revokeInvitation, staffRoleLabel } from '../services/staffService'
import { useAuthStore } from '../stores/authStore'
import { useSubscriptionStore } from '../stores/subscriptionStore'
import type { SessionUser, UserStatus } from '../types'

const inviteSchema = zod.object({
  email: zod.string().email('Email không hợp lệ'),
  role: zod.enum(['MANAGER', 'STAFF']),
})

type InviteValues = zod.infer<typeof inviteSchema>

const statusLabel: Record<UserStatus, string> = {
  PENDING_ACTIVATION: 'Chờ kích hoạt',
  ACTIVE: 'Đang hoạt động',
  DISABLED: 'Đã vô hiệu hóa',
}

const statusClass: Record<UserStatus, string> = {
  PENDING_ACTIVATION: 'bg-amber-500/10 text-amber-700 border-amber-500/20',
  ACTIVE: 'bg-sage/15 text-sage-dark border-sage/20',
  DISABLED: 'bg-ink/5 text-ink/45 border-ink/10',
}

export default function StaffManagementPage() {
  const currentStore = useAuthStore((state) => state.currentStore)
  const staffLimit = useSubscriptionStore((state) => state.current.limits.staff)
  const [staff, setStaff] = useState<SessionUser[]>([])
  const [isLoading, setIsLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isInviteOpen, setInviteOpen] = useState(false)
  const [inviteSuccess, setInviteSuccess] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [disableCandidate, setDisableCandidate] = useState<SessionUser | null>(null)
  const [isDisabling, setIsDisabling] = useState(false)

  const { register, handleSubmit, reset, watch, formState: { errors } } = useForm<InviteValues>({
    resolver: zodResolver(inviteSchema),
    defaultValues: { email: '', role: 'STAFF' },
  })

  const watchedRole = watch('role')
  const activeStaffCount = useMemo(
    () => staff.filter((user) => user.role === 'STAFF' && user.status !== 'DISABLED').length,
    [staff],
  )
  // Kiểm tra giới hạn gói Free/Basic/Pro trước khi mở form mời Staff.
  const staffLimitReached = watchedRole === 'STAFF' && typeof staffLimit === 'number' && activeStaffCount >= staffLimit

  const loadStaff = useCallback(async () => {
    if (!currentStore) return
    setIsLoading(true)
    setError(null)
    try {
      const data = await listStaffByStore(currentStore.id)
      setStaff(data)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không tải được danh sách nhân viên.')
    } finally {
      setIsLoading(false)
    }
  }, [currentStore])

  useEffect(() => {
    loadStaff()
  }, [loadStaff])

  async function onInvite(values: InviteValues) {
    if (!currentStore) return
    if (values.role === 'STAFF' && typeof staffLimit === 'number' && activeStaffCount >= staffLimit) {
      setError(`Đã đạt giới hạn gói ${currentStore.subscriptionPlan}: tối đa ${staffLimit} Staff.`)
      return
    }

    setIsSubmitting(true)
    setError(null)
    try {
      const result = await inviteStaff(currentStore.id, values.email, values.role)
      setInviteSuccess(result.message)
      setInviteOpen(false)
      reset({ email: '', role: 'STAFF' })
      await loadStaff()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể tạo lời mời.')
    } finally {
      setIsSubmitting(false)
    }
  }

  async function handleRevoke(userId: number) {
    setError(null)
    try {
      await revokeInvitation(userId)
      await loadStaff()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể thu hồi lời mời.')
    }
  }

  function handleDisable(user: SessionUser) {
    setDisableCandidate(user)
  }

  async function confirmDisable() {
    if (!disableCandidate) return
    setError(null)
    setIsDisabling(true)
    try {
      await disableStaff(disableCandidate.id)
      setDisableCandidate(null)
      await loadStaff()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể vô hiệu hóa nhân viên.')
    } finally {
      setIsDisabling(false)
    }
  }

  async function handleEnable(userId: number) {
    setError(null)
    try {
      await enableStaff(userId)
      await loadStaff()
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể gỡ vô hiệu hóa tài khoản.')
    }
  }

  return (
    <div className="space-y-6">
      <div className="flex flex-col gap-3 md:flex-row md:items-end md:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Quản lý nhân viên</h2>
          <p className="text-xs text-ink/60">
            Store hiện tại: <span className="font-bold text-ink">{currentStore?.name ?? 'Chưa chọn store'}</span>
          </p>
        </div>
        <button
          onClick={() => setInviteOpen(true)}
          disabled={!currentStore}
          className="inline-flex items-center justify-center gap-2 rounded-xl bg-sage-dark px-4 py-2.5 text-xs font-bold text-white transition hover:bg-sage disabled:opacity-50"
        >
          <Plus size={16} />
          Mời nhân viên
        </button>
      </div>

      {typeof staffLimit === 'number' && activeStaffCount >= staffLimit && (
        <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm font-semibold text-terracotta">
          Đã đạt giới hạn gói {currentStore?.subscriptionPlan}: {activeStaffCount}/{staffLimit} Staff. Nâng cấp gói để mời thêm Staff.
        </div>
      )}

      {error && <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm font-semibold text-terracotta">{error}</div>}

      <div className="overflow-hidden rounded-2xl border border-ink/10 bg-white shadow-sm">
        <table className="w-full min-w-[760px] text-left text-xs">
          <thead className="bg-ink/5 text-ink/70">
            <tr>
              <th className="px-5 py-4">Nhân viên</th>
              <th className="px-5 py-4">Role</th>
              <th className="px-5 py-4">Trạng thái</th>
              <th className="px-5 py-4 text-right">Thao tác</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-ink/10">
            {isLoading ? (
              Array.from({ length: 3 }).map((_, index) => (
                <tr key={index}>
                  <td className="px-5 py-4"><div className="h-4 w-44 animate-pulse rounded bg-ink/10" /></td>
                  <td className="px-5 py-4"><div className="h-4 w-20 animate-pulse rounded bg-ink/10" /></td>
                  <td className="px-5 py-4"><div className="h-4 w-28 animate-pulse rounded bg-ink/10" /></td>
                  <td className="px-5 py-4" />
                </tr>
              ))
            ) : staff.length === 0 ? (
              <tr><td colSpan={4} className="px-5 py-10 text-center text-sm text-ink/45">Chưa có nhân viên trong store này.</td></tr>
            ) : (
              staff.map((user) => (
                <tr key={user.id} className="hover:bg-ink/5">
                  <td className="px-5 py-4">
                    <div className="font-bold text-ink">{user.fullName}</div>
                    <div className="mt-1 text-ink/50">{user.email}</div>
                  </td>
                  <td className="px-5 py-4 font-semibold">{user.role === 'MANAGER' || user.role === 'STAFF' ? staffRoleLabel[user.role] : user.role}</td>
                  <td className="px-5 py-4">
                    <span className={`inline-flex rounded-full border px-2.5 py-1 font-bold ${statusClass[user.status]}`}>
                      {statusLabel[user.status]}
                    </span>
                  </td>
                  <td className="px-5 py-4 text-right">
                    {user.status === 'PENDING_ACTIVATION' && (
                      <button onClick={() => handleRevoke(user.id)} className="inline-flex items-center gap-1.5 rounded-lg border border-terracotta/20 px-3 py-2 font-bold text-terracotta hover:bg-terracotta/5">
                        <UserMinus size={14} /> Thu hồi
                      </button>
                    )}
                    {user.status === 'ACTIVE' && (
                      <button onClick={() => handleDisable(user)} className="inline-flex items-center gap-1.5 rounded-lg border border-ink/10 px-3 py-2 font-bold text-ink/65 hover:bg-ink/5">
                        <Prohibit size={14} /> Vô hiệu hóa
                      </button>
                    )}
                    {user.status === 'DISABLED' && (
                      <button onClick={() => handleEnable(user.id)} className="inline-flex items-center gap-1.5 rounded-lg border border-sage/20 px-3 py-2 font-bold text-sage-dark hover:bg-sage/10">
                        <ArrowCounterClockwise size={14} /> Gỡ vô hiệu hóa
                      </button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {isInviteOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <form onSubmit={handleSubmit(onInvite)} className="w-full max-w-md rounded-3xl bg-white p-6 shadow-2xl">
            <div className="mb-5 flex items-center justify-between">
              <div>
                <h3 className="text-lg font-bold">Mời nhân viên</h3>
                <p className="text-xs text-ink/55">Gửi liên kết kích hoạt cho store hiện tại.</p>
              </div>
              <button type="button" onClick={() => setInviteOpen(false)} className="rounded-lg border border-ink/10 p-2"><X size={16} /></button>
            </div>

            <label className="block">
              <span className="text-xs font-bold text-ink/75">Email</span>
              <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
                <EnvelopeSimple size={17} className="text-ink/35" />
                <input {...register('email')} className="w-full bg-transparent py-3 text-sm outline-none" />
              </span>
              {errors.email && <span className="mt-1 block text-xs text-terracotta">{errors.email.message}</span>}
            </label>

            <label className="mt-4 block">
              <span className="text-xs font-bold text-ink/75">Role</span>
              <select {...register('role')} className="mt-1.5 w-full rounded-xl border border-ink/10 bg-offwhite/50 px-3 py-3 text-sm outline-none">
                <option value="MANAGER">Manager</option>
                <option value="STAFF">Staff</option>
              </select>
            </label>

            {staffLimitReached && (
              <div className="mt-4 rounded-xl border border-terracotta/20 bg-terracotta/10 px-3 py-2 text-xs font-semibold text-terracotta">
                Gói {currentStore?.subscriptionPlan} đã đạt giới hạn {staffLimit} Staff. Hãy chọn Manager hoặc nâng cấp gói.
              </div>
            )}

            <button disabled={isSubmitting || staffLimitReached} className="mt-5 w-full rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white transition hover:bg-sage disabled:cursor-not-allowed disabled:opacity-55">
              {isSubmitting ? 'Đang tạo lời mời...' : 'Tạo lời mời'}
            </button>
          </form>
        </div>
      )}

      {inviteSuccess && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl bg-white p-6 shadow-2xl">
            <div className="flex items-start gap-3">
              <CheckCircle size={26} weight="fill" className="mt-0.5 shrink-0 text-sage-dark" />
              <div>
                <h3 className="text-lg font-bold">Đã gửi lời mời</h3>
                <p className="mt-1 text-sm text-ink/60">{inviteSuccess}</p>
              </div>
            </div>
            <button onClick={() => setInviteSuccess(null)} className="mt-5 w-full rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white">
              Đã hiểu
            </button>
          </div>
        </div>
      )}

      {disableCandidate && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-md rounded-3xl bg-white p-6 shadow-2xl">
            <div className="flex items-start gap-3">
              <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-2xl bg-terracotta/10 text-terracotta">
                <Prohibit size={22} weight="bold" />
              </div>
              <div>
                <h3 className="text-lg font-bold text-ink">Xác nhận vô hiệu hóa</h3>
                <p className="mt-1 text-sm leading-6 text-ink/60">
                  Bạn có chắc chắn muốn vô hiệu hoá tài khoản này không?
                </p>
              </div>
            </div>

            <div className="mt-5 rounded-2xl border border-ink/10 bg-offwhite p-4">
              <div className="text-sm font-bold text-ink">{disableCandidate.fullName}</div>
              <div className="mt-1 text-xs text-ink/55">{disableCandidate.email}</div>
            </div>

            <div className="mt-6 flex flex-col-reverse gap-3 sm:flex-row sm:justify-end">
              <button
                type="button"
                onClick={() => setDisableCandidate(null)}
                disabled={isDisabling}
                className="rounded-xl border border-ink/10 px-4 py-3 text-sm font-bold text-ink/70 transition hover:bg-ink/5 disabled:cursor-not-allowed disabled:opacity-55"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={confirmDisable}
                disabled={isDisabling}
                className="rounded-xl bg-terracotta px-4 py-3 text-sm font-bold text-white transition hover:bg-terracotta/90 disabled:cursor-not-allowed disabled:opacity-55"
              >
                {isDisabling ? 'Đang vô hiệu hóa...' : 'Vô hiệu hóa'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
