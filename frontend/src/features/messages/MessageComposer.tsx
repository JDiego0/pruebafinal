import { type FormEvent, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

// =============================================================================
// MessageComposer — FE-05, FE-06. Optimistic send, Enter to submit.
// =============================================================================

interface Props {
  channelId: string
  onSend: (body: string, clientMessageId: string) => Promise<void>
}

export function MessageComposer({ channelId: _channelId, onSend }: Props) {
  const { t } = useTranslation()
  const [body, setBody] = useState('')
  const [sending, setSending] = useState(false)
  const textareaRef = useRef<HTMLTextAreaElement>(null)

  async function handleSubmit(e?: FormEvent) {
    e?.preventDefault()
    const text = body.trim()
    if (!text || sending) return

    const clientMessageId = crypto.randomUUID()
    setBody('')
    setSending(true)
    try {
      await onSend(text, clientMessageId)
    } finally {
      setSending(false)
      textareaRef.current?.focus()
    }
  }

  function handleKeyDown(e: React.KeyboardEvent<HTMLTextAreaElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      handleSubmit()
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className="border-t border-gray-200 dark:border-gray-700 px-4 py-3 flex items-end gap-3 bg-white dark:bg-gray-900"
    >
      <textarea
        ref={textareaRef}
        value={body}
        onChange={(e) => setBody(e.target.value)}
        onKeyDown={handleKeyDown}
        placeholder={t('messages.placeholder')}
        rows={1}
        maxLength={4000}
        disabled={sending}
        className="flex-1 resize-none rounded-xl border border-gray-300 dark:border-gray-600 bg-gray-50 dark:bg-gray-800 text-sm text-gray-900 dark:text-gray-100 placeholder-gray-400 px-4 py-2.5 focus:outline-none focus:ring-2 focus:ring-blue-500 transition-colors min-h-[42px] max-h-40 overflow-y-auto"
        style={{ height: 'auto' }}
        onInput={(e) => {
          const el = e.currentTarget
          el.style.height = 'auto'
          el.style.height = `${Math.min(el.scrollHeight, 160)}px`
        }}
      />
      <button
        type="submit"
        disabled={!body.trim() || sending}
        className="shrink-0 bg-blue-600 text-white rounded-xl px-4 py-2.5 text-sm font-medium hover:bg-blue-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
        aria-label={t('messages.send')}
      >
        <svg xmlns="http://www.w3.org/2000/svg" className="w-5 h-5 rotate-90" fill="currentColor" viewBox="0 0 20 20">
          <path d="M10.894 2.553a1 1 0 00-1.788 0l-7 14a1 1 0 001.169 1.409l5-1.429A1 1 0 009 15.571V11a1 1 0 112 0v4.571a1 1 0 00.725.962l5 1.428a1 1 0 001.17-1.408l-7-14z" />
        </svg>
      </button>
    </form>
  )
}
