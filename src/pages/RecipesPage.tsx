import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import apiClient from '../api/client'
import { useStore } from '../context/StoreContext'
import { useAuth } from '../context/AuthContext'
import StateView from '../components/StateView'
import { Plus, Pencil, Trash, X, Coffee, WarningCircle } from '@phosphor-icons/react'
import { apiErrorMessage } from '../utils/apiResponse'
import { listRecipes, createRecipe, updateRecipe, deleteRecipe } from '../services/recipeService'
import type { Recipe, Ingredient, ApiResponse } from '../types'
import { formatVND } from '../utils/fefo'

interface IngredientItemInput {
  ingredientId: number
  quantity: number
}

export default function RecipesPage() {
  const { activeStore } = useStore()
  const { role } = useAuth()
  const queryClient = useQueryClient()
  const canEdit = role === 'STORE_OWNER' || role === 'MANAGER'

  const [searchTerm, setSearchTerm] = useState('')
  const [isModalOpen, setIsModalOpen] = useState(false)
  const [editingRecipe, setEditingRecipe] = useState<Recipe | null>(null)
  
  // Form states
  const [code, setCode] = useState('')
  const [name, setName] = useState('')
  const [price, setPrice] = useState(0)
  const [active, setActive] = useState(true)
  const [selectedIngredients, setSelectedIngredients] = useState<IngredientItemInput[]>([])
  const [formError, setFormError] = useState<string | null>(null)

  // 1. Load recipes
  const { data: recipes = [], isLoading, isError, error } = useQuery({
    queryKey: ['recipes', activeStore?.id],
    queryFn: listRecipes,
    enabled: !!activeStore?.id
  })

  // 2. Load ingredients list for selection
  const { data: ingredientsData } = useQuery({
    queryKey: ['ingredients', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data.data
    },
    enabled: !!activeStore?.id
  })
  const ingredients = ingredientsData ?? []

  // Mutations
  const createMutation = useMutation({
    mutationFn: createRecipe,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      closeModal()
    },
    onError: (err: any) => {
      setFormError(err.message || 'Không thể tạo công thức.')
    }
  })

  const updateMutation = useMutation({
    mutationFn: (args: { id: number; data: any }) => updateRecipe(args.id, args.data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
      closeModal()
    },
    onError: (err: any) => {
      setFormError(err.message || 'Không thể cập nhật công thức.')
    }
  })

  const deleteMutation = useMutation({
    mutationFn: deleteRecipe,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['recipes'] })
    }
  })

  // Actions
  function openCreateModal() {
    setEditingRecipe(null)
    setCode('')
    setName('')
    setPrice(0)
    setActive(true)
    setSelectedIngredients([])
    setFormError(null)
    setIsModalOpen(true)
  }

  function openEditModal(recipe: Recipe) {
    setEditingRecipe(recipe)
    setCode(recipe.code)
    setName(recipe.name)
    setPrice(recipe.price)
    setActive(recipe.active)
    setSelectedIngredients(
      recipe.ingredients.map(i => ({
        ingredientId: i.ingredientId,
        quantity: i.quantity
      }))
    )
    setFormError(null)
    setIsModalOpen(true)
  }

  function closeModal() {
    setIsModalOpen(false)
    setEditingRecipe(null)
    setFormError(null)
  }

  function handleAddIngredientRow() {
    // Find the first ingredient that isn't selected yet, or default to first
    const available = ingredients.find(ing => !selectedIngredients.some(sel => sel.ingredientId === ing.id))
    const nextId = available ? available.id : (ingredients[0]?.id ?? 0)
    
    if (nextId === 0) return // No ingredients available in system
    
    setSelectedIngredients([...selectedIngredients, { ingredientId: nextId, quantity: 1 }])
  }

  function handleIngredientChange(index: number, field: keyof IngredientItemInput, value: number) {
    const updated = [...selectedIngredients]
    updated[index] = { ...updated[index], [field]: value }
    setSelectedIngredients(updated)
  }

  function handleRemoveIngredientRow(index: number) {
    setSelectedIngredients(selectedIngredients.filter((_, i) => i !== index))
  }

  function handleSubmitForm(e: React.FormEvent) {
    e.preventDefault()
    setFormError(null)

    if (!code.trim()) {
      setFormError('Vui lòng nhập mã công thức.')
      return
    }
    if (!name.trim()) {
      setFormError('Vui lòng nhập tên công thức.')
      return
    }
    if (price < 0) {
      setFormError('Giá bán không thể nhỏ hơn 0.')
      return
    }
    if (selectedIngredients.length === 0) {
      setFormError('Công thức phải có ít nhất một nguyên liệu.')
      return
    }
    
    // Check duplicates
    const ids = selectedIngredients.map(i => i.ingredientId)
    const hasDuplicates = ids.some((val, i) => ids.indexOf(val) !== i)
    if (hasDuplicates) {
      setFormError('Nguyên liệu trong công thức không được lặp lại.')
      return
    }

    // Check invalid quantities
    if (selectedIngredients.some(i => i.quantity <= 0)) {
      setFormError('Số lượng nguyên liệu phải lớn hơn 0.')
      return
    }

    const payload = {
      code: code.trim().toUpperCase(),
      name: name.trim(),
      price,
      active,
      ingredients: selectedIngredients
    }

    if (editingRecipe) {
      updateMutation.mutate({ id: editingRecipe.id, data: payload })
    } else {
      createMutation.mutate(payload)
    }
  }

  function handleDeleteRecipe(id: number) {
    if (confirm('Bạn có chắc muốn xóa công thức này?')) {
      deleteMutation.mutate(id)
    }
  }

  // Filter recipes
  const filteredRecipes = recipes.filter(
    r =>
      r.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
      r.code.toLowerCase().includes(searchTerm.toLowerCase())
  )

  return (
    <div className="space-y-6 font-sans">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Công thức định lượng</h2>
          <p className="text-xs text-ink/60">Định nghĩa định mức nguyên liệu hao hụt cho từng món ăn/đồ uống phục vụ bán hàng.</p>
        </div>

        {canEdit && (
          <button
            onClick={openCreateModal}
            className="flex items-center justify-center gap-1.5 rounded-xl bg-sage-dark px-4 py-2.5 text-xs font-semibold text-white shadow-sm hover:bg-sage transition-all"
          >
            <Plus size={15} />
            <span>Thêm công thức</span>
          </button>
        )}
      </div>

      <StateView
        isLoading={isLoading}
        isError={isError}
        errorMessage={apiErrorMessage(error, 'Không thể tải danh sách công thức.')}
      >
        {/* Filter and Search */}
        <div className="rounded-2xl border border-ink/10 bg-white p-4 shadow-sm">
          <div className="max-w-md">
            <label htmlFor="search" className="sr-only">Tìm công thức</label>
            <input
              id="search"
              type="text"
              placeholder="Tìm theo tên hoặc mã công thức..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full rounded-xl border border-ink/10 bg-offwhite/50 px-4 py-2.5 text-sm outline-none focus:border-sage-dark"
            />
          </div>
        </div>

        {/* Recipes Grid */}
        {filteredRecipes.length === 0 ? (
          <div className="flex min-h-60 flex-col items-center justify-center rounded-2xl border border-dashed border-ink/10 bg-white p-8 text-center">
            <Coffee size={36} className="text-ink/30" />
            <h3 className="mt-3 text-sm font-bold text-ink">Chưa có công thức nào</h3>
            <p className="mt-1 text-xs text-ink/50">Định nghĩa công thức để tự động trừ kho nguyên liệu F&B khi bán hàng.</p>
            {canEdit && (
              <button
                onClick={openCreateModal}
                className="mt-4 rounded-xl bg-sage-dark px-4 py-2 text-xs font-bold text-white shadow-sm hover:bg-sage"
              >
                Tạo công thức đầu tiên
              </button>
            )}
          </div>
        ) : (
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {filteredRecipes.map((recipe) => (
              <article
                key={recipe.id}
                className="flex flex-col justify-between overflow-hidden rounded-2xl border border-ink/10 bg-white p-5 shadow-sm transition-all hover:shadow-md"
              >
                <div>
                  <div className="flex items-start justify-between gap-2">
                    <div>
                      <span className="inline-block rounded-lg bg-sage/15 px-2 py-0.5 text-[10px] font-bold text-sage-dark uppercase tracking-wider">
                        {recipe.code}
                      </span>
                      <h3 className="mt-1.5 font-bold text-ink line-clamp-1">{recipe.name}</h3>
                    </div>
                    <span className="text-sm font-bold text-terracotta shrink-0">
                      {formatVND(recipe.price)}
                    </span>
                  </div>

                  {/* Status */}
                  <div className="mt-2 flex items-center gap-1.5">
                    <span className={`h-1.5 w-1.5 rounded-full ${recipe.active ? 'bg-sage-dark' : 'bg-ink/30'}`} />
                    <span className="text-[10px] font-semibold text-ink/65">
                      {recipe.active ? 'Đang bán' : 'Ngừng hoạt động'}
                    </span>
                  </div>

                  {/* Ingredients list */}
                  <div className="mt-4 border-t border-ink/5 pt-3">
                    <span className="text-[10px] font-bold text-ink/40 uppercase tracking-wider block mb-1.5">
                      Thành phần định lượng ({recipe.ingredients.length})
                    </span>
                    <ul className="space-y-1">
                      {recipe.ingredients.map((ing) => (
                        <li key={ing.ingredientId} className="flex justify-between items-center text-xs text-ink/75">
                          <span className="truncate pr-2">{ing.ingredientName}</span>
                          <span className="font-semibold shrink-0">
                            {ing.quantity} {ing.unit}
                          </span>
                        </li>
                      ))}
                    </ul>
                  </div>
                </div>

                {canEdit && (
                  <div className="mt-5 flex gap-2 border-t border-ink/5 pt-3">
                    <button
                      onClick={() => openEditModal(recipe)}
                      className="flex-1 flex items-center justify-center gap-1 rounded-xl border border-ink/10 py-1.5 text-xs font-semibold text-ink/80 hover:bg-ink/5"
                    >
                      <Pencil size={13} />
                      <span>Sửa</span>
                    </button>
                    <button
                      onClick={() => handleDeleteRecipe(recipe.id)}
                      className="flex items-center justify-center gap-1 rounded-xl border border-red-500/10 px-3 py-1.5 text-xs font-semibold text-red-600 hover:bg-red-50"
                    >
                      <Trash size={13} />
                    </button>
                  </div>
                )}
              </article>
            ))}
          </div>
        )}
      </StateView>

      {/* Modal create/edit */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4 backdrop-blur-sm">
          <div className="w-full max-w-2xl overflow-hidden rounded-2xl border border-ink/10 bg-white shadow-2xl animate-in fade-in zoom-in-95 duration-200">
            {/* Modal Header */}
            <div className="flex items-center justify-between border-b border-ink/10 px-5 py-4">
              <h3 className="text-base font-extrabold text-ink">
                {editingRecipe ? 'Cập nhật công thức' : 'Thêm công thức mới'}
              </h3>
              <button onClick={closeModal} className="text-ink/40 hover:text-ink">
                <X size={18} />
              </button>
            </div>

            <form onSubmit={handleSubmitForm} className="flex flex-col h-[calc(100vh-8rem)] max-h-[600px]">
              {/* Modal Body */}
              <div className="flex-1 overflow-y-auto p-5 space-y-4">
                {formError && (
                  <div className="rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-xs font-semibold text-red-600 flex items-center gap-2">
                    <WarningCircle size={15} />
                    <span>{formError}</span>
                  </div>
                )}

                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="block">
                    <span className="text-xs font-bold text-ink/75">Mã công thức (Code) *</span>
                    <input
                      type="text"
                      placeholder="VD: CF-SUA-DA"
                      value={code}
                      onChange={(e) => setCode(e.target.value)}
                      disabled={!!editingRecipe}
                      className="mt-1.5 w-full rounded-xl border border-ink/10 bg-offwhite/50 px-3 py-2 text-sm outline-none focus:border-sage-dark disabled:opacity-60"
                    />
                  </label>
                  <label className="block">
                    <span className="text-xs font-bold text-ink/75">Tên món ăn / Đồ uống *</span>
                    <input
                      type="text"
                      placeholder="VD: Cà phê sữa đá"
                      value={name}
                      onChange={(e) => setName(e.target.value)}
                      className="mt-1.5 w-full rounded-xl border border-ink/10 bg-offwhite/50 px-3 py-2 text-sm outline-none focus:border-sage-dark"
                    />
                  </label>
                </div>

                <div className="grid gap-4 sm:grid-cols-2">
                  <label className="block">
                    <span className="text-xs font-bold text-ink/75">Giá bán (VND) *</span>
                    <input
                      type="number"
                      placeholder="0"
                      value={price || ''}
                      onChange={(e) => setPrice(Number(e.target.value))}
                      className="mt-1.5 w-full rounded-xl border border-ink/10 bg-offwhite/50 px-3 py-2 text-sm outline-none focus:border-sage-dark"
                    />
                  </label>
                  <div className="flex items-center pt-6">
                    <label className="flex items-center gap-2 cursor-pointer">
                      <input
                        type="checkbox"
                        checked={active}
                        onChange={(e) => setActive(e.target.checked)}
                        className="h-4 w-4 rounded border-ink/10 text-sage-dark focus:ring-sage"
                      />
                      <span className="text-xs font-bold text-ink/75">Công thức đang hoạt động</span>
                    </label>
                  </div>
                </div>

                {/* Ingredients definition */}
                <div className="border-t border-ink/10 pt-4">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-xs font-extrabold text-ink/75 uppercase tracking-wider">
                      Định lượng nguyên liệu thành phần
                    </span>
                    <button
                      type="button"
                      onClick={handleAddIngredientRow}
                      className="inline-flex items-center gap-1 rounded-lg border border-sage-dark/25 bg-white px-2.5 py-1.5 text-[11px] font-bold text-sage-dark hover:bg-sage/10"
                    >
                      <Plus size={11} />
                      <span>Thêm dòng</span>
                    </button>
                  </div>

                  {selectedIngredients.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-ink/15 p-6 text-center text-xs text-ink/50 bg-offwhite/30">
                      Chưa định nghĩa thành phần. Hãy nhấn "Thêm dòng" để chọn nguyên liệu.
                    </div>
                  ) : (
                    <div className="space-y-2 max-h-56 overflow-y-auto pr-1">
                      {selectedIngredients.map((item, idx) => {
                        const activeIngredient = ingredients.find(ing => ing.id === item.ingredientId)
                        return (
                          <div key={idx} className="flex gap-2 items-center">
                            {/* Ingredient drop down */}
                            <select
                              value={item.ingredientId}
                              onChange={(e) => handleIngredientChange(idx, 'ingredientId', Number(e.target.value))}
                              className="flex-1 rounded-xl border border-ink/10 bg-offwhite/50 px-3 py-2 text-xs outline-none focus:border-sage-dark"
                            >
                              {ingredients.map(ing => (
                                <option key={ing.id} value={ing.id}>
                                  {ing.name} ({ing.code})
                                </option>
                              ))}
                            </select>

                            {/* Quantity input */}
                            <div className="w-28 flex items-center rounded-xl border border-ink/10 bg-offwhite/50 px-3 text-xs">
                              <input
                                type="number"
                                step="any"
                                placeholder="Lượng"
                                value={item.quantity || ''}
                                onChange={(e) => handleIngredientChange(idx, 'quantity', Number(e.target.value))}
                                className="w-full bg-transparent py-2 outline-none"
                              />
                              <span className="text-ink/40 ml-1">
                                {activeIngredient?.unit || ''}
                              </span>
                            </div>

                            {/* Remove row */}
                            <button
                              type="button"
                              onClick={() => handleRemoveIngredientRow(idx)}
                              className="p-2 rounded-lg text-red-500 hover:bg-red-50"
                            >
                              <X size={14} />
                            </button>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              </div>

              {/* Modal Footer */}
              <div className="border-t border-ink/10 bg-offwhite/30 px-5 py-4 flex justify-end gap-3 shrink-0">
                <button
                  type="button"
                  onClick={closeModal}
                  className="rounded-xl border border-ink/10 bg-white px-4 py-2 text-xs font-bold text-ink hover:bg-ink/5"
                >
                  Hủy
                </button>
                <button
                  type="submit"
                  disabled={createMutation.isPending || updateMutation.isPending}
                  className="rounded-xl bg-sage-dark px-5 py-2 text-xs font-bold text-white shadow-sm hover:bg-sage disabled:opacity-60"
                >
                  {createMutation.isPending || updateMutation.isPending ? 'Đang lưu...' : 'Lưu lại'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  )
}
