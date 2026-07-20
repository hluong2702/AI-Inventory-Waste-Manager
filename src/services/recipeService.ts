import apiClient from '../api/client'
import type { ApiResponse, Recipe, CreateRecipeRequest } from '../types'
import { apiErrorMessage, unwrapApiData } from '../utils/apiResponse'

export interface PosSalesImportResult {
  provider: string
  mode: string
  rowsParsed: number
  totalRevenue: number
  persisted: boolean
  warnings: string[]
}

export async function listRecipes(): Promise<Recipe[]> {
  try {
    const res = await apiClient.get<ApiResponse<Recipe[]>>('/recipes')
    return unwrapApiData(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể tải danh sách công thức.'))
  }
}

export async function getRecipe(id: number): Promise<Recipe> {
  try {
    const res = await apiClient.get<ApiResponse<Recipe>>(`/recipes/${id}`)
    return unwrapApiData(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể tải chi tiết công thức.'))
  }
}

export async function createRecipe(request: CreateRecipeRequest): Promise<Recipe> {
  try {
    const res = await apiClient.post<ApiResponse<Recipe>>('/recipes', request)
    return unwrapApiData(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể tạo mới công thức.'))
  }
}

export async function updateRecipe(id: number, request: CreateRecipeRequest): Promise<Recipe> {
  try {
    const res = await apiClient.put<ApiResponse<Recipe>>(`/recipes/${id}`, request)
    return unwrapApiData(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể cập nhật công thức.'))
  }
}

export async function deleteRecipe(id: number): Promise<void> {
  try {
    await apiClient.delete(`/recipes/${id}`)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Không thể xóa công thức.'))
  }
}

export async function deductPosSales(file: File): Promise<PosSalesImportResult> {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await apiClient.post<ApiResponse<PosSalesImportResult>>('/integrations/pos/csv/deduct', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return unwrapApiData(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Lỗi khi đồng bộ trừ kho theo doanh số.'))
  }
}

export async function previewPosSales(file: File): Promise<PosSalesImportResult> {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await apiClient.post<ApiResponse<PosSalesImportResult>>('/integrations/pos/csv/preview', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
    return unwrapApiData(res.data)
  } catch (error) {
    throw new Error(apiErrorMessage(error, 'Lỗi khi xem trước doanh số.'))
  }
}
