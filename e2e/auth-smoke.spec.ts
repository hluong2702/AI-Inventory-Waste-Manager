import { expect, test } from '@playwright/test'

test('login page exposes the required credentials and registration path', async ({ page }) => {
  await page.goto('/login')

  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible()
  await expect(page.getByText('Email', { exact: true })).toBeVisible()
  await expect(page.getByText('Mật khẩu', { exact: true })).toBeVisible()
  await expect(page.getByRole('link', { name: 'Đăng ký store mới' })).toHaveAttribute('href', '/register')
})
