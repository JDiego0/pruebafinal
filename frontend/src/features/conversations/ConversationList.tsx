import { useInfiniteQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router-dom'
import { conversationsApi } from './api'
import { Avatar } from '@/shared/ui/Avatar'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import type { ConversationSummary } from '@/shared/api/types'
import { formatRelative } from 'date-fns'
import { es, enUS } from 'date-fns/locale'
import { useTranslation as useT } from 'react-i18next'

function useLocale() {
  const { i18n } = useT()
  return i18n.language.startsWith('es') ? es : enUS
}

function ConversationItem({
  conv,
  active,
}: {
  conv: ConversationSummary
  active: boolean
}) {
  const locale = useLocale()
  const name =
    conv.type === 'DIRECT'
      ? (conv.counterpart?.displayName ?? '?')
      : (conv.name ?? conv.slug ?? '—')
  const preview = conv.lastMessage?.isDeleted
    ? '—'
    : (conv.lastMessage?.preview ?? '')
  const when = conv.lastMessage
    ? formatRelative(new Date(conv.lastMessage.createdAt), new Date(), { locale })
    : ''

  return (
    <Link
      to={`/channels/${conv.channelId}`}
      className={[
        'flex items-start gap-3 px-3 py-3 rounded-lg cursor-pointer transition-colors',
        active
          ? 'bg-blue-600 text-white'
          : 'text-gray-200 hover:bg-gray-700',
      ].join(' ')}
    >
      <Avatar name={name} size="md" />
      <div className="flex-1 min-w-0">
        <div className="flex justify-between items-baseline">
          <span className="font-medium text-sm truncate">{name}</span>
          <span className={`text-xs shrink-0 ml-1 ${active ? 'text-blue-200' : 'text-gray-400'}`}>
            {when}
          </span>
        </div>
        <p className={`text-xs truncate mt-0.5 ${active ? 'text-blue-100' : 'text-gray-400'}`}>
          {preview}
        </p>
      </div>
      {conv.unreadCount > 0 && !active && (
        <span className="self-center bg-blue-500 text-white text-xs font-bold rounded-full px-1.5 py-0.5 min-w-[1.25rem] text-center">
          {conv.unreadCount > 99 ? '99+' : conv.unreadCount}
        </span>
      )}
    </Link>
  )
}

export function ConversationList({ activeChannelId }: { activeChannelId?: string }) {
  const { t } = useTranslation()
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError, refetch } =
    useInfiniteQuery({
      queryKey: ['conversations'],
      queryFn: ({ pageParam }) => conversationsApi.list(pageParam as string | undefined),
      initialPageParam: undefined as string | undefined,
      getNextPageParam: (last) => (last.pageInfo.hasMore ? last.pageInfo.nextCursor : undefined),
    })

  if (isLoading) return <LoadingSpinner className="py-8" />
  if (isError) return <ErrorState message={t('common.error')} onRetry={refetch} />

  const all = data?.pages.flatMap((p) => p.items) ?? []

  return (
    <nav className="flex flex-col gap-1">
      {all.length === 0 ? (
        <EmptyState message={t('conversations.noConversations')} />
      ) : (
        all.map((c) => (
          <ConversationItem key={c.channelId} conv={c} active={c.channelId === activeChannelId} />
        ))
      )}
      {hasNextPage && (
        <button
          onClick={() => fetchNextPage()}
          disabled={isFetchingNextPage}
          className="text-xs text-gray-400 hover:text-gray-200 text-center py-2"
        >
          {isFetchingNextPage ? <LoadingSpinner size="sm" /> : t('messages.loadMore')}
        </button>
      )}
    </nav>
  )
}
