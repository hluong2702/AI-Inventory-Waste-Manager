import type { ReactNode } from 'react'

interface DoubleBezelCardProps {
  children: ReactNode
  className?: string
  outerClassName?: string
  title?: string
  subtitle?: string
  action?: ReactNode
}

/**
 * Thẻ giao diện Double-Bezel cao cấp (Doppelrand)
 * Thiết kế bo viền lồng nhau tạo độ nổi vật lý sang trọng
 */
export default function DoubleBezelCard({
  children,
  className = '',
  outerClassName = '',
  title,
  subtitle,
  action,
}: DoubleBezelCardProps) {
  return (
    <div className={`bg-ink/5 p-1.5 rounded-[1.75rem] border border-ink/5 shadow-sm ${outerClassName}`}>
      <div className={`bg-white rounded-[calc(1.75rem-0.375rem)] p-6 shadow-[inset_0_1px_1px_rgba(255,255,255,0.5)] ${className}`}>
        {(title || subtitle || action) && (
          <div className="flex items-center justify-between mb-5 pb-3 border-b border-ink/10">
            <div>
              {title && <h3 className="text-base font-bold text-ink">{title}</h3>}
              {subtitle && <p className="text-xs text-ink/60 mt-0.5">{subtitle}</p>}
            </div>
            {action && <div className="flex items-center">{action}</div>}
          </div>
        )}
        {children}
      </div>
    </div>
  )
}
