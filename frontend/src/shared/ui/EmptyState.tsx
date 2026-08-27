import { type ReactNode } from 'react'
import { cn } from './cn'

// Accepts either `message` (short) or `title`+`description` (full) pattern
interface EmptyStateProps {
  message?: string
  title?: string
  description?: string
  icon?: ReactNode
  action?: ReactNode
  className?: string
}

export function EmptyState({ message, title, description, icon, action, className }: EmptyStateProps) {
  const heading = message ?? title ?? ''
  return (
    <div className={cn('flex flex-col items-center justify-center p-8 text-center', className)}>
      {icon && <div className="text-gray-400 mb-4">{icon}</div>}
      <p className="text-sm text-gray-500 dark:text-gray-400">{heading}</p>
      {description && <p className="mt-1 text-xs text-gray-400">{description}</p>}
      {action && <div className="mt-4">{action}</div>}
    </div>
  )
}
