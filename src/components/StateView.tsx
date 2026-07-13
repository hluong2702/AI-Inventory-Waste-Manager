import type { ReactNode } from 'react'

interface StateViewProps {
  isLoading: boolean
  isEmpty?: boolean
  isError?: boolean
  errorMessage?: string
  emptyTitle?: string
  emptySubtitle?: string
  loadingCount?: number
  children: ReactNode
}

/**
 * Component hiển thị các trạng thái Loading (Skeleton), Empty State và Error State một cách đồng bộ
 */
export default function StateView({
  isLoading,
  isEmpty = false,
  isError = false,
  errorMessage = 'Đã có lỗi xảy ra. Vui lòng thử lại sau.',
  emptyTitle = 'Không có dữ liệu',
  emptySubtitle = 'Chưa có thông tin hiển thị ở mục này.',
  loadingCount = 3,
  children,
}: StateViewProps) {
  if (isError) {
    return (
      <div className="bg-terracotta/10 border border-terracotta/20 text-terracotta p-5 rounded-2xl flex flex-col items-center justify-center text-center my-6">
        <span className="text-3xl mb-2">⚠️</span>
        <h4 className="font-bold text-sm">Lỗi tải dữ liệu</h4>
        <p className="text-xs text-ink/80 mt-1 max-w-md">{errorMessage}</p>
      </div>
    )
  }

  if (isLoading) {
    return (
      <div className="space-y-4 w-full">
        {Array.from({ length: loadingCount }).map((_, i) => (
          <div key={i} className="bg-white/60 border border-ink/5 p-5 rounded-2xl animate-pulse flex flex-col gap-3">
            <div className="h-4 bg-ink/10 rounded w-1/4"></div>
            <div className="h-3 bg-ink/10 rounded w-3/4"></div>
            <div className="h-3 bg-ink/10 rounded w-1/2"></div>
          </div>
        ))}
      </div>
    )
  }

  if (isEmpty) {
    return (
      <div className="border border-dashed border-ink/15 rounded-2xl p-10 flex flex-col items-center justify-center text-center my-4 bg-white/40">
        <span className="text-4xl mb-3 opacity-60">📦</span>
        <h4 className="font-bold text-ink text-sm">{emptyTitle}</h4>
        <p className="text-xs text-ink/60 mt-1 max-w-sm leading-relaxed">{emptySubtitle}</p>
      </div>
    )
  }

  return <>{children}</>
}
