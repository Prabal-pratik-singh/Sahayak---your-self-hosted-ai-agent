import { useEffect, useState } from 'react'
import { api, tokenStore } from './api.js'
import Login from './components/Login.jsx'
import Chat from './components/Chat.jsx'
import DispatchBoard from './components/DispatchBoard.jsx'
import Connections from './components/Connections.jsx'

export default function App() {
  const [user, setUser] = useState(undefined) // undefined = checking token, null = logged out
  const [tasks, setTasks] = useState([])
  const [online, setOnline] = useState(false)
  const [boardOpen, setBoardOpen] = useState(false)
  const [connectionsOpen, setConnectionsOpen] = useState(false)
  const [notice, setNotice] = useState('')

  // Restore the session on page load, and log out if the backend rejects the token.
  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener('sahayak:unauthorized', onUnauthorized)
    if (!tokenStore.get()) {
      setUser(null)
    } else {
      api('/auth/me')
        .then(setUser)
        .catch(() => setUser(null))
    }
    return () => window.removeEventListener('sahayak:unauthorized', onUnauthorized)
  }, [])

  // After the LinkedIn redirect, the URL carries ?connected=... or ?error=...
  useEffect(() => {
    if (!user) return
    const params = new URLSearchParams(window.location.search)
    if (params.get('connected') === 'linkedin') {
      setNotice('LinkedIn connected ✓')
      setConnectionsOpen(true)
    } else if (params.get('error')) {
      setNotice(params.get('error'))
      setConnectionsOpen(true)
    }
    if ([...params.keys()].length > 0) {
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [user])

  const loadTasks = async () => {
    try {
      setTasks(await api('/tasks'))
      setOnline(true)
    } catch {
      setOnline(false)
    }
  }

  useEffect(() => {
    if (!user) return
    loadTasks()
    const id = setInterval(loadTasks, 8000)
    return () => clearInterval(id)
  }, [user])

  const cancelTask = async (id) => {
    try {
      await api(`/tasks/${id}`, { method: 'DELETE' })
    } catch {
      /* board refresh below shows the truth */
    } finally {
      loadTasks()
    }
  }

  const logout = async () => {
    try {
      await api('/auth/logout', { method: 'POST' })
    } catch {
      /* token is cleared regardless */
    }
    tokenStore.clear()
    setUser(null)
  }

  if (user === undefined) return null
  if (!user) return <Login onLogin={setUser} />

  const pendingCount = tasks.filter((t) => t.status === 'PENDING').length

  return (
    <div className="shell">
      <header className="topbar">
        <div className="brand">
          <span className="orb" aria-hidden="true" />
          <span className="wordmark">Sahayak</span>
          <span className="tagline">personal agent</span>
        </div>
        <div className="topbar-right">
          <span className={`status ${online ? 'on' : 'off'}`}>
            <span className="dot" />
            {online ? 'backend online' : 'backend offline'}
          </span>
          <button className="board-toggle always" onClick={() => setConnectionsOpen(true)}>
            Connections
          </button>
          <button className="board-toggle" onClick={() => setBoardOpen((v) => !v)}>
            Dispatch{pendingCount > 0 ? ` (${pendingCount})` : ''}
          </button>
          <span className="userchip" title={user.email}>
            {user.name}
          </span>
          <button className="board-toggle always" onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <main className={`layout ${boardOpen ? 'board-open' : ''}`}>
        <Chat user={user} onActivity={loadTasks} />
        <DispatchBoard tasks={tasks} onCancel={cancelTask} />
      </main>

      <Connections
        open={connectionsOpen}
        notice={notice}
        onClose={() => {
          setConnectionsOpen(false)
          setNotice('')
        }}
      />
    </div>
  )
}
