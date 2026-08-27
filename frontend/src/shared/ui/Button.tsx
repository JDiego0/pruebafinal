import { ButtonHTMLAttributes, forwardRef } from 'react'
import { cn } from './cn'
import { LoadingSpinner } from './LoadingSpinner'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: 'primary' | 'secondary' | 'danger' | 'ghost'
  size?: 'sm' | 'md' | 'lg'
  isLoading?: boolean
  loading?: boolean
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(
  ({ className, variant = 'primary', size = 'md', isLoading, loading, disabled, children, ...props }, ref) => {
    const busy = isLoading || loading
    const variants = {
      primary: 'bg-brand-blue text-white hover:bg-blue-600 border border-transparent',
      secondary: 'bg-white text-gray-700 hover:bg-gray-50 border border-gray-300',
      danger: 'bg-red-600 text-white hover:bg-red-700 border border-transparent',
      ghost: 'bg-transparent text-gray-700 hover:bg-gray-100 border border-transparent',
    }

    const sizes = {
      sm: 'px-3 py-1.5 text-sm',
      md: 'px-4 py-2 text-sm',
      lg: 'px-6 py-3 text-base',
    }

    return (
      <button
        ref={ref}
        disabled={busy || disabled}
        className={cn(
          'inline-flex items-center justify-center font-medium rounded-xl focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 transition-colors disabled:opacity-50 disabled:cursor-not-allowed',
          variants[variant],
          sizes[size],
          className
        )}
        {...props}
      >
        {busy && <LoadingSpinner size="sm" className="mr-2 text-current" />}
        {children}
      </button>
    )
  }
)
Button.displayName = 'Button'
