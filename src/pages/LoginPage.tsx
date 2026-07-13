import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, Lock, Storefront, User } from '@phosphor-icons/react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import * as zod from 'zod'
import { loginWithPassword } from '../services/authService'
import { useAuthStore } from '../stores/authStore'

const loginSchema = zod.object({
  email: zod.string().email('Email không hợp lệ'),
  password: zod.string().min(1, 'Vui lòng nhập mật khẩu'),
})

type LoginValues = zod.infer<typeof loginSchema>

export default function LoginPage() {
  const navigate = useNavigate()
  const setSession = useAuthStore((state) => state.setSession)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)

  const { register, handleSubmit, formState: { errors } } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  async function onSubmit(values: LoginValues) {
    setError(null)
    setIsSubmitting(true)
    try {
      const session = await loginWithPassword(values.email, values.password)
      setSession(session)
      // Nếu đăng nhập bằng mật khẩu tạm, ép user đi đổi mật khẩu trước mọi nghiệp vụ.
      navigate(session.currentUser.mustChangePassword ? '/first-login' : '/', { replace: true })
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể đăng nhập.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-[100dvh] bg-offwhite p-4 md:p-8 text-ink">
      <div className="mx-auto grid min-h-[calc(100dvh-2rem)] max-w-5xl grid-cols-1 overflow-hidden rounded-[28px] border border-ink/10 bg-white shadow-2xl md:grid-cols-12">
        <section className="md:col-span-5 bg-sage-dark p-8 text-white md:p-10">
          <div className="flex items-center gap-2">
            <Storefront size={26} weight="duotone" className="text-terracotta" />
            <div>
              <h1 className="text-lg font-extrabold">AI Inventory</h1>
              <p className="text-[10px] font-bold uppercase tracking-widest text-white/45">Staff access</p>
            </div>
          </div>
          <div className="mt-16 max-w-sm">
            <h2 className="text-2xl font-bold leading-tight">Đăng nhập theo đúng store được phân quyền.</h2>
            <p className="mt-3 text-sm leading-6 text-white/70">
              Owner có thể đổi cửa hàng. Manager và Staff chỉ nhìn thấy dữ liệu của store được gán.
            </p>
          </div>
        </section>

        <main className="md:col-span-7 flex items-center p-6 md:p-10">
          <form onSubmit={handleSubmit(onSubmit)} className="w-full space-y-5">
            <div>
              <h2 className="text-2xl font-bold tracking-tight">Đăng nhập</h2>
              <p className="mt-1 text-sm text-ink/55">Dùng chung cho Owner, Manager và Staff.</p>
            </div>

            {error && <div className="rounded-xl border border-terracotta/20 bg-terracotta/10 px-4 py-3 text-sm font-semibold text-terracotta">{error}</div>}

            <label className="block">
              <span className="text-xs font-bold text-ink/75">Email</span>
              <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
                <User size={17} className="text-ink/35" />
                <input {...register('email')} className="w-full bg-transparent py-3 text-sm outline-none" />
              </span>
              {errors.email && <span className="mt-1 block text-xs text-terracotta">{errors.email.message}</span>}
            </label>

            <label className="block">
              <span className="text-xs font-bold text-ink/75">Mật khẩu</span>
              <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
                <Lock size={17} className="text-ink/35" />
                <input type="password" {...register('password')} className="w-full bg-transparent py-3 text-sm outline-none" />
              </span>
              {errors.password && <span className="mt-1 block text-xs text-terracotta">{errors.password.message}</span>}
            </label>

            <button
              disabled={isSubmitting}
              className="flex w-full items-center justify-center gap-2 rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white transition hover:bg-sage disabled:cursor-not-allowed disabled:opacity-60"
            >
              {isSubmitting ? 'Đang kiểm tra...' : 'Đăng nhập'}
              <ArrowRight size={17} />
            </button>

            <p className="text-center text-sm text-ink/55">
              Chưa có tài khoản Owner? <Link to="/register" className="font-bold text-sage-dark hover:underline">Đăng ký store mới</Link>
            </p>
          </form>
        </main>
      </div>
    </div>
  )
}
