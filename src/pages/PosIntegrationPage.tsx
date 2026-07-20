import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import apiClient from '../api/client'
import { useAuth } from '../context/AuthContext'
import { useStore } from '../context/StoreContext'
import StateView from '../components/StateView'
import {
  UploadSimple,
  WarningCircle,
  CheckCircle,
  FileCsv,
  Cpu,
  Database
} from '@phosphor-icons/react'
import { apiErrorMessage } from '../utils/apiResponse'
import { deductPosSales, previewPosSales } from '../services/recipeService'
import type { PosSalesImportResult } from '../services/recipeService'
import { formatVND } from '../utils/fefo'

interface Provider {
  provider: string
  status: string
  persistsSales: boolean
  description: string
}

export default function PosIntegrationPage() {
  const { activeStore } = useStore()
  const { role } = useAuth()
  const queryClient = useQueryClient()
  const canImport = role === 'STORE_OWNER' || role === 'MANAGER'

  const [selectedFile, setSelectedFile] = useState<File | null>(null)
  const [result, setResult] = useState<PosSalesImportResult | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | null>(null)

  // 1. Fetch available POS integration providers
  const { data: providers = [], isLoading, isError, error } = useQuery({
    queryKey: ['pos-providers', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<Provider[]>('/integrations/pos/providers')
      return res.data
    },
    enabled: !!activeStore?.id
  })

  // 2. Mutation for sales preview
  const previewMutation = useMutation({
    mutationFn: previewPosSales,
    onSuccess: (data) => {
      setResult(data)
      setErrorMessage(null)
    },
    onError: (err: any) => {
      setErrorMessage(err.message || 'Không thể xem trước tệp doanh số.')
      setResult(null)
    }
  })

  // 3. Mutation for sales deduct
  const deductMutation = useMutation({
    mutationFn: deductPosSales,
    onSuccess: (data) => {
      setResult(data)
      setErrorMessage(null)
      // Invalidate inventory/forecast stats since quantities have changed
      queryClient.invalidateQueries({ queryKey: ['inventory'] })
      queryClient.invalidateQueries({ queryKey: ['batches'] })
      queryClient.invalidateQueries({ queryKey: ['forecast'] })
    },
    onError: (err: any) => {
      setErrorMessage(err.message || 'Không thể thực thi trừ kho doanh số.')
      setResult(null)
    }
  })

  function handleFileChange(e: React.ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0]
    if (file) {
      setSelectedFile(file)
      setResult(null)
      setErrorMessage(null)
    }
  }

  function handlePreview() {
    if (selectedFile) {
      previewMutation.mutate(selectedFile)
    }
  }

  function handleDeduct() {
    if (selectedFile) {
      if (confirm('Hệ thống sẽ thực hiện trừ tồn kho nguyên liệu thực tế theo công thức định lượng. Bạn có chắc chắn muốn tiếp tục?')) {
        deductMutation.mutate(selectedFile)
      }
    }
  }

  function handleReset() {
    setSelectedFile(null)
    setResult(null)
    setErrorMessage(null)
  }

  const activeProvider = providers.find(p => p.provider === 'CSV')
  const otherProviders = providers.filter(p => p.provider !== 'CSV')

  return (
    <div className="space-y-6 font-sans">
      <div>
        <h2 className="text-2xl font-bold tracking-tight text-ink">Tích hợp POS & Bán hàng</h2>
        <p className="text-xs text-ink/60">Tự động đồng bộ doanh số bán hàng từ máy POS để giảm tồn kho nguyên vật liệu tương ứng.</p>
      </div>

      <StateView
        isLoading={isLoading}
        isError={isError}
        errorMessage={apiErrorMessage(error, 'Không thể tải cấu hình liên kết POS.')}
      >
        <div className="grid gap-6 lg:grid-cols-3">
          {/* Left Side: File Upload & Actions */}
          <div className="lg:col-span-2 space-y-6">
            <section className="rounded-2xl border border-ink/10 bg-white p-5 shadow-sm space-y-4">
              <div className="flex items-center gap-2 text-sage-dark">
                <FileCsv size={20} weight="duotone" />
                <h3 className="text-sm font-extrabold text-ink">Đồng bộ qua file doanh số CSV</h3>
              </div>
              <p className="text-xs text-ink/65 leading-relaxed">
                Tải lên file báo cáo bán hàng dạng CSV có chứa thông tin mã món ăn (<code className="bg-ink/5 px-1 rounded font-bold text-ink">code/ma</code>) và số lượng bán (<code className="bg-ink/5 px-1 rounded font-bold text-ink">quantity/qty</code>) để hệ thống chạy giải thuật FEFO trừ kho tự động.
              </p>

              {/* Upload Zone */}
              {!selectedFile ? (
                <label className="flex flex-col items-center justify-center min-h-48 border-2 border-dashed border-ink/10 rounded-2xl bg-offwhite/30 cursor-pointer hover:bg-offwhite/50 transition-colors">
                  <UploadSimple size={28} className="text-ink/40 mb-2" />
                  <span className="text-xs font-bold text-ink">Chọn tệp CSV doanh số</span>
                  <span className="text-[10px] text-ink/45 mt-1">Dung lượng tối đa 5MB</span>
                  <input type="file" accept=".csv" onChange={handleFileChange} className="hidden" />
                </label>
              ) : (
                <div className="rounded-xl border border-ink/10 bg-offwhite/40 p-4 flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="h-10 w-10 rounded-lg bg-sage/10 text-sage-dark flex items-center justify-center">
                      <FileCsv size={22} weight="fill" />
                    </div>
                    <div>
                      <div className="text-xs font-bold text-ink truncate max-w-xs">{selectedFile.name}</div>
                      <div className="text-[10px] text-ink/50">{(selectedFile.size / 1024).toFixed(1)} KB</div>
                    </div>
                  </div>
                  <button
                    onClick={handleReset}
                    disabled={previewMutation.isPending || deductMutation.isPending}
                    className="text-xs font-semibold text-red-600 hover:underline"
                  >
                    Chọn tệp khác
                  </button>
                </div>
              )}

              {/* Control buttons */}
              {selectedFile && !result && (
                <div className="flex flex-wrap gap-2 pt-2">
                  <button
                    onClick={handlePreview}
                    disabled={previewMutation.isPending}
                    className="rounded-xl border border-ink/10 bg-white px-4 py-2.5 text-xs font-bold text-ink hover:bg-ink/5 disabled:opacity-50"
                  >
                    {previewMutation.isPending ? 'Đang phân tích...' : 'Xem trước doanh số (Preview)'}
                  </button>
                  {canImport && (
                    <button
                      onClick={handleDeduct}
                      disabled={deductMutation.isPending}
                      className="flex items-center gap-1.5 rounded-xl bg-sage-dark px-4 py-2.5 text-xs font-bold text-white hover:bg-sage disabled:opacity-50"
                    >
                      <Database size={14} />
                      <span>{deductMutation.isPending ? 'Đang thực thi trừ kho...' : 'Xác nhận Bán hàng & Trừ kho'}</span>
                    </button>
                  )}
                </div>
              )}
            </section>

            {/* Error display */}
            {errorMessage && (
              <div className="rounded-2xl border border-red-500/20 bg-red-500/10 p-4 text-xs font-semibold text-red-600 flex items-start gap-2.5">
                <WarningCircle size={18} className="shrink-0 mt-0.5" />
                <div>
                  <div className="font-bold">Đồng bộ thất bại</div>
                  <p className="mt-1 leading-relaxed text-red-600/80">{errorMessage}</p>
                </div>
              </div>
            )}

            {/* Import Result Panel */}
            {result && (
              <section className="rounded-2xl border border-ink/10 bg-white p-5 shadow-sm space-y-4">
                <div className="flex items-center gap-2">
                  {result.persisted ? (
                    <CheckCircle size={20} className="text-sage-dark" weight="fill" />
                  ) : (
                    <Cpu size={20} className="text-ink/60" weight="duotone" />
                  )}
                  <h3 className="text-sm font-extrabold text-ink">
                    {result.persisted ? 'Kết quả trừ kho thực tế' : 'Xem trước kết quả phân tích'}
                  </h3>
                </div>

                {/* Statistics cards */}
                <div className="grid gap-4 grid-cols-2 sm:grid-cols-3">
                  <div className="rounded-xl bg-offwhite/50 p-3">
                    <span className="text-[10px] text-ink/50 block font-medium">Số món khớp công thức</span>
                    <span className="text-lg font-bold text-ink mt-0.5 block">{result.rowsParsed} món</span>
                  </div>
                  <div className="rounded-xl bg-offwhite/50 p-3">
                    <span className="text-[10px] text-ink/50 block font-medium">Doanh thu ghi nhận</span>
                    <span className="text-lg font-bold text-terracotta mt-0.5 block">{formatVND(result.totalRevenue)}</span>
                  </div>
                  <div className="col-span-2 sm:col-span-1 rounded-xl bg-offwhite/50 p-3">
                    <span className="text-[10px] text-ink/50 block font-medium">Trạng thái dữ liệu</span>
                    <span className={`text-xs font-bold mt-1.5 inline-block rounded-md px-2 py-0.5 uppercase tracking-wide ${
                      result.persisted ? 'bg-sage/20 text-sage-dark' : 'bg-ink/5 text-ink/60'
                    }`}>
                      {result.persisted ? 'ĐÃ TRỪ KHO' : 'CHỈ XEM TRƯỚC'}
                    </span>
                  </div>
                </div>

                {/* Action for non-persisted preview */}
                {!result.persisted && canImport && (
                  <div className="border-t border-ink/5 pt-4 flex justify-between items-center gap-3">
                    <span className="text-[11px] text-ink/55">
                      Dữ liệu trên là xem trước. Hãy nhấn nút để áp dụng trừ kho nguyên liệu thực tế.
                    </span>
                    <button
                      onClick={handleDeduct}
                      disabled={deductMutation.isPending}
                      className="flex items-center gap-1.5 rounded-xl bg-sage-dark px-4 py-2.5 text-xs font-bold text-white hover:bg-sage shadow-sm shrink-0"
                    >
                      <Database size={13} />
                      <span>Đồng ý trừ kho</span>
                    </button>
                  </div>
                )}

                {/* Warnings List */}
                {result.warnings.length > 0 && (
                  <div className="border-t border-ink/5 pt-4 space-y-2">
                    <div className="text-[10px] font-bold text-ink/40 uppercase tracking-wider flex items-center gap-1">
                      <WarningCircle size={12} className="text-terracotta" />
                      <span>Cảnh báo / Bỏ qua ({result.warnings.length})</span>
                    </div>
                    <ul className="space-y-1 max-h-40 overflow-y-auto pr-1">
                      {result.warnings.map((warn, i) => (
                        <li key={i} className="text-xs leading-relaxed text-ink/65 pl-2 border-l border-terracotta/40">
                          {warn}
                        </li>
                      ))}
                    </ul>
                  </div>
                )}
              </section>
            )}
          </div>

          {/* Right Side: Connections list */}
          <div className="space-y-6">
            <section className="rounded-2xl border border-ink/10 bg-white p-5 shadow-sm space-y-4">
              <h3 className="text-xs font-extrabold text-ink uppercase tracking-wider">Kênh liên kết POS</h3>
              
              <div className="space-y-3">
                {/* CSV Provider (Active) */}
                <div className="rounded-xl border border-sage-dark/20 bg-sage/5 p-4 space-y-2">
                  <div className="flex justify-between items-center">
                    <span className="font-bold text-xs text-sage-dark">{activeProvider?.provider}</span>
                    <span className="inline-block rounded bg-sage-dark px-1.5 py-0.5 text-[9px] font-bold text-white">
                      ĐANG HOẠT ĐỘNG
                    </span>
                  </div>
                  <p className="text-[11px] leading-relaxed text-ink/75">
                    {activeProvider?.description}
                  </p>
                </div>

                {/* Other Providers (Coming Soon) */}
                {otherProviders.map(p => (
                  <div key={p.provider} className="rounded-xl border border-ink/10 bg-offwhite/30 p-4 space-y-1.5 opacity-60">
                    <div className="flex justify-between items-center">
                      <span className="font-bold text-xs text-ink/70">{p.provider}</span>
                      <span className="text-[9px] font-bold text-ink/40">
                        CHƯA CẤU HÌNH
                      </span>
                    </div>
                    <p className="text-[11px] leading-relaxed text-ink/50">
                      {p.description}
                    </p>
                  </div>
                ))}
              </div>
            </section>
          </div>
        </div>
      </StateView>
    </div>
  )
}
