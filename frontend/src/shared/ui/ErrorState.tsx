import { useTranslation } from 'react-i18next'
import { Button } from './Button'
import { cn } from './cn'

interface ErrorStateProps {
  message?: string
  onRetry?: () => void
  className?: string
}

export function ErrorState({ message, onRetry, className }: ErrorStateProps) {
  const { t } = useTranslation()

  return (
    <div className={cn('flex flex-col items-center justify-center p-8 text-center', className)}>
      <div className="text-red-500 mb-4">
        <svg className="w-10 h-10" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
        </svg>
      </div>
      <p className="text-sm text-gray-600 dark:text-gray-300">{message ?? t('common.error')}</p>
      {onRetry && (
        <Button onClick={onRetry} variant="secondary" size="sm" className="mt-4">
          {t('common.retry')}
        </Button>
      )}
    </div>
  )
}
