import { useCallback, useRef } from 'react'

// =============================================================================
// useScrollAnchor — preserves scroll position when content is prepended.
// Before prepending: capture scrollHeight. After paint: restore the delta.
// This is the standard scroll-anchoring pattern for infinite-scroll-upward
// message lists (FE-08).
// =============================================================================

export function useScrollAnchor(containerRef: React.RefObject<HTMLElement | null>) {
  const savedHeightRef = useRef<number | null>(null)

  const saveAnchor = useCallback(() => {
    if (containerRef.current) {
      savedHeightRef.current = containerRef.current.scrollHeight
    }
  }, [containerRef])

  const restoreAnchor = useCallback(() => {
    const el = containerRef.current
    if (el && savedHeightRef.current !== null) {
      const delta = el.scrollHeight - savedHeightRef.current
      el.scrollTop += delta
      savedHeightRef.current = null
    }
  }, [containerRef])

  return { saveAnchor, restoreAnchor }
}
