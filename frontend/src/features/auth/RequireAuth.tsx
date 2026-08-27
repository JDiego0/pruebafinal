import { type ReactNode, useEffect, useState } from 'react'
import { Navigate } from 'react-router-dom'
import { useAuthStore } from './store'
import { authApi } from './api'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'

// =============================================================================
// RequireAuth — wraps protected routes, attempts silent refresh on load.
// =============================================================================

export function RequireAuth({ children }: { children: ReactNode }) {
  const { accessToken, user, setSession, clearSession } = useAuthStore()
  const [checking, setChecking] = useState(!accessToken && !!user)

  useEffect(() => {
    // We have a persisted user but no in-memory token → try to recover via cookie
    if (!accessToken && user) {
      authApi
        .refresh()
        .then((res) => setSession(res.accessToken, res.user))
        .catch(() => clearSession())
        .finally(() => setChecking(false))
    }
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  if (checking) return <LoadingSpinner className="min-h-screen" />
  if (!accessToken) return <Navigate to="/login" replace />
  return <>{children}</>
}
