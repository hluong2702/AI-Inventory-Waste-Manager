import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Calendar,
  CaretDown,
  CaretUp,
  GridFour,
  HourglassMedium,
  List,
  Tag,
  Warning,
} from '@phosphor-icons/react'
import apiClient from '../api/client'
import Pagination from '../components/Pagination'
import StateView from '../components/StateView'
import { useStore } from '../context/StoreContext'
import type { ApiResponse, InventoryBatch, InventorySummary, PageResponse } from '../types'
import { formatDate, formatVND, getDaysUntilExpiry } from '../utils/fefo'

interface IngredientBatchDetailsProps {
  storeId: number
  ingredientId: number
  unit: string
}

function IngredientBatchDetails({ storeId, ingredientId, unit }: IngredientBatchDetailsProps) {
  const [page, setPage] = useState(0)
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['ingredient-batches', storeId, ingredientId, page],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<InventoryBatch>>>(
        `/inventory/batches?ingredientId=${ingredientId}&page=${page}&size=10&sort=expiryDate,asc`
      )
      return res.data
    },
  })
  const batches = response?.data.content ?? []

  if (isLoading) {
    return <p className="py-4 text-center text-xs text-ink/50">Đang tải chi tiết lô hàng...</p>
  }
  if (isError) {
    return <p className="py-4 text-center text-xs font-semibold text-red-600">Không thể tải chi tiết lô hàng.</p>
  }
  if (batches.length === 0) {
    return <p className="py-4 text-center text-xs italic text-ink/50">Nguyên liệu này hiện không còn lô có tồn kho.</p>
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full border-collapse text-left text-[11px]">
        <thead>
          <tr className="border-b border-ink/5 font-bold text-ink/50">
            <th className="pb-2">Số lô</th>
            <th className="pb-2 text-right">Số lượng tồn</th>
            <th className="pb-2 text-center">Hạn sử dụng</th>
            <th className="pb-2 text-center">Ngày nhập</th>
            <th className="pb-2 text-right">Đơn giá vốn</th>
            <th className="pb-2 text-center">Tình trạng</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-ink/5 text-ink/80">
          {batches.map((batch) => {
            const daysLeft = getDaysUntilExpiry(batch.expiredDate)
            return (
              <tr key={batch.id} className="hover:bg-ink/5">
                <td className="py-2.5 font-mono font-bold text-ink">{batch.batchNumber}</td>
                <td className="py-2.5 text-right font-mono font-bold">{batch.quantity} {unit}</td>
                <td className="py-2.5 text-center font-medium">{formatDate(batch.expiredDate)}</td>
                <td className="py-2.5 text-center text-ink/60">{formatDate(batch.importDate)}</td>
                <td className="py-2.5 text-right font-mono">{formatVND(batch.costPerUnit)}</td>
                <td className="py-2.5 text-center">
                  <ExpiryBadge daysLeft={daysLeft} />
                </td>
              </tr>
            )
          })}
        </tbody>
      </table>
      <Pagination
        page={page}
        totalPages={response?.data.totalPages ?? 0}
        totalElements={response?.data.totalElements ?? 0}
        onPageChange={setPage}
      />
    </div>
  )
}

function ExpiryBadge({ daysLeft }: { daysLeft: number }) {
  const className = daysLeft < 0
    ? 'bg-red-500/10 text-red-600'
    : daysLeft <= 3
      ? 'bg-terracotta/15 text-terracotta'
      : 'bg-sage/15 text-sage-dark'
  const label = daysLeft < 0 ? 'Đã hết hạn' : daysLeft <= 3 ? `Cận hạn (${daysLeft} ngày)` : `Còn ${daysLeft} ngày`

  return <span className={`inline-block rounded-full px-2 py-0.5 text-[9px] font-bold ${className}`}>{label}</span>
}

export default function InventoryPage() {
  const { activeStore } = useStore()
  const [viewMode, setViewMode] = useState<'ingredient' | 'batch'>('ingredient')
  const [page, setPage] = useState(0)
  const [expandedIngredients, setExpandedIngredients] = useState<Record<number, boolean>>({})

  const { data: summaryResponse, isLoading: isLoadingSummary, isError: isSummaryError } = useQuery({
    queryKey: ['inventory-summary', activeStore?.id, page],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<InventorySummary>>>(
        `/inventory/summary?page=${page}&size=20`
      )
      return res.data
    },
    enabled: !!activeStore?.id && viewMode === 'ingredient',
  })

  const { data: batchesResponse, isLoading: isLoadingBatches, isError: isBatchesError } = useQuery({
    queryKey: ['batches', activeStore?.id, page],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<InventoryBatch>>>(
        `/inventory/batches?page=${page}&size=20&sort=expiryDate,asc`
      )
      return res.data
    },
    enabled: !!activeStore?.id && viewMode === 'batch',
  })

  const summaries = summaryResponse?.data.content ?? []
  const batches = batchesResponse?.data.content ?? []
  const isLoading = viewMode === 'ingredient' ? isLoadingSummary : isLoadingBatches
  const isError = viewMode === 'ingredient' ? isSummaryError : isBatchesError

  function switchView(nextMode: 'ingredient' | 'batch') {
    setViewMode(nextMode)
    setPage(0)
  }

  function toggleIngredient(ingredientId: number) {
    setExpandedIngredients((current) => ({
      ...current,
      [ingredientId]: !current[ingredientId],
    }))
  }

  const activeBatches = batches.map((batch) => ({
    ...batch,
    daysLeft: getDaysUntilExpiry(batch.expiredDate),
  }))

  const currentPage = viewMode === 'ingredient' ? summaryResponse?.data : batchesResponse?.data

  return (
    <div className="space-y-6 font-sans">
      <div className="flex flex-col items-start justify-between gap-4 sm:flex-row sm:items-center">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Báo cáo tồn kho</h2>
          <p className="text-xs text-ink/60">Tổng tồn được tính trên toàn bộ dữ liệu của cửa hàng, không phụ thuộc trang lô đang xem.</p>
        </div>

        <div className="flex shrink-0 rounded-xl border border-ink/5 bg-ink/5 p-1 shadow-sm">
          <button
            onClick={() => switchView('ingredient')}
            className={`flex items-center gap-1 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
              viewMode === 'ingredient' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            <List size={14} />
            <span>Gom nhóm</span>
          </button>
          <button
            onClick={() => switchView('batch')}
            className={`flex items-center gap-1 rounded-lg px-3.5 py-1.5 text-xs font-semibold transition-all ${
              viewMode === 'batch' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            <GridFour size={14} />
            <span>Chi tiết lô</span>
          </button>
        </div>
      </div>

      <StateView
        isLoading={isLoading}
        isEmpty={viewMode === 'ingredient' ? summaries.length === 0 : activeBatches.length === 0}
        isError={isError}
      >
        {viewMode === 'ingredient' ? (
          <div className="space-y-4">
            {summaries.map((item) => {
              const isExpanded = Boolean(expandedIngredients[item.ingredientId])
              const isBelowMin = item.sellableQuantity < item.minStock
              const isAboveMax = item.sellableQuantity > item.maxStock
              return (
                <div key={item.ingredientId} className="rounded-[1.5rem] border border-ink/5 bg-ink/5 p-1.5 shadow-sm">
                  <div className="rounded-[calc(1.5rem-0.375rem)] bg-white p-4 shadow-[inset_0_1px_1px_rgba(255,255,255,0.5)]">
                    <button
                      type="button"
                      onClick={() => toggleIngredient(item.ingredientId)}
                      className="flex w-full flex-wrap items-center justify-between gap-4 text-left"
                      aria-expanded={isExpanded}
                    >
                      <div className="flex min-w-[200px] items-center gap-3">
                        <span className="flex h-10 w-10 items-center justify-center rounded-xl bg-sage/10 font-bold text-sage-dark">
                          {item.name.charAt(0)}
                        </span>
                        <div>
                          <div className="text-sm font-bold text-ink">{item.name}</div>
                          <div className="mt-0.5 flex items-center gap-1 text-[10px] text-ink/50">
                            <Tag size={10} />
                            {item.category} · Mã: {item.code}
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center gap-6 text-xs">
                        <div className="text-right">
                          <span className="mb-0.5 block text-[10px] font-semibold uppercase text-ink/60">Tồn khả dụng</span>
                          <span className={`font-mono text-sm font-bold ${isBelowMin ? 'text-terracotta' : 'text-ink'}`}>
                            {item.sellableQuantity} {item.unit}
                          </span>
                          {item.totalQuantity !== item.sellableQuantity && (
                            <span className="block text-[9px] text-ink/45">Tồn vật lý: {item.totalQuantity} {item.unit}</span>
                          )}
                        </div>

                        <div className="hidden text-right sm:block">
                          <span className="mb-0.5 block text-[10px] font-semibold uppercase text-ink/60">Lô còn tồn</span>
                          <span className="font-semibold text-ink">{item.activeBatchesCount} lô hàng</span>
                        </div>

                        <div className="flex max-w-56 flex-wrap justify-end gap-2">
                          {isBelowMin && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-terracotta/15 px-2 py-0.5 text-[9px] font-extrabold uppercase text-terracotta">
                              <Warning size={10} /> Hụt hàng
                            </span>
                          )}
                          {item.expiredBatchesCount > 0 && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-red-500/10 px-2 py-0.5 text-[9px] font-extrabold uppercase text-red-600">
                              <HourglassMedium size={10} /> {item.expiredBatchesCount} lô hết hạn
                            </span>
                          )}
                          {item.expiringSoonBatchesCount > 0 && (
                            <span className="inline-flex items-center gap-1 rounded-full bg-terracotta/15 px-2 py-0.5 text-[9px] font-extrabold uppercase text-terracotta">
                              <HourglassMedium size={10} /> {item.expiringSoonBatchesCount} lô cận hạn
                            </span>
                          )}
                          {!isBelowMin && !isAboveMax && item.expiredBatchesCount === 0 && item.expiringSoonBatchesCount === 0 && (
                            <span className="rounded-full bg-sage/15 px-2 py-0.5 text-[9px] font-extrabold uppercase text-sage-dark">An toàn</span>
                          )}
                        </div>

                        <span className="rounded-lg border border-ink/10 p-1 text-ink/50">
                          {isExpanded ? <CaretUp size={12} weight="bold" /> : <CaretDown size={12} weight="bold" />}
                        </span>
                      </div>
                    </button>

                    {isExpanded && activeStore && (
                      <div className="mt-4 border-t border-ink/10 pt-4">
                        <IngredientBatchDetails
                          storeId={activeStore.id}
                          ingredientId={item.ingredientId}
                          unit={item.unit}
                        />
                      </div>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          <div className="overflow-x-auto rounded-2xl border border-ink/5 bg-white shadow-sm">
            <table className="w-full border-collapse text-left text-xs">
              <thead>
                <tr className="border-b border-ink/10 bg-ink/5 font-bold text-ink/80">
                  <th className="px-6 py-4">Số lô</th>
                  <th className="px-6 py-4">Nguyên liệu</th>
                  <th className="px-6 py-4 text-right">Số lượng tồn</th>
                  <th className="px-6 py-4 text-center">Hạn sử dụng</th>
                  <th className="px-6 py-4 text-center">Ngày nhập</th>
                  <th className="px-6 py-4 text-center">Tình trạng</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink/10 text-ink/80">
                {activeBatches.map((batch) => (
                  <tr key={batch.id} className="transition-colors hover:bg-ink/5">
                    <td className="px-6 py-4 font-mono font-bold text-ink">{batch.batchNumber}</td>
                    <td className="px-6 py-4">
                      <div className="font-semibold text-ink">{batch.ingredientName}</div>
                      <span className="text-[10px] text-ink/50">{batch.ingredientCategory}</span>
                    </td>
                    <td className="px-6 py-4 text-right font-mono font-bold">{batch.quantity} {batch.ingredientUnit}</td>
                    <td className="px-6 py-4 text-center font-medium">
                      <span className="flex items-center justify-center gap-1">
                        <Calendar size={12} /> {formatDate(batch.expiredDate)}
                      </span>
                    </td>
                    <td className="px-6 py-4 text-center text-ink/60">{formatDate(batch.importDate)}</td>
                    <td className="px-6 py-4 text-center"><ExpiryBadge daysLeft={batch.daysLeft} /></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </StateView>

      <Pagination
        page={page}
        totalPages={currentPage?.totalPages ?? 0}
        totalElements={currentPage?.totalElements ?? 0}
        onPageChange={setPage}
      />
    </div>
  )
}
