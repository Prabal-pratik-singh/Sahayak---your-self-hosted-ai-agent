import { useEffect, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { createRecognizer, speak, stopSpeaking } from '../hooks/useSpeech.js'
import { XIcon } from './Icons.jsx'

// Hands-free voice mode: it keeps listening, waits for the wake word (if set),
// asks the agent, speaks the answer, then listens again — like a desk Jarvis.
// Voice threads are ephemeral on purpose (they don't clutter the sidebar).

export default function VoiceOverlay({ onClose }) {
  const { settings, toast } = useApp()
  const [status, setStatus] = useState('listening') // listening | thinking | speaking | error
  const [transcript, setTranscript] = useState('')
  const [lastExchange, setLastExchange] = useState(null) // {question, answer}
  const recognizerRef = useRef(null)
  const aliveRef = useRef(true)
  const conversationRef = useRef('voice-' + Math.random().toString(36).slice(2, 10))

  const wakeWord = (settings.wakeWord || '').trim().toLowerCase()

  const extractCommand = (text) => {
    if (!wakeWord) return text.trim()
    const lower = text.toLowerCase()
    const at = lower.lastIndexOf(wakeWord)
    if (at < 0) return null
    return text.slice(at + wakeWord.length).replace(/^[\s,.!?-]+/, '').trim()
  }

  const listen = () => {
    if (!aliveRef.current) return
    setStatus('listening')
    setTranscript('')
    const recognizer = createRecognizer({
      continuous: true,
      onInterim: (text) => setTranscript(text),
      onFinal: (segment) => {
        const command = extractCommand(segment)
        if (command) {
          recognizer.stop()
          ask(command)
        }
      },
      onEnd: () => {
        // Browsers stop continuous recognition after a while — restart quietly.
        if (aliveRef.current && status === 'listening') {
          setTimeout(() => {
            if (aliveRef.current && recognizerRef.current === recognizer) listen()
          }, 400)
        }
      },
      onError: (code) => {
        if (code === 'not-allowed' || code === 'service-not-allowed') {
          setStatus('error')
          setTranscript('Microphone permission is blocked. Allow it in the address bar, then reopen voice mode.')
        }
      },
    })
    if (!recognizer) {
      setStatus('error')
      setTranscript('This browser cannot listen. Use Chrome or Edge for voice mode.')
      return
    }
    recognizerRef.current = recognizer
    try {
      recognizer.start()
    } catch {
      /* already started */
    }
  }

  const ask = async (question) => {
    setStatus('thinking')
    setTranscript(question)
    try {
      const data = await api('/chat', {
        method: 'POST',
        body: {
          message: question,
          conversationId: conversationRef.current,
          provider: settings.defaultProvider || undefined,
        },
      })
      const answer = data.reply || '(no reply)'
      setLastExchange({ question, answer })
      if (!aliveRef.current) return
      setStatus('speaking')
      speak(answer, {
        onEnd: () => {
          if (aliveRef.current) listen()
        },
      })
    } catch (e) {
      setLastExchange({ question, answer: e.message })
      if (!aliveRef.current) return
      setStatus('speaking')
      speak('Sorry, that failed. ' + e.message, {
        onEnd: () => {
          if (aliveRef.current) listen()
        },
      })
      toast(e.message, 'error')
    }
  }

  useEffect(() => {
    aliveRef.current = true
    listen()
    return () => {
      aliveRef.current = false
      recognizerRef.current?.abort?.()
      stopSpeaking()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const STATUS_TEXT = {
    listening: wakeWord ? `Listening — say "${settings.wakeWord}, …"` : 'Listening — just speak',
    thinking: 'Thinking…',
    speaking: 'Speaking…',
    error: 'Voice unavailable',
  }

  return (
    <div className="overlay voice">
      <button className="icon-btn voice-close" title="Exit voice mode" onClick={onClose}>
        <XIcon />
      </button>

      <div className={`voice-orb ${status}`}>
        <span />
        <span />
        <span />
      </div>

      <div className="voice-status">{STATUS_TEXT[status]}</div>
      {transcript && <div className="voice-transcript">{transcript}</div>}

      {lastExchange && status !== 'thinking' && (
        <div className="voice-answer card">
          <small>{lastExchange.question}</small>
          <p>{lastExchange.answer}</p>
        </div>
      )}

      <div className="voice-hint">
        Wake word can be changed in Settings · voice threads stay out of your sidebar
      </div>
    </div>
  )
}
