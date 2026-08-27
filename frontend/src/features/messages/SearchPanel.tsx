import { useState, type FormEvent } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { searchApi } from './searchApi'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'
import { EmptyState } from '@/shared/ui/EmptyState'
import type { SearchHitResult } from '@/shared/api/types'
import { formatDistanceToNow } from 'date-fns'
import { es, enUS } from 'date-fns/locale'

// =============================================================================
// SearchPanel — SRCH-01..SRCH-04. The <mark> highlight is rendered via
// dangerouslySetInnerHTML since it is already sanitized server-side by
// ts_headline (only <mark> tags are produced). No user content reaches this
// path without going through the RLS-enforced search function first.
// =============================================================================

function SearchHitItem({ hit }: { hit: SearchHitResult }) {
  const { i18n } = useTranslation()
  const locale = i18n.language.startsWith('es') ? es : enUS
  const when = formatDistanceToNow(new Date(hit.createdAt), { addSuffix: true, locale })

  return (
    <div className="border border-gray-200 dark:border-gray-700 rounded-lg p-3 space-y-1">
      <div className="flex justify-between items-baseline text-xs text-gray-400">
        <span>#{hit.messageId}</span>
        <span>{when}</span>
      </div>
      {/* SRCH-03: ts_headline produces <mark> only — safe to render */}
      <p
        className="text-sm text-gray-900 dark:text-gray-100 [&_mark]:bg-yellow-200 dark:[&_mark]:bg-yellow-700 [&_mark]:rounded-sm [&_mark]:px-0.5"
        dangerouslySetInnerHTML={{ __html: hit.highlight }}
      />
    </div>
  )
}

export function SearchPanel({ channelId }: { channelId?: string }) {
  const { t } = useTranslation()
  const [query, setQuery] = useState('')
  const [submittedQuery, setSubmittedQuery] = useState('')

  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError } =
    useInfiniteQuery({
      queryKey: ['search', submittedQuery, channelId],
      queryFn: ({ pageParam }) =>
        searchApi.messages(submittedQuery, channelId, pageParam as string | undefined),
      initialPageParam: undefined as string | undefined,
      getNextPageParam: (last) => (last.pageInfo.hasMore ? last.pageInfo.nextCursor : undefined),
      enabled: !!submittedQuery,
    })

  function handleSubmit(e: FormEvent) {
    e.preventDefault()
    const q = query.trim()
    if (q) setSubmittedQuery(q)
  }

  const hits = data?.pages.flatMap((p) => p.items) ?? []

  return (
    <div className="flex flex-col h-full bg-white dark:bg-gray-900 border-l border-gray-200 dark:border-gray-700">
      <div className="px-4 py-4 border-b border-gray-200 dark:border-gray-700">
        <h2 className="font-semibold text-gray-900 dark:text-gray-100 mb-3">{t('search.title')}</h2>
        <form onSubmit={handleSubmit} className="flex gap-2">
          <input
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={t('search.placeholder')}
            className="flex-1 text-sm rounded-lg border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 placeholder-gray-400 px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
          />
          <button
            type="submit"
            disabled={!query.trim()}
            className="bg-blue-600 text-white rounded-lg px-3 py-2 text-sm font-medium hover:bg-blue-700 disabled:opacity-40"
          >
            {t('common.search')}
          </button>
        </form>
      </div>

      <div className="flex-1 overflow-y-auto p-4 space-y-2">
        {!submittedQuery && (
          <p className="text-sm text-gray-400 text-center pt-8">{t('search.prompt')}</p>
        )}
        {isLoading && <LoadingSpinner className="py-8" />}
        {isError && <p className="text-sm text-red-500 text-center py-4">{t('common.error')}</p>}
        {submittedQuery && !isLoading && hits.length === 0 && (
          <EmptyState message={t('search.noResults')} className="pt-8" />
        )}
        {hits.map((h) => (
          <SearchHitItem key={h.messageId} hit={h} />
        ))}
        {hasNextPage && (
          <button
            onClick={() => fetchNextPage()}
            disabled={isFetchingNextPage}
            className="w-full text-sm text-gray-500 hover:text-gray-700 dark:hover:text-gray-300 py-2 text-center"
          >
            {isFetchingNextPage ? <LoadingSpinner size="sm" /> : t('messages.loadMore')}
          </button>
        )}
      </div>
    </div>
  )
}
