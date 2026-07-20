import AxeBuilder from '@axe-core/playwright'
import { expect, test } from '@playwright/test'

for (const path of ['/login', '/register']) {
  test(`${path} has no automatically detectable WCAG A/AA violations`, async ({ page }) => {
    await page.goto(path)
    await expect(page.locator('main')).toBeVisible()

    const results = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
      .analyze()

    expect(results.violations, results.violations.map((item) => (
      `${item.id}: ${item.help} (${item.nodes.length} nodes)`
    )).join('\n')).toEqual([])
  })
}
