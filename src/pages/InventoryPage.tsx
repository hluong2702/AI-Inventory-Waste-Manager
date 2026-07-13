import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Ingredient, InventoryBatch } from '../types'
import StateView from '../components/StateView'
import { formatDate, formatVND, getDaysUntilExpiry } from '../utils/fefo'
import { 
  Calendar, 
  Tag, 
  HourglassMedium,
  Warning,
  List,
  GridFour,
  CaretDown,
  CaretUp
} from '@phosphor-icons/react'

export default function InventoryPage() {
  // Chế độ xem: 'ingredient' (gom nhóm) | 'batch' (chi tiết lô)
  const [viewMode, setViewMode] = useState<'ingredient' | 'batch'>('ingredient')
  // Trạng thái mở rộng dòng trong chế độ gom nhóm
  const [expandedIngs, setExpandedIngs] = useState<Record<number, boolean>>({})

  // 1. Tải danh sách nguyên liệu
  const { data: ingResponse, isLoading: isLoadingIngs } = useQuery({
    queryKey: ['ingredients'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data
    }
  })
  const ingredients = ingResponse?.data ?? []

  // 2. Tải danh sách lô hàng trong kho
  const { data: batchesResponse, isLoading: isLoadingBatches, isError } = useQuery({
    queryKey: ['batches'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<InventoryBatch[]>>('/inventory/batches')
      return res.data
    }
  })
  const batches = batchesResponse?.data ?? []

  const isLoading = isLoadingIngs || isLoadingBatches

  function toggleExpandIngredient(id: number) {
    setExpandedIngs((prev) => ({ ...prev, [id]: !prev[id] }))
  }

  // ==========================================
  // XỬ LÝ DỮ LIỆU GOM NHÓM
  // ==========================================
  const groupedStock = ingredients.map((ing) => {
    // Lọc các lô hàng còn tồn kho (> 0) của nguyên liệu này
    const ingBatches = batches.filter((b) => b.ingredientId === ing.id && b.quantity > 0)
    
    // Tính tổng tồn kho khả dụng
    const totalQty = ingBatches.reduce((sum, b) => sum + b.quantity, 0)
    
    // Đếm số lô hàng sắp hết hạn (<= 3 ngày) hoặc đã hết hạn (< 0 ngày)
    const alertBatchesCount = ingBatches.filter((b) => getDaysUntilExpiry(b.expiredDate) <= 3).length

    return {
      ...ing,
      totalQuantity: totalQty,
      batchesCount: ingBatches.length,
      alertBatchesCount,
      batches: ingBatches
    }
  })

  // Lọc các lô còn tồn kho cho viewMode === 'batch'
  const activeBatches = batches
    .filter((b) => b.quantity > 0)
    .map((b) => {
      const ing = ingredients.find((i) => i.id === b.ingredientId)
      const daysLeft = getDaysUntilExpiry(b.expiredDate)
      return {
        ...b,
        ingredientName: ing?.name ?? 'Nguyên liệu ẩn',
        unit: ing?.unit ?? '',
        category: ing?.category ?? 'Chưa phân loại',
        daysLeft
      }
    })
    .sort((a, b) => a.daysLeft - b.daysLeft) // Ưu tiên hết hạn trước xếp đầu

  return (
    <div className="space-y-6 font-sans">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Báo cáo tồn kho</h2>
          <p className="text-xs text-ink/60">Quản lý chi tiết số lượng thực tế trong kho theo lô hàng và hạn sử dụng.</p>
        </div>

        {/* View Mode Switcher */}
        <div className="flex bg-ink/5 p-1 rounded-xl border border-ink/5 shadow-sm shrink-0">
          <button
            onClick={() => setViewMode('ingredient')}
            className={`flex items-center gap-1 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              viewMode === 'ingredient' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            <List size={14} />
            <span>Gom nhóm</span>
          </button>
          <button
            onClick={() => setViewMode('batch')}
            className={`flex items-center gap-1 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all ${
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
        isEmpty={viewMode === 'ingredient' ? groupedStock.length === 0 : activeBatches.length === 0} 
        isError={isError}
      >
        
        {viewMode === 'ingredient' ? (
          // ==========================================
          // CHẾ ĐỘ XEM 1: GOM NHÓM THEO NGUYÊN LIỆU
          // ==========================================
          <div className="space-y-4">
            {groupedStock.map((item) => {
              const isExpanded = !!expandedIngs[item.id]
              const isBelowMin = item.totalQuantity < item.minStock
              const isAboveMax = item.totalQuantity > item.maxStock

              return (
                <div key={item.id} className="bg-ink/5 p-1.5 rounded-[1.5rem] border border-ink/5 shadow-sm">
                  <div className="bg-white rounded-[calc(1.5rem-0.375rem)] p-4 shadow-[inset_0_1px_1px_rgba(255,255,255,0.5)]">
                    
                    {/* Hàng thông tin tổng quan */}
                    <div 
                      onClick={() => toggleExpandIngredient(item.id)}
                      className="flex flex-wrap items-center justify-between gap-4 cursor-pointer select-none"
                    >
                      <div className="flex items-center gap-3 min-w-[200px]">
                        <span className="w-10 h-10 rounded-xl bg-sage/10 text-sage-dark flex items-center justify-center font-bold">
                          {item.name.charAt(0)}
                        </span>
                        <div>
                          <div className="font-bold text-sm text-ink">{item.name}</div>
                          <div className="text-[10px] text-ink/50 mt-0.5 flex items-center gap-1">
                            <Tag size={10} />
                            {item.category} · Mã: {item.code}
                          </div>
                        </div>
                      </div>

                      {/* Thống kê tồn kho */}
                      <div className="flex gap-8 items-center text-xs">
                        <div className="text-right">
                          <span className="text-ink/60 text-[10px] uppercase font-semibold block mb-0.5">Tổng tồn kho</span>
                          <span className={`font-mono font-bold text-sm ${isBelowMin ? 'text-terracotta' : 'text-ink'}`}>
                            {item.totalQuantity} {item.unit}
                          </span>
                        </div>

                        <div className="text-right hidden sm:block">
                          <span className="text-ink/60 text-[10px] uppercase font-semibold block mb-0.5">Số lô hoạt động</span>
                          <span className="font-semibold text-ink">
                            {item.batchesCount} lô hàng
                          </span>
                        </div>

                        {/* Badges Cảnh báo */}
                        <div className="flex gap-2">
                          {isBelowMin && (
                            <span className="inline-flex items-center gap-1 text-[9px] font-extrabold uppercase bg-terracotta/15 text-terracotta px-2 py-0.5 rounded-full">
                              <Warning size={10} />
                              Hụt hàng
                            </span>
                          )}
                          {item.alertBatchesCount > 0 && (
                            <span className="inline-flex items-center gap-1 text-[9px] font-extrabold uppercase bg-red-500/10 text-red-600 px-2 py-0.5 rounded-full">
                              <HourglassMedium size={10} />
                              {item.alertBatchesCount} lô cận hạn
                            </span>
                          )}
                          {!isBelowMin && !isAboveMax && item.alertBatchesCount === 0 && (
                            <span className="inline-flex items-center gap-0.5 text-[9px] font-extrabold uppercase bg-sage/15 text-sage-dark px-2 py-0.5 rounded-full">
                              An toàn
                            </span>
                          )}
                        </div>

                        {/* Nút Caret để expand */}
                        <div className="p-1 rounded-lg border border-ink/10 text-ink/50 hover:text-ink">
                          {isExpanded ? <CaretUp size={12} weight="bold" /> : <CaretDown size={12} weight="bold" />}
                        </div>
                      </div>
                    </div>

                    {/* Danh sách các lô chi tiết (Khi được mở rộng) */}
                    {isExpanded && (
                      <div className="mt-4 pt-4 border-t border-ink/10 animate-slide-down">
                        {item.batches.length === 0 ? (
                          <p className="text-xs text-ink/50 py-2 italic text-center">Nguyên liệu này hiện đang hết hàng hoàn toàn trong kho.</p>
                        ) : (
                          <div className="overflow-x-auto">
                            <table className="w-full text-left text-[11px] border-collapse">
                              <thead>
                                <tr className="text-ink/50 font-bold border-b border-ink/5">
                                  <th className="pb-2">Số lô (Batch)</th>
                                  <th className="pb-2 text-right">Số lượng tồn</th>
                                  <th className="pb-2 text-center">Hạn sử dụng</th>
                                  <th className="pb-2 text-center">Ngày nhập</th>
                                  <th className="pb-2 text-right">Đơn giá vốn</th>
                                  <th className="pb-2 text-center">Tình trạng HSD</th>
                                </tr>
                              </thead>
                              <tbody className="divide-y divide-ink/5 text-ink/80">
                                {item.batches.map((batch) => {
                                  const daysLeft = getDaysUntilExpiry(batch.expiredDate)
                                  return (
                                    <tr key={batch.id} className="hover:bg-ink/5">
                                      <td className="py-2.5 font-mono font-bold text-ink">{batch.batchNumber}</td>
                                      <td className="py-2.5 text-right font-mono font-bold">{batch.quantity} {item.unit}</td>
                                      <td className="py-2.5 text-center font-medium">{formatDate(batch.expiredDate)}</td>
                                      <td className="py-2.5 text-center text-ink/60">{formatDate(batch.importDate)}</td>
                                      <td className="py-2.5 text-right font-mono">{formatVND(batch.costPerUnit)}</td>
                                      <td className="py-2.5 text-center">
                                        <span className={`inline-block px-2 py-0.5 rounded-full text-[9px] font-bold ${
                                          daysLeft < 0
                                            ? 'bg-red-500/10 text-red-600'
                                            : daysLeft <= 3
                                            ? 'bg-terracotta/15 text-terracotta'
                                            : 'bg-sage/15 text-sage-dark'
                                        }`}>
                                          {daysLeft < 0 ? 'Đã hết hạn' : daysLeft <= 3 ? `Cận hạn (${daysLeft} ngày)` : `Còn ${daysLeft} ngày`}
                                        </span>
                                      </td>
                                    </tr>
                                  )
                                })}
                              </tbody>
                            </table>
                          </div>
                        )}
                      </div>
                    )}

                  </div>
                </div>
              )
            })}
          </div>
        ) : (
          // ==========================================
          // CHẾ ĐỘ XEM 2: CHI TIẾT THEO LÔ HÀNG (FLAT LIST)
          // ==========================================
          <div className="bg-white rounded-2xl border border-ink/5 overflow-hidden shadow-sm">
            <table className="w-full text-left border-collapse text-xs">
              <thead>
                <tr className="bg-ink/5 text-ink/80 font-bold border-b border-ink/10">
                  <th className="px-6 py-4">Số lô</th>
                  <th className="px-6 py-4">Nguyên liệu</th>
                  <th className="px-6 py-4 text-right">Số lượng tồn</th>
                  <th className="px-6 py-4 text-center">Hạn sử dụng</th>
                  <th className="px-6 py-4 text-center">Ngày nhập</th>
                  <th className="px-6 py-4">Hạn dùng trực quan</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink/10 text-ink/80">
                {activeBatches.map((batch) => {
                  const isExpired = batch.daysLeft < 0
                  const isNear = batch.daysLeft >= 0 && batch.daysLeft <= 3
                  const isWarning = batch.daysLeft > 3 && batch.daysLeft <= 10

                  // Tính phần trăm hiển thị trên Progress Bar giả lập
                  // Giả lập chu kỳ hạn sử dụng tối đa của nguyên liệu F&B là 30 ngày để tính %
                  const maxDuration = 30
                  const percent = isExpired ? 100 : Math.min(100, ((maxDuration - batch.daysLeft) / maxDuration) * 100)

                  return (
                    <tr key={batch.id} className="hover:bg-ink/5 transition-colors">
                      <td className="px-6 py-4 font-mono font-bold text-ink">{batch.batchNumber}</td>
                      <td className="px-6 py-4">
                        <div className="font-semibold text-ink">{batch.ingredientName}</div>
                        <span className="text-[10px] text-ink/50">{batch.category}</span>
                      </td>
                      <td className="px-6 py-4 text-right font-mono font-bold">{batch.quantity} {batch.unit}</td>
                      <td className="px-6 py-4 text-center font-medium">
                        <span className="flex items-center justify-center gap-1">
                          <Calendar size={12} />
                          {formatDate(batch.expiredDate)}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-center text-ink/60">{formatDate(batch.importDate)}</td>
                      <td className="px-6 py-4 w-60">
                        <div className="space-y-1">
                          <div className="flex justify-between items-center text-[10px]">
                            <span className={`font-bold ${
                              isExpired ? 'text-red-600' : isNear ? 'text-terracotta' : 'text-sage-dark'
                            }`}>
                              {isExpired ? 'Đã hết hạn' : isNear ? `Cận hạn (${batch.daysLeft} ngày)` : `Còn ${batch.daysLeft} ngày`}
                            </span>
                            <span className="text-ink/50 font-mono">{batch.daysLeft > 0 ? `${batch.daysLeft}d` : '0d'}</span>
                          </div>
                          
                          {/* Progress Bar trực quan */}
                          <div className="w-full bg-ink/5 h-2 rounded-full overflow-hidden">
                            <div 
                              className={`h-full rounded-full transition-all duration-500 ${
                                isExpired 
                                  ? 'bg-red-500' 
                                  : isNear 
                                  ? 'bg-red-500 animate-pulse' 
                                  : isWarning 
                                  ? 'bg-terracotta' 
                                  : 'bg-sage-dark'
                              }`}
                              style={{ width: `${isExpired ? 100 : 100 - percent}%` }}
                            ></div>
                          </div>
                        </div>
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
        
      </StateView>
    </div>
  )
}
