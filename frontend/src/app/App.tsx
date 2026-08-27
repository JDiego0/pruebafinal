import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Providers } from './providers'
import { RequireAuth } from '@/features/auth/RequireAuth'
import { LoginPage } from '@/features/auth/LoginPage'
import { ChannelPage } from '@/features/conversations/ChannelPage'
import { ProfilePage } from '@/features/profile/ProfilePage'
import { AppShell } from './AppShell'

// =============================================================================
// App — router + providers. All user-facing routes require auth (API-05).
// =============================================================================

function HomeRedirect() {
  // Default landing: the shell with the conversation list but no channel selected
  return (
    <AppShell>
      <div className="flex-1 flex items-center justify-center text-gray-400 text-sm">
        {/* Will be populated by ConversationList auto-redirect in a real build */}
        Select a conversation
      </div>
    </AppShell>
  )
}

export function App() {
  return (
    <BrowserRouter>
      <Providers>
        <Routes>
          <Route path="/login" element={<LoginPage />} />

          <Route
            path="/"
            element={
              <RequireAuth>
                <HomeRedirect />
              </RequireAuth>
            }
          />

          <Route
            path="/channels/:channelId"
            element={
              <RequireAuth>
                <ChannelPage />
              </RequireAuth>
            }
          />

          <Route
            path="/profile"
            element={
              <RequireAuth>
                <AppShell>
                  <ProfilePage />
                </AppShell>
              </RequireAuth>
            }
          />

          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </Providers>
    </BrowserRouter>
  )
}
