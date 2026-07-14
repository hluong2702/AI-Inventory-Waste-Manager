import { CaretLeft, CaretRight } from '@phosphor-icons/react'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  onPageChange: (page: number) => void
  dark?: boolean
}

export default function Pagination({ page, totalPages, totalElements, onPageChange, dark = false }: PaginationProps) {
  if (totalPages <= 1) return null
  const text = dark ? 'text-white/50' : 'text-ink/55'
  const button = dark ? 'border-white/10 text-white disabled:text-white/20' : 'border-ink/10 text-ink disabled:text-ink/25'
  return (
    <div className={`flex items-center justify-between gap-3 pt-4 text-xs ${text}`}>
      <span>{totalElements} bản ghi · Trang {page + 1}/{totalPages}</span>
      <div className="flex gap-2">
        <button aria-label="Trang trước" disabled={page === 0} onClick={() => onPageChange(page - 1)} className={`rounded-lg border p-2 disabled:cursor-not-allowed ${button}`}><CaretLeft size={14} /></button>
        <button aria-label="Trang sau" disabled={page + 1 >= totalPages} onClick={() => onPageChange(page + 1)} className={`rounded-lg border p-2 disabled:cursor-not-allowed ${button}`}><CaretRight size={14} /></button>
      </div>
    </div>
  )
}
