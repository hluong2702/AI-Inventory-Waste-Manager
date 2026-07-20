import { beforeEach, describe, expect, it } from 'vitest'
import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { RoleGuard } from './RouteGuards'
import { rolesForRoute } from '../services/routeManifest'
import { useAuthStore } from '../stores/authStore'

describe('RoleGuard', () => {
  beforeEach(() => {
    useAuthStore.setState({ role: null })
  })

  it('renders an owner-only route for an owner', () => {
    useAuthStore.setState({ role: 'STORE_OWNER' })

    renderBillingRoute()

    expect(screen.getByText('Billing')).toBeInTheDocument()
  })

  it('redirects a manager away from an owner-only route', () => {
    useAuthStore.setState({ role: 'MANAGER' })

    renderBillingRoute()

    expect(screen.getByText('Dashboard')).toBeInTheDocument()
    expect(screen.queryByText('Billing')).not.toBeInTheDocument()
  })
})

function renderBillingRoute() {
  return render(
    <MemoryRouter initialEntries={['/billing']}>
      <Routes>
        <Route element={<RoleGuard allowedRoles={rolesForRoute('/billing')} />}>
          <Route path="/billing" element={<div>Billing</div>} />
        </Route>
        <Route path="/" element={<div>Dashboard</div>} />
      </Routes>
    </MemoryRouter>,
  )
}
