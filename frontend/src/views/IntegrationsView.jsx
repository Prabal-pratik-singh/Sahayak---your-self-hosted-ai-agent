import { useEffect, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'

// Every platform gets an honest card: connect it if the API allows it,
// or a plain explanation of why it isn't supported yet.

const UNSUPPORTED = [
  { name: 'WhatsApp', why: 'Only the paid WhatsApp Business API allows bots; personal chats are closed. Planned via a Business-API provider.' },
  { name: 'Instagram', why: 'Posting needs a Business/Creator account + Meta app review. Planned once an approved Meta app exists.' },
  { name: 'X (Twitter)', why: 'The free API tier no longer allows posting reliably; paid tiers start high. On hold.' },
  { name: 'Facebook Pages', why: 'Needs a Meta developer app + review for page permissions. Planned together with Instagram.' },
  { name: 'Google Calendar', why: 'Needs Google OAuth (cloud project). Meanwhile, Sahayak’s own scheduler covers reminders.' },
]

const EMPTY_EMAIL = { host: 'smtp.gmail.com', port: 587, username: '', password: '', fromAddress: '' }

export default function IntegrationsView() {
  const { toast } = useApp()
  const [connections, setConnections] = useState([])
  const [openForm, setOpenForm] = useState(null) // 'EMAIL' | 'TELEGRAM' | 'DISCORD' | 'SLACK' | null
  const [busy, setBusy] = useState(false)
  const [email, setEmail] = useState(EMPTY_EMAIL)
  const [telegram, setTelegram] = useState({ botToken: '', chatId: '' })
  const [webhook, setWebhook] = useState('')

  const load = () => api('/connections').then(setConnections).catch((e) => toast(e.message, 'error'))
  useEffect(() => {
    load()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const connected = (type) => connections.find((c) => c.type === type)

  const disconnect = async (c) => {
    try {
      await api(`/connections/${c.id}`, { method: 'DELETE' })
      toast(`${c.type} disconnected`, 'ok')
      load()
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const submit = async (path, body, okMsg) => {
    if (busy) return
    setBusy(true)
    try {
      await api(path, { method: 'POST', body })
      toast(okMsg, 'ok')
      setOpenForm(null)
      setEmail(EMPTY_EMAIL)
      setTelegram({ botToken: '', chatId: '' })
      setWebhook('')
      load()
    } catch (e) {
      toast(e.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const connectLinkedIn = async () => {
    try {
      const { url } = await api('/integrations/linkedin/authorize')
      window.location.href = url
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const Card = ({ type, title, blurb, children }) => {
    const c = connected(type)
    return (
      <div className="card integ-card">
        <div className="integ-head">
          <div>
            <h3>{title}</h3>
            <p>{blurb}</p>
          </div>
          {c ? <span className={`chip ${c.status === 'ACTIVE' ? 'ok' : 'bad'}`}>{c.status}</span> : <span className="chip">not connected</span>}
        </div>
        {c ? (
          <div className="integ-connected">
            <span className="row-text">{c.displayName}</span>
            <button className="btn ghost danger" onClick={() => disconnect(c)}>
              Disconnect
            </button>
          </div>
        ) : (
          children
        )}
      </div>
    )
  }

  return (
    <div className="integrations view-in">
      <div className="integ-grid">
        <Card type="EMAIL" title="Email (any provider)" blurb="Send email from your own mailbox via SMTP.">
          {openForm === 'EMAIL' ? (
            <form
              className="integ-form"
              onSubmit={(e) => {
                e.preventDefault()
                submit('/connections/email', { ...email, port: Number(email.port), fromAddress: email.fromAddress || null }, 'Email connected ✓')
              }}
            >
              <p className="hint">Gmail: enable 2-step verification, create an App Password, paste it below. Other providers: search “provider SMTP settings”.</p>
              <div className="form-row">
                <label className="field grow">
                  SMTP host
                  <input value={email.host} onChange={(e) => setEmail({ ...email, host: e.target.value })} required />
                </label>
                <label className="field small">
                  Port
                  <input type="number" value={email.port} onChange={(e) => setEmail({ ...email, port: e.target.value })} required />
                </label>
              </div>
              <label className="field">
                Email address (login)
                <input type="email" value={email.username} onChange={(e) => setEmail({ ...email, username: e.target.value })} required />
              </label>
              <label className="field">
                Password / App Password
                <input type="password" value={email.password} onChange={(e) => setEmail({ ...email, password: e.target.value })} required />
              </label>
              <button className="btn wide" disabled={busy}>
                {busy ? 'Checking mailbox…' : 'Save & test'}
              </button>
            </form>
          ) : (
            <button className="btn ghost" onClick={() => setOpenForm('EMAIL')}>
              Connect email
            </button>
          )}
        </Card>

        <Card type="LINKEDIN" title="LinkedIn" blurb="Publish posts on your own profile (official OAuth).">
          <button className="btn ghost" onClick={connectLinkedIn}>
            Connect LinkedIn
          </button>
        </Card>

        <Card type="TELEGRAM" title="Telegram" blurb="Your own bot sends messages to a chat you choose. Free.">
          {openForm === 'TELEGRAM' ? (
            <form
              className="integ-form"
              onSubmit={(e) => {
                e.preventDefault()
                submit('/connections/telegram', telegram, 'Telegram connected ✓ (check the test message)')
              }}
            >
              <p className="hint">
                1. In Telegram, message <b>@BotFather</b> → /newbot → copy the token. 2. Send your new bot any message. 3. Open
                api.telegram.org/bot&lt;TOKEN&gt;/getUpdates in a browser and copy <b>chat.id</b>.
              </p>
              <label className="field">
                Bot token
                <input value={telegram.botToken} onChange={(e) => setTelegram({ ...telegram, botToken: e.target.value })} required />
              </label>
              <label className="field">
                Chat id
                <input value={telegram.chatId} onChange={(e) => setTelegram({ ...telegram, chatId: e.target.value })} required />
              </label>
              <button className="btn wide" disabled={busy}>
                {busy ? 'Sending test message…' : 'Save & test'}
              </button>
            </form>
          ) : (
            <button className="btn ghost" onClick={() => setOpenForm('TELEGRAM')}>
              Connect Telegram
            </button>
          )}
        </Card>

        <Card type="DISCORD" title="Discord" blurb="Post into one channel via an incoming webhook.">
          {openForm === 'DISCORD' ? (
            <form
              className="integ-form"
              onSubmit={(e) => {
                e.preventDefault()
                submit('/connections/discord', { webhookUrl: webhook }, 'Discord connected ✓ (check the test message)')
              }}
            >
              <p className="hint">Channel → Edit → Integrations → Webhooks → New webhook → Copy URL.</p>
              <label className="field">
                Webhook URL
                <input value={webhook} onChange={(e) => setWebhook(e.target.value)} placeholder="https://discord.com/api/webhooks/…" required />
              </label>
              <button className="btn wide" disabled={busy}>
                {busy ? 'Sending test message…' : 'Save & test'}
              </button>
            </form>
          ) : (
            <button className="btn ghost" onClick={() => { setWebhook(''); setOpenForm('DISCORD') }}>
              Connect Discord
            </button>
          )}
        </Card>

        <Card type="SLACK" title="Slack" blurb="Post into one channel via an incoming webhook.">
          {openForm === 'SLACK' ? (
            <form
              className="integ-form"
              onSubmit={(e) => {
                e.preventDefault()
                submit('/connections/slack', { webhookUrl: webhook }, 'Slack connected ✓ (check the test message)')
              }}
            >
              <p className="hint">Slack → Apps → Incoming Webhooks → Add to channel → Copy URL.</p>
              <label className="field">
                Webhook URL
                <input value={webhook} onChange={(e) => setWebhook(e.target.value)} placeholder="https://hooks.slack.com/services/…" required />
              </label>
              <button className="btn wide" disabled={busy}>
                {busy ? 'Sending test message…' : 'Save & test'}
              </button>
            </form>
          ) : (
            <button className="btn ghost" onClick={() => { setWebhook(''); setOpenForm('SLACK') }}>
              Connect Slack
            </button>
          )}
        </Card>
      </div>

      <div className="card-title spaced">Not supported yet — and why (no fake buttons here)</div>
      <div className="integ-grid">
        {UNSUPPORTED.map((u) => (
          <div key={u.name} className="card integ-card muted">
            <h3>{u.name}</h3>
            <p>{u.why}</p>
          </div>
        ))}
      </div>
    </div>
  )
}
