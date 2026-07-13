// WasteLogsPage đã được gộp vào ReportsPage
// File này redirect về /reports để không phá vỡ bất kỳ link cũ nào
import { Navigate } from 'react-router-dom'

export default function WasteLogsPage() {
  return <Navigate to="/reports" replace />
}
