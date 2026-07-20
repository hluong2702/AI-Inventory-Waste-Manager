import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import DailyActionCenter from './DailyActionCenter'

describe('DailyActionCenter', () => {
  it('does not claim the inventory is safe when advanced insights are unavailable', () => {
    render(
      <DailyActionCenter
        storeName="Coffee A"
        insightsAvailable={false}
        insights={null}
        openAlerts={2}
        monthlyWasteCost={null}
        canViewForecast={false}
        onNavigate={vi.fn()}
      />,
    )

    expect(screen.getByText('Gói hiện tại chưa có phân tích nâng cao')).toBeInTheDocument()
    expect(screen.queryByText('Kho đang trong vùng an toàn')).not.toBeInTheDocument()
  })

  it('shows the safe state only after a successful insight summary', () => {
    render(
      <DailyActionCenter
        storeName="Coffee A"
        insightsAvailable
        insights={{
          totalCount: 1,
          highRiskCount: 0,
          suggestedOrderCount: 0,
          healthyCount: 1,
          topInsights: [{
            storeId: 1,
            ingredientId: 10,
            ingredientName: 'Sữa',
            ingredientCode: 'MILK',
            unit: 'lít',
            nearestBatchId: null,
            nearestBatchExpiryDate: null,
            avgDailyUsage7d: 1,
            avgDailyUsage28d: 1,
            weekdayAdjustedUsage: null,
            currentStock: 20,
            daysUntilStockout: 20,
            daysUntilExpiry: null,
            wasteRiskLevel: 'LOW',
            recommendedOrderQty: 0,
            explanationBullets: ['Ổn định'],
            ctaLabel: 'Theo dõi',
          }],
        }}
        openAlerts={0}
        monthlyWasteCost={0}
        canViewForecast
        onNavigate={vi.fn()}
      />,
    )

    expect(screen.getByText('Kho đang trong vùng an toàn')).toBeInTheDocument()
  })
})
