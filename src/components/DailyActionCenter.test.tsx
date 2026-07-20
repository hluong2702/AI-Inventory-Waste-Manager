import { render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import DailyActionCenter from './DailyActionCenter'

// Mock React Query to return custom data for the two calls
vi.mock('@tanstack/react-query', () => {
  return {
    useQueryClient: () => ({
      invalidateQueries: vi.fn(),
    }),
    useQuery: vi.fn((options: any) => {
      const key = options.queryKey?.[0]
      if (key === 'daily-actions') {
        return {
          data: {
            content: [],
            totalElements: 0,
            totalPages: 1,
            number: 0,
            size: 5,
            first: true,
            last: true,
          },
          isLoading: false,
        }
      }
      if (key === 'daily-actions-count') {
        return {
          data: { openCount: 0 },
          isLoading: false,
        }
      }
      return { data: null, isLoading: false }
    }),
    useMutation: () => ({
      mutate: vi.fn(),
      isPending: false,
    }),
  }
})

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
          topInsights: [],
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
