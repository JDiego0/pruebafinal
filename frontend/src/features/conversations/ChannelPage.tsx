import { useParams } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useState } from 'react'
import { conversationsApi } from '@/features/conversations/api'
import { MessageList } from '@/features/messages/MessageList'
import { CopilotPanel } from '@/features/copilot/CopilotPanel'
import { SearchPanel } from '@/features/messages/SearchPanel'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'
import { ErrorState } from '@/shared/ui/ErrorState'
import { AppShell } from '@/app/AppShell'

// =============================================================================
// ChannelPage — combines channel header, message list, copilot / search panels.
// =============================================================================

type RightPanelMode = 'copilot' | 'search' | null

export function ChannelPage() {
  const { channelId } = useParams<{ channelId: string }>()
  const { t } = useTranslation()
  const [rightPanel, setRightPanel] = useState<RightPanelMode>('copilot')

  const { data: channel, isLoading, isError, refetch } = useQuery({
    queryKey: ['channel', channelId],
    queryFn: () => conversationsApi.getChannel(channelId!),
    enabled: !!channelId,
  })

  if (!channelId) return null

  const channelName = channel?.name ?? channel?.slug ?? channelId

  const panelNode =
    rightPanel === 'copilot' ? (
      <CopilotPanel channelId={channelId} />
    ) : rightPanel === 'search' ? (
      <SearchPanel channelId={channelId} />
    ) : null

  return (
    <AppShell rightPanel={panelNode}>
      {/* Channel header */}
      <div className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 shrink-0">
        <div className="flex-1 min-w-0">
          {isLoading ? (
            <LoadingSpinner size="sm" />
          ) : isError ? (
            <ErrorState message={t('common.error')} onRetry={refetch} />
          ) : (
            <>
              <h1 className="font-semibold text-gray-900 dark:text-gray-100 truncate">
                {channel?.type === 'DIRECT' ? '#DM' : `# ${channelName}`}
              </h1>
              {channel?.topic && (
                <p className="text-xs text-gray-500 truncate">{channel.topic}</p>
              )}
            </>
          )}
        </div>

        {/* Toggle buttons for copilot / search */}
        <div className="flex items-center gap-1 shrink-0">
          <button
            onClick={() => setRightPanel((p) => (p === 'search' ? null : 'search'))}
            className={`p-2 rounded-lg transition-colors ${rightPanel === 'search' ? 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-300' : 'text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
            aria-label={t('search.title')}
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
          </button>
          <button
            onClick={() => setRightPanel((p) => (p === 'copilot' ? null : 'copilot'))}
            className={`p-2 rounded-lg transition-colors ${rightPanel === 'copilot' ? 'bg-blue-100 dark:bg-blue-900 text-blue-600 dark:text-blue-300' : 'text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 hover:bg-gray-100 dark:hover:bg-gray-800'}`}
            aria-label={t('copilot.title')}
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.75 17L9 20l-1 1h8l-1-1-.75-3M3 13h18M5 17h14a2 2 0 002-2V5a2 2 0 00-2-2H5a2 2 0 00-2 2v10a2 2 0 002 2z" />
            </svg>
          </button>
        </div>
      </div>

      {/* Message list takes all remaining height */}
      <MessageList channelId={channelId} />
    </AppShell>
  )
}
