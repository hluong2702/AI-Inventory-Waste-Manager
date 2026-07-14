import { zodResolver } from '@hookform/resolvers/zod'
import { Key, Lock } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { useNavigate } from 'react-router-dom'
import * as zod from 'zod'
import { changeFirstPassword } from '../services/authService'
import { useAuthStore } from '../stores/authStore'

const firstLoginSchema = zod.object({
  newPassword: zod.string().min(8, 'Mật khẩu mới phải có ít nhất 8 ký tự'),
  confirmPassword: zod.string().min(1, 'Vui lòng xác nhận mật khẩu'),
}).refine((data) => data.newPassword === data.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Mật khẩu xác nhận không khớp',
})

type FirstLoginValues = zod.infer<typeof firstLoginSchema>

export default function FirstLoginPage() {
  const navigate = useNavigate()
  const currentUser = useAuthStore((state) => state.currentUser)
  const setSession = useAuthStore((state) => state.setSession)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const { register, handleSubmit, formState: { errors } } = useForm<FirstLoginValues>({
    resolver: zodResolver(firstLoginSchema),
  })

  async function onSubmit(values: FirstLoginValues) {
    if (!currentUser) return
    setError(null)
    setIsSubmitting(true)
    try {
      const session = await changeFirstPassword(currentUser.id, values.newPassword)
      setSession(session)
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể đổi mật khẩu.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="flex min-h-[100dvh] items-center justify-center bg-offwhite p-4 text-ink">
      <form onSubmit={handleSubmit(onSubmit)} className="w-full max-w-md rounded-3xl border border-ink/10 bg-white p-7 shadow-2xl">
        <div className="mb-6 flex items-center gap-3">
          <div className="rounded-2xl bg-sage/15 p-3 text-sage-dark">
            <Key size={24} />
          </div>
          <div>
            <h1 className="text-xl font-bold">Đổi mật khẩu lần đầu</h1>
            <p className="text-sm text-ink/55">Tài khoản mời bắt buộc đổi mật khẩu tạm trước khi vào hệ thống.</p>
          </div>
        </div>

        {error && <div className="mb-4 rounded-xl border border-terracotta/20 bg-terracotta/10 px-4 py-3 text-sm font-semibold text-terracotta">{error}</div>}

        <label className="block">
          <span className="text-xs font-bold text-ink/75">Mật khẩu mới</span>
          <span className="mt-1 block text-xs text-ink/50">Sử dụng ít nhất 8 ký tự.</span>
          <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
            <Lock size={17} className="text-ink/35" />
            <input type="password" {...register('newPassword')} className="w-full bg-transparent py-3 text-sm outline-none" />
          </span>
          {errors.newPassword && <span className="mt-1 block text-xs text-terracotta">{errors.newPassword.message}</span>}
        </label>

        <label className="mt-4 block">
          <span className="text-xs font-bold text-ink/75">Xác nhận mật khẩu</span>
          <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
            <Lock size={17} className="text-ink/35" />
            <input type="password" {...register('confirmPassword')} className="w-full bg-transparent py-3 text-sm outline-none" />
          </span>
          {errors.confirmPassword && <span className="mt-1 block text-xs text-terracotta">{errors.confirmPassword.message}</span>}
        </label>

        <button disabled={isSubmitting} className="mt-6 w-full rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white transition hover:bg-sage disabled:opacity-60">
          {isSubmitting ? 'Đang cập nhật...' : 'Đổi mật khẩu và tiếp tục'}
        </button>
      </form>
    </div>
  )
}
