import { useEffect, useState } from 'react'

// User preferences, kept in this browser (localStorage) and applied to the
// document as data attributes the CSS reads.

const KEY = 'sahayak_settings'

const DEFAULTS = {
  theme: 'dark', // 'dark' | 'light' | 'system'
  accent: 'amber', // 'amber' | 'cyan' | 'violet'
  wakeWord: 'sahayak',
  voiceReplies: false,
  defaultProvider: '', // '' = server default
}

function load() {
  try {
    return { ...DEFAULTS, ...JSON.parse(localStorage.getItem(KEY) || '{}') }
  } catch {
    return { ...DEFAULTS }
  }
}

function resolveTheme(theme) {
  if (theme !== 'system') return theme
  return window.matchMedia?.('(prefers-color-scheme: light)').matches ? 'light' : 'dark'
}

export function useSettings() {
  const [settings, setSettings] = useState(load)

  useEffect(() => {
    localStorage.setItem(KEY, JSON.stringify(settings))
    document.documentElement.dataset.theme = resolveTheme(settings.theme)
    document.documentElement.dataset.accent = settings.accent
  }, [settings])

  // follow the OS when in "system" mode
  useEffect(() => {
    if (settings.theme !== 'system') return
    const media = window.matchMedia('(prefers-color-scheme: light)')
    const apply = () => {
      document.documentElement.dataset.theme = resolveTheme('system')
    }
    media.addEventListener('change', apply)
    return () => media.removeEventListener('change', apply)
  }, [settings.theme])

  const set = (patch) => setSettings((s) => ({ ...s, ...patch }))
  return { ...settings, set }
}
