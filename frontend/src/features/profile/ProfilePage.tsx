import { useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import { useQuery } from '@tanstack/react-query'
import { authApi } from '@/features/auth/api'
import { useAuthStore } from '@/features/auth/store'
import { LoadingSpinner } from '@/shared/ui/LoadingSpinner'
import { Avatar } from '@/shared/ui/Avatar'
import { Button } from '@/shared/ui/Button'
import { Input } from '@/shared/ui/Input'

// =============================================================================
// ProfilePage — FE-04. Self-service profile edit, locale switch (FE-14/15).
// =============================================================================

export function ProfilePage() {
  const { t, i18n } = useTranslation()
  const { user, setSession } = useAuthStore()
  const [displayName, setDisplayName] = useState(user?.displayName ?? '')
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const { data: profile, isLoading } = useQuery({
    queryKey: ['me'],
    queryFn: authApi.getMe,
    initialData: user ?? undefined,
  })

  async function handleSave(e: FormEvent) {
    e.preventDefault()
    if (!profile) return
    setSaving(true)
    setSaved(false)
    try {
      const updated = await authApi.updateMe({ displayName })
      setSession(useAuthStore.getState().accessToken!, updated)
      setSaved(true)
      setTimeout(() => setSaved(false), 2000)
    } finally {
      setSaving(false)
    }
  }

  function switchLocale(locale: 'es' | 'en') {
    i18n.changeLanguage(locale)
    if (profile) {
      authApi.updateMe({ locale }).catch(() => { /* best-effort */ })
    }
  }

  if (isLoading || !profile) return <LoadingSpinner className="flex-1" />

  return (
    <div className="max-w-lg mx-auto px-4 py-10 space-y-8">
      <div className="flex items-center gap-4">
        <Avatar name={profile.displayName} size="xl" />
        <div>
          <h1 className="text-2xl font-bold text-gray-900 dark:text-gray-100">
            {profile.displayName}
          </h1>
          <p className="text-sm text-gray-500">{profile.email}</p>
          <span className="inline-block mt-1 text-xs bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300 rounded-full px-2 py-0.5">
            {profile.role}
          </span>
        </div>
      </div>

      <form onSubmit={handleSave} className="bg-white dark:bg-gray-900 shadow rounded-xl p-6 space-y-5">
        <h2 className="font-semibold text-gray-900 dark:text-gray-100">{t('profile.title')}</h2>

        <Input
          label={t('profile.displayName')}
          value={displayName}
          onChange={(e) => setDisplayName(e.target.value)}
          required
          minLength={1}
          maxLength={100}
        />

        <div>
          <p className="text-sm font-medium text-gray-700 dark:text-gray-300 mb-2">
            {t('profile.locale')}
          </p>
          <div className="flex gap-2">
            {(['es', 'en'] as const).map((loc) => (
              <button
                key={loc}
                type="button"
                onClick={() => switchLocale(loc)}
                className={[
                  'px-4 py-2 rounded-lg text-sm font-medium transition-colors',
                  i18n.language.startsWith(loc)
                    ? 'bg-blue-600 text-white'
                    : 'bg-gray-100 dark:bg-gray-800 text-gray-700 dark:text-gray-300 hover:bg-gray-200 dark:hover:bg-gray-700',
                ].join(' ')}
              >
                {loc === 'es' ? 'Español' : 'English'}
              </button>
            ))}
          </div>
        </div>

        <Button type="submit" loading={saving} className="w-full">
          {saved ? t('profile.saved') : t('profile.save')}
        </Button>
      </form>
    </div>
  )
}
