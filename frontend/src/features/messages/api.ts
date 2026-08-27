import { apiClient } from '@/shared/api/client'
import type { MessageRecord, PagedResponse } from '@/shared/api/types'

export const messagesApi = {
  history: (channelId: string, cursor?: string, limit = 50) =>
    apiClient
      .get<PagedResponse<MessageRecord>>(`/channels/${channelId}/messages`, {
        params: { cursor, limit },
      })
      .then((r) => r.data),

  send: (channelId: string, body: string, clientMessageId: string, parentMessageId?: number) =>
    apiClient
      .post<MessageRecord>(`/channels/${channelId}/messages`, {
        body,
        clientMessageId,
        parentMessageId: parentMessageId ?? null,
      })
      .then((r) => r.data),

  edit: (messageId: number, body: string) =>
    apiClient
      .patch<{ messageId: number; revision: number; editedAt: string }>(
        `/messages/${messageId}`,
        { body },
      )
      .then((r) => r.data),

  delete: (messageId: number) => apiClient.delete(`/messages/${messageId}`),

  updateReadCursor: (channelId: string, lastReadMessageId: number) =>
    apiClient.put(`/channels/${channelId}/read-cursor`, { lastReadMessageId }),
}
