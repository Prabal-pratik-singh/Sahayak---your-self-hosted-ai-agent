import { useEffect, useMemo, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { SearchIcon } from './Icons.jsx'

// Ctrl/Cmd+K — search actions, conversations (server-side, full text) and tasks.

export default function CommandPalette({ onClose }) {
  const { nav, newChat, openConversation, setVoiceOpen, settings, tasks } = useApp()
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState([])
  const [active, setActive] = useState(0)
  const inputRef = useRef(null)

  useEffect(() => inputRef.current?.focus(), [])

  // debounce backend chat search
  useEffect(() => {
    const q = query.trim()
    if (q.length < 2) {
      setHits([])
      return
    }
    const t = setTimeout(() => {
      api(`/conversations/search?q=${encodeURIComponent(q)}`).then(setHits).catch(() => setHits([]))
    }, 250)
    return () => clearTimeout(t)
  }, [query])

  const items = useMemo(() => {
    const q = query.trim().toLowerCase()
    const actions = [
      { kind: 'action', label: 'New chat', run: () => newChat() },
      { kind: 'action', label: 'Voice mode', run: () => setVoiceOpen(true) },
      {
        kind: 'action',
        label: `Switch to ${settings.theme === 'light' ? 'dark' : 'light'} mode`,
        run: () => settings.set({ theme: settings.theme === 'light' ? 'dark' : 'light' }),
      },
      ...['home', 'chat', 'tasks', 'calendar', 'activity', 'integrations', 'tools', 'settings'].map((v) => ({
        kind: 'action',
        label: `Go to ${v[0].toUpperCase() + v.slice(1)}`,
        run: () => nav(v),
      })),
    ].filter((a) => !q || a.label.toLowerCase().includes(q))

    const chatHits = hits.map((h) => ({
      kind: 'chat',
      label: h.title,
      detail: h.snippet,
      run: () => openConversation({ id: h.conversationId, title: h.title }),
    }))

    const taskHits = q
      ? tasks
          .filter((t) => t.instruction.toLowerCase().includes(q))
          .slice(0, 5)
          .map((t) => ({
            kind: 'task',
            label: `Task #${t.id} · ${t.status}`,
            detail: t.instruction,
            run: () => nav('tasks'),
          }))
      : []

    return [...actions.slice(0, 6), ...chatHits, ...taskHits]
  }, [query, hits, tasks, settings, nav, newChat, openConversation, setVoiceOpen])

  useEffect(() => setActive(0), [query, items.length])

  const run = (item) => {
    onClose()
    item.run()
  }

  const onKeyDown = (e) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault()
      setActive((a) => Math.min(a + 1, items.length - 1))
    } else if (e.key === 'ArrowUp') {
      e.preventDefault()
      setActive((a) => Math.max(a - 1, 0))
    } else if (e.key === 'Enter' && items[active]) {
      e.preventDefault()
      run(items[active])
    }
  }

  return (
    <div className="overlay center" onClick={onClose}>
      <div className="palette card" onClick={(e) => e.stopPropagation()}>
        <div className="palette-input">
          <SearchIcon />
          <input
            ref={inputRef}
            value={query}
            placeholder="Search chats, tasks, actions…"
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onKeyDown}
          />
          <kbd>esc</kbd>
        </div>
        <div className="palette-list">
          {items.length === 0 && <p className="palette-empty">No matches.</p>}
          {items.map((item, i) => (
            <button
              key={`${item.kind}-${item.label}-${i}`}
              className={`palette-item ${i === active ? 'active' : ''}`}
              onMouseEnter={() => setActive(i)}
              onClick={() => run(item)}
            >
              <span className={`palette-kind ${item.kind}`}>{item.kind}</span>
              <span className="palette-label">
                {item.label}
                {item.detail && <small>{item.detail}</small>}
              </span>
            </button>
          ))}
        </div>
      </div>
    </div>
  )
}
