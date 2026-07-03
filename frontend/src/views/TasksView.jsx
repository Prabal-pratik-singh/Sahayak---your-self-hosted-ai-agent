import { useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { XIcon } from '../components/Icons.jsx'

const FILTERS = ['ALL', 'PENDING', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED']

function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  return Number.isNaN(d.getTime())
    ? iso
    : d.toLocaleString(undefined, { day: '2-digit', month: 'short', hour: '2-digit', minute: '2-digit', hour12: false })
}

export default function TasksView() {
  const { tasks, refreshTasks, toast, setPrefill, newChat } = useApp()
  const [filter, setFilter] = useState('ALL')

  const cancel = async (id) => {
    try {
      await api(`/tasks/${id}`, { method: 'DELETE' })
      refreshTasks()
      toast(`Task #${id} cancelled`, 'ok')
    } catch (e) {
      toast(e.message, 'error')
    }
  }

  const scheduleSomething = async () => {
    setPrefill('Schedule this for tomorrow 9 AM: ')
    await newChat()
  }

  const visible = tasks.filter((t) => filter === 'ALL' || t.status === filter)

  return (
    <div className="tasks view-in">
      <div className="toolbar">
        <div className="chip-row">
          {FILTERS.map((f) => (
            <button key={f} className={`chip clickable ${filter === f ? 'active' : ''}`} onClick={() => setFilter(f)}>
              {f.toLowerCase()}
              {f !== 'ALL' && <b> {tasks.filter((t) => t.status === f).length}</b>}
            </button>
          ))}
        </div>
        <button className="btn ghost" onClick={scheduleSomething}>
          + Schedule via chat
        </button>
      </div>

      {visible.length === 0 && (
        <div className="card empty-card">
          Nothing here. Ask the agent for something “tomorrow at 6 PM” and it lands on this board.
        </div>
      )}

      <div className="task-grid">
        {visible.map((t) => (
          <div key={t.id} className={`card task-card status-${t.status.toLowerCase()}`}>
            <div className="task-card-top">
              <span className="task-time">{formatTime(t.runAt)}</span>
              {t.provider && <span className="tag">{t.provider}</span>}
              <span className="task-stamp">{t.status}</span>
              {t.status === 'PENDING' && (
                <button className="icon-btn tiny" title="Cancel task" onClick={() => cancel(t.id)}>
                  <XIcon />
                </button>
              )}
            </div>
            <p className="task-text">{t.instruction}</p>
            {t.result && <p className="task-result">{t.result}</p>}
          </div>
        ))}
      </div>
    </div>
  )
}
