import { useEffect, useRef, useState } from 'react'
import { useApp } from '../App.jsx'
import { api } from '../api.js'
import { createRecognizer, speechSupported } from '../hooks/useSpeech.js'
import { SpeakerIcon, XIcon } from './Icons.jsx'

// Hands-free voice mode: listen → recognize → ask the agent → speak → listen…
//
// How a "turn" ends: we do NOT rely on the browser's "final result" signal
// (Chrome often never fires it in continuous mode → the reply never came).
// Instead we accumulate everything you say and end the turn when you go quiet.
// A comfortable base pause lets you finish your sentence; if you trail off on a
// "still-thinking" word (a filler / conjunction / preposition) we wait longer,
// so pausing to think mid-sentence doesn't cut you off.
//
// Reliability fixes: every mic restart fully tears down the old recognizer and
// retries start() if the browser is still busy; speech synthesis is kept alive
// with periodic resume() and a watchdog so it can never get stuck "speaking".

const PAUSE_BASE_MS = 2500 // silence after a normal sentence that ends your turn
const PAUSE_THINKING_MS = 4500 // longer grace when you paused on a mid-thought word

// If your speech ends on one of these, you're probably not done — wait longer.
const CONTINUATION_WORDS = new Set([
  'um', 'umm', 'uh', 'uhh', 'hmm', 'er', 'erm', 'like',
  'and', 'or', 'but', 'so', 'because', 'then', 'also', 'plus',
  'a', 'an', 'the', 'to', 'of', 'in', 'on', 'at', 'with', 'for', 'from', 'about', 'into',
  'my', 'your', 'this', 'that',
])

const ERROR_TEXT = {
  'not-allowed': 'Microphone permission is blocked. Click the 🔒/camera icon in the address bar, allow the microphone, then reopen voice mode.',
  'service-not-allowed': 'This browser blocked the speech service. Try Chrome or Edge.',
  'audio-capture': 'No microphone was found. Plug one in or check your sound settings.',
}

export default function VoiceOverlay({ onClose }) {
  const { settings, toast } = useApp()
  const [status, setStatus] = useState('listening') // listening | thinking | speaking | error
  const [transcript, setTranscript] = useState('')
  const [muted, setMuted] = useState(false)
  const [lastExchange, setLastExchange] = useState(null)

  const statusRef = useRef('listening')
  const mutedRef = useRef(false)
  const aliveRef = useRef(true)
  const recognizerRef = useRef(null)
  const resumeTimerRef = useRef(null)
  const speakWatchdogRef = useRef(null)
  const startWatchdogRef = useRef(null)
  const silenceTimerRef = useRef(null)
  const pendingTextRef = useRef('') // everything heard so far this turn
  const conversationRef = useRef('voice-' + Math.random().toString(36).slice(2, 10))

  const setPhase = (next) => {
    statusRef.current = next
    setStatus(next)
  }

  // Fully detach and kill the current recognizer so no late event fires and the
  // mic is released before we start a new one.
  const teardownRecognizer = () => {
    clearTimeout(silenceTimerRef.current)
    const rec = recognizerRef.current
    recognizerRef.current = null
    if (rec) {
      rec.onresult = null
      rec.onend = null
      rec.onerror = null
      try {
        rec.abort()
      } catch {
        /* already stopped */
      }
    }
  }

  // You've gone quiet — whatever you said is your message. No wake word: this
  // is a call, so everything you say gets sent, like ChatGPT/Gemini voice.
  const endTurn = () => {
    if (statusRef.current !== 'listening') return
    const text = pendingTextRef.current.trim()
    if (!text) return
    pendingTextRef.current = ''
    ask(text)
  }

  // Reset the "stopped talking" countdown every time new speech arrives.
  // Wait longer if the last word suggests you're still mid-thought.
  const armSilence = () => {
    clearTimeout(silenceTimerRef.current)
    const words = pendingTextRef.current.trim().toLowerCase().split(/\s+/).filter(Boolean)
    const last = (words[words.length - 1] || '').replace(/[^a-z]/g, '')
    const wait = last && CONTINUATION_WORDS.has(last) ? PAUSE_THINKING_MS : PAUSE_BASE_MS
    silenceTimerRef.current = setTimeout(endTurn, wait)
  }

  const startListening = (attempt = 0) => {
    if (!aliveRef.current) return
    teardownRecognizer()
    setPhase('listening')
    if (attempt === 0) {
      setTranscript('')
      pendingTextRef.current = ''
    }

    const onHeard = (text) => {
      if (statusRef.current !== 'listening' || !text) return
      pendingTextRef.current = text
      setTranscript(text)
      armSilence() // keep waiting while you're still talking
    }

    const recognizer = createRecognizer({
      continuous: true,
      lang: settings.voiceLang,
      onInterim: onHeard,
      onFinal: (_segment, fullBuffer) => onHeard(fullBuffer),
      onEnd: () => {
        if (!aliveRef.current || statusRef.current !== 'listening') return
        // If the browser ended the session but we already heard something,
        // that's the end of the turn — answer it. Otherwise keep listening.
        if (pendingTextRef.current.trim()) endTurn()
        else setTimeout(() => {
          if (aliveRef.current && statusRef.current === 'listening') startListening()
        }, 300)
      },
      onError: (code) => {
        if (code === 'not-allowed' || code === 'service-not-allowed' || code === 'audio-capture') {
          setPhase('error')
          setTranscript(ERROR_TEXT[code])
        }
        // no-speech / network / aborted → onEnd decides what to do
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
      // "recognition has already started" — the previous instance is still
      // releasing. Abort and retry a few times instead of silently dying.
      teardownRecognizer()
      if (attempt < 6) setTimeout(() => startListening(attempt + 1), 300)
      else {
        setPhase('error')
        setTranscript('Could not start the microphone. Close and reopen voice mode.')
      }
    }
  }

  const clearSpeechTimers = () => {
    clearInterval(resumeTimerRef.current)
    clearTimeout(speakWatchdogRef.current)
    clearTimeout(startWatchdogRef.current)
  }

  // Speak the reply. Hardened against Chrome's speech engine going stale after
  // long uptime / tab switches: if speech doesn't actually START within 2s we
  // kick it and retry once, then give up gracefully (the answer is already on
  // screen) and resume listening — so it can never get stuck "not answering".
  const speakAnswer = (answer, attempt = 0) => {
    if (!aliveRef.current) return
    const synth = window.speechSynthesis
    if (mutedRef.current || !synth) {
      startListening() // muted: answer is on screen, resume listening
      return
    }
    setPhase('speaking')
    clearSpeechTimers()
    try {
      synth.cancel()
      synth.resume() // wake an engine that a background tab left paused
    } catch {
      /* ignore */
    }

    const clean = answer.replace(/https?:\/\/\S+/g, 'link').replace(/[*_#`~>|•]/g, ' ').slice(0, 900)
    const utterance = new SpeechSynthesisUtterance(clean)
    utterance.lang = settings.voiceLang || navigator.language || 'en-IN'
    utterance.rate = 1.05

    let started = false
    const done = () => {
      if (statusRef.current !== 'speaking') return
      clearSpeechTimers()
      startListening()
    }
    utterance.onstart = () => {
      started = true
      clearTimeout(startWatchdogRef.current)
      // keep long replies alive (Chrome pauses them) …
      resumeTimerRef.current = setInterval(() => {
        if (synth.speaking) synth.resume()
        else clearInterval(resumeTimerRef.current)
      }, 4000)
      // … and never hang in "speaking" if onend is missed
      speakWatchdogRef.current = setTimeout(() => {
        if (statusRef.current === 'speaking') {
          try {
            synth.cancel()
          } catch {
            /* ignore */
          }
          done()
        }
      }, Math.min(60000, 4000 + clean.length * 90))
    }
    utterance.onend = done
    utterance.onerror = done

    setTimeout(() => {
      if (!aliveRef.current || statusRef.current !== 'speaking') return
      try {
        synth.speak(utterance)
      } catch {
        done()
        return
      }
      // If the engine is dead it never fires onstart — recover fast.
      startWatchdogRef.current = setTimeout(() => {
        if (started || statusRef.current !== 'speaking') return
        try {
          synth.cancel()
        } catch {
          /* ignore */
        }
        if (attempt < 1) speakAnswer(answer, attempt + 1) // one kick + retry
        else done() // truly stuck — answer is on screen, go back to listening
      }, 2000)
    }, 90)
  }

  const ask = async (question) => {
    teardownRecognizer() // stop listening while we think/speak (no echo, no churn)
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
      const limitHit = /limit|quota|rate/i.test(e.message)
      setLastExchange({ question, answer: e.message, error: true })
      toast(e.message, 'error')
      speakAnswer(limitHit
        ? "You've run out of your AI usage limit for now. Add your own key in settings, or try again later."
        : 'Sorry, that request failed. ' + e.message)
    }
  }

  const stopSpeakingNow = () => {
    clearSpeechTimers()
    try {
      window.speechSynthesis?.cancel()
    } catch {
      /* ignore */
    }
    if (statusRef.current === 'speaking') startListening()
  }

  const toggleMute = () => {
    setMuted((m) => {
      const next = !m
      mutedRef.current = next
      if (next && statusRef.current === 'speaking') stopSpeakingNow()
      return next
    })
  }

  useEffect(() => {
    aliveRef.current = true
    try {
      window.speechSynthesis?.getVoices() // warm up the speech engine early
    } catch {
      /* ignore */
    }
    startListening()

    // Coming back to this tab after it was in the background (or another window
    // grabbed the mic/speech engine): the recognizer usually died and speech
    // got paused. Recover so voice keeps working after long / multi-window use.
    const onVisible = () => {
      if (document.hidden || !aliveRef.current) return
      if (statusRef.current === 'listening') startListening()
      else if (statusRef.current === 'speaking') {
        try {
          window.speechSynthesis?.resume()
        } catch {
          /* ignore */
        }
      }
    }
    document.addEventListener('visibilitychange', onVisible)

    return () => {
      aliveRef.current = false
      document.removeEventListener('visibilitychange', onVisible)
      clearSpeechTimers()
      teardownRecognizer()
      try {
        window.speechSynthesis?.cancel()
      } catch {
        /* ignore */
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const STATUS_TEXT = {
    listening: 'Listening — just speak',
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
          ? 'Just talk — I reply, then listen again. Tap ✕ to end the call.'
          : 'Listening needs Chrome or Edge'}
      </div>
    </div>
  )
}
