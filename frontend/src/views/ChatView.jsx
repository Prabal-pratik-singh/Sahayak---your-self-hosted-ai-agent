import { useEffect, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api, streamChat } from '../api.js'
import Markdown from '../components/Markdown.jsx'
import { createRecognizer, speak, speechSupported, stopSpeaking } from '../hooks/useSpeech.js'
import { CopyIcon, MicIcon, SendIcon, SpeakerIcon } from '../components/Icons.jsx'

const SUGGESTIONS = [
  "What's the weather in Delhi right now?",
  'Remember that I prefer short replies',
  'Draft a LinkedIn post about my new project',
  'Remind yourself to say hello in 2 minutes',
]

function Bubble({ message, user, onRetry }) {
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(message.content)
    } catch {
      /* clipboard unavailable */
    }
  }
  if (message.role === 'user') {
    return (
      <div className="msg user">
        <div className="msg-bubble">{message.content}</div>
        <span className="avatar small">{(user.name || '?')[0].toUpperCase()}</span>
      </div>
    )
  }
  return (
    <div className={`msg assistant ${message.error ? 'error' : ''}`}>
      <span className="orb small" aria-hidden="true" />
      <div className="msg-bubble">
        {message.error && <span className="msg-error-label">Something went wrong</span>}
        {message.error ? message.content : <Markdown>{message.content}</Markdown>}
        {message.streaming && <span className="caret" />}
        {message.error && message.retryFor && (
          <button className="btn ghost retry-btn" onClick={() => onRetry(message.retryFor)}>
            Try again
          </button>
        )}
        {!message.streaming && !message.error && message.content && (
          <button className="msg-copy" title="Copy reply" aria-label="Copy reply" onClick={copy}>
            <CopyIcon />
          </button>
        )}
      </div>
    </div>
  )
}

export default function ChatView() {
  const {
    user, models, settings, activeConversation, openConversation,
    refreshConversations, refreshTasks, newChat, prefill, setPrefill, toast,
  } = useApp()

  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [provider, setProvider] = useState(settings.defaultProvider || '')
  const [listening, setListening] = useState(false)

  const scrollRef = useRef(null)
  const inputRef = useRef(null)
  const recognizerRef = useRef(null)
  const stickToBottom = useRef(true)
  // Set when send() creates the conversation itself: the history effect must
  // NOT clear the optimistic messages then, or the first reply vanishes
  // (this race was the "AI sometimes doesn't reply" bug).
  const justCreatedRef = useRef(null)

  // Load history whenever the active conversation changes.
  useEffect(() => {
    if (activeConversation?.id && justCreatedRef.current === activeConversation.id) {
      justCreatedRef.current = null
      return // brand-new chat created mid-send — nothing to load, keep messages
    }
    setMessages([])
    if (!activeConversation?.id) return
    setLoadingHistory(true)
    api(`/conversations/${activeConversation.id}/messages`)
      .then((history) => setMessages(history.map((m) => ({ role: m.role, content: m.content }))))
      .catch((e) => toast(e.message, 'error'))
      .finally(() => setLoadingHistory(false))
  }, [activeConversation?.id]) // eslint-disable-line react-hooks/exhaustive-deps

  // Prefill from Tools / Home quick actions.
  useEffect(() => {
    if (prefill) {
      setInput(prefill)
      setPrefill('')
      inputRef.current?.focus()
    }
  }, [prefill, setPrefill])

  // Autoscroll only while the user is near the bottom.
  const onScroll = () => {
    const el = scrollRef.current
    if (!el) return
    stickToBottom.current = el.scrollHeight - el.scrollTop - el.clientHeight < 120
  }
  useEffect(() => {
    if (stickToBottom.current) {
      scrollRef.current?.scrollTo({ top: scrollRef.current.scrollHeight })
    }
  }, [messages])

  const appendToLast = (token) =>
    setMessages((m) => {
      if (m.length === 0) return m
      const next = [...m]
      const last = next[next.length - 1]
      next[next.length - 1] = { ...last, content: (last.content || '') + token }
      return next
    })

  const finishLast = (patch) =>
    setMessages((m) => {
      if (m.length === 0) return m
      const next = [...m]
      next[next.length - 1] = { ...next[next.length - 1], streaming: false, ...patch }
      return next
    })

  const send = async (textOverride, { skipEcho = false } = {}) => {
    const text = (textOverride ?? input).trim()
    if (!text || busy) return

    let conversation = activeConversation
    if (!conversation?.id) {
      conversation = await newChat()
      if (!conversation) return
      justCreatedRef.current = conversation.id
    }

    setInput('')
    stickToBottom.current = true
    setMessages((m) => [
      ...m,
      ...(skipEcho ? [] : [{ role: 'user', content: text }]),
      { role: 'assistant', content: '', streaming: true },
    ])
    setBusy(true)

    const body = {
      message: text,
      conversationId: String(conversation.id),
      provider: provider || settings.defaultProvider || undefined,
    }

    let spoken = ''
    try {
      let streamedError = null
      await streamChat(body, {
        onToken: (t) => {
          spoken += t
          appendToLast(t)
        },
        onError: (msg) => {
          streamedError = msg
        },
      })
      if (streamedError && !spoken) {
        finishLast({ error: true, content: streamedError, retryFor: text })
      } else if (streamedError) {
        finishLast({})
        toast(streamedError, 'error')
      } else if (!spoken.trim()) {
        // A "successful" stream with zero content used to leave a blank bubble.
        finishLast({ error: true, content: 'The AI returned an empty reply — please try again.', retryFor: text })
      } else {
        finishLast({})
        if (settings.voiceReplies) speak(spoken)
      }
    } catch {
      // Streaming refused (proxy, old server…) — fall back to the plain endpoint.
      try {
        const data = await api('/chat', { method: 'POST', body })
        if (data.reply?.trim()) {
          finishLast({ content: data.reply })
          if (settings.voiceReplies) speak(data.reply)
        } else {
          finishLast({ error: true, content: 'The AI returned an empty reply — please try again.', retryFor: text })
        }
      } catch (e2) {
        finishLast({ error: true, content: e2.message, retryFor: text })
      }
    } finally {
      setBusy(false)
      refreshConversations()
      refreshTasks()
      inputRef.current?.focus()
    }
  }

  // Replaces the error bubble with a fresh attempt; the user's original
  // message is already in the thread, so don't echo it again.
  const retry = (text) => {
    setMessages((m) => (m.length && m[m.length - 1].error ? m.slice(0, -1) : m))
    send(text, { skipEcho: true })
  }

  const toggleMic = () => {
    if (listening) {
      recognizerRef.current?.stop()
      return
    }
    stopSpeaking()
    const recognizer = createRecognizer({
      onInterim: (text) => setInput(text),
      onEnd: (finalText) => {
        setListening(false)
        if (finalText) {
          setInput('')
          send(finalText)
        }
      },
      onError: () => setListening(false),
    })
    if (!recognizer) return
    recognizerRef.current = recognizer
    setListening(true)
    recognizer.start()
  }

  useEffect(
    () => () => {
      recognizerRef.current?.abort?.()
      stopSpeaking()
    },
    [],
  )

  const onKeyDown = (e) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      send()
    }
  }

  const empty = messages.length === 0 && !loadingHistory

  return (
    <section className="chat-view">
      <div className="chat-scroll" ref={scrollRef} onScroll={onScroll}>
        {loadingHistory && (
          <>
            <div className="skeleton-row right"><div className="skeleton-bubble w-md" /></div>
            <div className="skeleton-row"><div className="skeleton-bubble w-lg" /></div>
            <div className="skeleton-row right"><div className="skeleton-bubble w-sm" /></div>
            <div className="skeleton-row"><div className="skeleton-bubble w-md" /></div>
          </>
        )}
        {empty && (
          <div className="chat-empty view-in">
            <span className="orb big" />
            <h2>
              Namaste {user.name.split(' ')[0]} — <span className="grad-text">what shall we do?</span>
            </h2>
            <p>I can answer, look things up live, remember, schedule, and act through your apps.</p>
            <div className="suggestions">
              {SUGGESTIONS.map((s) => (
                <button key={s} className="suggestion" onClick={() => send(s)}>
                  {s}
                </button>
              ))}
            </div>
          </div>
        )}
        {messages.map((m, i) => (
          <Bubble key={i} message={m} user={user} onRetry={retry} />
        ))}
      </div>

      <div className="composer">
        {models && models.options.length > 1 && (
          <select
            className="model-select"
            title="Which AI answers"
            value={provider || models.defaultId}
            onChange={(e) => setProvider(e.target.value)}
          >
            {models.options.map((o) => (
              <option key={o.id} value={o.id}>
                {o.label}
                {o.source === 'your key' ? ' · yours' : ''}
                {o.tools === false ? ' · chat only' : ''}
              </option>
            ))}
          </select>
        )}
        <textarea
          ref={inputRef}
          rows={1}
          value={input}
          placeholder={listening ? 'Listening…' : 'Ask anything, or give an order…'}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
        />
        {speechSupported && (
          <button
            className={`icon-btn mic ${listening ? 'listening' : ''}`}
            title={listening ? 'Stop listening' : 'Talk instead of typing'}
            aria-label={listening ? 'Stop listening' : 'Talk instead of typing'}
            onClick={toggleMic}
          >
            <MicIcon />
          </button>
        )}
        <button
          className={`icon-btn ${settings.voiceReplies ? 'active' : ''}`}
          title={settings.voiceReplies ? 'Voice replies: on' : 'Voice replies: off'}
          aria-label={settings.voiceReplies ? 'Turn voice replies off' : 'Turn voice replies on'}
          onClick={() => {
            if (settings.voiceReplies) stopSpeaking()
            settings.set({ voiceReplies: !settings.voiceReplies })
          }}
        >
          <SpeakerIcon on={settings.voiceReplies} />
        </button>
        <button className="btn send" onClick={() => send()} disabled={busy || !input.trim()}>
          <SendIcon />
          <span>Send</span>
        </button>
      </div>
    </section>
  )
}
