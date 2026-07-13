import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, Ingredient, StockTransaction, InventoryBatch } from '../types'
import { useStore } from '../context/StoreContext'
import { useAuth } from '../context/AuthContext'
import DoubleBezelCard from '../components/DoubleBezelCard'
import StateView from '../components/StateView'
import { formatVND, formatDate } from '../utils/fefo'
import { Plus, Trash, ArrowUp, ArrowDown, ListDashes, Info } from '@phosphor-icons/react'

export default function TransactionsPage() {
  const { activeStore } = useStore()
  const { username } = useAuth()
  const queryClient = useQueryClient()
  
  // Tab hiện tại: 'history' | 'create'
  const [activeTab, setActiveTab] = useState<'history' | 'create'>('history')

  // Form State cho phiếu giao dịch mới
  const [txType, setTxType] = useState<'IMPORT' | 'EXPORT'>('IMPORT')
  const [txReason, setTxReason] = useState<string>('IMPORT_NEW')
  const [wasteReason, setWasteReason] = useState<'EXPIRED' | 'DAMAGED' | 'PREP_ERROR' | 'OTHER'>('EXPIRED')
  
  // Danh sách nguyên liệu trong phiếu hiện tại
  interface FormItem {
    ingredientId: number
    batchNumber: string
    quantity: number
    expiredDate: string
    costPerUnit: number
  }
  const [formItems, setFormItems] = useState<FormItem[]>([
    { ingredientId: 0, batchNumber: '', quantity: 1, expiredDate: '', costPerUnit: 0 }
  ])

  const [formError, setFormError] = useState<string | null>(null)
  const [formSuccess, setFormSuccess] = useState<string | null>(null)

  // 1. Tải danh sách nguyên liệu hoạt động
  const { data: ingResponse } = useQuery({
    queryKey: ['ingredients'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data
    }
  })
  const ingredients = ingResponse?.data ?? []

  // 2. Tải danh sách lô hàng trong kho (để xem tồn kho hiện tại)
  const { data: batchesResponse } = useQuery({
    queryKey: ['batches'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<InventoryBatch[]>>('/inventory/batches')
      return res.data
    }
  })
  const batches = batchesResponse?.data ?? []

  // 3. Tải lịch sử giao dịch
  const { data: txResponse, isLoading: isLoadingTxs, isError } = useQuery({
    queryKey: ['transactions', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<StockTransaction[]>>('/reports/transactions')
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const transactions = txResponse?.data ?? []

  // 4. Mutation gửi phiếu giao dịch lên backend
  const createTxMutation = useMutation({
    mutationFn: async (payload: any) => {
      await apiClient.post('/inventory/transactions', payload)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['transactions'] })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
      queryClient.invalidateQueries({ queryKey: ['alerts'] })
      queryClient.invalidateQueries({ queryKey: ['waste-logs'] })
      queryClient.invalidateQueries({ queryKey: ['reports-waste'] })
      queryClient.invalidateQueries({ queryKey: ['waste-dashboard'] })
      queryClient.invalidateQueries({ queryKey: ['audit-log'] })
      setFormSuccess('Tạo phiếu giao dịch và cập nhật kho thành công!')
      setFormItems([{ ingredientId: 0, batchNumber: '', quantity: 1, expiredDate: '', costPerUnit: 0 }])
      setFormError(null)
      // Chuyển về tab lịch sử sau 1.5 giây
      setTimeout(() => {
        setActiveTab('history')
        setFormSuccess(null)
      }, 1500)
    },
    onError: (err: any) => {
      setFormError(err.response?.data?.message || err.message || 'Lỗi xử lý giao dịch.')
    }
  })

  // Thêm một dòng nguyên liệu trong form phiếu
  function addFormRow() {
    setFormItems([...formItems, { ingredientId: 0, batchNumber: '', quantity: 1, expiredDate: '', costPerUnit: 0 }])
  }

  // Xóa một dòng nguyên liệu trong form phiếu
  function removeFormRow(idx: number) {
    if (formItems.length === 1) return
    setFormItems(formItems.filter((_, i) => i !== idx))
  }

  // Cập nhật giá trị một cột của dòng cụ thể
  function updateFormItem(idx: number, field: keyof FormItem, val: any) {
    const updated = [...formItems]
    updated[idx] = { ...updated[idx], [field]: val }
    
    // Tự sinh mã lô gợi ý khi chọn nguyên liệu (IMPORT)
    if (field === 'ingredientId' && txType === 'IMPORT') {
      const ing = ingredients.find(i => i.id === Number(val))
      if (ing) {
        updated[idx].batchNumber = `LOT-${ing.code}-${new Date().toISOString().split('T')[0].replace(/-/g, '')}`
      }
    }
    setFormItems(updated)
  }

  // Gửi phiếu
  function handleFormSubmit(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)
    setFormSuccess(null)

    // Validate
    const invalidItem = formItems.find(item => item.ingredientId === 0 || item.quantity <= 0)
    if (invalidItem) {
      setFormError('Vui lòng chọn nguyên liệu và nhập số lượng lớn hơn 0.')
      return
    }

    if (txType === 'IMPORT') {
      const missingBatchOrDate = formItems.find(item => !item.batchNumber || !item.expiredDate)
      if (missingBatchOrDate) {
        setFormError('Giao dịch nhập kho yêu cầu điền đầy đủ Số lô và Ngày hết hạn.')
        return
      }
    }

    // Kiểm tra xem có xuất kho âm hay không trước khi gửi (Client-side fast validation)
    if (txType === 'EXPORT') {
      for (const item of formItems) {
        const totalStock = batches
          .filter(b => b.ingredientId === item.ingredientId)
          .reduce((sum, b) => sum + b.quantity, 0)
        
        if (totalStock < item.quantity) {
          const ing = ingredients.find(i => i.id === item.ingredientId)
          setFormError(`Không đủ tồn kho cho nguyên liệu "${ing?.name}". Hiện tại chỉ còn ${totalStock} ${ing?.unit} khả dụng.`);
          return
        }
      }
    }

    const payload = {
      type: txType,
      reason: txReason,
      recordedBy: username || 'staff',
      wasteReason: txReason === 'EXPORT_WASTE' ? wasteReason : undefined,
      items: formItems.map(item => ({
        ingredientId: item.ingredientId,
        batchNumber: txType === 'IMPORT' ? item.batchNumber : undefined,
        quantity: item.quantity,
        expiredDate: txType === 'IMPORT' ? item.expiredDate : undefined,
        costPerUnit: txType === 'IMPORT' ? item.costPerUnit : undefined
      }))
    }

    createTxMutation.mutate(payload)
  }

  // Khi thay đổi Type (Nhập/Xuất), reset lại các reason tương ứng
  function handleTypeChange(type: 'IMPORT' | 'EXPORT') {
    setTxType(type)
    setTxReason(type === 'IMPORT' ? 'IMPORT_NEW' : 'EXPORT_CONSUME')
  }

  return (
    <div className="space-y-6 font-sans">
      
      {/* Header */}
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Giao dịch kho</h2>
          <p className="text-xs text-ink/60">Nhập nguyên liệu mới theo lô hoặc xuất tiêu hao, hủy hàng lãng phí.</p>
        </div>

        {/* Tab switcher */}
        <div className="flex bg-ink/5 p-1 rounded-xl border border-ink/5">
          <button
            onClick={() => setActiveTab('history')}
            className={`flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'history' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            <ListDashes size={14} />
            <span>Lịch sử phiếu</span>
          </button>
          <button
            onClick={() => setActiveTab('create')}
            className={`flex items-center gap-1.5 px-4 py-1.5 rounded-lg text-xs font-semibold transition-all ${
              activeTab === 'create' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
            }`}
          >
            <Plus size={14} />
            <span>Tạo phiếu mới</span>
          </button>
        </div>
      </div>

      {activeTab === 'history' ? (
        // ==========================================
        // TAB 1: LỊCH SỬ GIAO DỊCH KHO
        // ==========================================
        <StateView 
          isLoading={isLoadingTxs} 
          isEmpty={transactions.length === 0} 
          isError={isError}
          emptyTitle="Chưa có giao dịch"
          emptySubtitle="Bấm tạo phiếu mới để nhập hoặc xuất kho nguyên liệu."
        >
          <div className="space-y-4">
            {[...transactions].reverse().map((tx) => (
              <DoubleBezelCard
                key={tx.id}
                title={`Phiếu ${tx.type === 'IMPORT' ? 'Nhập kho' : 'Xuất kho'} #${tx.id}`}
                subtitle={`Thực hiện bởi: @${tx.recordedBy} · Thời gian: ${new Date(tx.createdAt).toLocaleString('vi-VN')}`}
                action={
                  <span className={`inline-flex items-center gap-1 px-3 py-1 rounded-full text-xs font-bold ${
                    tx.type === 'IMPORT' ? 'bg-sage/15 text-sage-dark' : 'bg-terracotta/15 text-terracotta'
                  }`}>
                    {tx.type === 'IMPORT' ? <ArrowDown size={12} /> : <ArrowUp size={12} />}
                    {tx.reason === 'IMPORT_NEW' 
                      ? 'Nhập lô mới' 
                      : tx.reason === 'EXPORT_CONSUME' 
                      ? 'Xuất bán hàng' 
                      : tx.reason === 'EXPORT_WASTE' 
                      ? 'Hủy lãng phí' 
                      : 'Điều chỉnh kho'}
                  </span>
                }
              >
                {/* Bảng chi tiết mặt hàng trong phiếu */}
                <div className="overflow-x-auto">
                  <table className="w-full text-left text-xs border-t border-ink/10 mt-1">
                    <thead>
                      <tr className="text-ink/60 font-semibold border-b border-ink/5">
                        <th className="py-2.5">Nguyên liệu</th>
                        <th className="py-2.5 text-center">Số lô</th>
                        <th className="py-2.5 text-right">Số lượng</th>
                        {tx.type === 'IMPORT' && (
                          <>
                            <th className="py-2.5 text-center">Hạn sử dụng</th>
                            <th className="py-2.5 text-right">Đơn giá nhập</th>
                          </>
                        )}
                      </tr>
                    </thead>
                    <tbody className="divide-y divide-ink/5 text-ink/80">
                      {tx.items.map((item, idx) => {
                        const ing = ingredients.find(i => i.id === item.ingredientId)
                        return (
                          <tr key={idx}>
                            <td className="py-2 font-medium text-ink">{ing?.name ?? `Nguyên liệu #${item.ingredientId}`}</td>
                            <td className="py-2 text-center font-mono">{item.batchNumber || '-'}</td>
                            <td className="py-2 text-right font-semibold font-mono">{item.quantity} {ing?.unit}</td>
                            {tx.type === 'IMPORT' && (
                              <>
                                <td className="py-2 text-center">{formatDate(item.expiredDate || '')}</td>
                                <td className="py-2 text-right font-mono">{formatVND(item.costPerUnit || 0)}</td>
                              </>
                            )}
                          </tr>
                        )
                      })}
                    </tbody>
                  </table>
                </div>
              </DoubleBezelCard>
            ))}
          </div>
        </StateView>
      ) : (
        // ==========================================
        // TAB 2: TẠO PHIẾU GIAO DỊCH MỚI
        // ==========================================
        <div className="max-w-4xl mx-auto">
          <DoubleBezelCard 
            title="Lập phiếu nhập xuất kho" 
            subtitle="Hệ thống tự động phân bổ FEFO (lô sắp hết hạn xuất trước) khi thực hiện Xuất kho."
          >
            {formError && (
              <div className="bg-terracotta/10 border border-terracotta/20 text-terracotta p-3 rounded-xl text-xs font-semibold mb-4">
                {formError}
              </div>
            )}

            {formSuccess && (
              <div className="bg-sage/10 border border-sage/20 text-sage-dark p-3 rounded-xl text-xs font-semibold mb-4 animate-pulse">
                {formSuccess}
              </div>
            )}

            <form onSubmit={handleFormSubmit} className="space-y-6">
              
              {/* Cấu hình chung của phiếu */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-6 bg-offwhite/50 p-4 rounded-2xl border border-ink/5">
                <div>
                  <label className="block text-xs font-bold text-ink mb-1.5">Loại giao dịch</label>
                  <div className="flex bg-ink/5 p-1 rounded-xl">
                    <button
                      type="button"
                      onClick={() => handleTypeChange('IMPORT')}
                      className={`flex-1 text-center py-1.5 rounded-lg text-xs font-bold transition-all ${
                        txType === 'IMPORT' ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60'
                      }`}
                    >
                      Nhập kho
                    </button>
                    <button
                      type="button"
                      onClick={() => handleTypeChange('EXPORT')}
                      className={`flex-1 text-center py-1.5 rounded-lg text-xs font-bold transition-all ${
                        txType === 'EXPORT' ? 'bg-white text-terracotta shadow-sm' : 'text-ink/60'
                      }`}
                    >
                      Xuất kho
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold text-ink mb-1.5">Lý do giao dịch</label>
                  <select
                    value={txReason}
                    onChange={(e) => setTxReason(e.target.value)}
                    className="w-full bg-white border border-ink/10 rounded-xl px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage"
                  >
                    {txType === 'IMPORT' ? (
                      <option value="IMPORT_NEW">Nhập hàng mới từ nhà cung cấp</option>
                    ) : (
                      <>
                        <option value="EXPORT_CONSUME">Xuất tiêu hao chế biến bán hàng</option>
                        <option value="EXPORT_WASTE">Báo hủy lãng phí (hết hạn, hỏng...)</option>
                        <option value="EXPORT_ADJUST">Điều chỉnh cân đối kho</option>
                      </>
                    )}
                  </select>
                </div>

                {txReason === 'EXPORT_WASTE' && (
                  <div>
                    <label className="block text-xs font-bold text-ink mb-1.5">Lý do hủy nguyên liệu</label>
                    <select
                      value={wasteReason}
                      onChange={(e) => setWasteReason(e.target.value as any)}
                      className="w-full bg-white border border-ink/10 rounded-xl px-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage"
                    >
                      <option value="EXPIRED">Quá hạn sử dụng</option>
                      <option value="DAMAGED">Hao hụt/Hư hỏng vật lý</option>
                      <option value="PREP_ERROR">Sai hỏng do chế biến</option>
                      <option value="OTHER">Lý do khác</option>
                    </select>
                  </div>
                )}
              </div>

              {/* Chi tiết nguyên liệu trong phiếu */}
              <div className="space-y-4">
                <div className="flex items-center justify-between border-b border-ink/10 pb-2">
                  <h4 className="text-xs font-bold text-ink uppercase tracking-wider">Danh sách nguyên vật liệu</h4>
                  {txType === 'EXPORT' && (
                    <span className="text-[10px] text-terracotta font-medium flex items-center gap-1">
                      <Info size={12} />
                      Tự động xuất theo FEFO (Lô gần hết hạn trước)
                    </span>
                  )}
                </div>

                {formItems.map((item, idx) => (
                  <div key={idx} className="grid grid-cols-1 md:grid-cols-12 gap-3 items-end bg-offwhite/20 p-3 rounded-xl border border-ink/5">
                    
                    {/* Chọn nguyên liệu */}
                    <div className="md:col-span-3">
                      <label className="block text-[10px] font-semibold text-ink/70 mb-1">Nguyên liệu</label>
                      <select
                        value={item.ingredientId}
                        onChange={(e) => updateFormItem(idx, 'ingredientId', Number(e.target.value))}
                        className="w-full bg-white border border-ink/15 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage"
                      >
                        <option value={0}>-- Chọn nguyên liệu --</option>
                        {ingredients.map(ing => (
                          <option key={ing.id} value={ing.id}>{ing.name} ({ing.unit})</option>
                        ))}
                      </select>
                    </div>

                    {/* Số lượng */}
                    <div className="md:col-span-2">
                      <label className="block text-[10px] font-semibold text-ink/70 mb-1">Số lượng</label>
                      <input
                        type="number"
                        min={0.1}
                        step="any"
                        placeholder="Số lượng"
                        value={item.quantity}
                        onChange={(e) => updateFormItem(idx, 'quantity', parseFloat(e.target.value) || 0)}
                        className="w-full bg-white border border-ink/15 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage font-mono"
                      />
                    </div>

                    {txType === 'IMPORT' ? (
                      <>
                        {/* Số lô */}
                        <div className="md:col-span-2">
                          <label className="block text-[10px] font-semibold text-ink/70 mb-1">Số lô hàng</label>
                          <input
                            type="text"
                            placeholder="LOT-XXX"
                            value={item.batchNumber}
                            onChange={(e) => updateFormItem(idx, 'batchNumber', e.target.value)}
                            className="w-full bg-white border border-ink/15 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage font-mono"
                          />
                        </div>

                        {/* Hạn sử dụng */}
                        <div className="md:col-span-3">
                          <label className="block text-[10px] font-semibold text-ink/70 mb-1">Hạn sử dụng</label>
                          <input
                            type="date"
                            value={item.expiredDate}
                            onChange={(e) => updateFormItem(idx, 'expiredDate', e.target.value)}
                            className="w-full bg-white border border-ink/15 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage"
                          />
                        </div>

                        {/* Đơn giá nhập */}
                        <div className="md:col-span-2">
                          <label className="block text-[10px] font-semibold text-ink/70 mb-1">Đơn giá</label>
                          <input
                            type="number"
                            placeholder="đ/đơn vị"
                            value={item.costPerUnit}
                            onChange={(e) => updateFormItem(idx, 'costPerUnit', parseInt(e.target.value) || 0)}
                            className="w-full bg-white border border-ink/15 rounded-lg px-2.5 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage font-mono"
                          />
                        </div>
                      </>
                    ) : (
                      // Hiển thị thông tin tồn kho khả dụng để nhân viên biết
                      <div className="md:col-span-6 flex items-center h-9 pl-2 text-xs text-ink/60">
                        {item.ingredientId > 0 && (
                          <span>
                            Tồn kho khả dụng: <span className="font-bold font-mono text-ink">
                              {batches
                                .filter(b => b.ingredientId === item.ingredientId)
                                .reduce((sum, b) => sum + b.quantity, 0)}
                            </span> {ingredients.find(i => i.id === item.ingredientId)?.unit}
                          </span>
                        )}
                      </div>
                    )}

                    {/* Nút xóa dòng */}
                    <div className="md:col-span-1 flex justify-center pb-1">
                      <button
                        type="button"
                        onClick={() => removeFormRow(idx)}
                        disabled={formItems.length === 1}
                        className="p-1.5 rounded-lg text-terracotta border border-terracotta/20 hover:bg-terracotta/5 disabled:opacity-30 transition-colors"
                      >
                        <Trash size={14} />
                      </button>
                    </div>

                  </div>
                ))}

                <button
                  type="button"
                  onClick={addFormRow}
                  className="flex items-center gap-1.5 border border-dashed border-ink/20 hover:border-sage-dark/40 rounded-xl px-4 py-2.5 text-xs font-semibold text-ink/75 hover:text-sage-dark w-full justify-center transition-colors"
                >
                  <Plus size={14} />
                  <span>Thêm dòng nguyên liệu</span>
                </button>
              </div>

              {/* Action Buttons */}
              <div className="flex justify-end gap-3 pt-6 border-t border-ink/10">
                <button
                  type="button"
                  onClick={() => setActiveTab('history')}
                  className="px-4 py-2 rounded-xl border border-ink/10 text-xs font-semibold hover:bg-ink/5 transition-colors"
                >
                  Hủy bỏ
                </button>
                <button
                  type="submit"
                  disabled={createTxMutation.isPending}
                  className={`px-5 py-2.5 text-white rounded-xl text-xs font-bold transition-all shadow-sm ${
                    txType === 'IMPORT' ? 'bg-sage-dark hover:bg-sage' : 'bg-terracotta hover:bg-opacity-90'
                  } disabled:opacity-50`}
                >
                  {createTxMutation.isPending ? 'Đang thực thi giao dịch...' : `Xác nhận ${txType === 'IMPORT' ? 'Nhập kho' : 'Xuất kho'}`}
                </button>
              </div>

            </form>
          </DoubleBezelCard>
        </div>
      )}
    </div>
  )
}
