import type { InventoryBatch } from '../types'

/**
 * Thuật toán xuất kho FEFO (First Expired First Out)
 * Sắp xếp các lô hàng theo ngày hết hạn sớm nhất để xuất trước.
 * Trả về danh sách lô hàng cần trừ và lượng trừ tương ứng.
 * Ném lỗi nếu tổng số lượng tồn kho không đủ để xuất (tránh tồn âm).
 */
export interface FEFOAllocation {
  batchId: number
  batchNumber: string
  quantityToDeduct: number
}

export function allocateStockFEFO(
  batches: InventoryBatch[],
  quantityToExport: number
): FEFOAllocation[] {
  if (quantityToExport <= 0) {
    return []
  }

  // Lọc lấy các lô có số lượng > 0
  const activeBatches = batches.filter((b) => b.quantity > 0)

  // Sắp xếp các lô hàng theo thứ tự ngày hết hạn tăng dần (gần hết hạn nhất lên đầu)
  // Nếu cùng ngày hết hạn, lô nào nhập trước (importDate) hoặc ID nhỏ hơn sẽ xuất trước.
  const sortedBatches = [...activeBatches].sort((a, b) => {
    const dateA = new Date(a.expiredDate).getTime()
    const dateB = new Date(b.expiredDate).getTime()
    if (dateA !== dateB) {
      return dateA - dateB
    }
    return new Date(a.importDate).getTime() - new Date(b.importDate).getTime()
  })

  // Tính tổng tồn kho khả dụng
  const totalAvailable = sortedBatches.reduce((sum, b) => sum + b.quantity, 0)
  if (totalAvailable < quantityToExport) {
    throw new Error(
      `Không đủ số lượng trong kho. Tổng tồn hiện tại là ${totalAvailable}, lượng yêu cầu xuất là ${quantityToExport}.`
    )
  }

  const allocations: FEFOAllocation[] = []
  let remainingNeed = quantityToExport

  for (const batch of sortedBatches) {
    if (remainingNeed <= 0) break

    const takeAmount = Math.min(batch.quantity, remainingNeed)
    allocations.push({
      batchId: batch.id,
      batchNumber: batch.batchNumber,
      quantityToDeduct: takeAmount,
    })

    remainingNeed -= takeAmount
  }

  return allocations
}

/**
 * Định dạng tiền tệ VND
 */
export function formatVND(value: number): string {
  return new Intl.NumberFormat('vi-VN', {
    style: 'currency',
    currency: 'VND',
  }).format(value)
}

/**
 * Định dạng ngày dd/MM/yyyy
 */
export function formatDate(dateString: string): string {
  if (!dateString) return '-'
  try {
    const date = new Date(dateString)
    if (isNaN(date.getTime())) return dateString
    return new Intl.DateTimeFormat('vi-VN', {
      day: '2-digit',
      month: '2-digit',
      year: 'numeric',
    }).format(date)
  } catch {
    return dateString
  }
}

/**
 * Tính số ngày còn lại đến khi hết hạn
 */
export function getDaysUntilExpiry(expiredDateString: string): number {
  const expiredDate = new Date(expiredDateString)
  const today = new Date()
  
  // Reset giờ để chỉ so sánh ngày
  expiredDate.setHours(0, 0, 0, 0)
  today.setHours(0, 0, 0, 0)
  
  const diffTime = expiredDate.getTime() - today.getTime()
  return Math.ceil(diffTime / (1000 * 60 * 60 * 24))
}
