import { useCallback, useEffect, useRef } from 'react'
import { authApi } from '@/features/auth/api'
import type { SseMessageCreated, SseMessageUpdated, SseMessageDeleted } from '@/shared/api/types'

// =============================================================================
// useSse — SSE subscription via single-use ticket (RT-01..RT-06, D-15).
// The EventSource cannot set Authorization headers, so we first POST to
// /realtime/ticket, get a 60-second single-use token, then open the stream.
// =============================================================================

interface SseHandlers {
  onMessageCreated?: (data: SseMessageCreated) => void
  onMessageUpdated?: (data: SseMessageUpdated) => void
  onMessageDeleted?: (data: SseMessageDeleted) => void
}

export function useSse(handlers: SseHandlers) {
  const esRef = useRef<EventSource | null>(null)
  const handlersRef = useRef(handlers)
  handlersRef.current = handlers

  const connect = useCallback(async (lastEventId?: string) => {
    esRef.current?.close()

    try {
      // D-15: single-use ticket
      const res = await fetch('/api/v1/realtime/ticket', {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${document.cookie}`, // will be overridden by interceptor
        },
      })
      if (!res.ok) return
      const { ticket } = await res.json()

      const url = `/api/v1/realtime/stream?ticket=${encodeURIComponent(ticket)}${lastEventId ? `&lastEventId=${lastEventId}` : ''}`
      const es = new EventSource(url)
      esRef.current = es

      es.addEventListener('message.created', (e) => {
        try {
          handlersRef.current.onMessageCreated?.(JSON.parse(e.data))
        } catch { /* ignore */ }
      })

      es.addEventListener('message.updated', (e) => {
        try {
          handlersRef.current.onMessageUpdated?.(JSON.parse(e.data))
        } catch { /* ignore */ }
      })

      es.addEventListener('message.deleted', (e) => {
        try {
          handlersRef.current.onMessageDeleted?.(JSON.parse(e.data))
        } catch { /* ignore */ }
      })

      es.onerror = () => {
        // RT-05: reconnect after a brief delay
        es.close()
        setTimeout(() => connect(), 3000)
      }
    } catch {
      setTimeout(() => connect(), 5000)
    }
  }, [])

  useEffect(() => {
    connect()
    return () => {
      esRef.current?.close()
    }
  }, [connect])
}
