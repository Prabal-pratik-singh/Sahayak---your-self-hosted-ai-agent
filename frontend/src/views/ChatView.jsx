import { useEffect, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api, streamChat, uploadFile } from '../api.js'
import Markdown from '../components/Markdown.jsx'
import { createRecognizer, speak, speechSupported, stopSpeaking } from '../hooks/useSpeech.js'
import { CopyIcon, FileIcon, MicIcon, PaperclipIcon, SendIcon, SpeakerIcon, XIcon } from '../components/Icons.jsx'
import { ACCEPT, formatSize, releaseStaged, stageFiles } from '../attachments.js'

const SUGGESTIONS = [
  "What's the weather in Delhi right now?",
  'Remember that I prefer short replies',
  'Draft a LinkedIn post about my new project',
  'Remind yourself to say hello in 2 minutes',
]

// Shown while the agent works but hasn't produced any text yet. Tool turns
// (posting, searching, calendar…) can take 30+ seconds with zero tokens — an
// empty bubble looks like a glitch, so escalate the label as time passes.
function ThinkingIndicator() {
  const [label, setLabel] = useState('Thinking')
  useEffect(() => {
    const t1 = setTimeout(() => setLabel('Working on it'), 6000)
    const t2 = setTimeout(() => setLabel('Still working — actions like posting can take up to a minute'), 20000)
    return () => {
      clearTimeout(t1)
      clearTimeout(t2)
    }
  }, [])
  return (
    <span className="thinking-line" role="status">
      {label}
      <span className="thinking-dots" aria-hidden="true"><i /><i /><i /></span>
    </span>
  )
}

// Attachment thumbnails/chips shown inside a sent message bubble.
function MessageAttachments({ attachments }) {
  if (!attachments?.length) return null
  return (
    <div className="msg-attachments">
      {attachments.map((a, i) =>
        a.kind === 'image' && a.previewUrl ? (
          <a key={i} className="msg-attach image" href={a.previewUrl} target="_blank" rel="noreferrer" title={a.name}>
            <img src={a.previewUrl} alt={a.name} />
          </a>
        ) : (
          <span key={i} className="msg-attach">
            <span className="attach-ico" aria-hidden="true"><FileIcon /></span>
            <span className="attach-meta">
              <span className="attach-name">{a.name}</span>
              <span className="attach-sub">{a.ext?.toUpperCase()}</span>
            </span>
          </span>
        ),
      )}
    </div>
  )
}

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
        <div className="msg-bubble">
          <MessageAttachments attachments={message.attachments} />
          {message.content}
        </div>
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
        {message.streaming && !message.content && <ThinkingIndicator />}
        {message.streaming && message.content && <span className="caret" />}
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
    refreshConversations, refreshTasks, newChat, prefill, setPrefill,
    prefillFiles, setPrefillFiles, toast,
  } = useApp()

  const [messages, setMessages] = useState([])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [listening, setListening] = useState(false)
  const [attachments, setAttachments] = useState([])
  const [dragging, setDragging] = useState(false)

  const scrollRef = useRef(null)
  const inputRef = useRef(null)
  const recognizerRef = useRef(null)
  const stickToBottom = useRef(true)
  const fileInputRef = useRef(null)
  // Always holds the latest staged attachments, so unmount cleanup can revoke
  // their object URLs without a stale closure.
  const stagedRef = useRef([])
  stagedRef.current = attachments
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

  // Prefill from Tools / Home quick actions — text and/or files picked on the
  // dashboard land here already staged, ready to send.
  useEffect(() => {
    if (prefill) {
      setInput(prefill)
      setPrefill('')
      inputRef.current?.focus()
    }
    if (prefillFiles?.length) {
      const accepted = stageFiles(prefillFiles, stagedRef.current, (msg) => toast(msg, 'error'))
      if (accepted.length) setAttachments((c) => [...c, ...accepted])
      setPrefillFiles([])
      inputRef.current?.focus()
    }
  }, [prefill, setPrefill, prefillFiles, setPrefillFiles]) // eslint-disable-line react-hooks/exhaustive-deps

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

  // Validate + stage picked/dropped files. This is a UX guard only — the
  // server re-validates type + size on upload (never trust the client).
  const addFiles = (fileList) => {
    const accepted = stageFiles(fileList, attachments, (msg) => toast(msg, 'error'))
    if (accepted.length) setAttachments((c) => [...c, ...accepted])
  }

  const removeAttachment = (id) => {
    setAttachments((c) => {
      const found = c.find((a) => a.id === id)
      if (found?.previewUrl) URL.revokeObjectURL(found.previewUrl)
      return c.filter((a) => a.id !== id)
    })
  }

  const onDrop = (e) => {
    e.preventDefault()
    setDragging(false)
    if (e.dataTransfer?.files?.length) addFiles(e.dataTransfer.files)
  }

  const send = async (textOverride, { skipEcho = false } = {}) => {
    const text = (textOverride ?? input).trim()
    // Attachment-only sends are fine (e.g. just an image: "what's this?" implied).
    if ((!text && !attachments.length) || busy) return

    let conversation = activeConversation
    if (!conversation?.id) {
      conversation = await newChat()
      if (!conversation) return
      justCreatedRef.current = conversation.id
    }

    const staged = attachments
    setBusy(true)

    // Upload attachments FIRST, so a failed upload aborts before we commit the
    // message — we never show a bubble referencing a file that didn't store.
    let refs = []
    if (staged.length) {
      try {
        refs = await Promise.all(staged.map((a) => uploadFile(a.file, conversation.id)))
      } catch (e) {
        setBusy(false)
        toast(e.message || 'Could not upload the attachment.', 'error')
        return // keep the text + staged files so the user can retry
      }
    }

    setInput('')
    setAttachments([])
    // Pair each stored ref with its local preview for the sent bubble. The
    // object URLs now live on the message, so they aren't revoked here.
    const outgoing = staged.map((a, i) => ({
      id: refs[i]?.id,
      name: a.name,
      kind: a.kind,
      ext: a.ext,
      previewUrl: a.previewUrl,
    }))
    stickToBottom.current = true
    setMessages((m) => [
      ...m,
      ...(skipEcho ? [] : [{ role: 'user', content: text, attachments: outgoing }]),
      { role: 'assistant', content: '', streaming: true },
    ])

    const attachmentIds = refs.map((r) => r?.id).filter((id) => id != null)
    const body = {
      message: text,
      conversationId: String(conversation.id),
      provider: settings.defaultProvider || undefined,
      // Consumed by the backend in the vision/analysis phase; ignored for now.
      ...(attachmentIds.length ? { attachmentIds } : {}),
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
      lang: settings.voiceLang,
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
      // Free any still-staged image previews (sent ones live on their bubbles).
      releaseStaged(stagedRef.current)
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

      <div
        className={`composer ${dragging ? 'dragging' : ''}`}
        onDragOver={(e) => {
          e.preventDefault()
          if (!dragging) setDragging(true)
        }}
        onDragLeave={(e) => {
          if (e.currentTarget === e.target) setDragging(false)
        }}
        onDrop={onDrop}
      >
        {attachments.length > 0 && (
          <div className="attach-strip">
            {attachments.map((a) => (
              <div key={a.id} className={`attach-chip ${a.kind}`}>
                {a.kind === 'image' ? (
                  <img src={a.previewUrl} alt={a.name} />
                ) : (
                  <span className="attach-ico" aria-hidden="true"><FileIcon /></span>
                )}
                <span className="attach-meta">
                  <span className="attach-name">{a.name}</span>
                  <span className="attach-sub">{a.ext.toUpperCase()} · {formatSize(a.size)}</span>
                </span>
                <button
                  type="button"
                  className="attach-remove"
                  aria-label={`Remove ${a.name}`}
                  onClick={() => removeAttachment(a.id)}
                >
                  <XIcon />
                </button>
              </div>
            ))}
          </div>
        )}
        <div className="composer-row">
          {models && models.options.length > 1 && (
            <select
              className="model-select"
              title="Which AI answers"
              value={settings.defaultProvider || models.defaultId}
              onChange={(e) => settings.set({ defaultProvider: e.target.value })}
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
          <input
            ref={fileInputRef}
            type="file"
            multiple
            accept={ACCEPT}
            style={{ display: 'none' }}
            onChange={(e) => {
              addFiles(e.target.files)
              e.target.value = ''
            }}
          />
          <button
            type="button"
            className="icon-btn attach-btn"
            title="Attach images or documents"
            aria-label="Attach images or documents"
            onClick={() => fileInputRef.current?.click()}
          >
            <PaperclipIcon />
          </button>
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
          <button
            className="btn send"
            onClick={() => send()}
            disabled={busy || (!input.trim() && !attachments.length)}
          >
            <SendIcon />
            <span>Send</span>
          </button>
        </div>
      </div>
    </section>
  )
}
