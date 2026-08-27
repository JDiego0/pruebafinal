import { useState } from 'react'
import { Link, useParams, useNavigate } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { useAuthStore } from '@/features/auth/store'
import { authApi } from '@/features/auth/api'
import { ConversationList } from '@/features/conversations/ConversationList'
import { Avatar } from '@/shared/ui/Avatar'
import { cn } from '@/shared/ui/cn'

// =============================================================================
// AppShell — responsive 3-pane layout (FE-12, FE-13).
// Mobile:  single pane, drawer for sidebar.
// ≥md:     sidebar + content (2 pane).
// ≥xl:     + copilot panel (3 pane).
// =============================================================================

interface Props {
  children: React.ReactNode
  rightPanel?: React.ReactNode
}

export function AppShell({ children, rightPanel }: Props) {
  const { t } = useTranslation()
  const { user, clearSession } = useAuthStore()
  const { channelId } = useParams()
  const navigate = useNavigate()
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [showCopilot] = useState(true)

  async function handleLogout() {
    try { await authApi.logout() } catch { /* ignore */ }
    clearSession()
    navigate('/login', { replace: true })
  }

  const sidebar = (
    <div className="flex flex-col h-full bg-gray-800 text-white">
      {/* Brand */}
      <div className="px-4 py-5 border-b border-gray-700 flex items-center justify-between">
        <span className="text-xl font-bold tracking-tight">Riwi</span>
        <button
          onClick={() => setSidebarOpen(false)}
          className="md:hidden text-gray-400 hover:text-white p-1"
          aria-label={t('common.close')}
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
          </svg>
        </button>
      </div>

      {/* Conversation list */}
      <div className="flex-1 overflow-y-auto px-2 py-3">
        <p className="text-xs font-semibold text-gray-400 uppercase px-3 mb-2">
          {t('conversations.title')}
        </p>
        <ConversationList activeChannelId={channelId} />
      </div>

      {/* User footer */}
      <div className="border-t border-gray-700 p-3 flex items-center gap-2">
        <Link to="/profile" className="flex items-center gap-2 flex-1 min-w-0 hover:bg-gray-700 rounded-lg px-2 py-1.5 transition-colors">
          <Avatar name={user?.displayName ?? '?'} size="sm" />
          <div className="min-w-0">
            <p className="text-sm font-medium truncate">{user?.displayName}</p>
            <p className="text-xs text-gray-400 truncate">{user?.email}</p>
          </div>
        </Link>
        <button
          onClick={handleLogout}
          className="text-gray-400 hover:text-white p-1.5 rounded-lg hover:bg-gray-700 transition-colors shrink-0"
          title={t('auth.logout')}
        >
          <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
          </svg>
        </button>
      </div>
    </div>
  )

  return (
    <div className="flex h-screen bg-gray-50 dark:bg-gray-950 overflow-hidden">
      {/* Mobile overlay */}
      {sidebarOpen && (
        <div
          className="fixed inset-0 z-20 bg-black/50 md:hidden"
          onClick={() => setSidebarOpen(false)}
        />
      )}

      {/* Sidebar */}
      <aside
        className={cn(
          'fixed md:static z-30 inset-y-0 left-0 w-72 shrink-0 flex flex-col transition-transform duration-200',
          sidebarOpen ? 'translate-x-0' : '-translate-x-full md:translate-x-0',
        )}
      >
        {sidebar}
      </aside>

      {/* Main content */}
      <div className="flex flex-col flex-1 min-w-0 overflow-hidden">
        {/* Top bar (mobile menu toggle) */}
        <header className="flex items-center gap-3 px-4 py-3 border-b border-gray-200 dark:border-gray-700 bg-white dark:bg-gray-900 md:hidden">
          <button
            onClick={() => setSidebarOpen(true)}
            className="text-gray-500 hover:text-gray-900 dark:hover:text-gray-100 p-1"
            aria-label={t('common.menu')}
          >
            <svg className="w-6 h-6" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>
          <span className="font-semibold text-gray-900 dark:text-gray-100">Riwi</span>
        </header>

        <div className="flex flex-1 min-h-0">
          {/* Channel / page content */}
          <main className="flex flex-col flex-1 min-w-0 overflow-hidden">
            {children}
          </main>

          {/* Right panel (copilot/search) — xl+ only by default */}
          {rightPanel && showCopilot && (
            <aside className="hidden xl:flex flex-col w-80 shrink-0 overflow-hidden">
              {rightPanel}
            </aside>
          )}
        </div>
      </div>
    </div>
  )
}
