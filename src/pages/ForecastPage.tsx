import { useEffect, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import apiClient from '../api/client'
import type {
  AiForecast,
  ApiResponse,
  DailyForecastPoint,
  Forecast,
  InventoryInsight,
  PageResponse,
  WasteRiskLevel,
} from '../types'
import { useStore } from '../context/StoreContext'
import StateView from '../components/StateView'
import {
  ArrowRight,
  ArrowsClockwise,
  Brain,
  CalendarCheck,
  Calculator,
  CloudRain,
  Info,
  Lightning,
  ShoppingCart,
  Sun,
  Thermometer,
  WarningCircle,
} from '@phosphor-icons/react'

// ─── ForecastPage ─────────────────────────────────────────────────────────────

export default function ForecastPage() {
  const { activeStore } = useStore()
  const navigate = useNavigate()

  // Tab: 'classic' (Moving Average) | 'ai' (Prophet + Weather)
  const [activeTab, setActiveTab] = useState<'classic' | 'ai'>('classic')

  // Classic tab state
  const [days, setDays] = useState<number>(7)
  const [forecastPage, setForecastPage] = useState(0)
  const forecastPageSize = 25

  // AI tab state
  const [selectedIngredientId, setSelectedIngredientId] = useState<number | null>(null)
  const [aiDays, setAiDays] = useState<number>(7)

  useEffect(() => {
    setForecastPage(0)
  }, [activeStore?.id, days])

  useEffect(() => {
    setSelectedIngredientId(null)
  }, [activeStore?.id])

  // ── Classic forecast (Moving Average) ────────────────────────────────────
  const { data: response, isLoading, isError } = useQuery({
    queryKey: ['forecast', activeStore?.id, days, forecastPage],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<Forecast>>>(
        `/forecast?days=${days}&page=${forecastPage}&size=${forecastPageSize}`,
      )
      return res.data
    },
    enabled: !!activeStore?.id && activeTab === 'classic',
  })
  const forecastList = response?.data.content ?? []
  const forecastResult = response?.data

  // ── Insights (top 3) for insight cards ────────────────────────────────────
  const { data: insightResponse, isLoading: isLoadingInsights, isError: isInsightError } = useQuery({
    queryKey: ['inventory-insights', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<InventoryInsight>>>(
        '/insights/inventory?page=0&size=3',
      )
      return res.data
    },
    enabled: !!activeStore?.id,
  })
  const insights = insightResponse?.data.content ?? []

  // ── Ingredient list for AI tab dropdown ───────────────────────────────────
  const { data: ingredientListResp } = useQuery({
    queryKey: ['forecast-ingredient-list', activeStore?.id],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<PageResponse<Forecast>>>(
        `/forecast?days=7&page=0&size=100`,
      )
      return res.data
    },
    enabled: !!activeStore?.id && activeTab === 'ai',
  })
  const ingredientList = ingredientListResp?.data.content ?? []

  // Auto-select first ingredient when list loads
  useEffect(() => {
    if (activeTab === 'ai' && ingredientList.length > 0 && selectedIngredientId === null) {
      setSelectedIngredientId(ingredientList[0].ingredientId)
    }
  }, [activeTab, ingredientList, selectedIngredientId])

  // ── AI Forecast (Prophet) ─────────────────────────────────────────────────
  const {
    data: aiResponse,
    isLoading: isAiLoading,
    isError: isAiError,
    refetch: refetchAi,
  } = useQuery({
    queryKey: ['forecast-ai', activeStore?.id, selectedIngredientId, aiDays],
    queryFn: async () => {
      const res = await apiClient.get<ApiResponse<AiForecast>>(
        `/forecast/ai?ingredientId=${selectedIngredientId}&days=${aiDays}`,
      )
      return res.data
    },
    enabled: !!activeStore?.id && activeTab === 'ai' && selectedIngredientId !== null,
    staleTime: 5 * 60 * 1000, // 5 phút
  })
  const aiForecast = aiResponse?.data

  return (
    <div className="space-y-6 font-sans">
      {/* ── Header ── */}
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h2 className="text-2xl font-bold tracking-tight text-ink">Dự báo & Đề xuất nhập hàng</h2>
          <p className="text-xs text-ink/60">
            Thuật toán tự động tính toán khối lượng hàng hóa cần đặt để tối ưu dòng tiền và tránh cạn kho.
          </p>
        </div>
        <div
          className="flex items-center gap-1.5 bg-ink/5 border border-ink/10 text-ink/55 rounded-xl py-2.5 px-4 text-xs font-semibold shrink-0"
          title="Hệ thống chưa có quy trình phiếu mua hàng và nhận hàng hoàn chỉnh."
        >
          <ShoppingCart size={16} />
          <span>Phiếu mua hàng chưa khả dụng</span>
        </div>
      </div>

      {/* ── Tab Switcher ── */}
      <div className="flex bg-ink/5 p-1 rounded-2xl border border-ink/5 w-fit gap-1">
        <button
          onClick={() => setActiveTab('classic')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'classic'
              ? 'bg-white text-ink shadow-sm'
              : 'text-ink/55 hover:text-ink'
          }`}
        >
          <Calculator size={15} />
          Dự báo thông thường
        </button>
        <button
          onClick={() => setActiveTab('ai')}
          className={`flex items-center gap-2 px-4 py-2.5 rounded-xl text-xs font-bold transition-all ${
            activeTab === 'ai'
              ? 'bg-gradient-to-r from-violet-600 to-indigo-600 text-white shadow-sm'
              : 'text-ink/55 hover:text-ink'
          }`}
        >
          <Brain size={15} />
          AI Forecast
          <span className="bg-violet-100 text-violet-700 text-[9px] font-extrabold px-1.5 py-0.5 rounded-full">
            PRO
          </span>
        </button>
      </div>

      {/* ═══════════════════════════════════════════════════════════════════
          CLASSIC TAB
      ═══════════════════════════════════════════════════════════════════ */}
      {activeTab === 'classic' && (
        <>
          <div className="bg-amber-50 border border-amber-200 text-amber-900 p-3.5 rounded-xl text-xs leading-relaxed flex items-start gap-2">
            <WarningCircle size={16} className="shrink-0 mt-0.5" />
            <span>
              Các con số dưới đây chỉ là nhu cầu dự kiến. Hệ thống không tự tạo lô hoặc tăng tồn kho từ đề xuất;
              chỉ ghi nhận nhập kho sau khi hàng thực tế đã được nhận và có số lô, hạn dùng, số lượng cùng giá vốn xác thực.
            </span>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="lg:col-span-2 bg-white border border-ink/5 rounded-2xl p-5 shadow-sm flex items-start gap-4">
              <div className="w-10 h-10 rounded-xl bg-sage/10 text-sage-dark flex items-center justify-center shrink-0">
                <Calculator size={20} />
              </div>
              <div>
                <h4 className="font-bold text-xs text-ink uppercase tracking-wider">Công thức dự báo thông minh</h4>
                <div className="text-xs text-ink/75 leading-relaxed mt-2 space-y-1">
                  <p>Số lượng đề xuất nhập = <span className="font-bold text-terracotta">(Avg × Days)</span> + <span className="font-semibold text-sage-dark">MinStock</span> - <span className="font-semibold text-ink">CurrentStock</span></p>
                  <ul className="text-[10px] text-ink/60 list-disc list-inside space-y-0.5">
                    <li><span className="font-bold">Avg (Tiêu thụ ngày):</span> Lượng tiêu thụ trung bình hàng ngày của nguyên liệu.</li>
                    <li><span className="font-bold">Days (Ngày dự trữ):</span> Số ngày dự kiến bán hàng cần chuẩn bị hàng hóa.</li>
                    <li><span className="font-bold">MinStock (Ngưỡng an toàn):</span> Số lượng tối thiểu cần có sẵn để phòng ngừa rủi ro.</li>
                    <li><span className="font-bold">CurrentStock (Tồn hiện tại):</span> Tổng tồn kho khả dụng hiện thực của các lô hàng.</li>
                  </ul>
                </div>
              </div>
            </div>

            <div className="bg-white border border-ink/5 rounded-2xl p-5 shadow-sm flex flex-col justify-between">
              <div>
                <label className="block text-xs font-bold text-ink uppercase tracking-wider mb-2">Số ngày bán hàng cần dự trữ</label>
                <p className="text-[10px] text-ink/50 leading-relaxed mb-3">
                  Tùy biến số ngày dự kiến chuẩn bị nguyên liệu (Ví dụ: đặt hàng cho 7 ngày tới, 14 ngày tới).
                </p>
              </div>
              <div className="flex bg-ink/5 p-1 rounded-xl border border-ink/5">
                {[7, 14, 30].map((d) => (
                  <button
                    key={d}
                    onClick={() => setDays(d)}
                    className={`flex-1 text-center py-1.5 rounded-lg text-xs font-bold transition-all ${
                      days === d ? 'bg-white text-sage-dark shadow-sm' : 'text-ink/60 hover:text-ink'
                    }`}
                  >
                    {d} ngày
                  </button>
                ))}
              </div>
            </div>
          </div>

          <StateView isLoading={isLoadingInsights} isError={isInsightError}>
            <div className="grid grid-cols-1 lg:grid-cols-3 gap-4">
              {insights.slice(0, 3).map((insight) => (
                <InsightCard
                  key={insight.ingredientId}
                  insight={insight}
                  onPrimaryAction={() => {
                    if (insight.recommendedOrderQty > 0) {
                      navigate('/transactions')
                    } else {
                      navigate('/inventory')
                    }
                  }}
                />
              ))}
            </div>
          </StateView>

          <StateView isLoading={isLoading} isError={isError}>
            <div className="bg-white rounded-2xl border border-ink/5 overflow-hidden shadow-sm">
              <table className="w-full text-left border-collapse text-xs">
                <thead>
                  <tr className="bg-ink/5 text-ink/80 font-bold border-b border-ink/10">
                    <th className="px-6 py-4">Mã</th>
                    <th className="px-6 py-4">Nguyên liệu</th>
                    <th className="px-6 py-4 text-right">Mức tiêu thụ/ngày</th>
                    <th className="px-6 py-4 text-right">Tồn kho hiện tại</th>
                    <th className="px-6 py-4 text-right">Tồn tối thiểu (Min)</th>
                    <th className="px-6 py-4 text-right bg-sage/5 text-sage-dark font-extrabold">Đề xuất đặt ({days} ngày)</th>
                    <th className="px-6 py-4 text-center">Trạng thái đặt hàng</th>
                    <th className="px-6 py-4 text-right">Bước tiếp theo</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-ink/10 text-ink/80">
                  {forecastList.map((item) => {
                    const needsOrder = item.recommendedOrder > 0
                    const isUrgent = item.currentStock < item.minStock && needsOrder

                    return (
                      <tr key={item.ingredientId} className="hover:bg-ink/5 transition-colors">
                        <td className="px-6 py-4 font-mono font-bold text-ink">{item.ingredientCode}</td>
                        <td className="px-6 py-4 font-semibold text-ink">{item.ingredientName}</td>
                        <td className="px-6 py-4 text-right font-mono font-semibold">{item.avgDailyUsage} {item.unit}</td>
                        <td className="px-6 py-4 text-right font-mono font-semibold">{item.currentStock} {item.unit}</td>
                        <td className="px-6 py-4 text-right font-mono font-semibold text-ink/60">{item.minStock} {item.unit}</td>
                        <td className={`px-6 py-4 text-right font-mono font-extrabold bg-sage/5 ${
                          needsOrder ? 'text-terracotta' : 'text-sage-dark'
                        }`}>
                          {item.recommendedOrder} {item.unit}
                        </td>
                        <td className="px-6 py-4 text-center">
                          <span className={`inline-flex items-center gap-0.5 px-2 py-0.5 rounded-full font-bold text-[10px] ${
                            isUrgent 
                              ? 'bg-red-500/10 text-red-600 animate-pulse' 
                              : needsOrder 
                              ? 'bg-terracotta/15 text-terracotta' 
                              : 'bg-sage/15 text-sage-dark'
                          }`}>
                            {isUrgent ? <WarningCircle size={10} /> : null}
                            {isUrgent ? 'Cần nhập gấp' : needsOrder ? 'Chuẩn bị nhập' : 'Đủ dùng'}
                          </span>
                        </td>
                        <td className="px-6 py-4 text-right">
                          <button
                            onClick={() => navigate('/transactions')}
                            disabled={!needsOrder}
                            title="Chỉ ghi nhận nhập kho sau khi hàng thực tế đã được nhận."
                            className="inline-flex items-center gap-1 bg-white hover:bg-sage hover:text-white border border-sage-dark/25 text-sage-dark px-3 py-1.5 rounded-xl font-bold transition-all disabled:opacity-40 disabled:bg-ink/5 disabled:text-ink/30 disabled:border-ink/10 shadow-sm"
                          >
                            <CalendarCheck size={13} />
                            <span>Nhập khi nhận hàng</span>
                          </button>
                        </td>
                      </tr>
                    )
                  })}
                </tbody>
              </table>
              {forecastResult && forecastResult.totalPages > 1 && (
                <div className="flex items-center justify-between gap-3 border-t border-ink/10 px-6 py-3 text-xs">
                  <span className="text-ink/55">
                    Trang {forecastResult.number + 1}/{forecastResult.totalPages} · {forecastResult.totalElements} nguyên liệu
                  </span>
                  <div className="flex items-center gap-2">
                    <button
                      type="button"
                      onClick={() => setForecastPage((current) => Math.max(current - 1, 0))}
                      disabled={forecastResult.first || isLoading}
                      className="rounded-lg border border-ink/10 bg-white px-3 py-1.5 font-bold text-ink transition-colors hover:border-sage-dark/30 hover:text-sage-dark disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Trang trước
                    </button>
                    <button
                      type="button"
                      onClick={() => setForecastPage((current) => current + 1)}
                      disabled={forecastResult.last || isLoading}
                      className="rounded-lg border border-ink/10 bg-white px-3 py-1.5 font-bold text-ink transition-colors hover:border-sage-dark/30 hover:text-sage-dark disabled:cursor-not-allowed disabled:opacity-40"
                    >
                      Trang sau
                    </button>
                  </div>
                </div>
              )}
            </div>
          </StateView>
        </>
      )}

      {/* ═══════════════════════════════════════════════════════════════════
          AI FORECAST TAB
      ═══════════════════════════════════════════════════════════════════ */}
      {activeTab === 'ai' && (
        <div className="space-y-6">
          {/* Controls row */}
          <div className="flex flex-col sm:flex-row gap-4">
            {/* Ingredient selector */}
            <div className="flex-1">
              <label className="block text-xs font-bold text-ink/60 mb-1.5">Chọn nguyên liệu</label>
              <select
                value={selectedIngredientId ?? ''}
                onChange={(e) => setSelectedIngredientId(Number(e.target.value))}
                className="w-full bg-white border border-ink/10 rounded-xl px-3 py-2.5 text-sm font-semibold text-ink focus:outline-none focus:ring-2 focus:ring-violet-500/30 focus:border-violet-400"
              >
                <option value="" disabled>— Chọn nguyên liệu —</option>
                {ingredientList.map((item) => (
                  <option key={item.ingredientId} value={item.ingredientId}>
                    {item.ingredientCode} · {item.ingredientName} ({item.unit})
                  </option>
                ))}
              </select>
            </div>

            {/* Days selector */}
            <div className="shrink-0">
              <label className="block text-xs font-bold text-ink/60 mb-1.5">Số ngày dự báo</label>
              <div className="flex bg-ink/5 p-1 rounded-xl border border-ink/5 h-[42px]">
                {[7, 14].map((d) => (
                  <button
                    key={d}
                    onClick={() => setAiDays(d)}
                    className={`px-4 rounded-lg text-xs font-bold transition-all ${
                      aiDays === d ? 'bg-white text-violet-700 shadow-sm' : 'text-ink/60 hover:text-ink'
                    }`}
                  >
                    {d} ngày
                  </button>
                ))}
              </div>
            </div>

            {/* Refresh button */}
            <div className="flex items-end">
              <button
                onClick={() => refetchAi()}
                disabled={isAiLoading || selectedIngredientId === null}
                className="flex items-center gap-2 bg-violet-600 hover:bg-violet-700 text-white px-4 py-2.5 rounded-xl text-xs font-bold transition-all disabled:opacity-40 disabled:cursor-not-allowed shadow-sm"
              >
                <ArrowsClockwise size={14} className={isAiLoading ? 'animate-spin' : ''} />
                Tính lại
              </button>
            </div>
          </div>

          {/* AI Result */}
          <StateView isLoading={isAiLoading} isError={isAiError}>
            {aiForecast ? (
              <AiForecastPanel forecast={aiForecast} />
            ) : (
              <div className="bg-white border border-ink/5 rounded-2xl p-8 text-center text-ink/50 text-sm">
                Chọn nguyên liệu và nhấn <strong>Tính lại</strong> để xem dự báo AI
              </div>
            )}
          </StateView>
        </div>
      )}
    </div>
  )
}

// ─── AI Forecast Panel ────────────────────────────────────────────────────────

function AiForecastPanel({ forecast }: { forecast: AiForecast }) {
  const isProphet = forecast.modelUsed === 'prophet'

  return (
    <div className="space-y-5">
      {/* Fallback banner */}
      {forecast.isJavaFallback && (
        <div className="bg-amber-50 border border-amber-200 text-amber-900 p-3 rounded-xl text-xs flex items-start gap-2">
          <WarningCircle size={15} className="shrink-0 mt-0.5" />
          <span>
            <strong>AI Service đang offline.</strong> Đang hiển thị kết quả Moving Average từ Spring Boot.
            Khởi động lại Python service để dùng Prophet + Weather.
          </span>
        </div>
      )}

      {/* Summary cards */}
      <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
        <SummaryCard
          label="Tổng nhu cầu dự báo"
          value={`${forecast.totalPredictedDemand.toFixed(2)} ${forecast.unit}`}
          accent="violet"
        />
        <SummaryCard
          label="Trung bình/ngày (AI)"
          value={`${forecast.avgDailyPredicted.toFixed(2)} ${forecast.unit}`}
          accent="indigo"
        />
        <SummaryCard
          label="Tồn kho hiện tại"
          value={`${forecast.currentStock} ${forecast.unit}`}
          accent="sage"
        />
        <SummaryCard
          label="AI Đề xuất đặt"
          value={`${forecast.aiRecommendedOrder.toFixed(2)} ${forecast.unit}`}
          accent={forecast.aiRecommendedOrder > 0 ? 'terracotta' : 'sage'}
          strong
        />
      </div>

      {/* Model info bar */}
      <div className="bg-white border border-ink/5 rounded-2xl p-4 flex flex-wrap items-center gap-3 justify-between shadow-sm">
        <div className="flex items-center gap-3">
          <div className={`flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[10px] font-extrabold ${
            isProphet
              ? 'bg-violet-100 text-violet-700'
              : 'bg-amber-100 text-amber-700'
          }`}>
            <Brain size={11} />
            {isProphet ? 'Prophet AI Model' : 'Moving Average (Fallback)'}
          </div>
          <span className="text-xs text-ink/55">{forecast.historyDaysUsed} ngày lịch sử</span>
          {forecast.modelAccuracyMape !== null && (
            <span className="text-xs text-ink/55">
              · Độ chính xác MAPE: <strong className="text-violet-700">{forecast.modelAccuracyMape}%</strong>
            </span>
          )}
        </div>
        <div className="flex items-center gap-1.5 text-[10px] text-ink/50">
          <Info size={12} />
          {forecast.confidenceNote}
        </div>
      </div>

      {/* Daily breakdown */}
      <div className="bg-white border border-ink/5 rounded-2xl overflow-hidden shadow-sm">
        <div className="px-5 py-4 border-b border-ink/5 flex items-center gap-2">
          <h3 className="font-bold text-sm text-ink">Dự báo theo ngày</h3>
          <span className="text-[10px] text-ink/45 font-semibold">Kèm dự báo thời tiết</span>
        </div>
        <div className="divide-y divide-ink/5">
          {forecast.dailyBreakdown.map((day) => (
            <DayRow key={day.date} day={day} unit={forecast.unit} />
          ))}
        </div>
      </div>
    </div>
  )
}

// ─── DayRow: một ngày trong breakdown ─────────────────────────────────────────

function DayRow({ day, unit }: { day: DailyForecastPoint; unit: string }) {
  const dateObj = new Date(day.date)
  const dateLabel = dateObj.toLocaleDateString('vi-VN', { weekday: 'short', day: '2-digit', month: '2-digit' })
  const isWeekend = day.isWeekend
  const isHot = (day.temperatureMax ?? 0) >= 33
  const isRainy = (day.rainMm ?? 0) >= 5
  const factorUp = day.weatherFactor > 1.03
  const factorDown = day.weatherFactor < 0.97

  return (
    <div className={`flex items-center gap-4 px-5 py-3.5 hover:bg-ink/[0.02] transition-colors ${
      day.isHoliday ? 'bg-red-50/50' : isWeekend ? 'bg-violet-50/30' : ''
    }`}>
      {/* Date */}
      <div className="w-28 shrink-0">
        <p className={`text-xs font-extrabold ${isWeekend ? 'text-violet-600' : 'text-ink'}`}>{dateLabel}</p>
        {day.isHoliday && (
          <span className="text-[9px] text-red-600 font-bold">Ngày lễ</span>
        )}
      </div>

      {/* Demand bar */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <div className="flex-1 bg-ink/5 rounded-full h-1.5 overflow-hidden">
            <div
              className="h-full bg-gradient-to-r from-violet-400 to-indigo-500 rounded-full transition-all"
              style={{ width: `${Math.min(100, (day.predictedDemand / Math.max(...[day.predictedDemand])) * 100)}%` }}
            />
          </div>
          <span className="text-xs font-bold text-ink w-20 text-right shrink-0">
            {day.predictedDemand.toFixed(2)} {unit}
          </span>
        </div>
        <p className="text-[10px] text-ink/40 mt-0.5">
          [{day.lowerBound.toFixed(1)} – {day.upperBound.toFixed(1)}] confidence 80%
        </p>
      </div>

      {/* Weather */}
      <div className="w-44 shrink-0 flex items-center gap-2">
        {isRainy ? (
          <CloudRain size={14} className="text-blue-400 shrink-0" />
        ) : (
          <Sun size={14} className="text-amber-400 shrink-0" />
        )}
        <div className="min-w-0">
          <p className="text-[10px] text-ink/60 truncate">{day.weatherCondition}</p>
          {day.temperatureMax !== null && (
            <p className="text-[9px] text-ink/40 flex items-center gap-0.5">
              <Thermometer size={9} />{day.temperatureMax.toFixed(0)}°C
              {day.rainMm !== null && day.rainMm > 0 && ` · ${day.rainMm.toFixed(0)}mm`}
            </p>
          )}
        </div>
      </div>

      {/* Weather factor badge */}
      <div className="w-20 shrink-0 text-right">
        <span className={`inline-block text-[10px] font-bold px-2 py-0.5 rounded-full ${
          factorUp
            ? 'bg-terracotta/10 text-terracotta'
            : factorDown
            ? 'bg-blue-100 text-blue-600'
            : 'bg-ink/5 text-ink/50'
        }`}>
          {factorUp ? '▲' : factorDown ? '▼' : '='} ×{day.weatherFactor.toFixed(2)}
        </span>
      </div>
    </div>
  )
}

// ─── Summary card ─────────────────────────────────────────────────────────────

function SummaryCard({
  label,
  value,
  accent,
  strong = false,
}: {
  label: string
  value: string
  accent: 'violet' | 'indigo' | 'sage' | 'terracotta'
  strong?: boolean
}) {
  const accentMap = {
    violet: 'bg-violet-50 border-violet-100',
    indigo: 'bg-indigo-50 border-indigo-100',
    sage: 'bg-green-50 border-green-100',
    terracotta: 'bg-orange-50 border-orange-100',
  }
  const textMap = {
    violet: 'text-violet-700',
    indigo: 'text-indigo-700',
    sage: 'text-green-700',
    terracotta: 'text-orange-600',
  }

  return (
    <div className={`rounded-2xl border p-4 ${accentMap[accent]}`}>
      <p className="text-[10px] font-bold uppercase tracking-wider text-ink/50 mb-1">{label}</p>
      <p className={`text-lg font-extrabold ${strong ? textMap[accent] : 'text-ink'}`}>{value}</p>
    </div>
  )
}

// ─── InsightCard ──────────────────────────────────────────────────────────────

function InsightCard({
  insight,
  onPrimaryAction
}: {
  insight: InventoryInsight
  onPrimaryAction: () => void
}) {
  const tone = riskTone(insight.wasteRiskLevel)
  const stockoutText = insight.daysUntilStockout === null ? 'Chưa rõ' : `${insight.daysUntilStockout} ngày`
  const expiryText = insight.daysUntilExpiry === null ? 'Không có lô' : `${insight.daysUntilExpiry} ngày`

  return (
    <div className={`bg-white border ${tone.border} rounded-2xl p-4 shadow-sm space-y-3`}>
      <div className="flex items-start justify-between gap-3">
        <div className="min-w-0">
          <p className="text-[10px] font-bold uppercase tracking-wider text-ink/50">{insight.ingredientCode}</p>
          <h3 className="font-bold text-sm text-ink truncate mt-0.5">{insight.ingredientName}</h3>
        </div>
        <span className={`shrink-0 inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-extrabold ${tone.badge}`}>
          <Lightning size={11} weight="fill" />
          {tone.label}
        </span>
      </div>

      <div className="grid grid-cols-3 gap-2">
        <Metric label="Cạn kho" value={stockoutText} />
        <Metric label="Cận hạn" value={expiryText} />
        <Metric label="Nên nhập" value={`${insight.recommendedOrderQty} ${insight.unit}`} strong={insight.recommendedOrderQty > 0} />
      </div>

      <ul className="space-y-1.5 text-[11px] leading-relaxed text-ink/70">
        {insight.explanationBullets.slice(0, 3).map((bullet) => (
          <li key={bullet} className="flex gap-1.5">
            <span className={`mt-1 h-1.5 w-1.5 rounded-full ${tone.dot}`} />
            <span>{bullet}</span>
          </li>
        ))}
      </ul>

      <button
        onClick={onPrimaryAction}
        className={`w-full inline-flex items-center justify-center gap-1.5 rounded-xl px-3 py-2 text-xs font-bold transition-colors ${tone.button}`}
      >
        {insight.ctaLabel}
        <ArrowRight size={13} />
      </button>
    </div>
  )
}

function Metric({ label, value, strong = false }: { label: string; value: string; strong?: boolean }) {
  return (
    <div className="rounded-xl bg-offwhite/60 border border-ink/5 px-2 py-2 min-w-0">
      <div className="text-[9px] uppercase font-bold text-ink/45 truncate">{label}</div>
      <div className={`text-[11px] font-extrabold truncate mt-0.5 ${strong ? 'text-terracotta' : 'text-ink'}`}>{value}</div>
    </div>
  )
}

function riskTone(level: WasteRiskLevel) {
  if (level === 'HIGH') {
    return {
      label: 'Rủi ro cao',
      border: 'border-red-500/20',
      badge: 'bg-red-500/10 text-red-600',
      dot: 'bg-red-500',
      button: 'bg-red-600 text-white hover:bg-red-700'
    }
  }
  if (level === 'MEDIUM') {
    return {
      label: 'Cần chú ý',
      border: 'border-terracotta/20',
      badge: 'bg-terracotta/15 text-terracotta',
      dot: 'bg-terracotta',
      button: 'bg-terracotta text-white hover:brightness-95'
    }
  }
  return {
    label: 'Ổn định',
    border: 'border-sage/25',
    badge: 'bg-sage/15 text-sage-dark',
    dot: 'bg-sage-dark',
    button: 'bg-sage-dark text-white hover:bg-sage'
  }
}
