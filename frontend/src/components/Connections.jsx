import { useEffect, useState } from 'react'
import { api } from '../api.js'

const TYPE_LABELS = { EMAIL: 'Email (SMTP)', LINKEDIN: 'LinkedIn' }

const EMPTY_FORM = { host: 'smtp.gmail.com', port: 587, username: '', password: '', fromAddress: '' }

export default function Connections({ open, notice, onClose }) {
  const [connections, setConnections] = useState([])
  const [form, setForm] = useState(EMPTY_FORM)
  const [showEmailForm, setShowEmailForm] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const load = async () => {
    try {
      setConnections(await api('/connections'))
    } catch (err) {
      setError(err.message)
    }
  }

  useEffect(() => {
    if (open) {
      setError('')
      load()
    }
  }, [open])

  const connectLinkedIn = async () => {
    setError('')
    try {
      const { url } = await api('/integrations/linkedin/authorize')
      window.location.href = url // LinkedIn sends the browser back here afterwards
    } catch (err) {
      setError(err.message)
    }
  }

  const saveEmail = async (e) => {
    e.preventDefault()
    if (busy) return
    setError('')
    setBusy(true)
    try {
      await api('/connections/email', {
        method: 'POST',
        body: { ...form, port: Number(form.port), fromAddress: form.fromAddress || null },
      })
      setForm(EMPTY_FORM)
      setShowEmailForm(false)
      await load()
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id) => {
    setError('')
    try {
      await api(`/connections/${id}`, { method: 'DELETE' })
      await load()
    } catch (err) {
      setError(err.message)
    }
  }

  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }))

  if (!open) return null

  return (
    <div className="overlay" onClick={onClose}>
      <aside className="panel" onClick={(e) => e.stopPropagation()}>
        <div className="panel-head">
          <span className="board-title">Connections</span>
          <button className="iconbtn" title="Close" onClick={onClose}>
            ×
          </button>
        </div>

        {notice && <p className="notice">{notice}</p>}

        <p className="hint">
          Connect your own accounts here. The agent can only act through what YOU connect —
          each user of this server connects their own apps.
        </p>

        {connections.length > 0 && (
          <ul className="conn-list">
            {connections.map((c) => (
              <li key={c.id} className="conn">
                <div className="conn-main">
                  <span className="conn-type">{TYPE_LABELS[c.type] ?? c.type}</span>
                  <span className="conn-name">{c.displayName}</span>
                </div>
                <span className={`chip ${c.status === 'ACTIVE' ? 'ok' : 'bad'}`}>{c.status}</span>
                <button className="task-cancel" title="Remove connection" onClick={() => remove(c.id)}>
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}

        <div className="panel-actions">
          <button className="btn" onClick={() => setShowEmailForm((v) => !v)}>
            {showEmailForm ? 'Hide email form' : 'Connect email (SMTP)'}
          </button>
          <button className="btn" onClick={connectLinkedIn}>
            Connect LinkedIn
          </button>
        </div>

        {showEmailForm && (
          <form className="email-form" onSubmit={saveEmail}>
            <p className="hint">
              Works with any mail provider. For Gmail: keep the values below, turn on 2-step
              verification in your Google account, create an <b>App Password</b>, and paste it
              as the password. Details in the README.
            </p>
            <div className="form-row">
              <label className="field grow">
                SMTP host
                <input value={form.host} onChange={set('host')} placeholder="smtp.gmail.com" required />
              </label>
              <label className="field small">
                Port
                <input type="number" value={form.port} onChange={set('port')} min={1} max={65535} required />
              </label>
            </div>
            <label className="field">
              Email address (login)
              <input type="email" value={form.username} onChange={set('username')} placeholder="you@gmail.com" required />
            </label>
            <label className="field">
              Password / App Password
              <input type="password" value={form.password} onChange={set('password')} required />
            </label>
            <label className="field">
              Send as (optional, defaults to login address)
              <input type="email" value={form.fromAddress} onChange={set('fromAddress')} placeholder="you@yourdomain.com" />
            </label>
            <button className="send wide" type="submit" disabled={busy}>
              {busy ? 'Checking mailbox…' : 'Save & test connection'}
            </button>
          </form>
        )}

        {error && <p className="error-text">{error}</p>}
      </aside>
    </div>
  )
}
