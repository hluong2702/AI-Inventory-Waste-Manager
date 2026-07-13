import { useNavigate } from 'react-router-dom'
import { useAuth } from '../context/AuthContext'
import { useStore } from '../context/StoreContext'
import { useEffect } from 'react'
import { Storefront, MapPin, Phone, ArrowRight, Plus, Sparkle } from '@phosphor-icons/react'

/**
 * StoreSelectPage — Màn hình chọn cửa hàng làm việc.
 * Hiển thị sau khi đăng nhập nếu Store Owner có nhiều cửa hàng.
 * Khi chọn store → gọi switchStore → navigate về Dashboard.
 */
export default function StoreSelectPage() {
  const { isAuthenticated, role, fullName } = useAuth()
  const { stores, isLoadingStores, switchStore } = useStore()
  const navigate = useNavigate()

  // Redirect nếu chưa đăng nhập
  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { replace: true })
    }
    // Admin không cần chọn store
    if (role === 'SYSTEM_ADMIN') {
      navigate('/admin', { replace: true })
    }
  }, [isAuthenticated, role, navigate])

  // Nếu chỉ có 1 store, tự động chọn và vào Dashboard
  useEffect(() => {
    if (!isLoadingStores && stores.length === 1) {
      switchStore(stores[0].id)
      navigate('/', { replace: true })
    }
  }, [isLoadingStores, stores, switchStore, navigate])

  function handleSelectStore(storeId: number) {
    // Gọi switchStore → sẽ cập nhật StoreContext và header x-store-id
    switchStore(storeId)
    navigate('/', { replace: true })
  }

  return (
    <div className="min-h-screen bg-offwhite flex flex-col items-center justify-center p-6 font-sans">
      {/* Header */}
      <div className="text-center mb-10 max-w-md">
        <div className="inline-flex items-center gap-1.5 bg-sage-dark/10 text-sage-dark text-[10px] font-bold uppercase tracking-widest px-3 py-1 rounded-full border border-sage-dark/20 mb-4">
          <Sparkle size={10} weight="fill" />
          AI Inventory & Waste Manager
        </div>

        <h1 className="text-2xl font-bold tracking-tight text-ink leading-tight">
          Chào {fullName?.split(' ')[0] || 'bạn'} 👋
        </h1>
        <p className="text-sm text-ink/60 mt-2">
          Tài khoản của bạn có nhiều cửa hàng. Vui lòng chọn chi nhánh để bắt đầu làm việc.
        </p>
      </div>

      {/* Store Grid */}
      {isLoadingStores ? (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 w-full max-w-2xl">
          {[1, 2].map((i) => (
            <div key={i} className="bg-ink/5 p-1.5 rounded-[1.5rem] border border-ink/5 animate-pulse">
              <div className="bg-white rounded-[calc(1.5rem-0.375rem)] p-6 h-36" />
            </div>
          ))}
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4 w-full max-w-2xl">
          {stores.map((store, idx) => (
            <button
              key={store.id}
              onClick={() => handleSelectStore(store.id)}
              className="group text-left"
              style={{ animationDelay: `${idx * 80}ms` }}
            >
              {/* Double Bezel Card */}
              <div className="bg-ink/5 p-1.5 rounded-[1.5rem] border border-ink/5 shadow-sm transition-all duration-300 group-hover:shadow-md group-hover:scale-[1.01] group-hover:border-sage-dark/20 group-hover:bg-sage-dark/5">
                <div className="bg-white rounded-[calc(1.5rem-0.375rem)] p-5 shadow-[inset_0_1px_1px_rgba(255,255,255,0.8)]">
                  {/* Icon + Name */}
                  <div className="flex items-start justify-between mb-3">
                    <div className="w-10 h-10 rounded-xl bg-sage/10 text-sage-dark flex items-center justify-center">
                      <Storefront size={20} weight="duotone" />
                    </div>
                    <span className="text-[9px] font-bold text-sage-dark bg-sage/10 px-2 py-0.5 rounded-full border border-sage/20">
                      #{store.id}
                    </span>
                  </div>

                  <h3 className="font-bold text-sm text-ink leading-tight mb-3 group-hover:text-sage-dark transition-colors">
                    {store.name}
                  </h3>

                  <div className="space-y-1.5 text-[10px] text-ink/60">
                    <div className="flex items-center gap-1.5">
                      <MapPin size={10} className="shrink-0" />
                      <span className="truncate">{store.address}</span>
                    </div>
                    <div className="flex items-center gap-1.5">
                      <Phone size={10} className="shrink-0" />
                      <span>{store.phone}</span>
                    </div>
                  </div>

                  {/* CTA */}
                  <div className="mt-4 pt-3 border-t border-ink/5 flex items-center justify-between">
                    <span className="text-[10px] font-semibold text-ink/50 group-hover:text-sage-dark transition-colors">
                      Chọn chi nhánh này
                    </span>
                    <div className="w-6 h-6 rounded-full bg-ink/5 group-hover:bg-sage-dark group-hover:text-white flex items-center justify-center transition-all duration-300">
                      <ArrowRight size={11} className="group-hover:translate-x-0.5 transition-transform" />
                    </div>
                  </div>
                </div>
              </div>
            </button>
          ))}

          {/* Thêm chi nhánh mới (chỉ Owner) */}
          {role === 'STORE_OWNER' && (
            <button
              onClick={() => {
                switchStore(stores[0]?.id || 1)
                navigate('/stores')
              }}
              className="group text-left"
            >
              <div className="bg-ink/5 p-1.5 rounded-[1.5rem] border border-dashed border-ink/20 transition-all duration-300 group-hover:border-sage-dark/30 group-hover:bg-sage-dark/5 min-h-[164px]">
                <div className="rounded-[calc(1.5rem-0.375rem)] p-5 h-full flex flex-col items-center justify-center gap-2 text-ink/40 group-hover:text-sage-dark transition-colors">
                  <div className="w-10 h-10 rounded-xl border border-dashed border-ink/20 group-hover:border-sage-dark/40 flex items-center justify-center">
                    <Plus size={18} />
                  </div>
                  <span className="text-xs font-semibold">Thêm chi nhánh mới</span>
                </div>
              </div>
            </button>
          )}
        </div>
      )}
    </div>
  )
}
