import { useEffect, useRef, useState } from 'react'
import { api } from '../api.js'

const welcome = (name) => ({
  role: 'assistant',
  content:
    `Namaste ${name}! I'm Sahayak — your operations agent.\n\n` +
    'I can chat like a normal assistant, and act through the apps you connect ' +
    '(Connections, top right). Try:\n' +
    '• "Draft a LinkedIn post about my new project"\n' +
    '• "Post it tomorrow at 6 PM"\n' +
    '• "Send an email to myself saying the agent is alive"\n\n' +
    'Anything scheduled lands on the dispatch board.',
})

export default function Chat({ user, onActivity }) {
  const [messages, setMessages] = useState([welcome(user.name)])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [models, setModels] = useState(null) // { defaultId, options: [{id, label, model}] }
  const [provider, setProvider] = useState('')

  const conversationId = useRef(crypto.randomUUID())
  const scrollRef = useRef(null)
  const inputRef = useRef(null)

  useEffect(() => {
    api('/models')
      .then((m) => {
        setModels(m)
        setProvider(m.defaultId)
      })
      .catch(() => {})
  }, [])

  useEffect(() => {
    scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight, behavior: 'smooth' })
  }, [messages, busy])

  const send = async () => {
    const text = input.trim()
    if (!text || busy) return
    setInput('')
    setMessages((m) => [...m, { role: 'user', content: text }])
    setBusy(true)
    try {
      const data = await api('/chat', {
        method: 'POST',
        body: { message: text, conversationId: conversationId.current, provider: provider || undefined },
      })
      setMessages((m) => [...m, { role: 'assistant', content: data.reply ?? '(empty reply)' }])
    } catch (err) {
      setMessages((m) => [...m, { role: 'assistant', error: true, content: err.message }])
    } finally {
      setBusy(false)
      onActivity?.()
      inputRef.current?.focus()
    }
  }

  const newChat = () => {
    conversationId.current = crypto.randomUUID()
    setMessages([welcome(user.name)])
  }

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  return (
    <section className="chat">
      <div className="scroll" ref={scrollRef}>
        {messages.map((m, i) => (
          <div
            key={i}
            className={`slip ${m.role} ${m.error ? 'error' : ''}`}
            style={{ '--tilt': `${i % 2 === 0 ? -0.3 : 0.3}deg` }}
          >
            {m.content}
          </div>
        ))}
        {busy && (
          <div className="slip assistant typing">
            <span />
            <span />
            <span />
          </div>
        )}
      </div>

      <div className="composer">
        <button className="iconbtn" title="Start a new chat" onClick={newChat}>
          ↺
        </button>
        {models && models.options.length > 1 && (
          <select
            className="model-select"
            title="Which AI answers"
            value={provider}
            onChange={(e) => setProvider(e.target.value)}
          >
            {models.options.map((o) => (
              <option key={o.id} value={o.id}>
                {o.label}
              </option>
            ))}
          </select>
        )}
        <textarea
          ref={inputRef}
          rows={1}
          value={input}
          placeholder='Give an order… e.g. "Post this on LinkedIn tomorrow 6 PM"'
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
        />
        <button className="send" onClick={send} disabled={busy || !input.trim()}>
          Send
        </button>
      </div>
    </section>
  )
}
