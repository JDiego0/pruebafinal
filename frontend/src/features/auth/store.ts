import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import type { UserProfile } from '@/shared/api/types'

// =============================================================================
// Auth store — Zustand persisted session.
// Access token in memory (short-lived); refresh token lives in HttpOnly cookie.
// =============================================================================

interface AuthState {
  accessToken: string | null
  user: UserProfile | null
  // Actions
  setSession: (accessToken: string, user: UserProfile) => void
  setAccessToken: (accessToken: string) => void
  clearSession: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      user: null,

      setSession: (accessToken, user) => set({ accessToken, user }),

      setAccessToken: (accessToken) => set({ accessToken }),

      clearSession: () => set({ accessToken: null, user: null }),
    }),
    {
      name: 'riwi-auth',
      // Only persist the user profile, not the access token —
      // the token is re-fetched via the HttpOnly refresh cookie on reload
      partialize: (state) => ({ user: state.user }),
    },
  ),
)
