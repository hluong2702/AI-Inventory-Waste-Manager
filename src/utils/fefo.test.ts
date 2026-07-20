import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { InventoryBatch } from '../types'
import { allocateStockFEFO, formatDate, formatVND, getDaysUntilExpiry } from './fefo'

function batch(overrides: Partial<InventoryBatch>): InventoryBatch {
  return {
    id: 1,
    storeId: 7,
    ingredientId: 11,
    ingredientName: 'Sữa',
    ingredientUnit: 'lít',
    ingredientCategory: 'Dairy',
    batchNumber: 'LOT-1',
    quantity: 5,
    expiredDate: '2026-07-20',
    importDate: '2026-07-01',
    costPerUnit: 30_000,
    ...overrides,
  }
}

describe('allocateStockFEFO', () => {
  it('allocates earliest expiry first without mutating the input', () => {
    const batches = [
      batch({ id: 2, batchNumber: 'LATE', expiredDate: '2026-08-01', quantity: 4 }),
      batch({ id: 1, batchNumber: 'EARLY', expiredDate: '2026-07-18', quantity: 3 }),
    ]

    expect(allocateStockFEFO(batches, 5)).toEqual([
      { batchId: 1, batchNumber: 'EARLY', quantityToDeduct: 3 },
      { batchId: 2, batchNumber: 'LATE', quantityToDeduct: 2 },
    ])
    expect(batches.map((item) => item.batchNumber)).toEqual(['LATE', 'EARLY'])
  })

  it('uses import date as the deterministic tie breaker and ignores empty batches', () => {
    const batches = [
      batch({ id: 3, batchNumber: 'EMPTY', quantity: 0 }),
      batch({ id: 2, batchNumber: 'NEWER', importDate: '2026-07-02', quantity: 2 }),
      batch({ id: 1, batchNumber: 'OLDER', importDate: '2026-07-01', quantity: 2 }),
    ]

    expect(allocateStockFEFO(batches, 3)).toEqual([
      { batchId: 1, batchNumber: 'OLDER', quantityToDeduct: 2 },
      { batchId: 2, batchNumber: 'NEWER', quantityToDeduct: 1 },
    ])
  })

  it('rejects insufficient stock and treats non-positive requests as no-op', () => {
    expect(() => allocateStockFEFO([batch({ quantity: 1 })], 2)).toThrow('Không đủ số lượng')
    expect(allocateStockFEFO([batch({})], 0)).toEqual([])
    expect(allocateStockFEFO([batch({})], -1)).toEqual([])
  })
})

describe('FEFO presentation helpers', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-07-16T03:00:00+07:00'))
  })

  afterEach(() => vi.useRealTimers())

  it('formats VND and safe date fallbacks', () => {
    expect(formatVND(125_000)).toContain('125.000')
    expect(formatDate('2026-07-16')).toMatch(/16\/07\/2026/)
    expect(formatDate('not-a-date')).toBe('not-a-date')
    expect(formatDate('')).toBe('-')
  })

  it('calculates expiry using calendar-day boundaries', () => {
    expect(getDaysUntilExpiry('2026-07-18')).toBe(2)
    expect(getDaysUntilExpiry('2026-07-15')).toBe(-1)
  })
})
