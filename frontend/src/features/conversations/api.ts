import { apiClient } from '@/shared/api/client'
import type {
  ConversationSummary,
  ChannelDetail,
  ChannelMember,
  PagedResponse,
  UserSummary,
} from '@/shared/api/types'

export const conversationsApi = {
  list: (cursor?: string) =>
    apiClient
      .get<PagedResponse<ConversationSummary>>('/conversations', {
        params: { cursor, limit: 50 },
      })
      .then((r) => r.data),

  // GET /channels and GET /channels/{id}/members answer with a bare array —
  // they are bounded lists, not keyset pages.
  discoverChannels: (query?: string) =>
    apiClient
      .get<ChannelDetail[]>('/channels', {
        params: { type: 'PUBLIC', query },
      })
      .then((r) => r.data),

  getChannel: (channelId: string) =>
    apiClient.get<ChannelDetail>(`/channels/${channelId}`).then((r) => r.data),

  createChannel: (data: {
    name: string
    slug: string
    topic?: string
    type: 'PUBLIC' | 'PRIVATE'
    memberIds?: string[]
  }) => apiClient.post<ChannelDetail>('/channels', data).then((r) => r.data),

  openDirect: (counterpartUserId: string) =>
    apiClient
      .post<ChannelDetail>('/channels/direct', { counterpartUserId })
      .then((r) => r.data),

  getMembers: (channelId: string) =>
    apiClient
      .get<ChannelMember[]>(`/channels/${channelId}/members`)
      .then((r) => r.data),

  joinChannel: (channelId: string) =>
    apiClient
      .post<ChannelMember>(`/channels/${channelId}/members`, {})
      .then((r) => r.data),

  searchUsers: (query: string) =>
    apiClient
      .get<PagedResponse<UserSummary>>('/users', { params: { query, limit: 20 } })
      .then((r) => r.data),
}
