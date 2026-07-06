import { lazy, Suspense, useEffect, useMemo, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import RingGauge from '../components/RingGauge.jsx'
import { FileIcon, MicIcon, PaperclipIcon, SendIcon, XIcon } from '../components/Icons.jsx'
import { speechSupported } from '../hooks/useSpeech.js'
import { ACCEPT, formatSize, releaseStaged, stageFiles } from '../attachments.js'

// three.js is heavy (~250KB gz) — load the WebGL hero as its own async chunk so
// the dashboard shell paints instantly and the sphere streams in behind it.
const HeroSphere = lazy(() => import('../components/HeroSphere.jsx'))

// The command center: everything on this screen is real, live data —
// no decorative fake numbers.

const QUICK_ACTIONS = [
  { icon: '🌐', title: 'Search the web', sub: 'Wikipedia & live weather', prefill: 'Look up on Wikipedia: ' },
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

// "2m ago" style — for the activity feed.
function relativeTime(iso) {
  if (!iso) return ''
  const then = new Date(iso).getTime()
  if (Number.isNaN(then)) return ''
  const secs = Math.round((Date.now() - then) / 1000)
  if (secs < 60) return 'just now'
  const mins = Math.round(secs / 60)
  if (mins < 60) return `${mins}m ago`
  const hrs = Math.round(mins / 60)
  if (hrs < 24) return `${hrs}h ago`
  const days = Math.round(hrs / 24)
  return days < 30 ? `${days}d ago` : formatTime(iso)
}

const ACTION_ICONS = {
  linkedin: '💼',
  email: '✉️',
  telegram: '✈️',
  discord: '🎮',
  slack: '📢',
  github: '🐙',
  calendar: '📅',
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
    nav, newChat, openConversation, setVoiceOpen, setPrefill, setPrefillFiles, toast, settings,
  } = useApp()
  const [connections, setConnections] = useState([])
  const [actions, setActions] = useState([])
  const [ask, setAsk] = useState('')
  const [askFiles, setAskFiles] = useState([])
  const askFileInputRef = useRef(null)
  // Latest staged files for unmount cleanup (avoids a stale closure).
  const askFilesRef = useRef([])
  askFilesRef.current = askFiles

  // Free preview URLs if the user navigates away without sending.
  useEffect(() => () => releaseStaged(askFilesRef.current), [])

  useEffect(() => {
    api('/connections').then(setConnections).catch(() => {})
    api('/activity').then(setActions).catch(() => {})
  }, [])

  const pending = tasks.filter((t) => t.status === 'PENDING')
  const done = tasks.filter((t) => t.status === 'DONE')
  const upcoming = [...pending].sort((a, b) => (a.runAt || '').localeCompare(b.runAt || '')).slice(0, 3)

  const activity = useMemo(() => {
    const items = []
    // real actions Sahayak took (immediate OR scheduled) — the unified record
    for (const a of actions) {
      if (a.createdAt) items.push({ at: a.createdAt, icon: ACTION_ICONS[a.kind] || '⚡', text: a.text })
    }
    for (const t of tasks) {
      if ((t.status === 'DONE' || t.status === 'FAILED') && t.runAt) {
        items.push({ at: t.runAt, icon: t.status === 'DONE' ? '✅' : '⚠️', text: `Task #${t.id} ${t.status === 'DONE' ? 'finished' : 'failed'} — ${t.instruction}` })
      }
    }
    for (const c of conversations.slice(0, 6)) {
      if (c.updatedAt) items.push({ at: c.updatedAt, icon: '💬', text: `Chat "${c.title}"`, conversation: c })
    }
    return items.sort((a, b) => b.at.localeCompare(a.at)).slice(0, 6)
  }, [actions, tasks, conversations])

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

  const addAskFiles = (fileList) => {
    const accepted = stageFiles(fileList, askFiles, (msg) => toast(msg, 'error'))
    if (accepted.length) setAskFiles((c) => [...c, ...accepted])
  }

  const removeAskFile = (id) => {
    setAskFiles((c) => {
      const found = c.find((a) => a.id === id)
      if (found?.previewUrl) URL.revokeObjectURL(found.previewUrl)
      return c.filter((a) => a.id !== id)
    })
  }

  const askNow = async (e) => {
    e.preventDefault()
    const text = ask.trim()
    if (!text && !askFiles.length) return
    setAsk('')
    if (askFiles.length) {
      // Hand the raw File objects to the chat composer — it stages them with
      // its own previews, so release ours here.
      setPrefillFiles(askFiles.map((a) => a.file))
      releaseStaged(askFiles)
      setAskFiles([])
    }
    setPrefill(text)
    await newChat() // lands in the chat with message + attachments ready — Enter sends it
  }

  return (
    <div className="command view-in">
      <div className="command-main">
        <section className="card glassy hero-panel">
          <h2>
            {timeGreeting()}, <span className="grad-text">{user.name.split(' ')[0]}</span>.
          </h2>
          <p className="hero-sub">I'm Sahayak, your AI assistant — ask, order, or just talk.</p>

          <Suspense fallback={<div className="hero-sphere" />}>
            <HeroSphere accent={settings.accent} />
          </Suspense>

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

          {askFiles.length > 0 && (
            <div className="attach-strip dash">
              {askFiles.map((a) => (
                <div key={a.id} className={`attach-chip ${a.kind}`}>
                  {a.kind === 'image' ? (
                    <img src={a.previewUrl} alt={a.name} />
                  ) : (
                    <span className="attach-ico" aria-hidden="true"><FileIcon /></span>
                  )}
                  <span className="attach-meta">
                    <span className="attach-name">{a.name}</span>
                    <span className="attach-sub">{a.ext.toUpperCase()} · {formatSize(a.size)}</span>
                  </span>
                  <button
                    type="button"
                    className="attach-remove"
                    aria-label={`Remove ${a.name}`}
                    onClick={() => removeAskFile(a.id)}
                  >
                    <XIcon />
                  </button>
                </div>
              ))}
            </div>
          )}
          <form className="dash-composer" onSubmit={askNow}>
            <input
              value={ask}
              onChange={(e) => setAsk(e.target.value)}
              placeholder="Type your message here…"
              aria-label="Ask Sahayak"
            />
            <input
              ref={askFileInputRef}
              type="file"
              multiple
              accept={ACCEPT}
              style={{ display: 'none' }}
              onChange={(e) => {
                addAskFiles(e.target.files)
                e.target.value = ''
              }}
            />
            <button
              type="button"
              className="icon-btn"
              title="Attach images or documents"
              aria-label="Attach images or documents"
              onClick={() => askFileInputRef.current?.click()}
            >
              <PaperclipIcon />
            </button>
            {speechSupported && (
              <button type="button" className="icon-btn" title="Voice mode" aria-label="Voice mode" onClick={() => setVoiceOpen(true)}>
                <MicIcon />
              </button>
            )}
            <button type="submit" className="btn" disabled={!ask.trim() && !askFiles.length} aria-label="Send">
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
              <span className="row-time">{relativeTime(a.at)}</span>
            </div>
          ))}
        </section>
      </div>

      <div className="command-rail">
        <section className="card">
          <div className="card-title">AI Status</div>
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
              <span>
                {!online
                  ? 'Backend unreachable'
                  : systemPct === 100
                    ? 'All engines operational'
                    : 'Some engines need attention'}
              </span>
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
          {upcoming.length > 0 && (
            <div className="mini-timeline">
              {upcoming.map((t) => (
                <div key={t.id} className="mini-tl-item" onClick={() => nav('calendar')}>
                  <span className="mini-tl-dot" />
                  <span className="mini-tl-body">
                    <b>{t.instruction}</b>
                    <span className="mini-tl-time">{formatTime(t.runAt)}</span>
                  </span>
                </div>
              ))}
            </div>
          )}
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
