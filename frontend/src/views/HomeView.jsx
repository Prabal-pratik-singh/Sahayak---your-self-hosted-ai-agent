import { useEffect, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { ChatIcon, MicIcon, PlugIcon, SparkIcon } from '../components/Icons.jsx'
import { speechSupported } from '../hooks/useSpeech.js'

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

export default function HomeView() {
  const { user, tasks, conversations, nav, newChat, openConversation, setVoiceOpen, setPrefill } = useApp()
  const [connections, setConnections] = useState([])

  useEffect(() => {
    api('/connections').then(setConnections).catch(() => {})
  }, [])

  const pending = tasks.filter((t) => t.status === 'PENDING')
  const upcoming = [...pending].sort((a, b) => (a.runAt || '').localeCompare(b.runAt || '')).slice(0, 3)
  const recentChats = conversations.slice(0, 5)
  const recentResults = tasks
    .filter((t) => t.status === 'DONE' || t.status === 'FAILED')
    .sort((a, b) => (b.runAt || '').localeCompare(a.runAt || ''))
    .slice(0, 4)

  const askWeather = async () => {
    setPrefill("What's the weather here in ")
    const c = await newChat()
    if (!c) nav('chat')
  }

  return (
    <div className="home view-in">
      <div className="home-hero card glassy">
        <div>
          <h2>
            {timeGreeting()}, <span className="grad-text">{user.name.split(' ')[0]}</span>.
          </h2>
          <p>Your agent is standing by — ask, order, or just talk.</p>
        </div>
        <div className="hero-actions">
          <button className="btn" onClick={newChat}>
            <ChatIcon /> New chat
          </button>
          {speechSupported && (
            <button className="btn ghost" onClick={() => setVoiceOpen(true)}>
              <MicIcon /> Voice mode
            </button>
          )}
          <button className="btn ghost" onClick={askWeather}>
            <SparkIcon /> Ask the weather
          </button>
        </div>
      </div>

      <div className="stat-row">
        <button className="stat card" onClick={() => nav('tasks')}>
          <b>{pending.length}</b>
          <span>pending tasks</span>
        </button>
        <button className="stat card" onClick={() => nav('chat')}>
          <b>{conversations.length}</b>
          <span>conversations</span>
        </button>
        <button className="stat card" onClick={() => nav('integrations')}>
          <b>{connections.length}</b>
          <span>connected apps</span>
        </button>
      </div>

      <div className="home-grid">
        <section className="card">
          <div className="card-title">Coming up</div>
          {upcoming.length === 0 && (
            <p className="empty-line">
              Nothing scheduled. Try <i>“remind yourself to say hello in 2 minutes”.</i>
            </p>
          )}
          {upcoming.map((t) => (
            <div key={t.id} className="row-item" onClick={() => nav('calendar')}>
              <span className="row-time">{formatTime(t.runAt)}</span>
              <span className="row-text">{t.instruction}</span>
            </div>
          ))}
        </section>

        <section className="card">
          <div className="card-title">Recent conversations</div>
          {recentChats.length === 0 && <p className="empty-line">No chats yet.</p>}
          {recentChats.map((c) => (
            <div key={c.id} className="row-item" onClick={() => openConversation(c)}>
              <span className="row-text">{c.title}</span>
              <span className="row-time">{formatTime(c.updatedAt)}</span>
            </div>
          ))}
        </section>

        <section className="card">
          <div className="card-title">Latest results</div>
          {recentResults.length === 0 && <p className="empty-line">Task results will land here.</p>}
          {recentResults.map((t) => (
            <div key={t.id} className="row-item" onClick={() => nav('tasks')}>
              <span className={`dot ${t.status === 'DONE' ? 'ok' : 'bad'}`} />
              <span className="row-text">{t.instruction}</span>
            </div>
          ))}
        </section>

        <section className="card">
          <div className="card-title">Connected apps</div>
          {connections.length === 0 && (
            <p className="empty-line">
              Nothing connected yet — open <b>Integrations</b> to link email, LinkedIn, Telegram…
            </p>
          )}
          {connections.map((c) => (
            <div key={c.id} className="row-item" onClick={() => nav('integrations')}>
              <PlugIcon />
              <span className="row-text">
                {c.type} · {c.displayName}
              </span>
              <span className={`chip ${c.status === 'ACTIVE' ? 'ok' : 'bad'}`}>{c.status}</span>
            </div>
          ))}
        </section>
      </div>
    </div>
  )
}
