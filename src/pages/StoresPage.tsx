import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { useForm } from 'react-hook-form'
import { zodResolver } from '@hookform/resolvers/zod'
import * as zod from 'zod'
import apiClient from '../api/client'
import type { ApiResponse, Store } from '../types'
import { useAuth } from '../context/AuthContext'
import { useStore } from '../context/StoreContext'
import DoubleBezelCard from '../components/DoubleBezelCard'
import StateView from '../components/StateView'
import { Plus, Pencil, Trash, X, Storefront } from '@phosphor-icons/react'
import { apiErrorMessage } from '../utils/apiResponse'

// Validate schema
const storeSchema = zod.object({
  name: zod.string().min(3, 'Tên cửa hàng phải có ít nhất 3 ký tự'),
  address: zod.string().min(5, 'Địa chỉ phải có ít nhất 5 ký tự'),
  phone: zod.string().min(9, 'Số điện thoại không hợp lệ')
})

type StoreFormValues = zod.infer<typeof storeSchema>

export default function StoresPage() {
  const { role } = useAuth()
  const { activeStore } = useStore()
  const queryClient = useQueryClient()
  const [editingStore, setEditingStore] = useState<Store | null>(null)
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [mutationError, setMutationError] = useState<string | null>(null)

  // 1. Tải danh sách cửa hàng
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['stores'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Store[]>>('/stores')
      return res.data
    }
  })
  const stores = response?.data ?? []

  // Hook Form setup
  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors }
  } = useForm<StoreFormValues>({
    resolver: zodResolver(storeSchema),
    defaultValues: { name: '', address: '', phone: '' }
  })

  // 2. Mutation thêm cửa hàng
  const createStoreMutation = useMutation({
    mutationFn: async (values: StoreFormValues) => {
      await apiClient.post('/stores', values)
    },
    onMutate: () => setMutationError(null),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] })
      closeModal()
    },
    onError: (error: unknown) => setMutationError(apiErrorMessage(error, 'Không thể tạo cửa hàng.')),
  })

  // 3. Mutation cập nhật cửa hàng
  const updateStoreMutation = useMutation({
    mutationFn: async (values: StoreFormValues & { id: number }) => {
      await apiClient.put(`/stores/${values.id}`, values)
    },
    onMutate: () => setMutationError(null),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] })
      closeModal()
    },
    onError: (error: unknown) => setMutationError(apiErrorMessage(error, 'Không thể cập nhật cửa hàng.')),
  })

  // 4. Mutation xóa cửa hàng
  const deleteStoreMutation = useMutation({
    mutationFn: async (id: number) => {
      await apiClient.delete(`/stores/${id}`)
    },
    onMutate: () => setMutationError(null),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stores'] })
    },
    onError: (error: unknown) => setMutationError(apiErrorMessage(error, 'Không thể xóa cửa hàng.')),
  })

  function openCreateModal() {
    setMutationError(null)
    setEditingStore(null)
    reset({ name: '', address: '', phone: '' })
    setIsModalOpen(true)
  }

  function openEditModal(store: Store) {
    setMutationError(null)
    setEditingStore(store)
    setValue('name', store.name)
    setValue('address', store.address ?? '')
    setValue('phone', store.phone ?? '')
    setIsModalOpen(true)
  }

  function closeModal() {
    setIsModalOpen(false)
    setEditingStore(null)
    reset()
  }

  function onFormSubmit(values: StoreFormValues) {
    if (editingStore) {
      updateStoreMutation.mutate({ ...values, id: editingStore.id })
    } else {
      createStoreMutation.mutate(values)
    }
  }

  function handleDelete(id: number) {
    if (confirm('Bạn có chắc chắn muốn xóa cửa hàng này?')) {
      deleteStoreMutation.mutate(id)
    }
  }

  const isSaving = createStoreMutation.isPending || updateStoreMutation.isPending

  return (
    <div className="space-y-6 font-sans">
      <div className="flex justify-between items-center">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Quản lý cửa hàng</h2>
          <p className="text-xs text-ink/60">Danh sách các chi nhánh của hệ thống F&B.</p>
        </div>

        {role === 'STORE_OWNER' && (
          <button
            onClick={openCreateModal}
            className="flex items-center gap-1.5 bg-sage-dark text-white rounded-xl py-2.5 px-4 text-xs font-semibold hover:bg-sage transition-all shadow-sm"
          >
            <Plus size={16} />
            <span>Thêm chi nhánh</span>
          </button>
        )}
      </div>

      {mutationError && (
        <div className="rounded-2xl border border-terracotta/20 bg-terracotta/10 p-4 text-sm font-semibold text-terracotta">
          {mutationError}
        </div>
      )}

      <StateView isLoading={isLoading} isEmpty={stores.length === 0} isError={isError}>
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
          {stores.map((store) => (
            <DoubleBezelCard
              key={store.id}
              title={store.name}
              subtitle={`Mã chi nhánh: #${store.id}`}
              action={
                role === 'STORE_OWNER' && (
                  <div className="flex gap-1.5">
                    <button
                      onClick={() => openEditModal(store)}
                      className="p-1.5 rounded-lg border border-ink/10 hover:bg-ink/5 text-ink transition-colors"
                      title="Sửa thông tin"
                    >
                      <Pencil size={14} />
                    </button>
                    <button
                      onClick={() => handleDelete(store.id)}
                      disabled={store.id === activeStore?.id}
                      className="p-1.5 rounded-lg border border-terracotta/20 hover:bg-terracotta/5 text-terracotta transition-colors disabled:cursor-not-allowed disabled:opacity-35"
                      title={store.id === activeStore?.id ? 'Hãy chuyển sang chi nhánh khác trước khi xóa' : 'Xóa cửa hàng'}
                    >
                      <Trash size={14} />
                    </button>
                  </div>
                )
              }
            >
              <div className="space-y-2.5 text-xs">
                <div className="flex items-center gap-2 text-ink/80">
                  <span className="font-semibold text-ink w-20 shrink-0">Địa chỉ:</span>
                  <span className="truncate">{store.address ?? 'Chưa cập nhật'}</span>
                </div>
                <div className="flex items-center gap-2 text-ink/80">
                  <span className="font-semibold text-ink w-20 shrink-0">Điện thoại:</span>
                  <span>{store.phone ?? 'Chưa cập nhật'}</span>
                </div>
              </div>
            </DoubleBezelCard>
          ))}
        </div>
      </StateView>

      {/* Modal Form */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 backdrop-blur-sm p-4 animate-fade-in">
          <div className="bg-offwhite p-1.5 rounded-[2rem] border border-white/20 shadow-2xl max-w-md w-full animate-scale-up">
            <div className="bg-white rounded-[calc(2rem-0.375rem)] p-6">
              <div className="flex items-center justify-between pb-4 border-b border-ink/10 mb-4">
                <div className="flex items-center gap-2">
                  <Storefront size={20} className="text-sage-dark" />
                  <h3 className="font-bold text-base text-ink">
                    {editingStore ? 'Chỉnh sửa chi nhánh' : 'Thêm chi nhánh mới'}
                  </h3>
                </div>
                <button
                  onClick={closeModal}
                  className="p-1.5 rounded-lg border border-ink/10 hover:bg-ink/5 transition-colors"
                >
                  <X size={16} />
                </button>
              </div>

              {mutationError && (
                <div className="mb-4 rounded-xl border border-terracotta/20 bg-terracotta/10 px-3 py-2 text-xs font-semibold text-terracotta">
                  {mutationError}
                </div>
              )}

              <form onSubmit={handleSubmit(onFormSubmit)} className="space-y-4">
                <div>
                  <label className="block text-xs font-semibold text-ink/80 mb-1">Tên cửa hàng *</label>
                  <input
                    type="text"
                    placeholder="Ví dụ: Chi nhánh Quận 3"
                    {...register('name')}
                    className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                  />
                  {errors.name && (
                    <p className="text-[10px] text-terracotta mt-1">{errors.name.message}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-ink/80 mb-1">Địa chỉ chi tiết *</label>
                  <input
                    type="text"
                    placeholder="Ví dụ: 456 Nguyễn Thị Minh Khai, Q3"
                    {...register('address')}
                    className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                  />
                  {errors.address && (
                    <p className="text-[10px] text-terracotta mt-1">{errors.address.message}</p>
                  )}
                </div>

                <div>
                  <label className="block text-xs font-semibold text-ink/80 mb-1">Số điện thoại *</label>
                  <input
                    type="text"
                    placeholder="Ví dụ: 0289999888"
                    {...register('phone')}
                    className="w-full bg-offwhite/50 border border-ink/10 rounded-xl px-3.5 py-2 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white transition-all"
                  />
                  {errors.phone && (
                    <p className="text-[10px] text-terracotta mt-1">{errors.phone.message}</p>
                  )}
                </div>

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
