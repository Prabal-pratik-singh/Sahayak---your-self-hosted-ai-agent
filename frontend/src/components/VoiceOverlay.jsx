import { useEffect, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { createRecognizer, speechSupported, stopSpeaking } from '../hooks/useSpeech.js'
import { SpeakerIcon, XIcon } from './Icons.jsx'

// Hands-free voice mode: listen → recognize → ask the agent → speak the answer
// → listen again. Two past bugs are guarded against here:
//  1. A stale-closure restart used to re-open the mic WHILE the AI was
//     thinking/speaking (breaking playback) — statusRef is the live source
//     of truth, and the mic only restarts from the 'listening' state.
//  2. The default wake word silently swallowed all speech; matching is now
//     forgiving, and non-matching speech gets visible feedback.

const ERROR_TEXT = {
  'not-allowed': 'Microphone permission is blocked. Click the 🔒/camera icon in the address bar, allow the microphone, then reopen voice mode.',
  'service-not-allowed': 'This browser blocked the speech service. Try Chrome or Edge.',
  'audio-capture': 'No microphone was found. Plug one in or check your sound settings.',
  network: 'Speech recognition needs internet and it seems unreachable right now.',
}

export default function VoiceOverlay({ onClose }) {
  const { settings, toast } = useApp()
  const [status, setStatus] = useState('listening') // listening | thinking | speaking | error
  const [transcript, setTranscript] = useState('')
  const [hint, setHint] = useState('')
  const [muted, setMuted] = useState(false)
  const [lastExchange, setLastExchange] = useState(null) // {question, answer}

  const statusRef = useRef('listening')
  const mutedRef = useRef(false)
  const aliveRef = useRef(true)
  const recognizerRef = useRef(null)
  const hintTimerRef = useRef(null)
  const conversationRef = useRef('voice-' + Math.random().toString(36).slice(2, 10))

  const wakeWord = (settings.wakeWord || '').trim().toLowerCase()

  const setPhase = (next) => {
    statusRef.current = next
    setStatus(next)
  }

  const flashHint = (text) => {
    setHint(text)
    clearTimeout(hintTimerRef.current)
    hintTimerRef.current = setTimeout(() => setHint(''), 3000)
  }

  // Forgiving wake-word check: case-insensitive, tolerates the recognizer
  // splitting or joining the word ("sahayak" vs "saha yak").
  const extractCommand = (text) => {
    if (!wakeWord) return text.trim()
    const squash = (s) => s.toLowerCase().replace(/[^a-z]/g, '')
    const squashedWake = squash(wakeWord)
    const lower = text.toLowerCase()
    const directAt = lower.lastIndexOf(wakeWord)
    if (directAt >= 0) {
      return text.slice(directAt + wakeWord.length).replace(/^[\s,.!?-]+/, '').trim()
    }
    if (squash(text).includes(squashedWake)) {
      return text.trim() // heard the wake word, mangled — take the whole sentence
    }
    return null
  }

  const listen = () => {
    if (!aliveRef.current) return
    setPhase('listening')
    setTranscript('')
    const recognizer = createRecognizer({
      continuous: true,
      onInterim: (text) => setTranscript(text),
      onFinal: (segment) => {
        const command = extractCommand(segment)
        if (command) {
          recognizerRef.current = null // this recognizer is done — no auto-restart
          recognizer.stop()
          ask(command)
        } else if (segment.trim()) {
          flashHint(`Heard you — start with "${settings.wakeWord}" to command me`)
        }
      },
      onEnd: () => {
        // Browsers cut continuous recognition off after a while. Restart ONLY
        // if we are still in the listening phase (live check, not a stale one).
        if (aliveRef.current && statusRef.current === 'listening' && recognizerRef.current === recognizer) {
          setTimeout(() => {
            if (aliveRef.current && statusRef.current === 'listening') listen()
          }, 350)
        }
      },
      onError: (code) => {
        if (code === 'no-speech' || code === 'aborted') return // harmless, onEnd restarts
        const text = ERROR_TEXT[code]
        if (text) {
          setPhase('error')
          setTranscript(text)
        }
      },
    })
    if (!recognizer) {
      setPhase('error')
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

  const speakAnswer = (answer) => {
    if (!aliveRef.current) return
    if (mutedRef.current || !window.speechSynthesis) {
      listen() // muted: the answer is on screen, go straight back to listening
      return
    }
    setPhase('speaking')
    const synth = window.speechSynthesis
    synth.cancel()
    const clean = answer.replace(/https?:\/\/\S+/g, 'link').replace(/[*_#`~>|•]/g, ' ').slice(0, 900)
    const utterance = new SpeechSynthesisUtterance(clean)
    utterance.lang = navigator.language || 'en-IN'
    utterance.rate = 1.05
    utterance.onend = () => {
      if (aliveRef.current && statusRef.current === 'speaking') listen()
    }
    utterance.onerror = () => {
      if (aliveRef.current && statusRef.current === 'speaking') listen()
    }
    // Chrome quirk: speaking immediately after cancel() sometimes gets swallowed.
    setTimeout(() => {
      if (aliveRef.current && statusRef.current === 'speaking') synth.speak(utterance)
    }, 60)
  }

  const ask = async (question) => {
    setPhase('thinking')
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
      const answer = data.reply?.trim() || 'I got an empty reply — please try again.'
      setLastExchange({ question, answer })
      speakAnswer(answer)
    } catch (e) {
      // Show the full reason on screen; speak a short, clear version so the
      // user isn't left with silence when e.g. the daily AI limit is hit.
      const limitHit = /limit|quota|rate/i.test(e.message)
      setLastExchange({ question, answer: e.message, error: true })
      toast(e.message, 'error')
      speakAnswer(limitHit
        ? "You've run out of your AI usage limit for now. Add your own key in settings, or try again later."
        : 'Sorry, that request failed. ' + e.message)
    }
  }

  const stopSpeakingNow = () => {
    stopSpeaking()
    if (statusRef.current === 'speaking') listen()
  }

  const toggleMute = () => {
    setMuted((m) => {
      mutedRef.current = !m
      if (!m) {
        stopSpeaking()
        if (statusRef.current === 'speaking') listen()
      }
      return !m
    })
  }

  useEffect(() => {
    aliveRef.current = true
    listen()
    return () => {
      aliveRef.current = false
      clearTimeout(hintTimerRef.current)
      recognizerRef.current?.abort?.()
      stopSpeaking()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const STATUS_TEXT = {
    listening: wakeWord ? `Listening — say "${settings.wakeWord}, …"` : 'Listening — just speak',
    thinking: 'Thinking…',
    speaking: muted ? 'Muted — reply is on screen' : 'Speaking…',
    error: 'Voice unavailable',
  }

  return (
    <div className="overlay voice" role="dialog" aria-label="Voice mode">
      <div className="voice-controls">
        {status === 'speaking' && !muted && (
          <button className="btn ghost" onClick={stopSpeakingNow}>
            ■ Stop speaking
          </button>
        )}
        <button
          className={`icon-btn ${muted ? '' : 'active'}`}
          title={muted ? 'Unmute replies' : 'Mute replies'}
          aria-label={muted ? 'Unmute replies' : 'Mute replies'}
          onClick={toggleMute}
        >
          <SpeakerIcon on={!muted} />
        </button>
        <button className="icon-btn" title="Exit voice mode" aria-label="Exit voice mode" onClick={onClose}>
          <XIcon />
        </button>
      </div>

      <div className={`voice-orb ${status}`}>
        <span />
        <span />
        <span />
      </div>

      {status === 'speaking' && !muted && (
        <div className="wave" aria-hidden="true">
          <span /><span /><span /><span /><span />
        </div>
      )}

      <div className={`voice-status ${status}`} role="status">{STATUS_TEXT[status]}</div>
      {hint && <div className="voice-hint-flash">{hint}</div>}
      {transcript && <div className="voice-transcript">{transcript}</div>}

      {lastExchange && status !== 'thinking' && (
        <div className={`voice-answer card ${lastExchange.error ? 'error' : ''}`}>
          <small>{lastExchange.question}</small>
          {lastExchange.error && <span className="msg-error-label">Could not answer</span>}
          <p>{lastExchange.answer}</p>
        </div>
      )}

      <div className="voice-hint">
        {speechSupported
          ? 'Wake word is optional — set or clear it in Settings · voice chats stay out of your sidebar'
          : 'Listening needs Chrome or Edge'}
      </div>
    </div>
  )
}
