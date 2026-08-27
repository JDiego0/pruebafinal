import axios from 'axios'
import { useAuthStore } from '@/features/auth/store'

// =============================================================================
// Axios instance — base URL, auth header, correlation id, silent refresh.
// =============================================================================

export const apiClient = axios.create({
  baseURL: '/api/v1',
  withCredentials: true, // send HttpOnly refresh-token cookie automatically
  headers: { 'Content-Type': 'application/json' },
})

// ── Request interceptor ───────────────────────────────────────────────────────

apiClient.interceptors.request.use((config) => {
  // Actor identity from store (already verified JWT) — API-03
  const token = useAuthStore.getState().accessToken
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  // Correlation id — API-04
  if (!config.headers['X-Correlation-Id']) {
    config.headers['X-Correlation-Id'] = crypto.randomUUID()
  }
  return config
})

// ── Response interceptor: silent refresh on 401 ───────────────────────────────

let refreshing: Promise<string> | null = null

apiClient.interceptors.response.use(
  (res) => res,
  async (error) => {
    const original = error.config as typeof error.config & { _retry?: boolean }

    if (error.response?.status === 401 && !original._retry) {
      original._retry = true

      // Collapse concurrent 401s into one refresh call
      if (!refreshing) {
        refreshing = axios
          .post<{ accessToken: string; expiresIn: number }>(
            '/api/v1/auth/refresh',
            {},
            { withCredentials: true },
          )
          .then((r) => {
            useAuthStore.getState().setAccessToken(r.data.accessToken)
            return r.data.accessToken
          })
          .catch((e) => {
            useAuthStore.getState().clearSession()
            return Promise.reject(e)
          })
          .finally(() => {
            refreshing = null
          })
      }

      try {
        const newToken = await refreshing
        original.headers.Authorization = `Bearer ${newToken}`
        return apiClient(original)
      } catch {
        return Promise.reject(error)
      }
    }

    return Promise.reject(error)
  },
)
