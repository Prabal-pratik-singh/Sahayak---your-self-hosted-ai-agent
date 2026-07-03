// Thin wrappers around the browser speech APIs.
// Recognition (listening) exists in Chrome/Edge; synthesis (speaking) is everywhere.

export const SpeechRecognitionImpl =
  typeof window !== 'undefined' ? window.SpeechRecognition || window.webkitSpeechRecognition : null

export const speechSupported = Boolean(SpeechRecognitionImpl)

export function speak(text, { rate = 1.05, onStart, onEnd } = {}) {
  const synth = window.speechSynthesis
  if (!synth || !text) {
    onEnd?.()
    return
  }
  synth.cancel()
  const clean = text
    .replace(/https?:\/\/\S+/g, 'link')
    .replace(/[*_#`~>|•]/g, ' ')
    .slice(0, 900)
  const utterance = new SpeechSynthesisUtterance(clean)
  utterance.lang = navigator.language || 'en-IN'
  utterance.rate = rate
  utterance.onstart = () => onStart?.()
  utterance.onend = () => onEnd?.()
  utterance.onerror = () => onEnd?.()
  synth.speak(utterance)
}

export function stopSpeaking() {
  window.speechSynthesis?.cancel()
}

/**
 * Creates a recognizer. Call .start(); it reports interim text as the user
 * speaks and final segments when they pause. Returns null if unsupported.
 */
export function createRecognizer({ continuous = false, onInterim, onFinal, onEnd, onError } = {}) {
  if (!SpeechRecognitionImpl) return null
  const recognition = new SpeechRecognitionImpl()
  recognition.lang = navigator.language || 'en-IN'
  recognition.interimResults = true
  recognition.continuous = continuous

  let finalBuffer = ''
  recognition.onresult = (event) => {
    let interim = ''
    let freshFinal = ''
    for (let i = event.resultIndex; i < event.results.length; i++) {
      const result = event.results[i]
      if (result.isFinal) freshFinal += result[0].transcript
      else interim += result[0].transcript
    }
    if (freshFinal) {
      finalBuffer += freshFinal
      onFinal?.(freshFinal.trim(), finalBuffer.trim())
    }
    onInterim?.((finalBuffer + ' ' + interim).trim())
  }
  recognition.onend = () => onEnd?.(finalBuffer.trim())
  recognition.onerror = (event) => onError?.(event.error)
  return recognition
}
