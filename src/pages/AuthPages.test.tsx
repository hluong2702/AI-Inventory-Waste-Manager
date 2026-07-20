import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import LoginPage from './LoginPage'
import RegisterPage from './RegisterPage'

const login = vi.fn()
const registerAccount = vi.fn()

vi.mock('../context/AuthContext', () => ({
  useAuth: () => ({ login, register: registerAccount }),
}))

describe('public authentication pages', () => {
  beforeEach(() => {
    login.mockReset()
    registerAccount.mockReset()
  })

  it('validates login input and submits normalized form values', async () => {
    render(
      <MemoryRouter initialEntries={['/login']}>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/" element={<div>Dashboard</div>} />
        </Routes>
      </MemoryRouter>,
    )

    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect(await screen.findByText('Email không hợp lệ')).toBeVisible()

    fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: 'owner@example.com' } })
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))

    await waitFor(() => expect(login).toHaveBeenCalledWith('owner@example.com', 'secret123'))
    expect(await screen.findByText('Dashboard')).toBeVisible()
  })

  it('renders a safe login failure without losing form semantics', async () => {
    login.mockRejectedValueOnce(new Error('Tài khoản đã bị khóa'))
    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText(/^Email/), { target: { value: 'staff@example.com' } })
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Đăng nhập' }))
    expect(await screen.findByText('Tài khoản đã bị khóa')).toBeVisible()
  })

  it('rejects mismatched registration passwords and confirms email activation', async () => {
    registerAccount.mockResolvedValueOnce({
      verificationRequired: true,
      email: 'owner@example.com',
      expiresInHours: 48,
    })
    render(
      <MemoryRouter>
        <RegisterPage />
      </MemoryRouter>,
    )

    fireEvent.change(screen.getByLabelText(/^Tên quán/), { target: { value: 'Bếp Xanh' } })
    fireEvent.change(screen.getByLabelText(/^Email Owner/), { target: { value: 'owner@example.com' } })
    fireEvent.change(screen.getByLabelText(/^Mật khẩu/), { target: { value: 'secret123' } })
    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu/), { target: { value: 'different' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản Owner' }))
    expect(await screen.findByText('Mật khẩu xác nhận không khớp')).toBeVisible()
    expect(registerAccount).not.toHaveBeenCalled()

    fireEvent.change(screen.getByLabelText(/^Xác nhận mật khẩu/), { target: { value: 'secret123' } })
    fireEvent.click(screen.getByRole('button', { name: 'Tạo tài khoản Owner' }))
    expect(await screen.findByText('Kiểm tra email để kích hoạt')).toBeVisible()
    expect(registerAccount).toHaveBeenCalledWith({
      storeName: 'Bếp Xanh',
      email: 'owner@example.com',
      password: 'secret123',
      confirmPassword: 'secret123',
    })
  })
})
