import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import * as zod from 'zod'
import apiClient from '../api/client'
import { useStore } from '../context/StoreContext'
import type { ApiResponse, Ingredient, IngredientImportResult } from '../types'
import { useAuth } from '../context/AuthContext'
import { useSubscriptionStore } from '../stores/subscriptionStore'
import StateView from '../components/StateView'
import { Plus, Pencil, Trash, X, Cube, Tag, Check, ShieldWarning, UploadSimple, DownloadSimple } from '@phosphor-icons/react'
import { apiErrorMessage } from '../utils/apiResponse'

// Schema validate cho Nguyên liệu F&B
const ingredientSchema = zod.object({
  code: zod.string()
    .min(3, 'Mã nguyên liệu phải từ 3 ký tự')
    .regex(/^[A-Z0-9-]+$/, 'Mã chỉ được chứa chữ hoa, số và dấu gạch ngang'),
  name: zod.string().min(2, 'Tên nguyên liệu phải từ 2 ký tự'),
  unit: zod.string().min(1, 'Đơn vị tính không được để trống'),
  category: zod.string().min(1, 'Danh mục không được để trống'),
  minStock: zod.number().min(0, 'Ngưỡng tồn tối thiểu phải lớn hơn hoặc bằng 0'),
  maxStock: zod.number().min(0, 'Ngưỡng tồn tối đa phải lớn hơn hoặc bằng 0')
}).refine((data) => data.maxStock >= data.minStock, {
  message: 'Ngưỡng tồn tối đa không thể nhỏ hơn ngưỡng tồn tối thiểu',
  path: ['maxStock']
})

type IngredientFormValues = zod.infer<typeof ingredientSchema>

export default function IngredientsPage() {
  const { activeStore } = useStore()
  const { role } = useAuth()
  const { current: subscription, isAtLimit, setLimitBanner } = useSubscriptionStore()
  const queryClient = useQueryClient()
  const [editingIng, setEditingIng] = useState<Ingredient | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [importMessage, setImportMessage] = useState<string | null>(null)

  // 1. Tải danh sách nguyên liệu
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['ingredients', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data
    }
  })
  const ingredients = response?.data ?? []

  // Setup React Hook Form
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors }
  } = useForm<IngredientFormValues>({
    resolver: zodResolver(ingredientSchema),
    defaultValues: { code: '', name: '', unit: '', category: '', minStock: 0, maxStock: 100 }
  })

  // 2. Mutation thêm nguyên liệu
  const createMutation = useMutation({
    mutationFn: async (values: IngredientFormValues) => {
      await apiClient.post('/ingredients', values)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ingredients'] })
      closeModal()
    }
  })

  // 3. Mutation cập nhật nguyên liệu
  const updateMutation = useMutation({
    mutationFn: async (values: IngredientFormValues & { id: number }) => {
      await apiClient.put(`/ingredients/${values.id}`, values)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ingredients'] })
      closeModal()
    }
  })

  // 4. Mutation dừng hoạt động (xóa mềm) nguyên liệu
  const deleteMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/ingredients/${id}`)
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ingredients'] })
    }
  })

  const importMutation = useMutation({
    mutationFn: async (file: File) => {
      const formData = new FormData()
      formData.append('file', file)
      const res = await apiClient.post<ApiResponse<IngredientImportResult>>('/ingredients/import', formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      })
      return res.data.data
    },
    onSuccess: (result) => {
      queryClient.invalidateQueries({ queryKey: ['ingredients'] })
      setImportMessage(`Đã import ${result.imported} nguyên liệu, bỏ qua ${result.skipped}. ${result.errors.length ? `Có ${result.errors.length} dòng lỗi.` : ''}`)
    },
    onError: (err: any) => {
      setImportMessage(err.response?.data?.message || err.message || 'Không import được file nguyên liệu.')
    }
  })

  function openCreateModal() {
    if (isAtLimit('ingredients', ingredients.length)) {
      setLimitBanner(`Đã đạt giới hạn gói ${subscription.plan}: tối đa ${subscription.limits.ingredients} nguyên liệu. Nâng cấp ngay để tiếp tục thêm mới.`)
      return
    }
    setEditingIng(null)
    reset({ code: '', name: '', unit: '', category: '', minStock: 0, maxStock: 100 })
    setIsModalOpen(true)
  }

  function openEditModal(ing: Ingredient) {
    setEditingIng(ing)
    setValue('code', ing.code)
    setValue('name', ing.name)
    setValue('unit', ing.unit)
    setValue('category', ing.category)
    setValue('minStock', ing.minStock)
    setValue('maxStock', ing.maxStock)
    setIsModalOpen(true)
  }

  function closeModal() {
    setIsModalOpen(false)
    setEditingIng(null)
    reset()
  }

  function onFormSubmit(values: IngredientFormValues) {
    if (editingIng) {
      updateMutation.mutate({ ...values, id: editingIng.id })
    } else {
      createMutation.mutate(values)
    }
  }

  function handleDelete(id: number) {
    if (confirm('Bạn có chắc muốn lưu trữ nguyên liệu này? Thao tác chỉ thành công khi toàn bộ tồn kho, kể cả lô đã hết hạn, đã được xử lý về 0.')) {
      deleteMutation.mutate(id)
    }
  }

  function handleImportFile(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0]
    if (file) importMutation.mutate(file)
    event.target.value = ''
  }

  async function downloadTemplate() {
    const res = await apiClient.get('/ingredients/import-template', { responseType: 'blob' })
    const blob = res.data instanceof Blob ? res.data : new Blob([res.data], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = 'ingredient-import-template.csv'
    link.click()
    URL.revokeObjectURL(url)
  }

  const isSaving = createMutation.isPending || updateMutation.isPending
  const ingredientLimitReached = isAtLimit('ingredients', ingredients.length)
  const mutationError = createMutation.error ?? updateMutation.error ?? deleteMutation.error

  return (
    <div className="space-y-6 font-sans">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Quản lý nguyên liệu</h2>
          <p className="text-xs text-ink/60">Quản lý danh sách nguyên vật liệu, đơn vị tính và định mức tồn kho tối thiểu.</p>
        </div>

        {role !== 'STAFF' && (
          <div className="flex flex-wrap items-center justify-end gap-2">
            <button
              onClick={downloadTemplate}
              className="flex items-center gap-1.5 rounded-xl border border-ink/10 bg-white py-2.5 px-3 text-xs font-semibold text-ink hover:bg-ink/5"
            >
              <DownloadSimple size={15} />
              <span>File mẫu</span>
            </button>
            <label className="flex cursor-pointer items-center gap-1.5 rounded-xl border border-sage-dark/20 bg-white py-2.5 px-3 text-xs font-semibold text-sage-dark hover:bg-sage/10">
              <UploadSimple size={15} />
              <span>{importMutation.isPending ? 'Đang import...' : 'Import CSV/XLSX'}</span>
              <input className="hidden" type="file" accept=".csv,.xlsx" onChange={handleImportFile} disabled={importMutation.isPending} />
            </label>
            <button
              onClick={openCreateModal}
              className={`flex items-center gap-1.5 rounded-xl py-2.5 px-4 text-xs font-semibold transition-all shadow-sm ${
                ingredientLimitReached ? 'bg-ink/10 text-ink/45' : 'bg-sage-dark text-white hover:bg-sage'
              }`}
            >
              <Plus size={16} />
              <span>{ingredientLimitReached ? 'Đã đạt giới hạn gói' : 'Thêm nguyên liệu'}</span>
            </button>
          </div>
        )}
      </div>

      {importMessage && (
        <div className="rounded-xl border border-sage/20 bg-sage/10 px-4 py-3 text-xs font-semibold text-sage-dark">
          {importMessage}
        </div>
      )}

      {mutationError && (
        <div className="rounded-xl border border-terracotta/20 bg-terracotta/10 px-4 py-3 text-xs font-semibold text-terracotta">
          {apiErrorMessage(mutationError, 'Không thể lưu thay đổi nguyên liệu.')}
        </div>
      )}

      <StateView 
        isLoading={isLoading} 
        isEmpty={ingredients.length === 0} 
        isError={isError}
        emptyTitle="Chưa có nguyên liệu"
        emptySubtitle="Nhấn Thêm nguyên liệu để bắt đầu định nghĩa danh mục F&B."
      >
        <div className="bg-white rounded-2xl border border-ink/5 overflow-hidden shadow-sm">
          <table className="w-full text-left border-collapse text-xs">
            <thead>
              <tr className="bg-ink/5 text-ink/80 font-bold border-b border-ink/10">
                <th className="px-6 py-4">Mã</th>
                <th className="px-6 py-4">Tên nguyên liệu</th>
                <th className="px-6 py-4">Phân loại</th>
                <th className="px-6 py-4 text-center">Đơn vị tính</th>
                <th className="px-6 py-4 text-right">Tồn min / max</th>
                <th className="px-6 py-4 text-center">Trạng thái</th>
                {role !== 'STAFF' && <th className="px-6 py-4 text-right">Thao tác</th>}
              </tr>
            </thead>
            <tbody className="divide-y divide-ink/10">
              {ingredients.map((ing) => (
                <tr key={ing.id} className="hover:bg-ink/5 transition-colors">
                  <td className="px-6 py-4 font-mono font-bold text-ink">{ing.code}</td>
                  <td className="px-6 py-4">
                    <div className="font-semibold text-ink">{ing.name}</div>
                  </td>
                  <td className="px-6 py-4">
                    <span className="inline-flex items-center gap-1 bg-sage/10 text-sage-dark px-2.5 py-0.5 rounded-full font-medium">
                      <Tag size={10} />
                      {ing.category}
                    </span>
                  </td>
                  <td className="px-6 py-4 text-center font-medium">{ing.unit}</td>
                  <td className="px-6 py-4 text-right font-mono font-semibold">
                    <span className="text-terracotta">{ing.minStock}</span> / <span className="text-sage-dark">{ing.maxStock}</span>
                  </td>
                  <td className="px-6 py-4 text-center">
                    <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full font-bold text-[10px] ${
                      ing.active ? 'bg-sage/15 text-sage-dark' : 'bg-ink/10 text-ink/40'
                    }`}>
                      {ing.active ? <Check size={10} /> : <X size={10} />}
                      {ing.active ? 'Đang chạy' : 'Dừng'}
                    </span>
                  </td>
                  {role !== 'STAFF' && (
                    <td className="px-6 py-4 text-right">
                      <div className="inline-flex gap-1.5">
                        <button
                          onClick={() => openEditModal(ing)}
                          className="p-1.5 rounded-lg border border-ink/10 hover:bg-ink/5 text-ink transition-colors"
                          title="Chỉnh sửa"
                        >
                          <Pencil size={12} />
                        </button>
                        <button
                          onClick={() => handleDelete(ing.id)}
                          className="p-1.5 rounded-lg border border-terracotta/20 hover:bg-terracotta/5 text-terracotta transition-colors"
                          title="Ngừng hoạt động"
                          disabled={!ing.active}
                        >
                          <Trash size={12} />
                        </button>
                      </div>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </StateView>

      {/* Modal Form */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-fade-in">
          <div className="bg-offwhite p-1.5 rounded-[2rem] border border-white/20 shadow-2xl max-w-lg w-full animate-scale-up">
            <div className="bg-white rounded-[calc(2rem-0.375rem)] p-6">
              <div className="flex items-center justify-between pb-4 border-b border-ink/10 mb-4">
                <div className="flex items-center gap-2">
                  <Cube size={20} className="text-sage-dark" />
                  <h3 className="font-bold text-base text-ink">
                    {editingIng ? 'Chỉnh sửa nguyên liệu' : 'Thêm nguyên liệu mới'}
                  </h3>
                </div>
                <button
                  onClick={closeModal}
                  className="p-1.5 rounded-lg border border-ink/10 hover:bg-ink/5 transition-colors"
                >
                  <X size={16} />
                </button>
              </div>

              <form onSubmit={handleSubmit(onFormSubmit)} className="space-y-4">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-ink/80 mb-1">Mã nguyên liệu *</label>
                    <input
                      type="text"
                      placeholder="VD: ING-MILK"
                      disabled={!!editingIng}
                      {...register('code')}
                      className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all disabled:opacity-50 disabled:bg-ink/5"
                    />
                    {errors.code && (
                      <p className="text-[10px] text-terracotta mt-1">{errors.code.message}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-ink/80 mb-1">Tên nguyên liệu *</label>
                    <input
                      type="text"
                      placeholder="VD: Sữa tươi Vinamilk 1L"
                      {...register('name')}
                      className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                    />
                    {errors.name && (
                      <p className="text-[10px] text-terracotta mt-1">{errors.name.message}</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-ink/80 mb-1">Đơn vị tính *</label>
                    <input
                      type="text"
                      placeholder="VD: kg, lít, hộp, chai"
                      {...register('unit')}
                      className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                    />
                    {errors.unit && (
                      <p className="text-[10px] text-terracotta mt-1">{errors.unit.message}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-ink/80 mb-1">Danh mục *</label>
                    <input
                      type="text"
                      placeholder="VD: Sữa, Topping, Cà phê"
                      {...register('category')}
                      className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                    />
                    {errors.category && (
                      <p className="text-[10px] text-terracotta mt-1">{errors.category.message}</p>
                    )}
                  </div>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                  <div>
                    <label className="block text-xs font-semibold text-ink/80 mb-1">Tồn tối thiểu (Min) *</label>
                    <input
                      type="number"
                      placeholder="0"
                      {...register('minStock', { valueAsNumber: true })}
                      className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                    />
                    {errors.minStock && (
                      <p className="text-[10px] text-terracotta mt-1">{errors.minStock.message}</p>
                    )}
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-ink/80 mb-1">Tồn tối đa (Max) *</label>
                    <input
                      type="number"
                      placeholder="100"
                      {...register('maxStock', { valueAsNumber: true })}
                      className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                    />
                    {errors.maxStock && (
                      <p className="text-[10px] text-terracotta mt-1">{errors.maxStock.message}</p>
                    )}
                  </div>
                </div>

                {errors.root && (
                  <div className="bg-terracotta/10 border border-terracotta/20 text-terracotta p-3 rounded-xl text-[10px] flex items-center gap-1.5">
                    <ShieldWarning size={14} />
                    <span>{errors.root.message}</span>
                  </div>
                )}

                <div className="flex justify-end gap-2.5 pt-4 border-t border-ink/10">
                  <button
                    type="button"
                    onClick={closeModal}
                    className="px-4 py-2 rounded-xl border border-ink/10 text-xs font-semibold hover:bg-ink/5 transition-colors"
                  >
                    Hủy
                  </button>
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-4 py-2 bg-sage-dark text-white rounded-xl text-xs font-semibold hover:bg-sage transition-all disabled:opacity-50"
                  >
                    {isSaving ? 'Đang lưu...' : 'Lưu lại'}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
