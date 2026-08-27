import {
  useCallback,
  useLayoutEffect,
  useRef,
  useState,
} from 'react'
import { useInfiniteQuery, useQueryClient } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { formatDistanceToNow } from 'date-fns'
import { es, enUS } from 'date-fns/locale'
import { messagesApi } from './api'
import { useSse } from '@/shared/hooks/useSse'
import { useScrollAnchor } from '@/shared/hooks/useScrollAnchor'
import { useAuthStore } from '@/features/auth/store'
import { Avatar } from '@/shared/ui/Avatar'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'
import { EmptyState } from '@/shared/ui/EmptyState'
import { ErrorState } from '@/shared/ui/ErrorState'
import { MessageComposer } from './MessageComposer'
import type { MessageRecord, OptimisticMessage } from '@/shared/api/types'

// =============================================================================
// MessageList — FE-07, FE-08, RT-01..RT-06, MSG-01..MSG-04
// Upward infinite scroll + SSE real-time + optimistic send + keyset pagination.
// =============================================================================

function useLocale() {
  const { i18n } = useTranslation()
  return i18n.language.startsWith('es') ? es : enUS
}

function MessageBubble({
  msg,
  isMine,
}: {
  msg: OptimisticMessage
  isMine: boolean
}) {
  const { t } = useTranslation()
  const locale = useLocale()
  const time = formatDistanceToNow(new Date(msg.createdAt), {
    addSuffix: true,
    locale,
  })

  const statusColor =
    msg.status === 'pending'
      ? 'opacity-60'
      : msg.status === 'failed'
        ? 'opacity-80 border border-red-400'
        : ''

  return (
    <div
      className={`flex gap-3 ${isMine ? 'flex-row-reverse' : 'flex-row'} group`}
    >
      <Avatar name={msg.senderDisplayName} size="sm" className="shrink-0 mt-1" />
      <div className={`max-w-[75%] ${isMine ? 'items-end' : 'items-start'} flex flex-col`}>
        <div className="flex items-baseline gap-2 mb-1">
          {!isMine && (
            <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">
              {msg.senderDisplayName}
            </span>
          )}
          <span className="text-xs text-gray-400">{time}</span>
          {msg.editedAt && (
            <span className="text-xs text-gray-400 italic">({t('messages.edited')})</span>
          )}
        </div>

        {msg.isDeleted ? (
          <span className="italic text-sm text-gray-400 px-3 py-2 rounded-xl bg-gray-100 dark:bg-gray-800">
            {t('messages.deleted')}
          </span>
        ) : (
          <div
            className={[
              'px-3 py-2 rounded-xl text-sm leading-relaxed whitespace-pre-wrap break-words',
              isMine
                ? 'bg-blue-600 text-white rounded-tr-none'
                : 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-gray-100 rounded-tl-none',
              statusColor,
            ].join(' ')}
          >
            {msg.body}
          </div>
        )}

        {msg.status === 'failed' && (
          <span className="text-xs text-red-500 mt-0.5">{t('messages.failed')}</span>
        )}
      </div>
    </div>
  )
}

export function MessageList({ channelId }: { channelId: string }) {
  const { t } = useTranslation()
  const { user } = useAuthStore()
  const qc = useQueryClient()
  const containerRef = useRef<HTMLDivElement>(null)
  const bottomRef = useRef<HTMLDivElement>(null)
  const { saveAnchor, restoreAnchor } = useScrollAnchor(containerRef)
  const [optimistic, setOptimistic] = useState<OptimisticMessage[]>([])
  const atBottomRef = useRef(true)

  // ── Infinite keyset query (newest first from API, reversed for display) ──────
  const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading, isError, refetch } =
    useInfiniteQuery({
      queryKey: ['messages', channelId],
      queryFn: ({ pageParam }) =>
        messagesApi.history(channelId, pageParam as string | undefined),
      initialPageParam: undefined as string | undefined,
      getNextPageParam: (last) => (last.pageInfo.hasMore ? last.pageInfo.nextCursor : undefined),
    })

  // Flatten pages into chronological order (API returns newest-first per page)
  const pages = data?.pages ?? []
  const serverMessages: MessageRecord[] = [...pages.flatMap((p) => p.items)].reverse()

  // ── Scroll to bottom on initial load ──────────────────────────────────────
  const didInitialScroll = useRef(false)
  useLayoutEffect(() => {
    if (!didInitialScroll.current && serverMessages.length > 0) {
      didInitialScroll.current = true
      bottomRef.current?.scrollIntoView({ behavior: 'instant' })
    }
  }, [serverMessages.length])

  // ── Scroll anchor: preserve position when prepending (FE-08) ─────────────
  useLayoutEffect(() => {
    restoreAnchor()
  })

  const onScrollNearTop = useCallback(() => {
    const el = containerRef.current
    if (!el) return
    atBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < 100
    if (el.scrollTop < 100 && hasNextPage && !isFetchingNextPage) {
      saveAnchor()
      fetchNextPage()
    }
  }, [hasNextPage, isFetchingNextPage, fetchNextPage, saveAnchor])

  // ── SSE: real-time events ─────────────────────────────────────────────────
  useSse({
    onMessageCreated: async (ev) => {
      if (ev.channelId !== channelId) return
      // Reconcile optimistic by clientMessageId (RT-03)
      setOptimistic((prev) =>
        prev.map((m) =>
          m.clientMessageId === ev.clientMessageId ? { ...m, status: 'sent' } : m,
        ),
      )
      // Refetch top of history to get the authoritative record
      await qc.invalidateQueries({ queryKey: ['messages', channelId] })
      // Scroll to bottom if we were already at the bottom
      if (atBottomRef.current) {
        setTimeout(() => bottomRef.current?.scrollIntoView({ behavior: 'smooth' }), 100)
      }
    },
    onMessageUpdated: (ev) => {
      if (ev.channelId !== channelId) return
      qc.invalidateQueries({ queryKey: ['messages', channelId] })
    },
    onMessageDeleted: (ev) => {
      if (ev.channelId !== channelId) return
      qc.invalidateQueries({ queryKey: ['messages', channelId] })
    },
  })

  // ── Optimistic send ──────────────────────────────────────────────────────
  const handleSend = useCallback(
    async (body: string, clientMessageId: string) => {
      const optimisticMsg: OptimisticMessage = {
        messageId: -Date.now(), // temporary negative id
        channelId,
        senderId: user!.userId,
        senderDisplayName: user!.displayName,
        senderAvatarUrl: user!.avatarUrl,
        parentMessageId: null,
        body,
        clientMessageId,
        isDeleted: false,
        revision: 1,
        createdAt: new Date().toISOString(),
        editedAt: null,
        status: 'pending',
      }
      setOptimistic((prev) => [...prev, optimisticMsg])
      atBottomRef.current = true
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })

      try {
        await messagesApi.send(channelId, body, clientMessageId)
        // SSE will promote the status to 'sent'
      } catch {
        setOptimistic((prev) =>
          prev.map((m) =>
            m.clientMessageId === clientMessageId ? { ...m, status: 'failed' } : m,
          ),
        )
      }
    },
    [channelId, user],
  )

  // ── Merge server + optimistic ────────────────────────────────────────────
  const allMessages: OptimisticMessage[] = [
    ...serverMessages
      .filter((sm) => !optimistic.some((o) => o.clientMessageId === sm.clientMessageId && o.status === 'sent'))
      .map((m) => ({
        ...m,
        senderDisplayName:
          m.senderDisplayName ??
          (m.senderId === user?.userId ? (user?.displayName ?? '') : m.senderId),
        senderAvatarUrl: null,
        status: 'sent' as const,
      })),
    ...optimistic.filter((o) => o.status !== 'sent'),
  ]

  if (isLoading) return <LoadingSpinner className="flex-1" />
  if (isError) return <ErrorState message={t('common.error')} onRetry={refetch} className="flex-1" />

  return (
    <div className="flex flex-col flex-1 min-h-0">
      {/* Scrollable message area */}
      <div
        ref={containerRef}
        className="flex-1 overflow-y-auto px-4 py-4 space-y-4"
        onScroll={onScrollNearTop}
      >
        {isFetchingNextPage && <LoadingSpinner size="sm" className="py-2" />}
        {!hasNextPage && !isFetchingNextPage && serverMessages.length > 0 && (
          <p className="text-xs text-center text-gray-400 py-2">{t('messages.beginningOfHistory')}</p>
        )}
        {allMessages.length === 0 && !isFetchingNextPage && (
          <EmptyState message={t('messages.noMessages')} className="pt-20" />
        )}
        {allMessages.map((msg) => (
          <MessageBubble
            key={msg.clientMessageId || msg.messageId}
            msg={msg}
            isMine={msg.senderId === user?.userId}
          />
        ))}
        <div ref={bottomRef} />
      </div>

      {/* Composer */}
      <MessageComposer channelId={channelId} onSend={handleSend} />
    </div>
  )
}
