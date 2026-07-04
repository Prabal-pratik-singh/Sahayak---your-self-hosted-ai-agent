import { useEffect, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { speechSupported } from '../hooks/useSpeech.js'

const ACCENTS = [
  { id: 'cyan', label: 'Cyan' },
  { id: 'amber', label: 'Amber' },
  { id: 'violet', label: 'Violet' },
  { id: 'darkblue', label: 'Dark Blue' },
]

const KEY_LINKS = {
  anthropic: 'console.anthropic.com (paid)',
  openai: 'platform.openai.com/api-keys (paid)',
  gemini: 'aistudio.google.com/apikey (free tier)',
  groq: 'console.groq.com/keys (free tier)',
  github: 'github.com/settings/tokens (free with any GitHub account)',
  cerebras: 'cloud.cerebras.ai (free tier)',
  mistral: 'console.mistral.ai (free tier)',
  openrouter: 'openrouter.ai/keys (free models)',
}

/** BYOK — paste your own API key per AI engine; it unlocks that brain for you. */
function AiKeysCard() {
  const { toast, refreshModels, refreshHealth } = useApp()
  const [keys, setKeys] = useState(null)
  const [inputs, setInputs] = useState({})
  const [busyProvider, setBusyProvider] = useState('')

  const load = () => api('/keys').then(setKeys).catch((e) => toast(e.message, 'error'))
  useEffect(() => {
    load()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const afterChange = () => {
    load()
    refreshModels()
    refreshHealth()
  }

  const save = async (provider) => {
    const apiKey = (inputs[provider] || '').trim()
    if (!apiKey || busyProvider) return
    setBusyProvider(provider)
    try {
      const res = await api(`/keys/${provider}`, { method: 'PUT', body: { apiKey } })
      toast(res.warning || 'Key verified and saved ✓', res.warning ? 'info' : 'ok')
      setInputs((s) => ({ ...s, [provider]: '' }))
      afterChange()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusyProvider('')
    }
  }

  const remove = async (provider) => {
    if (busyProvider) return
    setBusyProvider(provider)
    try {
      await api(`/keys/${provider}`, { method: 'DELETE' })
      toast('Key removed', 'ok')
      afterChange()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusyProvider('')
    }
  }

  return (
    <section className="card">
      <div className="card-title">AI engine keys (bring your own)</div>
      <p className="hint">
        Paste your own API key to use an engine on your own quota — several have free tiers. Keys
        are encrypted on the server and used only for your account. <b>“Actions”</b> means the
        engine can actually DO things for you (reminders, weather, email, posting);
        <b> “chat only”</b> means it can talk but not act.
      </p>
      {!keys && <p className="empty-line">Loading…</p>}
      {keys?.map((k) => (
        <div className="key-row" key={k.provider}>
          <div className="key-head">
            <b>{k.label}</b>
            <span className="tag">{k.model}</span>
            <span className={`chip ${k.toolCapable ? 'ok' : ''}`} title={k.toolCapable ? 'Can take real actions: reminders, weather, email, posting' : 'Can talk, but cannot take actions'}>
              {k.toolCapable ? '⚡ actions + chat' : 'chat only'}
            </span>
            <span className={`chip ${k.hasKey ? 'ok' : k.serverAvailable ? '' : 'bad'}`}>
              {k.hasKey ? 'your key' : k.serverAvailable ? 'server key' : 'no key'}
            </span>
          </div>
          <div className="key-controls">
            <input
              className="input"
              type="password"
              placeholder={`Paste your ${k.label} key — get it at ${KEY_LINKS[k.provider] || 'the provider'}`}
              value={inputs[k.provider] || ''}
              onChange={(e) => setInputs((s) => ({ ...s, [k.provider]: e.target.value }))}
              aria-label={`${k.label} API key`}
            />
            <button
              className="btn"
              disabled={busyProvider === k.provider || !(inputs[k.provider] || '').trim()}
              onClick={() => save(k.provider)}
            >
              {busyProvider === k.provider ? 'Checking…' : k.hasKey ? 'Replace' : 'Save'}
            </button>
            {k.hasKey && (
              <button className="btn ghost danger" disabled={busyProvider === k.provider} onClick={() => remove(k.provider)}>
                Remove
              </button>
            )}
          </div>
        </div>
      ))}
    </section>
  )
}

export default function SettingsView() {
  const { user, logout, settings, models } = useApp()

  return (
    <div className="settings view-in">
      <section className="card">
        <div className="card-title">Appearance</div>
        <div className="setting-row">
          <span>Theme</span>
          <div className="seg">
            {['dark', 'light', 'system'].map((t) => (
              <button key={t} className={settings.theme === t ? 'active' : ''} onClick={() => settings.set({ theme: t })}>
                {t}
              </button>
            ))}
          </div>
        </div>
        <div className="setting-row">
          <span>Accent</span>
          <div className="swatches">
            {ACCENTS.map((a) => (
              <button
                key={a.id}
                className={`swatch ${a.id} ${settings.accent === a.id ? 'active' : ''}`}
                title={a.label}
                onClick={() => settings.set({ accent: a.id })}
              />
            ))}
          </div>
        </div>
      </section>

      <section className="card">
        <div className="card-title">Voice</div>
        {!speechSupported && <p className="hint">Listening needs Chrome or Edge — voice replies work everywhere.</p>}
        <div className="setting-row">
          <span>
            Wake word <small className="hint-inline">(used in Voice mode; leave empty to react to everything)</small>
          </span>
          <input
            className="input"
            type="text"
            name="sahayak-wake-word"
            autoComplete="off"
            autoCorrect="off"
            autoCapitalize="off"
            spellCheck="false"
            value={settings.wakeWord}
            maxLength={20}
            onChange={(e) => settings.set({ wakeWord: e.target.value.replace(/[^a-zA-Z ]/g, '') })}
            placeholder="(none — I respond to everything)"
          />
        </div>
        <div className="setting-row">
          <span>Speak replies in normal chat</span>
          <button
            className={`switch ${settings.voiceReplies ? 'on' : ''}`}
            onClick={() => settings.set({ voiceReplies: !settings.voiceReplies })}
            aria-label="Toggle voice replies"
          >
            <span />
          </button>
        </div>
      </section>

      <AiKeysCard />

      <section className="card">
        <div className="card-title">AI</div>
        <div className="setting-row">
          <span>Default brain for new messages</span>
          <select
            className="input"
            value={settings.defaultProvider}
            onChange={(e) => settings.set({ defaultProvider: e.target.value })}
          >
            <option value="">Automatic</option>
            {(models?.options || []).map((o) => (
              <option key={o.id} value={o.id}>
                {o.label} ({o.model}){o.source === 'your key' ? ' · your key' : ''}
                {o.tools === false ? ' · chat only' : ''}
              </option>
            ))}
          </select>
        </div>
      </section>

      <section className="card">
        <div className="card-title">Account</div>
        <div className="setting-row">
          <span>
            <b>{user.name}</b>
            <br />
            <small className="hint-inline">{user.email}</small>
          </span>
          <button className="btn ghost danger" onClick={logout}>
            Log out
          </button>
        </div>
        <p className="hint">
          These preferences live in this browser. Sahayak v0.4 · self-hosted · your data stays on your server.
        </p>
      </section>
    </div>
  )
}
