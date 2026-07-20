import { describe, expect, it } from 'vitest'
import { rolesForRoute } from './routeManifest'

describe('route manifest', () => {
  it('keeps billing owner-only', () => {
    expect(rolesForRoute('/billing')).toEqual(['STORE_OWNER'])
  })

  it('allows operational pages for staff while protecting management pages', () => {
    expect(rolesForRoute('/transactions')).toContain('STAFF')
    expect(rolesForRoute('/settings/staff')).not.toContain('STAFF')
  })

  it('fails closed for unknown and guest routes', () => {
    expect(rolesForRoute('/missing')).toEqual([])
    expect(rolesForRoute('/login')).toEqual([])
  })
})
