import { type FormEvent, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { copilotApi } from './api'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'
import type { CopilotAnswer, Citation } from '@/shared/api/types'
import { formatDistanceToNow } from 'date-fns'
import { es, enUS } from 'date-fns/locale'

// =============================================================================
// CopilotPanel — AI-01..AI-15, FE-03.
// Sends question, displays grounded answer with citations or honest refusal.
// =============================================================================

function CitationChip({ cite }: { cite: Citation }) {
  const { i18n } = useTranslation()
  const locale = i18n.language.startsWith('es') ? es : enUS
  const when = formatDistanceToNow(new Date(cite.createdAt), { addSuffix: true, locale })
  return (
    <span
      title={`${cite.channelName} · ${cite.senderDisplayName} · ${when}`}
      className="inline-flex items-center gap-1 text-xs bg-blue-100 dark:bg-blue-900 text-blue-700 dark:text-blue-300 rounded-full px-2 py-0.5 cursor-default"
    >
      <svg className="w-3 h-3" fill="currentColor" viewBox="0 0 20 20">
        <path d="M2 5a2 2 0 012-2h12a2 2 0 012 2v10a2 2 0 01-2 2H4a2 2 0 01-2-2V5z" />
      </svg>
      #{cite.messageId}
    </span>
  )
}

function StatusMessage({
  status,
  answer,
}: {
  status: CopilotAnswer['status']
  answer: string | null
}) {
  const { t } = useTranslation()

  if (status === 'ANSWERED' && answer) {
    return <p className="text-sm text-gray-800 dark:text-gray-200 whitespace-pre-wrap leading-relaxed">{answer}</p>
  }

  const refusalKey: Record<string, string> = {
    INSUFFICIENT_CONTEXT: 'copilot.insufficientContext',
    NO_PERMISSION: 'copilot.noPermission',
    OUT_OF_SCOPE: 'copilot.outOfScope',
    PROVIDER_ERROR: 'copilot.providerUnavailable',
  }
  const msgKey = refusalKey[status] ?? 'copilot.noAnswer'

  return (
    <p className="text-sm text-gray-500 dark:text-gray-400 italic">{t(msgKey)}</p>
  )
}

export function CopilotPanel({ channelId }: { channelId?: string }) {
  const { t } = useTranslation()
  const [question, setQuestion] = useState('')
  const [loading, setLoading] = useState(false)
  const [answer, setAnswer] = useState<CopilotAnswer | null>(null)
  const [error, setError] = useState<string | null>(null)

  async function handleAsk(e: FormEvent) {
    e.preventDefault()
    const q = question.trim()
    if (!q || loading) return
    setLoading(true)
    setError(null)
    setAnswer(null)
    try {
      const res = await copilotApi.ask({ question: q, channelId })
      setAnswer(res)
    } catch {
      setError(t('common.error'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="flex flex-col h-full bg-white dark:bg-gray-900 border-l border-gray-200 dark:border-gray-700">
      {/* Header */}
      <div className="px-4 py-4 border-b border-gray-200 dark:border-gray-700">
        <h2 className="font-semibold text-gray-900 dark:text-gray-100 flex items-center gap-2">
          <svg className="w-5 h-5 text-blue-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
          </svg>
          {t('copilot.title')}
        </h2>
      </div>

      {/* Answer area */}
      <div className="flex-1 overflow-y-auto p-4 space-y-4">
        {!answer && !loading && !error && (
          <p className="text-sm text-gray-400 text-center pt-8">{t('copilot.noAnswer')}</p>
        )}
        {loading && (
          <div className="flex items-center gap-2 text-sm text-gray-500 dark:text-gray-400">
            <LoadingSpinner size="sm" />
            {t('copilot.thinking')}
          </div>
        )}
        {error && <p className="text-sm text-red-600 dark:text-red-400">{error}</p>}
        {answer && (
          <div className="space-y-3">
            <StatusMessage status={answer.status} answer={answer.answer} />
            {answer.citations.length > 0 && (
              <div>
                <p className="text-xs font-medium text-gray-500 dark:text-gray-400 mb-1.5">
                  {t('copilot.citations')}
                </p>
                <div className="flex flex-wrap gap-1.5">
                  {answer.citations.map((c) => (
                    <CitationChip key={c.messageId} cite={c} />
                  ))}
                </div>
              </div>
            )}
            <p className="text-xs text-gray-400">
              {answer.usage.totalTokens} {t('copilot.tokens')}
            </p>
          </div>
        )}
      </div>

      {/* Ask form */}
      <form onSubmit={handleAsk} className="border-t border-gray-200 dark:border-gray-700 p-4 space-y-2">
        <textarea
          value={question}
          onChange={(e) => setQuestion(e.target.value)}
          placeholder={t('copilot.askPlaceholder')}
          rows={3}
          disabled={loading}
          className="w-full resize-none text-sm rounded-xl border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
        />
        <button
          type="submit"
          disabled={!question.trim() || loading}
          className="w-full bg-blue-600 text-white text-sm font-medium rounded-xl py-2 hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        >
          {loading ? t('copilot.thinking') : t('copilot.ask')}
        </button>
      </form>
    </div>
  )
}
