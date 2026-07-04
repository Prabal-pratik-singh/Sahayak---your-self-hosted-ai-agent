import { useEffect, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { NotesIcon, PlusIcon, TrashIcon } from '../components/Icons.jsx'

// The user-facing window into the agent's long-term memory: the same notes it
// saves when you say "remember…" and reads on every message. Real data, no mock.

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
  if (days < 30) return `${days}d ago`
  return new Date(iso).toLocaleDateString(undefined, { day: '2-digit', month: 'short', year: 'numeric' })
}

export default function NotesView() {
  const { toast } = useApp()
  const [notes, setNotes] = useState(null)
  const [draft, setDraft] = useState('')
  const [busy, setBusy] = useState(false)

  const load = () => api('/notes').then(setNotes).catch((e) => toast(e.message, 'error'))
  useEffect(() => {
    load()
  }, []) // eslint-disable-line react-hooks/exhaustive-deps

  const add = async (e) => {
    e.preventDefault()
    const content = draft.trim()
    if (!content || busy) return
    setBusy(true)
    try {
      await api('/notes', { method: 'POST', body: { content } })
      setDraft('')
      toast('Note saved ✓', 'ok')
      load()
    } catch (err) {
      toast(err.message, 'error')
    } finally {
      setBusy(false)
    }
  }

  const remove = async (id) => {
    try {
      await api(`/notes/${id}`, { method: 'DELETE' })
      setNotes((n) => n.filter((x) => x.id !== id))
    } catch (err) {
      toast(err.message, 'error')
    }
  }

  return (
    <div className="notes-view view-in">
      <section className="card">
        <div className="card-title">What Sahayak remembers about you</div>
        <p className="hint">
          These notes are the agent's long-term memory — it uses them to personalize every reply.
          It adds them automatically when you say “remember…”, and you can add or remove them here.
        </p>
        <form className="note-add" onSubmit={add}>
          <input
            className="input"
            value={draft}
            maxLength={500}
            placeholder="e.g. I prefer short, direct answers"
            onChange={(e) => setDraft(e.target.value)}
            aria-label="New note"
          />
          <button className="btn" disabled={busy || !draft.trim()}>
            <PlusIcon /> <span>Add</span>
          </button>
        </form>
      </section>

      {notes === null && <p className="empty-line">Loading…</p>}

      {notes !== null && notes.length === 0 && (
        <div className="card empty-card">
          <NotesIcon />
          <p>No memories yet. Tell Sahayak “remember that…” in a chat, or add one above.</p>
        </div>
      )}

      {notes && notes.length > 0 && (
        <div className="notes-grid">
          {notes.map((n) => (
            <div key={n.id} className="card note-card">
              <p className="note-text">{n.content}</p>
              <div className="note-foot">
                <span className="note-time">{relativeTime(n.createdAt)}</span>
                <button className="icon-btn tiny" title="Forget this" aria-label="Delete note" onClick={() => remove(n.id)}>
                  <TrashIcon />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
