// ItemsPage đã được thay thế hoàn toàn bởi IngredientsPage
// Redirect để không phá vỡ bất kỳ link cũ nào
import { Navigate } from 'react-router-dom'

export default function ItemsPage() {
  return <Navigate to="/ingredients" replace />
}
