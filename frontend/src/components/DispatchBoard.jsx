function formatTime(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString(undefined, {
    day: '2-digit',
    month: 'short',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false,
  })
}

export default function DispatchBoard({ tasks, onCancel }) {
  const pending = tasks.filter((t) => t.status === 'PENDING').length
  return (
    <aside className="board">
      <div className="board-head">
        <span className="board-title">Dispatch board</span>
        <span className="board-count">
          {pending} pending / {tasks.length} total
        </span>
      </div>

      {tasks.length === 0 && (
        <p className="board-empty">
          Nothing scheduled. Ask for something &ldquo;tomorrow at 6 PM&rdquo; and it lands here.
        </p>
      )}

      <ul className="task-list">
        {tasks.map((t) => (
          <li key={t.id} className={`task status-${t.status.toLowerCase()}`}>
            <div className="task-top">
              <span className="task-time">{formatTime(t.runAt)}</span>
              {t.provider && <span className="task-provider">{t.provider}</span>}
              <span className="task-stamp">{t.status}</span>
              {t.status === 'PENDING' && (
                <button
                  className="task-cancel"
                  title="Cancel this task"
                  onClick={() => onCancel(t.id)}
                >
                  ×
                </button>
              )}
            </div>
            <p className="task-instruction">{t.instruction}</p>
            {t.result && <p className="task-result">{t.result}</p>}
          </li>
        ))}
      </ul>
    </aside>
  )
}
