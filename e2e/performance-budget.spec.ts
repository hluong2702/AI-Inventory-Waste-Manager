import { expect, test } from '@playwright/test'

test('login route stays inside the production performance budget', async ({ page }) => {
  await page.goto('/login')
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible()

  const metrics = await page.evaluate(() => {
    const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming
    const resources = performance.getEntriesByType('resource') as PerformanceResourceTiming[]
    const transferredBytes = resources.reduce(
      (total, resource) => total + (resource.transferSize || resource.decodedBodySize),
      0,
    )
    const largestJavaScriptBytes = resources
      .filter((resource) => resource.name.includes('.js'))
      .reduce((largest, resource) => Math.max(largest, resource.transferSize || resource.decodedBodySize), 0)
    return {
      loadDurationMs: navigation.duration,
      transferredBytes,
      largestJavaScriptBytes,
    }
  })

  expect(metrics.loadDurationMs).toBeLessThan(3_000)
  expect(metrics.transferredBytes).toBeLessThan(1_500_000)
  expect(metrics.largestJavaScriptBytes).toBeLessThan(500_000)
})
