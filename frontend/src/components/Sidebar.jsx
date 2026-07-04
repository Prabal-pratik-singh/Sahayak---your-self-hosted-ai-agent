import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import RingGauge from './RingGauge.jsx'
import {
  HomeIcon, ChatIcon, MicIcon, TasksIcon, CalendarIcon, NotesIcon, ActivityIcon,
  PlugIcon, GridIcon, GearIcon, PlusIcon, PinIcon, TrashIcon, LogoutIcon,
} from './Icons.jsx'

// Nav items. Most switch the main view; `action: 'voice'` opens the hands-free
// voice overlay instead of navigating (it reuses the existing voice mode).
const NAV = [
  { id: 'home', label: 'Dashboard', icon: HomeIcon },
  { id: 'chat', label: 'AI Chat', icon: ChatIcon },
  { id: 'voice', label: 'Voice Assistant', icon: MicIcon, action: 'voice' },
  { id: 'tasks', label: 'Tasks', icon: TasksIcon },
  { id: 'calendar', label: 'Calendar', icon: CalendarIcon },
  { id: 'notes', label: 'Notes', icon: NotesIcon },
  { id: 'activity', label: 'Activity', icon: ActivityIcon },
  { id: 'integrations', label: 'Integrations', icon: PlugIcon },
  { id: 'tools', label: 'Tools', icon: GridIcon },
  { id: 'settings', label: 'Settings', icon: GearIcon },
]

const STATUS_WORD = { ok: 'healthy', limited: 'rate-limited', error: 'key problem', down: 'trouble' }

export default function Sidebar() {
  const {
    user, logout, view, nav, conversations, refreshConversations,
    activeConversation, openConversation, newChat, sidebarOpen, toast,
    online, providerHealth, setVoiceOpen,
  } = useApp()
  const [collapsed, setCollapsed] = useState(() => localStorage.getItem('sahayak_sidebar') === 'collapsed')

  const toggleCollapse = () => {
    setCollapsed((c) => {
      localStorage.setItem('sahayak_sidebar', c ? 'open' : 'collapsed')
      return !c
    })
  }

  const healthy = providerHealth.filter((p) => p.status === 'ok').length
  const systemPct = !online ? 0 : providerHealth.length === 0 ? 100 : Math.round((healthy / providerHealth.length) * 100)

  // Full detail preserved on hover, so collapsing the per-service list into one
  // gauge loses nothing (prompt requirement).
  const statusDetail = [
    `API server: ${online ? 'online' : 'offline'}`,
    ...providerHealth.map((p) => `${p.label}: ${STATUS_WORD[p.status] || p.status}`),
  ].join('\n')

  const handleNav = (item) => {
    if (item.action === 'voice') setVoiceOpen(true)
    else nav(item.id)
  }

  const togglePin = async (e, conversation) => {
    e.stopPropagation()
    try {
      await api(`/conversations/${conversation.id}`, { method: 'PATCH', body: { pinned: !conversation.pinned } })
      refreshConversations()
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  const remove = async (e, conversation) => {
    e.stopPropagation()
    try {
      await api(`/conversations/${conversation.id}`, { method: 'DELETE' })
      if (activeConversation?.id === conversation.id) openConversation(null)
      refreshConversations()
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  return (
    <aside className={`sidebar ${sidebarOpen ? 'open' : ''} ${collapsed ? 'collapsed' : ''}`}>
      <button
        className="icon-btn side-collapse"
        title={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'}
        onClick={toggleCollapse}
      >
        {collapsed ? '›' : '‹'}
      </button>

      <div className="side-brand">
        <span className="orb" aria-hidden="true" />
        <span className="brand-text">
          <span className="wordmark">Sahayak</span>
          <span className="brand-sub">AI ASSISTANT</span>
        </span>
      </div>

      <button className="btn new-chat" title="New chat" onClick={newChat}>
        <PlusIcon /> <span>New chat</span>
      </button>

      <div className="side-scroll">
        <nav className="side-nav">
        {NAV.map((item) => {
          const Icon = item.icon
          const isActive = item.action !== 'voice' && view === item.id
          return (
            <button
              key={item.id}
              className={`nav-item ${isActive ? 'active' : ''}`}
              title={item.label}
              onClick={() => handleNav(item)}
            >
              <Icon />
              <span>{item.label}</span>
            </button>
          )
        })}
      </nav>

      <div className="side-section-title">Conversations</div>
      <div className="conv-list">
        {conversations.length === 0 && <p className="side-empty">Nothing yet — start a chat.</p>}
        {conversations.map((c) => (
          <div
            key={c.id}
            className={`conv-item ${activeConversation?.id === c.id && view === 'chat' ? 'active' : ''}`}
            onClick={() => openConversation(c)}
            title={c.title}
          >
            {c.pinned && <span className="conv-pin-dot" aria-label="pinned" />}
            <span className="conv-title">{c.title}</span>
            <span className="conv-actions">
              <button title={c.pinned ? 'Unpin' : 'Pin'} onClick={(e) => togglePin(e, c)}>
                <PinIcon filled={c.pinned} />
              </button>
              <button title="Delete chat" onClick={(e) => remove(e, c)}>
                <TrashIcon />
              </button>
            </span>
          </div>
        ))}
        </div>
      </div>

      <div className="side-status" title={statusDetail}>
        <RingGauge value={systemPct} size={46} stroke={4} label={`${healthy}/${providerHealth.length || 1}`} />
        <div className="side-status-text">
          <b className={systemPct >= 50 ? '' : 'bad-text'}>SYSTEM STATUS</b>
          <span>
            {!online ? 'Backend offline' : systemPct === 100 ? 'All systems operational' : 'Some engines need attention'}
          </span>
        </div>
      </div>

      <div className="side-footer">
        <div className="side-user" title={user.email}>
          <span className="avatar">
            {(user.name || '?').slice(0, 1).toUpperCase()}
            <span className={`avatar-dot ${online ? 'on' : 'off'}`} aria-hidden="true" />
          </span>
          <span className="side-user-text">
            <span className="side-user-name">{user.name}</span>
            <span className="side-user-role">{online ? 'Online' : 'Offline'}</span>
          </span>
        </div>
        <button className="icon-btn" title="Log out" aria-label="Log out" onClick={logout}>
          <LogoutIcon />
        </button>
      </div>
      <nav className="side-mini-links" aria-label="About this project">
        <Link to="/about">About</Link>
        <Link to="/services">Services</Link>
        <Link to="/contact">Contact</Link>
      </nav>
    </aside>
  )
}
