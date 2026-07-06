import { useEffect, useMemo, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { speechSupported } from '../hooks/useSpeech.js'
import { BellIcon, MenuIcon, MicIcon, MoonIcon, SearchIcon, SparkIcon, SunIcon } from './Icons.jsx'

const TITLES = {
  home: ['', 'How can I assist you today?'],
  chat: ['AI Chat', 'Talk to Sahayak'],
  tasks: ['Tasks', 'Everything scheduled and done'],
  calendar: ['Calendar', 'Scheduled work by day'],
  notes: ['Notes', 'What Sahayak remembers about you'],
  activity: ['Activity', 'What happened recently'],
  integrations: ['Integrations', 'Connect your apps'],
  tools: ['Tools', 'What Sahayak can do'],
  settings: ['Settings', 'Make it yours'],
}

/** Live HH:MM:SS — the heartbeat of the command center. */
function Clock() {
  const [now, setNow] = useState(() => new Date())
  useEffect(() => {
    const id = setInterval(() => setNow(new Date()), 1000)
    return () => clearInterval(id)
  }, [])
  return (
    <div className="clock" aria-hidden="true">
      <span className="clock-time">{now.toLocaleTimeString(undefined, { hour12: false })}</span>
      <span className="clock-date">
        {now.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'short' })}
      </span>
    </div>
  )
}

const SEEN_KEY = 'sahayak_notif_seen'

const STATUS_LABELS = {
  ok: 'healthy',
  limited: 'rate-limited',
  error: 'key problem',
  down: 'having trouble',
}

export default function Topbar() {
  const { view, user, online, tasks, settings, models, providerHealth, refreshHealth, setPaletteOpen, setVoiceOpen, setSidebarOpen, nav } = useApp()
  const [bellOpen, setBellOpen] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)
  const [seenAt, setSeenAt] = useState(() => localStorage.getItem(SEEN_KEY) || '')
  const bellRef = useRef(null)
  const aiRef = useRef(null)
  const [title, subtitle] = TITLES[view] || TITLES.home
  const aiIssues = providerHealth.filter((p) => p.status !== 'ok').length

  // Finished/failed tasks double as notifications.
  const finished = useMemo(
    () =>
      tasks
        .filter((t) => t.status === 'DONE' || t.status === 'FAILED')
        .sort((a, b) => (b.runAt || '').localeCompare(a.runAt || ''))
        .slice(0, 8),
    [tasks],
  )
  const unseen = finished.filter((t) => (t.runAt || '') > seenAt).length

  const openBell = () => {
    setBellOpen((v) => !v)
    const now = new Date().toISOString().slice(0, 19)
    localStorage.setItem(SEEN_KEY, now)
    setSeenAt(now)
  }

  useEffect(() => {
    const close = (e) => {
      if (bellRef.current && !bellRef.current.contains(e.target)) setBellOpen(false)
      if (aiRef.current && !aiRef.current.contains(e.target)) setAiOpen(false)
    }
    document.addEventListener('mousedown', close)
    return () => document.removeEventListener('mousedown', close)
  }, [])

  const nextTheme = settings.theme === 'light' ? 'dark' : 'light'

  return (
    <header className="topbar">
      <button className="icon-btn only-mobile" title="Menu" aria-label="Open menu" onClick={() => setSidebarOpen(true)}>
        <MenuIcon />
      </button>

      <Clock />

      <div className="top-title">
        <h1>
          {view === 'home' ? (
            <>
              Welcome back, <span className="grad-text">{user.name.split(' ')[0]}</span>
            </>
          ) : (
            title
          )}
        </h1>
        <p>{subtitle}</p>
      </div>

      <div className="top-actions">
        <span className={`presence ${online ? 'on' : 'off'}`} title={online ? 'Backend online' : 'Backend offline'} />

        {models && models.options.length > 1 && (
          <label className="brain-switch" title="Which AI answers your messages">
            <span className="brain-dot" aria-hidden="true" />
            <select
              aria-label="Choose AI engine"
              value={settings.defaultProvider || models.defaultId}
              onChange={(e) => settings.set({ defaultProvider: e.target.value })}
            >
              {models.options.map((o) => (
                <option key={o.id} value={o.id}>{o.label}</option>
              ))}
            </select>
          </label>
        )}

        <button className="search-pill" onClick={() => setPaletteOpen(true)}>
          <SearchIcon />
          <span>Search…</span>
          <kbd>Ctrl K</kbd>
        </button>

        <div className="bell-wrap" ref={aiRef}>
          <button
            className="icon-btn"
            title="AI provider status"
            aria-label={`AI provider status${aiIssues ? `, ${aiIssues} with issues` : ', all healthy'}`}
            onClick={() => {
              setAiOpen((v) => !v)
              refreshHealth()
            }}
          >
            <SparkIcon />
            {aiIssues > 0 && <span className="badge">{aiIssues}</span>}
          </button>
          {aiOpen && (
            <div className="popover">
              <div className="popover-title">AI providers</div>
              {providerHealth.length === 0 && <p className="popover-empty">No providers configured.</p>}
              {providerHealth.map((p) => (
                <div key={p.id} className="ph-row">
                  <div className="ph-head">
                    <b>{p.label}</b>
                    <span className="tag">{p.source === 'your key' ? 'your key' : p.model}</span>
                    <span className={`chip ${p.status === 'ok' ? 'ok' : p.status === 'limited' ? 'warn' : 'bad'}`}>
                      {STATUS_LABELS[p.status] || p.status}
                    </span>
                  </div>
                  <div className="ph-meta">
                    {p.totalRequests} calls · {p.totalFailures} failed
                    {p.consecutiveFailures > 1 ? ` · ${p.consecutiveFailures} in a row` : ''}
                  </div>
                  {p.status !== 'ok' && p.lastError && <p className="ph-error">{p.lastError.message}</p>}
                </div>
              ))}
            </div>
          )}
        </div>

        {speechSupported && (
          <button className="icon-btn" title="Voice mode" aria-label="Open voice mode" onClick={() => setVoiceOpen(true)}>
            <MicIcon />
          </button>
        )}

        <div className="bell-wrap" ref={bellRef}>
          <button className="icon-btn" title="Notifications" aria-label={`Notifications${unseen ? `, ${unseen} unread` : ''}`} onClick={openBell}>
            <BellIcon />
            {unseen > 0 && <span className="badge">{unseen}</span>}
          </button>
          {bellOpen && (
            <div className="popover">
              <div className="popover-title">Recent task results</div>
              {finished.length === 0 && <p className="popover-empty">Nothing yet.</p>}
              {finished.map((t) => (
                <button
                  key={t.id}
                  className="notif"
                  onClick={() => {
                    setBellOpen(false)
                    nav('tasks')
                  }}
                >
                  <span className={`notif-dot ${t.status === 'DONE' ? 'ok' : 'bad'}`} />
                  <span className="notif-text">
                    <b>Task #{t.id} {t.status === 'DONE' ? 'finished' : 'failed'}</b>
                    <span>{t.instruction}</span>
                  </span>
                </button>
              ))}
            </div>
          )}
        </div>

        <button
          className="icon-btn"
          title={`Switch to ${nextTheme} mode`}
          aria-label={`Switch to ${nextTheme} mode`}
          onClick={() => settings.set({ theme: nextTheme })}
        >
          {settings.theme === 'light' ? <MoonIcon /> : <SunIcon />}
        </button>
      </div>
    </header>
  )
}
