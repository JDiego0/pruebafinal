import { apiClient } from '@/shared/api/client'
import type { LoginRequest, LoginResponse, UserProfile } from '@/shared/api/types'

export const authApi = {
  login: (data: LoginRequest) =>
    apiClient.post<LoginResponse>('/auth/login', data).then((r) => r.data),

  refresh: () =>
    apiClient
      .post<LoginResponse>('/auth/refresh')
      .then((r) => r.data),

  logout: () => apiClient.post('/auth/logout'),

  getMe: () => apiClient.get<UserProfile>('/me').then((r) => r.data),

  updateMe: (data: Partial<Pick<UserProfile, 'displayName' | 'locale' | 'avatarUrl'>>) =>
    apiClient.patch<UserProfile>('/me', data).then((r) => r.data),
}
