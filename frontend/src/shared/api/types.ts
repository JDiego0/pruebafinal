// =============================================================================
// Shared API types — generated from docs/API.md contract.
// Matches the Java DTO records in presentation/dto exactly.
// =============================================================================

/** Generic keyset-paginated envelope */
export interface PagedResponse<T> {
  items: T[]
  pageInfo: PageInfo
}

export interface PageInfo {
  nextCursor: string | null
  hasMore: boolean
}

// RFC 9457 Problem Details
export interface ProblemDetail {
  type: string
  title: string
  status: number
  detail: string
  instance?: string
  code: string
  correlationId?: string
  errors?: FieldError[]
}

export interface FieldError {
  field: string
  code: string
  message: string
}

// ── Auth ──────────────────────────────────────────────────────────────────────

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  tokenType: string
  expiresIn: number
  user: UserProfile
}

export interface UserProfile {
  userId: string
  email: string
  displayName: string
  role: 'MEMBER' | 'ADMIN'
  locale: 'es' | 'en'
  avatarUrl: string | null
  createdAt: string
  lastSeenAt: string | null
}

// ── Conversations / Channels ──────────────────────────────────────────────────

export interface ConversationSummary {
  channelId: string
  type: 'PUBLIC' | 'PRIVATE' | 'DIRECT'
  name: string | null
  slug: string | null
  isArchived: boolean
  counterpart: Counterpart | null
  lastMessage: LastMessage | null
  unreadCount: number
  memberRole: 'OWNER' | 'MODERATOR' | 'MEMBER'
  isMuted: boolean
}

export interface Counterpart {
  userId: string
  displayName: string
  avatarUrl: string | null
}

export interface LastMessage {
  messageId: string
  preview: string
  senderDisplayName: string
  createdAt: string
  isDeleted: boolean
}

export interface ChannelDetail {
  channelId: string
  type: 'PUBLIC' | 'PRIVATE' | 'DIRECT'
  name: string | null
  slug: string | null
  topic: string | null
  isArchived: boolean
  createdBy: string
  createdAt: string
}

export interface ChannelMember {
  userId: string
  displayName: string
  avatarUrl: string | null
  role: 'OWNER' | 'MODERATOR' | 'MEMBER'
  joinedAt: string
}

export interface UserSummary {
  userId: string
  displayName: string
  email: string
  role: 'MEMBER' | 'ADMIN'
  avatarUrl: string | null
  isActive: boolean
}

// ── Messages ──────────────────────────────────────────────────────────────────

export interface MessageRecord {
  messageId: number
  channelId: string
  senderId: string
  parentMessageId: number | null
  body: string | null
  clientMessageId: string
  isDeleted: boolean
  revision: number
  createdAt: string
  editedAt: string | null
}

/** Message with sender details added by frontend cache */
export interface MessageWithSender extends MessageRecord {
  senderDisplayName: string
  senderAvatarUrl: string | null
}

/** Local-only optimistic send state */
export type MessageStatus = 'pending' | 'sent' | 'failed'

export interface OptimisticMessage extends MessageWithSender {
  status: MessageStatus
}

// ── Search ────────────────────────────────────────────────────────────────────

export interface SearchHitResult {
  messageId: number
  channelId: string
  senderId: string
  createdAt: string
  /** HTML fragment from ts_headline — contains <mark> only (SRCH-03) */
  highlight: string
  rank: number
}

// ── Realtime ──────────────────────────────────────────────────────────────────

export interface RealtimeTicket {
  ticket: string
  expiresIn: number
}

export interface SseMessageCreated {
  channelId: string
  messageId: number
  senderId: string
  clientMessageId: string
  createdAt: string
}

export interface SseMessageUpdated {
  channelId: string
  messageId: number
  revision: number
}

export interface SseMessageDeleted {
  channelId: string
  messageId: number
}

// ── AI Copilot ────────────────────────────────────────────────────────────────

export type CopilotStatus =
  | 'ANSWERED'
  | 'INSUFFICIENT_CONTEXT'
  | 'NO_PERMISSION'
  | 'OUT_OF_SCOPE'
  | 'PROVIDER_ERROR'

export interface CopilotAskRequest {
  question: string
  channelId?: string
  threadId?: string | null
}

export interface CopilotAnswer {
  turnId: string
  threadId: string
  status: CopilotStatus
  answer: string | null
  citations: Citation[]
  promptVersion: string
  usage: TokenUsage
}

export interface Citation {
  messageId: number
  channelId: string
  channelName: string
  senderDisplayName: string
  createdAt: string
  excerpt: string
  similarity: number
  rank: number
}

export interface TokenUsage {
  promptTokens: number
  completionTokens: number
  totalTokens: number
}

export interface CopilotUsage {
  userId: string
  totals: {
    requests: number
    promptTokens: number
    completionTokens: number
    totalTokens: number
  }
  byOperation: OperationUsage[]
  byModel: ModelUsage[]
  firstUsedAt: string | null
  lastUsedAt: string | null
}

export interface OperationUsage {
  operation: string
  requests: number
  totalTokens: number
}

export interface ModelUsage {
  provider: string
  model: string
  operation: string
  requests: number
  totalTokens: number
}
