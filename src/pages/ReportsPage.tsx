import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import apiClient from '../api/client'
import type { ApiResponse, WasteRecord, Ingredient, WasteDashboard, AuditLog } from '../types'
import { useStore } from '../context/StoreContext'
import DoubleBezelCard from '../components/DoubleBezelCard'
import StateView from '../components/StateView'
import { formatVND, formatDate } from '../utils/fefo'
import { 
  PieChart, 
  Pie, 
  Cell, 
  Tooltip, 
  Legend, 
  ResponsiveContainer 
} from 'recharts'
import { 
  Calendar, 
  Download, 
  Funnel,
  TrendDown,
  Coins,
  Package,
  ClipboardText
} from '@phosphor-icons/react'

const reasonLabel: Record<WasteRecord['reason'], string> = {
  EXPIRED: 'Hết hạn sử dụng',
  DAMAGED: 'Hư hỏng / Hao hụt',
  PREP_ERROR: 'Lỗi chế biến bếp',
  OTHER: 'Lý do khác',
}

const COLORS = {
  EXPIRED: '#C97B4A',    // Terracotta
  DAMAGED: '#E6B080',    // Muted Orange
  PREP_ERROR: '#8A9A7E', // Sage light
  OTHER: '#3A3A34'       // Ink (Dark)
}

export default function ReportsPage() {
  const { activeStore } = useStore()
  
  // Lọc theo khoảng thời gian (Mặc định lấy 30 ngày qua)
  const today = new Date()
  const defaultEndDate = today.toISOString().slice(0, 10)
  const defaultStartDate = new Date(today.getTime() - 14 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10)
  const [startDate, setStartDate] = useState(defaultStartDate)
  const [endDate, setEndDate] = useState(defaultEndDate)

  // 1. Tải danh sách nguyên liệu
  const { data: ingResponse } = useQuery({
    queryKey: ['ingredients'],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<Ingredient[]>>('/ingredients')
      return res.data
    }
  })
  const ingredients = ingResponse?.data ?? []

  // 2. Tải danh sách báo cáo lãng phí theo khoảng thời gian
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['reports-waste', activeStore?.id, startDate, endDate],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<WasteRecord[]>>(
        `/reports/waste?startDate=${startDate}&endDate=${endDate}`
      )
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const wasteRecords = response?.data ?? []

  const { data: dashboardResponse } = useQuery({
    queryKey: ['waste-dashboard', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<WasteDashboard>>('/reports/waste/dashboard?period=month')
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const wasteDashboard = dashboardResponse?.data

  const { data: auditResponse } = useQuery({
    queryKey: ['audit-log', activeStore?.id, startDate, endDate],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<AuditLog[]>>(`/reports/audit-log?startDate=${startDate}&endDate=${endDate}`)
      return res.data
    },
    enabled: !!activeStore?.id
  })
  const auditLogs = auditResponse?.data ?? []

  // ==========================================
  // XỬ LÝ SỐ LIỆU THỐNG KÊ LÃNG PHÍ
  // ==========================================
  
  // Tính tổng chi phí lãng phí
  const totalCost = wasteRecords.reduce((sum, w) => sum + w.estimatedCost, 0)
  
  // Tính tổng khối lượng hủy
  const totalQty = wasteRecords.reduce((sum, w) => sum + w.quantity, 0)

  // Nhóm lãng phí theo nguyên nhân cho biểu đồ Tròn (PieChart)
  const groupedByReason = wasteRecords.reduce<Record<string, number>>((acc, w) => {
    const label = reasonLabel[w.reason]
    acc[label] = (acc[label] || 0) + w.estimatedCost
    return acc
  }, {})

  const chartData = Object.entries(groupedByReason).map(([name, value]) => ({
    name,
    value,
    color: name === 'Hết hạn sử dụng' 
      ? COLORS.EXPIRED 
      : name === 'Hư hỏng / Hao hụt' 
      ? COLORS.DAMAGED 
      : name === 'Lỗi chế biến bếp' 
      ? COLORS.PREP_ERROR 
      : COLORS.OTHER
  }))

  async function downloadCsv(path: string, filename: string) {
    const res = await apiClient.get(path, { responseType: 'blob' })
    const url = URL.createObjectURL(res.data)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    link.click()
    URL.revokeObjectURL(url)
  }

  function handleExportWaste() {
    downloadCsv(`/reports/waste/export?startDate=${startDate}&endDate=${endDate}`, `bao-cao-that-thoat-${startDate}-${endDate}.csv`)
  }

  function handleExportInventory() {
    downloadCsv(`/reports/inventory/export?startDate=${startDate}&endDate=${endDate}`, `giao-dich-ton-kho-${startDate}-${endDate}.csv`)
  }

  return (
    <div className="space-y-6 font-sans">
      
      {/* Header */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Báo cáo lãng phí & Hủy hàng</h2>
          <p className="text-xs text-ink/60">Thống kê chi phí hao hụt và các nguyên nhân dẫn đến hủy bỏ nguyên liệu tại cửa hàng.</p>
        </div>

        {/* Nút xuất báo cáo */}
        <div className="flex flex-wrap gap-2">
          <button
            onClick={handleExportInventory}
            className="flex items-center gap-1.5 border border-ink/15 hover:border-sage-dark/45 bg-white text-ink rounded-xl py-2 px-3.5 text-xs font-semibold hover:bg-ink/5 transition-colors shadow-sm"
          >
            <Download size={14} />
            <span>Export tồn kho</span>
          </button>
          <button
            onClick={handleExportWaste}
            className="flex items-center gap-1.5 border border-ink/15 hover:border-sage-dark/45 bg-white text-ink rounded-xl py-2 px-3.5 text-xs font-semibold hover:bg-ink/5 transition-colors shadow-sm"
          >
            <Download size={14} />
            <span>Export thất thoát</span>
          </button>
        </div>
      </div>

      {/* Thanh bộ lọc thời gian */}
      <div className="bg-white rounded-2xl border border-ink/5 p-4 flex flex-wrap items-end gap-4 shadow-sm">
        <div className="flex items-center gap-2 text-xs font-bold text-ink shrink-0 mb-2 sm:mb-0">
          <Funnel size={16} className="text-sage-dark" />
          <span>Lọc khoảng thời gian:</span>
        </div>
        
        <div>
          <label className="block text-[10px] text-ink/60 font-semibold mb-1">Từ ngày</label>
          <div className="relative">
            <Calendar className="absolute left-3 top-2.5 text-ink/40" size={14} />
            <input
              type="date"
              value={startDate}
              onChange={(e) => setStartDate(e.target.value)}
              className="bg-offwhite/50 border border-ink/10 rounded-xl pl-9 pr-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white"
            />
          </div>
        </div>

        <div>
          <label className="block text-[10px] text-ink/60 font-semibold mb-1">Đến ngày</label>
          <div className="relative">
            <Calendar className="absolute left-3 top-2.5 text-ink/40" size={14} />
            <input
              type="date"
              value={endDate}
              onChange={(e) => setEndDate(e.target.value)}
              className="bg-offwhite/50 border border-ink/10 rounded-xl pl-9 pr-3 py-1.5 text-xs focus:outline-none focus:ring-2 focus:ring-sage focus:bg-white"
            />
          </div>
        </div>
      </div>

      <StateView isLoading={isLoading} isEmpty={wasteRecords.length === 0} isError={isError}>
        
        {/* KPI Mini Grid */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
          <div className="bg-white border border-ink/5 p-5 rounded-2xl shadow-sm flex items-center gap-4">
            <div className="w-10 h-10 rounded-xl bg-terracotta/10 text-terracotta flex items-center justify-center">
              <Coins size={20} />
            </div>
            <div>
              <span className="text-[10px] text-ink/50 uppercase font-bold block">Tổng tiền lãng phí</span>
              <span className="text-lg font-mono font-bold text-ink">{formatVND(totalCost)}</span>
            </div>
          </div>

          <div className="bg-white border border-ink/5 p-5 rounded-2xl shadow-sm flex items-center gap-4">
            <div className="w-10 h-10 rounded-xl bg-sage/15 text-sage-dark flex items-center justify-center">
              <Package size={20} />
            </div>
            <div>
              <span className="text-[10px] text-ink/50 uppercase font-bold block">Tổng lượng hao hụt</span>
              <span className="text-lg font-mono font-bold text-ink">{totalQty.toFixed(1)} đơn vị</span>
            </div>
          </div>

          <div className="bg-white border border-ink/5 p-5 rounded-2xl shadow-sm flex items-center gap-4">
            <div className="w-10 h-10 rounded-xl bg-red-500/10 text-red-600 flex items-center justify-center">
              <TrendDown size={20} />
            </div>
            <div>
              <span className="text-[10px] text-ink/50 uppercase font-bold block">Số lượt ghi nhận</span>
              <span className="text-lg font-mono font-bold text-ink">{wasteRecords.length} giao dịch</span>
            </div>
          </div>
        </div>

        {wasteDashboard && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
            <div className="lg:col-span-4 bg-white border border-ink/5 p-5 rounded-2xl shadow-sm">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-terracotta/10 text-terracotta flex items-center justify-center">
                  <TrendDown size={20} />
                </div>
                <div>
                  <span className="text-[10px] text-ink/50 uppercase font-bold block">Thất thoát tháng này</span>
                  <span className="text-xl font-mono font-bold text-ink">{formatVND(wasteDashboard.currentWasteCost)}</span>
                </div>
              </div>
              <div className="mt-4 text-xs text-ink/65">
                Kỳ trước: <span className="font-bold">{formatVND(wasteDashboard.previousWasteCost)}</span>
                <span className={`ml-2 font-extrabold ${wasteDashboard.changePercent > 0 ? 'text-terracotta' : 'text-sage-dark'}`}>
                  {wasteDashboard.changePercent > 0 ? '+' : ''}{wasteDashboard.changePercent}%
                </span>
              </div>
            </div>

            <div className="lg:col-span-8 bg-white border border-ink/5 p-5 rounded-2xl shadow-sm">
              <div className="mb-3 flex items-center justify-between">
                <h3 className="text-sm font-bold text-ink">Top 5 nguyên liệu thất thoát nhất</h3>
                <span className="text-[10px] font-bold uppercase text-ink/45">Theo estimated cost</span>
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                {wasteDashboard.topWasteIngredients.map((item) => (
                  <div key={item.ingredientId} className="rounded-xl border border-ink/5 bg-offwhite/30 px-3 py-2">
                    <div className="flex items-center justify-between gap-3">
                      <span className="text-xs font-bold text-ink truncate">{item.ingredientName}</span>
                      <span className="text-xs font-mono font-bold text-terracotta">{formatVND(item.estimatedCost)}</span>
                    </div>
                    <div className="mt-0.5 text-[10px] text-ink/50">{item.quantity} {item.unit} đã xuất hủy</div>
                  </div>
                ))}
                {wasteDashboard.topWasteIngredients.length === 0 && (
                  <div className="sm:col-span-2 rounded-xl border border-dashed border-ink/10 py-5 text-center text-xs text-ink/50">
                    Chưa có dữ liệu xuất hủy trong kỳ hiện tại.
                  </div>
                )}
              </div>
            </div>
          </div>
        )}

        <div className="grid grid-cols-1 lg:grid-cols-12 gap-8">
          
          {/* Biểu đồ tròn cơ cấu lãng phí */}
          <div className="lg:col-span-5">
            <DoubleBezelCard 
              title="Cơ cấu chi phí thất thoát" 
              subtitle="Tỷ trọng chi phí thiệt hại chia theo nguyên nhân"
            >
              <div className="h-64 w-full flex items-center justify-center">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={chartData}
                      cx="50%"
                      cy="50%"
                      innerRadius={60}
                      outerRadius={80}
                      paddingAngle={4}
                      dataKey="value"
                    >
                      {chartData.map((entry, index) => (
                        <Cell key={`cell-${index}`} fill={entry.color} />
                      ))}
                    </Pie>
                    <Tooltip 
                      formatter={(value: any) => formatVND(value)}
                      contentStyle={{ backgroundColor: '#fff', borderRadius: '8px', fontSize: '11px' }}
                    />
                    <Legend 
                      verticalAlign="bottom" 
                      height={36} 
                      iconType="circle"
                      iconSize={8}
                      wrapperStyle={{ fontSize: '10px' }}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </div>
            </DoubleBezelCard>
          </div>

          {/* Bảng chi tiết nhật ký hủy hàng */}
          <div className="lg:col-span-7">
            <DoubleBezelCard
              title="Nhật ký hủy hàng chi tiết"
              subtitle={`Danh sách giao dịch từ ${formatDate(startDate)} đến ${formatDate(endDate)}`}
            >
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs border-collapse">
                  <thead>
                    <tr className="text-ink/60 font-bold border-b border-ink/10 pb-2">
                      <th className="pb-2">Nguyên liệu</th>
                      <th className="pb-2 text-right">Lượng hủy</th>
                      <th className="pb-2">Lý do hủy</th>
                      <th className="pb-2 text-right">Thiệt hại</th>
                      <th className="pb-2 text-center">Thời gian</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-ink/5 text-ink/80">
                    {wasteRecords.map((log) => {
                      const ing = ingredients.find((i) => i.id === log.ingredientId)
                      return (
                        <tr key={log.id} className="hover:bg-ink/5">
                          <td className="py-2.5">
                            <div className="font-semibold text-ink">{ing?.name ?? 'Nguyên liệu'}</div>
                            <div className="text-[9px] text-ink/50 font-mono">ID Lô: #{log.batchId || '-'}</div>
                          </td>
                          <td className="py-2.5 text-right font-mono font-bold">{log.quantity} {ing?.unit}</td>
                          <td className="py-2.5">
                            <span className="text-[10px] font-bold text-ink bg-ink/5 px-2 py-0.5 rounded">
                              {reasonLabel[log.reason]}
                            </span>
                          </td>
                          <td className="py-2.5 text-right font-semibold font-mono text-terracotta">{formatVND(log.estimatedCost)}</td>
                          <td className="py-2.5 text-center text-ink/50">{formatDate(log.createdAt.split('T')[0])}</td>
                        </tr>
                      )
                    })}
                  </tbody>
                </table>
              </div>
            </DoubleBezelCard>
          </div>

        </div>

        <DoubleBezelCard
          title="Audit log nhập / xuất / xuất hủy"
          subtitle="Owner và quản lý theo dõi ai thao tác, thời gian, cửa hàng, nguyên liệu, lô hàng và số lượng"
          action={
            <span className="flex items-center gap-1 text-[11px] font-bold text-sage-dark uppercase bg-sage/10 px-2 py-0.5 rounded-full">
              <ClipboardText size={12} />
              {auditLogs.length} dòng
            </span>
          }
        >
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs border-collapse">
              <thead>
                <tr className="text-ink/60 font-bold border-b border-ink/10">
                  <th className="pb-2">Thời gian</th>
                  <th className="pb-2">Người thao tác</th>
                  <th className="pb-2">Nguyên liệu</th>
                  <th className="pb-2">Lô hàng</th>
                  <th className="pb-2">Hành động</th>
                  <th className="pb-2 text-right">Số lượng</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-ink/5 text-ink/80">
                {auditLogs.slice(0, 50).map((log) => (
                  <tr key={log.id} className="hover:bg-ink/5">
                    <td className="py-2.5 text-ink/60">{new Date(log.createdAt).toLocaleString('vi-VN')}</td>
                    <td className="py-2.5 font-semibold">{log.actorEmail}</td>
                    <td className="py-2.5">{log.ingredientName}</td>
                    <td className="py-2.5 font-mono text-[10px]">{log.batchNumber || '-'}</td>
                    <td className="py-2.5">
                      <span className={`rounded px-2 py-0.5 text-[10px] font-bold ${log.reason === 'EXPORT_WASTE' ? 'bg-terracotta/10 text-terracotta' : 'bg-sage/10 text-sage-dark'}`}>
                        {log.reason === 'IMPORT_NEW' ? 'Nhập lô hàng' : log.reason === 'EXPORT_WASTE' ? 'Xuất hủy' : log.reason === 'EXPORT_ADJUST' ? 'Điều chỉnh' : 'Xuất dùng'}
                      </span>
                    </td>
                    <td className="py-2.5 text-right font-mono font-bold">{log.quantity} {log.unit}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </DoubleBezelCard>

      </StateView>
    </div>
  )
}
