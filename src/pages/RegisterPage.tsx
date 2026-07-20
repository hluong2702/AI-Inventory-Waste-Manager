import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, CheckCircle, Lock, Storefront, User } from '@phosphor-icons/react'
import type React from 'react'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import * as zod from 'zod'
import { useAuth } from '../context/AuthContext'

const registerSchema = zod.object({
  storeName: zod.string().min(2, 'Tên quán phải có ít nhất 2 ký tự'),
  email: zod.string().email('Email không hợp lệ'),
  password: zod.string().min(8, 'Mật khẩu phải có ít nhất 8 ký tự'),
  confirmPassword: zod.string().min(1, 'Vui lòng xác nhận mật khẩu'),
}).refine((data) => data.password === data.confirmPassword, {
  path: ['confirmPassword'],
  message: 'Mật khẩu xác nhận không khớp',
})

type RegisterValues = zod.infer<typeof registerSchema>

export default function RegisterPage() {
  const { register: registerAccount } = useAuth()
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [verificationEmail, setVerificationEmail] = useState<string | null>(null)

  const { register, handleSubmit, formState: { errors } } = useForm<RegisterValues>({
    resolver: zodResolver(registerSchema),
    defaultValues: { storeName: '', email: '', password: '', confirmPassword: '' },
  })

  async function onSubmit(values: RegisterValues) {
    setError(null)
    setIsSubmitting(true)
    try {
      const result = await registerAccount(values)
      setVerificationEmail(result.email)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể đăng ký.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-[100dvh] bg-offwhite p-4 md:p-8 text-ink">
      <div className="mx-auto grid min-h-[calc(100dvh-2rem)] max-w-5xl grid-cols-1 overflow-hidden rounded-[28px] border border-ink/10 bg-white shadow-2xl md:grid-cols-12">
        <section className="md:col-span-5 bg-sage-dark p-8 text-white md:p-10">
          <Storefront size={30} weight="duotone" className="text-terracotta" />
          <h1 className="mt-12 text-2xl font-bold leading-tight">Tạo Store + Owner trong một bước.</h1>
          <p className="mt-3 text-sm leading-6 text-white/90">Backend sẽ tạo store mới, tài khoản Owner, gói Free và đăng nhập ngay.</p>
        </section>

        <main className="md:col-span-7 flex items-center p-6 md:p-10">
          {verificationEmail ? (
            <div className="w-full text-center">
              <CheckCircle size={44} weight="fill" className="mx-auto text-sage-dark" />
              <h2 className="mt-4 text-2xl font-bold">Kiểm tra email để kích hoạt</h2>
              <p className="mt-2 text-sm leading-6 text-ink/75">
                Liên kết xác minh đã được gửi tới <span className="font-semibold text-ink">{verificationEmail}</span> và có hiệu lực trong 48 giờ.
              </p>
              <Link to="/login" className="mt-6 inline-flex items-center gap-2 rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white">
                Về trang đăng nhập
                <ArrowRight size={17} />
              </Link>
            </div>
          ) : (
          <form onSubmit={handleSubmit(onSubmit)} className="w-full space-y-4">
            <div>
              <h2 className="text-2xl font-bold tracking-tight">Đăng ký Owner</h2>
              <p className="mt-1 text-sm text-ink/75">Tên quán, email và mật khẩu được lưu qua API backend.</p>
            </div>

            {error && <div role="alert" className="rounded-xl border border-terracotta/20 bg-terracotta/10 px-4 py-3 text-sm font-semibold text-terracotta">{error}</div>}

            <Field icon={<Storefront size={17} />} label="Tên quán" error={errors.storeName?.message}>
              <input autoComplete="organization" {...register('storeName')} className="w-full bg-transparent py-3 text-sm outline-none" />
            </Field>
            <Field icon={<User size={17} />} label="Email Owner" error={errors.email?.message}>
              <input type="email" autoComplete="email" {...register('email')} className="w-full bg-transparent py-3 text-sm outline-none" />
            </Field>
            <Field icon={<Lock size={17} />} label="Mật khẩu" error={errors.password?.message}>
              <input type="password" autoComplete="new-password" {...register('password')} className="w-full bg-transparent py-3 text-sm outline-none" />
            </Field>
            <Field icon={<Lock size={17} />} label="Xác nhận mật khẩu" error={errors.confirmPassword?.message}>
              <input type="password" autoComplete="new-password" {...register('confirmPassword')} className="w-full bg-transparent py-3 text-sm outline-none" />
            </Field>

            <button type="submit" disabled={isSubmitting} className="flex w-full items-center justify-center gap-2 rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white transition hover:bg-sage disabled:opacity-60">
              {isSubmitting ? 'Đang tạo store...' : 'Tạo tài khoản Owner'}
              <ArrowRight size={17} />
            </button>

            <p className="text-center text-sm text-ink/75">
              Đã có tài khoản? <Link to="/login" className="font-bold text-sage-dark hover:underline">Đăng nhập</Link>
            </p>
          </form>
          )}
        </main>
      </div>
    </div>
  )
}

function Field({ label, error, icon, children }: { label: string; error?: string; icon: React.ReactNode; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="text-xs font-bold text-ink/75">{label}</span>
      <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3 text-ink/75">
        {icon}
        {children}
      </span>
      {error && <span role="alert" className="mt-1 block text-xs text-terracotta">{error}</span>}
    </label>
  )
}
