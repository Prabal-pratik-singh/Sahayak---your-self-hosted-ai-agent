import { useEffect, useMemo, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import RingGauge from '../components/RingGauge.jsx'
import { MicIcon, SendIcon } from '../components/Icons.jsx'
import { speechSupported } from '../hooks/useSpeech.js'

// The command center: everything on this screen is real, live data —
// no decorative fake numbers.

const QUICK_ACTIONS = [
  { icon: '🌐', title: 'Look it up', sub: 'Wikipedia & live weather', prefill: 'Look up on Wikipedia: ' },
  { icon: '📅', title: 'Schedule a task', sub: 'Reminders & future actions', prefill: 'Tomorrow at 9 AM, ' },
  { icon: '🔗', title: 'Read a web page', sub: 'Summarize any public URL', prefill: 'Read this page and summarize it: https://' },
  { icon: '🎙️', title: 'Speak to me', sub: 'Hands-free voice mode', voice: true },
]

function timeGreeting() {
  const h = new Date().getHours()
  if (h < 5) return 'Working late'
  if (h < 12) return 'Good morning'
  if (h < 17) return 'Good afternoon'
  return 'Good evening'
}

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString(undefined, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
}

const PROVIDER_STATUS = {
  ok: ['Healthy', 'ok'],
  limited: ['Rate-limited', 'warn'],
  error: ['Key problem', 'bad'],
  down: ['Having trouble', 'bad'],
}

export default function HomeView() {
  const {
    user, tasks, conversations, providerHealth, sysInfo, latencyMs, online,
    nav, newChat, openConversation, setVoiceOpen, setPrefill, toast,
  } = useApp()
  const [connections, setConnections] = useState([])
  const [ask, setAsk] = useState('')

  useEffect(() => {
    api('/connections').then(setConnections).catch(() => {})
  }, [])

  const pending = tasks.filter((t) => t.status === 'PENDING')
  const done = tasks.filter((t) => t.status === 'DONE')
  const upcoming = [...pending].sort((a, b) => (a.runAt || '').localeCompare(b.runAt || '')).slice(0, 3)

  const activity = useMemo(() => {
    const items = []
    for (const t of tasks) {
      if ((t.status === 'DONE' || t.status === 'FAILED') && t.runAt) {
        items.push({ at: t.runAt, icon: t.status === 'DONE' ? '✅' : '⚠️', text: `Task #${t.id} ${t.status === 'DONE' ? 'finished' : 'failed'} — ${t.instruction}` })
      }
    }
    for (const c of conversations.slice(0, 6)) {
      if (c.updatedAt) items.push({ at: c.updatedAt, icon: '💬', text: `Chat "${c.title}"`, conversation: c })
    }
    return items.sort((a, b) => b.at.localeCompare(a.at)).slice(0, 4)
  }, [tasks, conversations])

  // System health: backend reachability + share of AI providers with no issues.
  const healthyProviders = providerHealth.filter((p) => p.status === 'ok').length
  const systemPct = !online
    ? 0
    : providerHealth.length === 0
      ? 100
      : Math.round((healthyProviders / providerHealth.length) * 100)
  const aiTrouble = providerHealth.find((p) => p.status !== 'ok')

  const runQuickAction = async (action) => {
    if (action.voice) {
      if (!speechSupported) {
        toast('Voice needs Chrome or Edge on this device.', 'error')
        return
      }
      setVoiceOpen(true)
      return
    }
    setPrefill(action.prefill)
    await newChat()
  }

  const askNow = async (e) => {
    e.preventDefault()
    const text = ask.trim()
    if (!text) return
    setAsk('')
    setPrefill(text)
    await newChat() // lands in the chat with the message ready — Enter sends it
  }

  return (
    <div className="command view-in">
      <div className="command-main">
        <section className="card glassy hero-panel">
          <h2>
            {timeGreeting()}, <span className="grad-text">{user.name.split(' ')[0]}</span>.
          </h2>
          <p className="hero-sub">I'm Sahayak, your AI assistant — ask, order, or just talk.</p>

          <div className="holo" aria-hidden="true">
            <span className="holo-ring r3" />
            <span className="holo-ring r2" />
            <span className="holo-ring r1" />
            <span className="holo-core" />
            <span className="holo-spark" />
            <span className="holo-spark s2" />
          </div>

          <div className="quick-strip">
            {QUICK_ACTIONS.map((a) => (
              <button key={a.title} className="quick-action" onClick={() => runQuickAction(a)}>
                <span className="quick-icon" aria-hidden="true">{a.icon}</span>
                <span>
                  <b>{a.title}</b>
                  <small>{a.sub}</small>
                </span>
              </button>
            ))}
          </div>

          <form className="dash-composer" onSubmit={askNow}>
            <input
              value={ask}
              onChange={(e) => setAsk(e.target.value)}
              placeholder="Type your message here…"
              aria-label="Ask Sahayak"
            />
            {speechSupported && (
              <button type="button" className="icon-btn" title="Voice mode" aria-label="Voice mode" onClick={() => setVoiceOpen(true)}>
                <MicIcon />
              </button>
            )}
            <button type="submit" className="btn" disabled={!ask.trim()} aria-label="Send">
              <SendIcon />
            </button>
          </form>
        </section>

        <section className="card">
          <div className="card-title">Latest activity</div>
          {activity.length === 0 && <p className="empty-line">Everything you and the agent do shows up here.</p>}
          {activity.map((a, i) => (
            <div
              key={i}
              className="row-item"
              onClick={() => (a.conversation ? openConversation(a.conversation) : nav('tasks'))}
            >
              <span aria-hidden="true">{a.icon}</span>
              <span className="row-text">{a.text}</span>
              <span className="row-time">{formatTime(a.at)}</span>
            </div>
          ))}
        </section>
      </div>

      <div className="command-rail">
        <section className="card">
          <div className="card-title">System status</div>
          <div className="sys-head">
            <RingGauge
              value={systemPct}
              size={54}
              stroke={4}
              label={`${healthyProviders}/${providerHealth.length || 1}`}
            />
            <div className="status-card-text">
              <b className={systemPct >= 50 ? 'ok-text' : 'bad-text'}>
                {!online ? 'OFFLINE' : systemPct === 100 ? 'ONLINE' : 'DEGRADED'}
              </b>
              <span>AI engines healthy</span>
            </div>
          </div>
          <div className="sys-row">
            <span className={`dot ${online ? 'ok' : 'bad'}`} />
            <span>API server</span>
            <b>{online ? `Online${latencyMs != null ? ` · ${latencyMs} ms` : ''}` : 'Unreachable'}</b>
          </div>
          <div className="sys-row">
            <span className={`dot ${sysInfo?.database === 'connected' ? 'ok' : 'bad'}`} />
            <span>Database</span>
            <b>{!online ? '—' : sysInfo?.database === 'connected' ? 'Connected' : 'Error'}</b>
          </div>
          {providerHealth.map((p) => {
            const [label, tone] = PROVIDER_STATUS[p.status] || ['Unknown', 'bad']
            return (
              <div className="sys-row" key={p.id} title={p.lastError?.message || ''}>
                <span className={`dot ${tone === 'ok' ? 'ok' : 'bad'}`} />
                <span>{p.label}</span>
                <b className={tone === 'warn' ? 'warn-text' : ''}>{label}</b>
              </div>
            )
          })}
          <div className="sys-row">
            <span className={`dot ${speechSupported ? 'ok' : 'bad'}`} />
            <span>Voice engine</span>
            <b>{speechSupported ? 'Ready' : 'Text only here'}</b>
          </div>
        </section>

        <section className="card">
          <div className="card-title">Today's overview</div>
          <div className="overview-row">
            <span className="overview-ico" aria-hidden="true">✅</span>
            <span>Tasks completed</span>
            <b>{done.length}</b>
          </div>
          <div className="overview-row">
            <span className="overview-ico" aria-hidden="true">⏳</span>
            <span>Pending tasks</span>
            <b>{pending.length}</b>
          </div>
          <div className="overview-row">
            <span className="overview-ico" aria-hidden="true">💬</span>
            <span>Conversations</span>
            <b>{conversations.length}</b>
          </div>
          <div className="overview-row">
            <span className="overview-ico" aria-hidden="true">🔌</span>
            <span>Connected apps</span>
            <b>{connections.length}</b>
          </div>
        </section>

        <section className="card">
          <div className="card-title">Upcoming</div>
          {upcoming.length === 0 && (
            <p className="empty-line">Nothing scheduled — try “remind yourself to say hello in 2 minutes”.</p>
          )}
          {upcoming.map((t) => (
            <div key={t.id} className="event-row" onClick={() => nav('calendar')} style={{ cursor: 'pointer' }}>
              <b>{t.instruction}</b>
              <span>{formatTime(t.runAt)}</span>
            </div>
          ))}
        </section>

        <section className="card">
          <div className="card-title">Quick nav</div>
          <div className="row-item" onClick={() => nav('integrations')}>
            <span aria-hidden="true">🔌</span>
            <span className="row-text">Connect your apps</span>
          </div>
          <div className="row-item" onClick={() => nav('tools')}>
            <span aria-hidden="true">🧰</span>
            <span className="row-text">See everything it can do</span>
          </div>
          <div className="row-item" onClick={() => nav('settings')}>
            <span aria-hidden="true">🎨</span>
            <span className="row-text">Theme, voice & wake word</span>
          </div>
        </section>
      </div>
    </div>
  )
}
