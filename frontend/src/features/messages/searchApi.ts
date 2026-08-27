import { apiClient } from '@/shared/api/client'
import type { SearchHitResult, PagedResponse } from '@/shared/api/types'

export const searchApi = {
  messages: (query: string, channelId?: string, cursor?: string, limit = 50) =>
    apiClient
      .get<PagedResponse<SearchHitResult>>('/search/messages', {
        params: { q: query, channelId, cursor, limit },
      })
      .then((r) => r.data),
}
