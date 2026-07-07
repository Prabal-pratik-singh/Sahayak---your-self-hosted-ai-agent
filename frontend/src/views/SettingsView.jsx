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

// "How do I get a key?" guidance, keyed by the app's provider ids
// (anthropic = Claude, openai = ChatGPT). URLs/notes/steps are verbatim —
// the provider's own page is the source of truth.
const KEY_GUIDES = {
  anthropic: {
    tier: 'Paid',
    link: 'https://platform.claude.com/settings/keys',
    note: 'Pay-as-you-go, no permanent free tier. A Claude.ai Pro/Max subscription does NOT include API access.',
    steps: [
      'Sign up at console.anthropic.com (it redirects to platform.claude.com) with email or Google.',
      'Open Settings → Billing and add a payment method / buy credits — keys fail without this.',
      'Go to API Keys → Create Key, give it a name, and copy it immediately (shown only once).',
      'Paste it above and Save. The key starts with sk-ant-.',
    ],
  },
  openai: {
    tier: 'Paid',
    link: 'https://platform.openai.com/api-keys',
    note: 'The API is separate from chatgpt.com — a ChatGPT Plus subscription does NOT include API access. Free tier is negligible; you need ~$5 credit.',
    steps: [
      'Sign up at platform.openai.com (not chatgpt.com) and verify your email + phone.',
      'Under Billing, add a payment method and at least $5 of credit.',
      'Go to API keys → Create new secret key, and copy it once.',
      'Paste it above and Save. The key starts with sk-proj-.',
    ],
  },
  gemini: {
    tier: 'Free tier',
    link: 'https://aistudio.google.com/apikey',
    note: 'Free tier covers the Flash models, no credit card. In the EEA, UK, or Switzerland you must enable billing even for the free tier.',
    steps: [
      'Go to aistudio.google.com and sign in with a Google account.',
      'Click Get API key → Create API key, and let it create a project.',
      'Copy the key (starts with AIza) — you can view it again later here.',
      'Paste it above and Save.',
    ],
  },
  groq: {
    tier: 'Free',
    link: 'https://console.groq.com/keys',
    note: 'Free, no credit card. Runs open-weight models only (no GPT/Claude/Gemini).',
    steps: [
      'Sign up at console.groq.com with email, Google, or GitHub.',
      'Go to API Keys → Create API Key and give it a name.',
      'Copy it immediately (shown only once). The key starts with gsk_.',
      'Paste it above and Save.',
    ],
  },
  github: {
    tier: 'Free',
    link: 'https://github.com/settings/tokens',
    note: 'GitHub Models uses a GitHub personal access token (PAT) as the credential — there is no separate API key. Free but rate-limited.',
    steps: [
      'Go to github.com/settings/tokens (Settings → Developer settings → Personal access tokens).',
      'Generate a new token with the models:read permission.',
      'Copy the token.',
      'Paste it above and Save — your GitHub token is the key here.',
    ],
  },
  cerebras: {
    tier: 'Free tier',
    link: 'https://cloud.cerebras.ai',
    note: 'Free tier (~1M tokens/day), no credit card. Open models only.',
    steps: [
      'Sign up at cloud.cerebras.ai with email (no card required).',
      'Open API Keys in the left nav → Create / Generate key.',
      'Copy it (starts with csk-).',
      'Paste it above and Save.',
    ],
  },
  mistral: {
    tier: 'Free tier',
    link: 'https://console.mistral.ai/api-keys',
    note: "Free 'Experiment' tier, no credit card but phone verification required. On the free tier your data may be used for training unless you opt out in Settings → Privacy.",
    steps: [
      'Sign up at console.mistral.ai and verify your phone number.',
      'Go to API Keys → Create new key, name it, and set an expiry.',
      'Copy it immediately (shown only once).',
      'Paste it above and Save.',
    ],
  },
}

/** BYOK — paste your own API key per AI engine; it unlocks that brain for you. */
function AiKeysCard() {
  const { toast, refreshModels, refreshHealth } = useApp()
  const [keys, setKeys] = useState(null)
  const [inputs, setInputs] = useState({})
  const [busyProvider, setBusyProvider] = useState('')
  const [openGuide, setOpenGuide] = useState({}) // provider → expanded?

  const toggleGuide = (provider) => setOpenGuide((s) => ({ ...s, [provider]: !s[provider] }))

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

          {KEY_GUIDES[k.provider] && (
            <div className="key-guide-wrap">
              <button
                className="key-guide-toggle"
                aria-expanded={!!openGuide[k.provider]}
                onClick={() => toggleGuide(k.provider)}
              >
                <span className={`guide-chevron ${openGuide[k.provider] ? 'open' : ''}`} aria-hidden="true">›</span>
                How do I get a {k.label} key?
              </button>
              {openGuide[k.provider] && (
                <GuidePanel label={k.label} guide={KEY_GUIDES[k.provider]} />
              )}
            </div>
          )}
        </div>
      ))}
    </section>
  )
}

/** Inline "how to get a key" steps for one provider — purely informational. */
function GuidePanel({ label, guide }) {
  const free = /free/i.test(guide.tier)
  return (
    <div className="key-guide">
      <div className="key-guide-top">
        <span className={`chip ${free ? 'ok' : 'warn'}`}>{guide.tier}</span>
        <p className="key-guide-note">{guide.note}</p>
      </div>
      <ol className="key-guide-steps">
        {guide.steps.map((step, i) => (
          <li key={i}>{step}</li>
        ))}
      </ol>
      <div className="key-guide-actions">
        <a className="btn ghost" href={guide.link} target="_blank" rel="noopener noreferrer">
          Open {label} key page ↗
        </a>
      </div>
      <p className="key-guide-fineprint">Steps can change — the provider's page is the source of truth.</p>
    </div>
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
        <p className="hint">
          Voice mode works like a call — tap the mic, just talk, and it replies, then listens
          again. No wake word needed.
        </p>
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
        <div className="setting-row">
          <span>Voice language / accent</span>
          <select
            value={settings.voiceLang || 'en-IN'}
            onChange={(e) => settings.set({ voiceLang: e.target.value })}
            aria-label="Voice recognition language"
          >
            <option value="en-IN">English (India)</option>
            <option value="en-US">English (US)</option>
            <option value="en-GB">English (UK)</option>
            <option value="hi-IN">हिन्दी (Hindi)</option>
          </select>
        </div>
        <p className="hint">
          Picking the language that matches how you actually speak is the biggest fix for
          misheard words — e.g. English (India) for an Indian accent.
        </p>
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
