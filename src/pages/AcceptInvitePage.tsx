import { zodResolver } from '@hookform/resolvers/zod'
import { ArrowRight, CheckCircle, Lock, User, WarningCircle } from '@phosphor-icons/react'
import { useEffect, useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import * as zod from 'zod'
import { acceptInvitation, verifyInvitation, type InvitationVerification } from '../services/staffService'

const acceptSchema = zod.object({
  fullName: zod.string().min(2, 'Vui lòng nhập họ tên').max(160, 'Họ tên quá dài'),
  password: zod.string().min(8, 'Mật khẩu tối thiểu 8 ký tự'),
  confirmPassword: zod.string().min(1, 'Vui lòng xác nhận mật khẩu'),
}).refine((value) => value.password === value.confirmPassword, {
  message: 'Mật khẩu xác nhận không khớp',
  path: ['confirmPassword'],
})

type AcceptValues = zod.infer<typeof acceptSchema>

const statusCopy = {
  INVALID: {
    title: 'Liên kết không hợp lệ',
    description: 'Token lời mời không tồn tại hoặc đã bị thay đổi.',
  },
  EXPIRED: {
    title: 'Liên kết đã hết hạn',
    description: 'Lời mời chỉ có hiệu lực trong 48 giờ. Vui lòng liên hệ Owner hoặc Manager để được gửi lại.',
  },
  USED: {
    title: 'Liên kết đã được sử dụng',
    description: 'Tài khoản này đã được kích hoạt trước đó. Bạn có thể đăng nhập bằng mật khẩu đã đặt.',
  },
}

export default function AcceptInvitePage() {
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''
  const [verification, setVerification] = useState<InvitationVerification | null>(null)
  const [isVerifying, setIsVerifying] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [isAccepted, setIsAccepted] = useState(false)

  const { register, handleSubmit, formState: { errors } } = useForm<AcceptValues>({
    resolver: zodResolver(acceptSchema),
    defaultValues: { fullName: '', password: '', confirmPassword: '' },
  })

  useEffect(() => {
    let isMounted = true
    async function run() {
      setIsVerifying(true)
      setError(null)
      try {
        const result = token
          ? await verifyInvitation(token)
          : { status: 'INVALID' as const, valid: false, email: null, storeName: null, role: null }
        if (isMounted) setVerification(result)
      } catch (err) {
        if (isMounted) setError(err instanceof Error ? err.message : 'Không thể kiểm tra lời mời.')
      } finally {
        if (isMounted) setIsVerifying(false)
      }
    }
    run()
    return () => {
      isMounted = false
    }
  }, [token])

  const invalidState = useMemo(() => {
    if (!verification || verification.valid) return null
    return statusCopy[verification.status as keyof typeof statusCopy] ?? statusCopy.INVALID
  }, [verification])

  async function onSubmit(values: AcceptValues) {
    if (!token) return
    setIsSubmitting(true)
    setError(null)
    try {
      await acceptInvitation({ token, fullName: values.fullName, password: values.password })
      setIsAccepted(true)
      window.setTimeout(() => navigate('/login', { replace: true }), 1300)
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Không thể kích hoạt tài khoản.')
    } finally {
      setIsSubmitting(false)
    }
  }

  return (
    <div className="min-h-[100dvh] bg-offwhite p-4 text-ink md:p-8">
      <div className="mx-auto grid min-h-[calc(100dvh-2rem)] max-w-5xl grid-cols-1 overflow-hidden rounded-[28px] border border-ink/10 bg-white shadow-2xl md:grid-cols-12">
        <section className="bg-sage-dark p-8 text-white md:col-span-5 md:p-10">
          <div>
            <h1 className="text-lg font-extrabold">AI Inventory</h1>
            <p className="text-[10px] font-bold uppercase tracking-widest text-white/45">Staff activation</p>
          </div>
          <div className="mt-16 max-w-sm">
            <h2 className="text-2xl font-bold leading-tight">Kích hoạt tài khoản nhân viên.</h2>
            <p className="mt-3 text-sm leading-6 text-white/70">
              Hoàn tất hồ sơ và đặt mật khẩu để truy cập store được phân quyền.
            </p>
          </div>
          {verification?.valid && (
            <div className="mt-12 rounded-2xl border border-white/10 bg-white/5 p-4 text-xs text-white/75">
              <div className="font-bold text-white">{verification.storeName}</div>
              <div className="mt-1">{verification.email}</div>
            </div>
          )}
        </section>

        <main className="flex items-center p-6 md:col-span-7 md:p-10">
          {isVerifying ? (
            <div className="w-full space-y-4">
              <div className="h-6 w-44 animate-pulse rounded bg-ink/10" />
              <div className="h-4 w-72 animate-pulse rounded bg-ink/10" />
              <div className="h-12 w-full animate-pulse rounded-xl bg-ink/10" />
            </div>
          ) : isAccepted ? (
            <div className="w-full text-center">
              <CheckCircle size={42} weight="fill" className="mx-auto text-sage-dark" />
              <h2 className="mt-4 text-2xl font-bold">Tài khoản đã được kích hoạt</h2>
              <p className="mt-2 text-sm text-ink/60">Bạn sẽ được chuyển đến trang đăng nhập.</p>
            </div>
          ) : invalidState ? (
            <div className="w-full">
              <WarningCircle size={38} weight="fill" className="text-terracotta" />
              <h2 className="mt-4 text-2xl font-bold">{invalidState.title}</h2>
              <p className="mt-2 text-sm leading-6 text-ink/60">{invalidState.description}</p>
              <Link to="/login" className="mt-6 inline-flex items-center gap-2 rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white">
                Về trang đăng nhập
                <ArrowRight size={17} />
              </Link>
            </div>
          ) : (
            <form onSubmit={handleSubmit(onSubmit)} className="w-full space-y-5">
              <div>
                <h2 className="text-2xl font-bold tracking-tight">Chấp nhận lời mời</h2>
                <p className="mt-1 text-sm text-ink/55">Đặt thông tin đăng nhập cho {verification?.email}.</p>
              </div>

              {error && <div className="rounded-xl border border-terracotta/20 bg-terracotta/10 px-4 py-3 text-sm font-semibold text-terracotta">{error}</div>}

              <label className="block">
                <span className="text-xs font-bold text-ink/75">Họ tên</span>
                <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
                  <User size={17} className="text-ink/35" />
                  <input {...register('fullName')} className="w-full bg-transparent py-3 text-sm outline-none" />
                </span>
                {errors.fullName && <span className="mt-1 block text-xs text-terracotta">{errors.fullName.message}</span>}
              </label>

              <label className="block">
                <span className="text-xs font-bold text-ink/75">Mật khẩu</span>
                <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
                  <Lock size={17} className="text-ink/35" />
                  <input type="password" {...register('password')} className="w-full bg-transparent py-3 text-sm outline-none" />
                </span>
                {errors.password && <span className="mt-1 block text-xs text-terracotta">{errors.password.message}</span>}
              </label>

              <label className="block">
                <span className="text-xs font-bold text-ink/75">Xác nhận mật khẩu</span>
                <span className="mt-1.5 flex items-center gap-2 rounded-xl border border-ink/10 bg-offwhite/50 px-3">
                  <Lock size={17} className="text-ink/35" />
                  <input type="password" {...register('confirmPassword')} className="w-full bg-transparent py-3 text-sm outline-none" />
                </span>
                {errors.confirmPassword && <span className="mt-1 block text-xs text-terracotta">{errors.confirmPassword.message}</span>}
              </label>

              <button
                disabled={isSubmitting}
                className="flex w-full items-center justify-center gap-2 rounded-xl bg-sage-dark px-4 py-3 text-sm font-bold text-white transition hover:bg-sage disabled:cursor-not-allowed disabled:opacity-60"
              >
                {isSubmitting ? 'Đang kích hoạt...' : 'Kích hoạt tài khoản'}
                <ArrowRight size={17} />
              </button>
            </form>
          )}
        </main>
      </div>
    </div>
  )
}
