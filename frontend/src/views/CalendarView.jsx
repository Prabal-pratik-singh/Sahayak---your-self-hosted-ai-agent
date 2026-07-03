import { useMemo, useState } from 'react'
import { useApp } from '../App.jsx'

// Scheduled tasks laid out on a month grid — click a day to see its tasks.

const DAY_NAMES = ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun']

function keyOf(date) {
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export default function CalendarView() {
  const { tasks } = useApp()
  const [cursor, setCursor] = useState(() => {
    const now = new Date()
    return new Date(now.getFullYear(), now.getMonth(), 1)
  })
  const [selected, setSelected] = useState(() => keyOf(new Date()))

  const byDay = useMemo(() => {
    const map = {}
    for (const t of tasks) {
      if (!t.runAt) continue
      const day = t.runAt.slice(0, 10)
      ;(map[day] = map[day] || []).push(t)
    }
    for (const day of Object.keys(map)) {
      map[day].sort((a, b) => a.runAt.localeCompare(b.runAt))
    }
    return map
  }, [tasks])

  const weeks = useMemo(() => {
    const first = new Date(cursor)
    const start = new Date(first)
    // back up to Monday
    start.setDate(first.getDate() - ((first.getDay() + 6) % 7))
    const cells = []
    const d = new Date(start)
    for (let i = 0; i < 42; i++) {
      cells.push(new Date(d))
      d.setDate(d.getDate() + 1)
    }
    const out = []
    for (let i = 0; i < 6; i++) out.push(cells.slice(i * 7, i * 7 + 7))
    return out
  }, [cursor])

  const monthLabel = cursor.toLocaleString(undefined, { month: 'long', year: 'numeric' })
  const todayKey = keyOf(new Date())
  const dayTasks = byDay[selected] || []
  const upcoming = tasks
    .filter((t) => t.status === 'PENDING')
    .sort((a, b) => (a.runAt || '').localeCompare(b.runAt || ''))
    .slice(0, 6)

  return (
    <div className="calendar view-in">
      <div className="card cal-card">
        <div className="cal-head">
          <button className="icon-btn" onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() - 1, 1))}>
            ‹
          </button>
          <h3>{monthLabel}</h3>
          <button className="icon-btn" onClick={() => setCursor(new Date(cursor.getFullYear(), cursor.getMonth() + 1, 1))}>
            ›
          </button>
        </div>
        <div className="cal-grid">
          {DAY_NAMES.map((d) => (
            <span key={d} className="cal-dow">
              {d}
            </span>
          ))}
          {weeks.flat().map((date) => {
            const k = keyOf(date)
            const inMonth = date.getMonth() === cursor.getMonth()
            const count = (byDay[k] || []).length
            return (
              <button
                key={k}
                className={`cal-day ${inMonth ? '' : 'faded'} ${k === todayKey ? 'today' : ''} ${k === selected ? 'selected' : ''}`}
                onClick={() => setSelected(k)}
              >
                {date.getDate()}
                {count > 0 && <span className="cal-dot">{count}</span>}
              </button>
            )
          })}
        </div>
      </div>

      <div className="cal-side">
        <div className="card">
          <div className="card-title">
            {new Date(selected + 'T00:00:00').toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
          </div>
          {dayTasks.length === 0 && <p className="empty-line">Nothing scheduled this day.</p>}
          {dayTasks.map((t) => (
            <div key={t.id} className="row-item">
              <span className="row-time">{t.runAt.slice(11, 16)}</span>
              <span className="row-text">{t.instruction}</span>
              <span className={`chip ${t.status === 'PENDING' ? '' : t.status === 'DONE' ? 'ok' : 'bad'}`}>
                {t.status.toLowerCase()}
              </span>
            </div>
          ))}
        </div>

        <div className="card">
          <div className="card-title">Upcoming reminders</div>
          {upcoming.length === 0 && <p className="empty-line">No pending reminders.</p>}
          {upcoming.map((t) => (
            <div key={t.id} className="row-item">
              <span className="row-time">{t.runAt?.slice(5, 16).replace('T', ' ')}</span>
              <span className="row-text">{t.instruction}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
