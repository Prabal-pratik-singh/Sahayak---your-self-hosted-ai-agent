import { Link } from 'react-router-dom'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import {
  HomeIcon, ChatIcon, TasksIcon, CalendarIcon, ActivityIcon,
  PlugIcon, GridIcon, GearIcon, PlusIcon, PinIcon, TrashIcon, LogoutIcon,
} from './Icons.jsx'

const NAV = [
  { id: 'home', label: 'Home', icon: HomeIcon },
  { id: 'chat', label: 'Chat', icon: ChatIcon },
  { id: 'tasks', label: 'Tasks', icon: TasksIcon },
  { id: 'calendar', label: 'Calendar', icon: CalendarIcon },
  { id: 'activity', label: 'Activity', icon: ActivityIcon },
  { id: 'integrations', label: 'Integrations', icon: PlugIcon },
  { id: 'tools', label: 'Tools', icon: GridIcon },
  { id: 'settings', label: 'Settings', icon: GearIcon },
]

export default function Sidebar() {
  const {
    user, logout, view, nav, conversations, refreshConversations,
    activeConversation, openConversation, newChat, sidebarOpen, toast,
  } = useApp()

  const togglePin = async (e, conversation) => {
    e.stopPropagation()
    try {
      await api(`/conversations/${conversation.id}`, {
        method: 'PATCH',
        body: { pinned: !conversation.pinned },
      })
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
    <aside className={`sidebar ${sidebarOpen ? 'open' : ''}`}>
      <div className="side-brand">
        <span className="orb" aria-hidden="true" />
        <span className="wordmark">Sahayak</span>
      </div>

      <button className="btn new-chat" onClick={newChat}>
        <PlusIcon /> New chat
      </button>

      <nav className="side-nav">
        {NAV.map(({ id, label, icon: Icon }) => (
          <button
            key={id}
            className={`nav-item ${view === id ? 'active' : ''}`}
            onClick={() => nav(id)}
          >
            <Icon />
            <span>{label}</span>
          </button>
        ))}
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

      <div className="side-footer">
        <div className="side-user" title={user.email}>
          <span className="avatar">{(user.name || '?').slice(0, 1).toUpperCase()}</span>
          <span className="side-user-name">{user.name}</span>
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
