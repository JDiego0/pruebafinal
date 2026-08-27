import { apiClient } from '@/shared/api/client'
import type { CopilotAskRequest, CopilotAnswer, CopilotUsage } from '@/shared/api/types'

export const copilotApi = {
  ask: (data: CopilotAskRequest) =>
    apiClient.post<CopilotAnswer>('/copilot/ask', data).then((r) => r.data),

  usage: () =>
    apiClient.get<CopilotUsage>('/copilot/usage').then((r) => r.data),
}
