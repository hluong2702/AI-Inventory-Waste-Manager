import { fireEvent, render, screen } from '@testing-library/react'
import { describe, expect, it, vi } from 'vitest'
import Pagination from './Pagination'

describe('Pagination', () => {
  it('does not render for a single page', () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} totalElements={4} onPageChange={vi.fn()} />,
    )
    expect(container).toBeEmptyDOMElement()
  })

  it('announces position and enforces page boundaries', () => {
    const onPageChange = vi.fn()
    render(<Pagination page={1} totalPages={3} totalElements={27} onPageChange={onPageChange} />)

    expect(screen.getByText('27 bản ghi · Trang 2/3')).toBeVisible()
    fireEvent.click(screen.getByRole('button', { name: 'Trang trước' }))
    fireEvent.click(screen.getByRole('button', { name: 'Trang sau' }))
    expect(onPageChange).toHaveBeenNthCalledWith(1, 0)
    expect(onPageChange).toHaveBeenNthCalledWith(2, 2)
  })

  it('disables navigation at the first and last pages', () => {
    const { rerender } = render(
      <Pagination page={0} totalPages={2} totalElements={12} onPageChange={vi.fn()} />,
    )
    expect(screen.getByRole('button', { name: 'Trang trước' })).toBeDisabled()

    rerender(<Pagination page={1} totalPages={2} totalElements={12} onPageChange={vi.fn()} />)
    expect(screen.getByRole('button', { name: 'Trang sau' })).toBeDisabled()
  })
})
