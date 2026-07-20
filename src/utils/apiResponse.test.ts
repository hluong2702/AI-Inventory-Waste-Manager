import { describe, expect, it } from 'vitest'
import { apiErrorMessage, unwrapApiData } from './apiResponse'

describe('apiResponse utilities', () => {
  it('unwraps the standard API envelope and preserves plain payloads', () => {
    expect(unwrapApiData({ success: true, data: { id: 7 } })).toEqual({ id: 7 })
    expect(unwrapApiData({ id: 8 })).toEqual({ id: 8 })
  })

  it('prefers a backend message, then Error message, then the fallback', () => {
    expect(apiErrorMessage({ response: { data: { message: 'Email đã tồn tại' } } }, 'fallback'))
      .toBe('Email đã tồn tại')
    expect(apiErrorMessage(new Error('Mất kết nối'), 'fallback')).toBe('Mất kết nối')
    expect(apiErrorMessage(null, 'fallback')).toBe('fallback')
  })
})
