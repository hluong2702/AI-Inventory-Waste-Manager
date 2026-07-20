import { afterEach, describe, expect, it } from 'vitest'
import { clearAccessToken, getAccessToken, setAccessToken } from './tokenStore'

describe('tokenStore', () => {
  afterEach(clearAccessToken)

  it('keeps the access token in memory and clears it explicitly', () => {
    expect(getAccessToken()).toBeNull()

    setAccessToken('access-token')
    expect(getAccessToken()).toBe('access-token')

    clearAccessToken()
    expect(getAccessToken()).toBeNull()
  })
})
