import { useEffect, useMemo, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'

// A day-grouped timeline of what the agent did: real actions it performed
// (posts, emails, messages), tasks scheduled / finished / failed, plus
// conversations that moved.

const ACTION_ICONS = { linkedin: '💼', email: '✉️', telegram: '✈️', discord: '🎮', slack: '📢', github: '🐙', calendar: '📅' }

function dayLabel(iso) {
  const d = new Date(iso)
  const today = new Date()
  const yesterday = new Date(today)
  yesterday.setDate(today.getDate() - 1)
  const k = (x) => x.toDateString()
  if (k(d) === k(today)) return 'Today'
  if (k(d) === k(yesterday)) return 'Yesterday'
  return d.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })
}

export default function ActivityView() {
  const { tasks, conversations, openConversation } = useApp()
  const [actions, setActions] = useState([])

  useEffect(() => {
    api('/activity').then(setActions).catch(() => {})
  }, [])

  const events = useMemo(() => {
    const list = []
    for (const a of actions) {
      if (a.createdAt) {
        list.push({ at: a.createdAt, kind: 'action', text: `${ACTION_ICONS[a.kind] || '⚡'} ${a.text}` })
      }
    }
    for (const t of tasks) {
      if (t.createdAt) {
        list.push({ at: t.createdAt, kind: 'scheduled', text: `Task #${t.id} scheduled — ${t.instruction}` })
      }
      if ((t.status === 'DONE' || t.status === 'FAILED') && t.runAt) {
        list.push({
          at: t.runAt,
          kind: t.status === 'DONE' ? 'done' : 'failed',
          text: `Task #${t.id} ${t.status === 'DONE' ? 'finished' : 'failed'} — ${t.instruction}`,
        })
      }
    }
    for (const c of conversations) {
      if (c.updatedAt) {
        list.push({ at: c.updatedAt, kind: 'chat', text: `Chat “${c.title}”`, conversation: c })
      }
    }
    return list.sort((a, b) => b.at.localeCompare(a.at)).slice(0, 60)
  }, [actions, tasks, conversations])

  const groups = useMemo(() => {
    const out = []
    let currentDay = null
    for (const e of events) {
      const day = e.at.slice(0, 10)
      if (day !== currentDay) {
        currentDay = day
        out.push({ day, label: dayLabel(e.at), items: [] })
      }
      out[out.length - 1].items.push(e)
    }
    return out
  }, [events])

  return (
    <div className="activity view-in">
      {groups.length === 0 && <div className="card empty-card">No activity yet — everything you and the agent do shows up here.</div>}
      {groups.map((g) => (
        <section key={g.day} className="timeline-day">
          <h3>{g.label}</h3>
          <div className="timeline">
            {g.items.map((e, i) => (
              <div
                key={i}
                className={`timeline-item ${e.conversation ? 'clickable' : ''}`}
                onClick={() => e.conversation && openConversation(e.conversation)}
              >
                <span className={`timeline-dot ${e.kind}`} />
                <span className="timeline-text">{e.text}</span>
                <span className="timeline-time">{e.at.slice(11, 16)}</span>
              </div>
            ))}
          </div>
        </section>
      ))}
    </div>
  )
}
