import { createContext, lazy, Suspense, useCallback, useContext, useEffect, useRef, useState } from 'react'
import { api, tokenStore } from './api.js'
import { useSettings } from './hooks/useSettings.js'
import Login from './components/Login.jsx'
import Sidebar from './components/Sidebar.jsx'
import Topbar from './components/Topbar.jsx'
import Toasts from './components/Toasts.jsx'
import HomeView from './views/HomeView.jsx'
import ChatView from './views/ChatView.jsx'

// Heavier views load on demand so first paint stays fast.
const TasksView = lazy(() => import('./views/TasksView.jsx'))
const CalendarView = lazy(() => import('./views/CalendarView.jsx'))
const ActivityView = lazy(() => import('./views/ActivityView.jsx'))
const IntegrationsView = lazy(() => import('./views/IntegrationsView.jsx'))
const ToolsView = lazy(() => import('./views/ToolsView.jsx'))
const SettingsView = lazy(() => import('./views/SettingsView.jsx'))
const CommandPalette = lazy(() => import('./components/CommandPalette.jsx'))
const VoiceOverlay = lazy(() => import('./components/VoiceOverlay.jsx'))

const AppContext = createContext(null)
export const useApp = () => useContext(AppContext)

const VIEWS = {
  home: HomeView,
  chat: ChatView,
  tasks: TasksView,
  calendar: CalendarView,
  activity: ActivityView,
  integrations: IntegrationsView,
  tools: ToolsView,
  settings: SettingsView,
}

function Loader() {
  return (
    <div className="loader">
      <span className="orb" />
    </div>
  )
}

export default function App() {
  const settings = useSettings()
  const [user, setUser] = useState(undefined) // undefined = checking token, null = logged out
  const [view, setView] = useState('home')
  const [conversations, setConversations] = useState([])
  const [activeConversation, setActiveConversation] = useState(null)
  const [tasks, setTasks] = useState([])
  const [models, setModels] = useState(null)
  const [providerHealth, setProviderHealth] = useState([])
  const [online, setOnline] = useState(true)
  const [toasts, setToasts] = useState([])
  const healthSeenRef = useRef({})
  const [prefill, setPrefill] = useState('')
  const [paletteOpen, setPaletteOpen] = useState(false)
  const [voiceOpen, setVoiceOpen] = useState(false)
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const toastSeq = useRef(0)

  const toast = useCallback((text, kind = 'info') => {
    const id = ++toastSeq.current
    setToasts((t) => [...t, { id, text, kind }])
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 4200)
  }, [])

  const dismissToast = (id) => setToasts((t) => t.filter((x) => x.id !== id))

  // ---- session ----
  useEffect(() => {
    const onUnauthorized = () => setUser(null)
    window.addEventListener('sahayak:unauthorized', onUnauthorized)
    if (!tokenStore.get()) setUser(null)
    else api('/auth/me').then(setUser).catch(() => setUser(null))
    return () => window.removeEventListener('sahayak:unauthorized', onUnauthorized)
  }, [])

  const logout = async () => {
    try {
      await api('/auth/logout', { method: 'POST' })
    } catch {
      /* token cleared regardless */
    }
    tokenStore.clear()
    setUser(null)
    setConversations([])
    setActiveConversation(null)
    setView('home')
  }

  // ---- data ----
  const refreshConversations = useCallback(async () => {
    try {
      setConversations(await api('/conversations'))
    } catch {
      /* sidebar just stays stale */
    }
  }, [])

  const refreshTasks = useCallback(async () => {
    try {
      setTasks(await api('/tasks'))
      setOnline(true)
    } catch {
      setOnline(false)
    }
  }, [])

  // AI provider health: poll, and toast when a NEW failure appears
  // (e.g. a scheduled task hit Gemini's quota while you were elsewhere).
  const refreshHealth = useCallback(async () => {
    try {
      const healthList = await api('/providers/health')
      setProviderHealth(healthList)
      for (const p of healthList) {
        const seenAt = healthSeenRef.current[p.id]
        if (p.lastError?.at && p.lastError.at !== seenAt) {
          if (seenAt !== undefined) toast(p.lastError.message, 'error')
          healthSeenRef.current[p.id] = p.lastError.at
        } else if (healthSeenRef.current[p.id] === undefined) {
          healthSeenRef.current[p.id] = p.lastError?.at ?? null
        }
      }
    } catch {
      /* panel just stays stale */
    }
  }, [toast])

  useEffect(() => {
    if (!user) return
    refreshConversations()
    refreshTasks()
    refreshHealth()
    api('/models').then(setModels).catch(() => {})
    const id = setInterval(refreshTasks, 10000)
    const healthId = setInterval(refreshHealth, 30000)
    return () => {
      clearInterval(id)
      clearInterval(healthId)
    }
  }, [user, refreshConversations, refreshTasks, refreshHealth])

  // LinkedIn OAuth lands back here with ?connected= / ?error=
  useEffect(() => {
    if (!user) return
    const params = new URLSearchParams(window.location.search)
    if (params.get('connected') === 'linkedin') {
      toast('LinkedIn connected ✓', 'ok')
      setView('integrations')
    } else if (params.get('error')) {
      toast(params.get('error'), 'error')
      setView('integrations')
    }
    if ([...params.keys()].length > 0) {
      window.history.replaceState({}, '', window.location.pathname)
    }
  }, [user, toast])

  // ---- navigation ----
  const nav = (next) => {
    setView(next)
    setSidebarOpen(false)
  }

  const openConversation = (conversation) => {
    setActiveConversation(conversation)
    nav('chat')
  }

  const newChat = async () => {
    try {
      const created = await api('/conversations', { method: 'POST', body: {} })
      setConversations((c) => [created, ...c])
      openConversation(created)
      return created
    } catch (e) {
      toast(e.message, 'error')
      return null
    }
  }

  // ---- keyboard: Ctrl/Cmd+K for the palette ----
  useEffect(() => {
    const onKey = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
        e.preventDefault()
        setPaletteOpen((v) => !v)
      }
      if (e.key === 'Escape') {
        setPaletteOpen(false)
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  if (user === undefined) return <Loader />
  if (!user) return <Login onLogin={setUser} />

  const ViewComponent = VIEWS[view] || HomeView

  const ctx = {
    user,
    logout,
    settings,
    view,
    nav,
    conversations,
    refreshConversations,
    activeConversation,
    openConversation,
    newChat,
    tasks,
    refreshTasks,
    models,
    providerHealth,
    refreshHealth,
    online,
    toast,
    prefill,
    setPrefill,
    setPaletteOpen,
    setVoiceOpen,
    sidebarOpen,
    setSidebarOpen,
  }

  return (
    <AppContext.Provider value={ctx}>
      <div className="app">
        <Sidebar />
        {sidebarOpen && <div className="scrim" onClick={() => setSidebarOpen(false)} />}
        <div className="main">
          <Topbar />
          <div className="content" key={view}>
            <Suspense fallback={<Loader />}>
              <ViewComponent />
            </Suspense>
          </div>
        </div>
        <Suspense fallback={null}>
          {paletteOpen && <CommandPalette onClose={() => setPaletteOpen(false)} />}
          {voiceOpen && <VoiceOverlay onClose={() => setVoiceOpen(false)} />}
        </Suspense>
        <Toasts toasts={toasts} dismiss={dismissToast} />
      </div>
    </AppContext.Provider>
  )
}
