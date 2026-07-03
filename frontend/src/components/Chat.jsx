import { useEffect, useRef, useState } from 'react'
import { api } from '../api.js'

// Browser speech APIs (Chrome/Edge have SpeechRecognition; Firefox does not yet).
const SpeechRecognitionImpl =
  typeof window !== 'undefined' ? window.SpeechRecognition || window.webkitSpeechRecognition : null

const welcome = (name) => ({
  role: 'assistant',
  content:
    `Namaste ${name}! I'm Sahayak — your personal agent.\n\n` +
    'I can answer anything, check live weather, read web pages, remember things about you, ' +
    'schedule work, and act through your connected apps (Connections, top right). Try:\n' +
    '• "What\'s the weather in Delhi right now?"\n' +
    '• "Remember that I prefer short replies"\n' +
    '• "Post this on LinkedIn tomorrow at 6 PM"\n\n' +
    (SpeechRecognitionImpl
      ? 'Tap the mic to talk to me, and turn on the speaker to hear me answer.'
      : 'Tip: open this in Chrome or Edge to talk to me by voice.'),
})

function MicIcon() {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <rect x="9" y="2" width="6" height="12" rx="3" />
      <path d="M5 10v1a7 7 0 0 0 14 0v-1" />
      <line x1="12" y1="18" x2="12" y2="22" />
    </svg>
  )
}

function SpeakerIcon({ on }) {
  return (
    <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" fill="currentColor" stroke="none" />
      {on ? (
        <>
          <path d="M15.5 8.5a5 5 0 0 1 0 7" />
          <path d="M18.5 5.5a9 9 0 0 1 0 13" />
        </>
      ) : (
        <>
          <line x1="16" y1="9" x2="22" y2="15" />
          <line x1="22" y1="9" x2="16" y2="15" />
        </>
      )}
    </svg>
  )
}

export default function Chat({ user, onActivity }) {
  const [messages, setMessages] = useState([welcome(user.name)])
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(false)
  const [models, setModels] = useState(null) // { defaultId, options: [{id, label, model}] }
  const [provider, setProvider] = useState('')
  const [listening, setListening] = useState(false)
  const [voiceOn, setVoiceOn] = useState(() => localStorage.getItem('sahayak_voice') === 'on')

  const conversationId = useRef(crypto.randomUUID())
  const scrollRef = useRef(null)
  const inputRef = useRef(null)
  const recognitionRef = useRef(null)
  const finalTranscriptRef = useRef('')

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

  // Stop mic/speech cleanly if the component unmounts (e.g. logout).
  useEffect(
    () => () => {
      recognitionRef.current?.abort?.()
      window.speechSynthesis?.cancel()
    },
    [],
  )

  const speak = (text) => {
    const synth = window.speechSynthesis
    if (!synth) return
    synth.cancel()
    const clean = text
      .replace(/https?:\/\/\S+/g, 'link')
      .replace(/[*_#`~>|•]/g, ' ')
      .slice(0, 800)
    const utterance = new SpeechSynthesisUtterance(clean)
    utterance.lang = navigator.language || 'en-IN'
    utterance.rate = 1.05
    synth.speak(utterance)
  }

  const send = async (textOverride) => {
    const text = (textOverride ?? input).trim()
    if (!text || busy) return
    setInput('')
    setMessages((m) => [...m, { role: 'user', content: text }])
    setBusy(true)
    try {
      const data = await api('/chat', {
        method: 'POST',
        body: { message: text, conversationId: conversationId.current, provider: provider || undefined },
      })
      const reply = data.reply ?? '(empty reply)'
      setMessages((m) => [...m, { role: 'assistant', content: reply }])
      if (voiceOn) speak(reply)
    } catch (err) {
      setMessages((m) => [...m, { role: 'assistant', error: true, content: err.message }])
    } finally {
      setBusy(false)
      onActivity?.()
      inputRef.current?.focus()
    }
  }

  const toggleMic = () => {
    if (listening) {
      recognitionRef.current?.stop()
      return
    }
    if (!SpeechRecognitionImpl) return
    window.speechSynthesis?.cancel()
    const recognition = new SpeechRecognitionImpl()
    recognition.lang = navigator.language || 'en-IN'
    recognition.interimResults = true
    finalTranscriptRef.current = ''
    recognition.onresult = (event) => {
      let finalText = ''
      let interimText = ''
      for (const result of event.results) {
        if (result.isFinal) finalText += result[0].transcript
        else interimText += result[0].transcript
      }
      finalTranscriptRef.current = finalText
      setInput((finalText + ' ' + interimText).trim())
    }
    recognition.onend = () => {
      setListening(false)
      const text = finalTranscriptRef.current.trim()
      if (text) {
        setInput('')
        send(text) // Siri-style: speak, then it sends itself
      }
    }
    recognition.onerror = () => setListening(false)
    recognitionRef.current = recognition
    setListening(true)
    recognition.start()
  }

  const toggleVoice = () => {
    setVoiceOn((v) => {
      const next = !v
      localStorage.setItem('sahayak_voice', next ? 'on' : 'off')
      if (!next) window.speechSynthesis?.cancel()
      return next
    })
  }

  const newChat = () => {
    conversationId.current = crypto.randomUUID()
    setMessages([welcome(user.name)])
    window.speechSynthesis?.cancel()
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
            {m.error && <span className="slip-error-label">Something went wrong</span>}
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
          placeholder={listening ? 'Listening…' : 'Ask anything, or give an order…'}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={onKeyDown}
        />
        {SpeechRecognitionImpl && (
          <button
            className={`iconbtn mic ${listening ? 'listening' : ''}`}
            title={listening ? 'Stop listening' : 'Talk to Sahayak'}
            onClick={toggleMic}
          >
            <MicIcon />
          </button>
        )}
        <button
          className={`iconbtn ${voiceOn ? 'active' : ''}`}
          title={voiceOn ? 'Voice replies: on' : 'Voice replies: off'}
          onClick={toggleVoice}
        >
          <SpeakerIcon on={voiceOn} />
        </button>
        <button className="send" onClick={() => send()} disabled={busy || !input.trim()}>
          Send
        </button>
      </div>
    </section>
  )
}
