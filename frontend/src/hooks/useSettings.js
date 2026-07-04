import { useEffect, useState } from 'react'

// User preferences, kept in this browser (localStorage) and applied to the
// document as data attributes the CSS reads.

const KEY = 'sahayak_settings'

const DEFAULTS = {
  theme: 'dark', // 'dark' | 'light' | 'system'
  accent: 'cyan', // 'cyan' | 'amber' | 'violet'
  wakeWord: '', // empty = respond to everything; set a word for Jarvis-style gating
  voiceReplies: false,
  defaultProvider: '', // '' = server default
}

function load() {
  try {
    const stored = JSON.parse(localStorage.getItem(KEY) || '{}')
    // v0.5 rebrand: cyan became the signature accent. Migrate old saves once;
    // anything the user picks afterwards sticks.
    if (!stored._v5) {
      stored.accent = 'cyan'
      stored._v5 = true
    }
    // v0.5.1: the default wake word made voice mode ignore normal speech
    // ("it only listens"). Voice now responds to everything by default;
    // a wake word is an opt-in choice in Settings.
    if (!stored._v6) {
      if (stored.wakeWord === 'sahayak') stored.wakeWord = ''
      stored._v6 = true
    }
    return { ...DEFAULTS, ...stored }
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
