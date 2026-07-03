import { useApp } from '../App.jsx'
import { speechSupported } from '../hooks/useSpeech.js'

const ACCENTS = [
  { id: 'amber', label: 'Amber' },
  { id: 'cyan', label: 'Cyan' },
  { id: 'violet', label: 'Violet' },
]

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
            value={settings.wakeWord}
            maxLength={24}
            onChange={(e) => settings.set({ wakeWord: e.target.value })}
            placeholder="sahayak"
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

      <section className="card">
        <div className="card-title">AI</div>
        <div className="setting-row">
          <span>Default brain for new messages</span>
          <select
            className="input"
            value={settings.defaultProvider}
            onChange={(e) => settings.set({ defaultProvider: e.target.value })}
          >
            <option value="">Server default</option>
            {(models?.options || []).map((o) => (
              <option key={o.id} value={o.id}>
                {o.label} ({o.model})
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
