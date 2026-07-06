// Tiny fetch wrapper: attaches the login token, parses JSON, and turns
// backend errors ({ "error": "..." }) into thrown Errors with that message.

const TOKEN_KEY = 'sahayak_token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

function authHeaders() {
  const headers = { 'Content-Type': 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function handleUnauthorized(res) {
  if (res.status === 401 && tokenStore.get()) {
    tokenStore.clear()
    window.dispatchEvent(new Event('sahayak:unauthorized'))
  }
}

/** Turns AbortSignal timeouts into readable errors instead of DOMException noise. */
function friendlyNetworkError(err) {
  if (err?.name === 'TimeoutError' || err?.name === 'AbortError') {
    return new Error('The server took too long to respond. Please try again.')
  }
  if (err instanceof TypeError) {
    return new Error('Could not reach the server. Is the backend running?')
  }
  return err
}

export async function api(path, { method = 'GET', body, signal } = {}) {
  let res
  try {
    res = await fetch(`/api${path}`, {
      method,
      headers: authHeaders(),
      body: body !== undefined ? JSON.stringify(body) : undefined,
      // Chat turns with tools can be slow, but nothing should hang forever.
      signal: signal ?? AbortSignal.timeout(90_000),
    })
  } catch (err) {
    throw friendlyNetworkError(err)
  }
  handleUnauthorized(res)
  let data = null
  try {
    data = await res.json()
  } catch {
    /* no body (e.g. 204) */
  }
  if (!res.ok) throw new Error(data?.error || `Request failed (${res.status})`)
  return data
}

/**
 * Uploads one file as multipart/form-data and returns its stored reference
 * ({ id, filename, mime, kind, size }). We must NOT set Content-Type here —
 * the browser adds the multipart boundary itself.
 */
export async function uploadFile(file, conversationId) {
  const form = new FormData()
  form.append('file', file)
  if (conversationId) form.append('conversationId', String(conversationId))

  const headers = {}
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  let res
  try {
    res = await fetch('/api/attachments', {
      method: 'POST',
      headers,
      body: form,
      signal: AbortSignal.timeout(120_000),
    })
  } catch (err) {
    throw friendlyNetworkError(err)
  }
  handleUnauthorized(res)
  let data = null
  try {
    data = await res.json()
  } catch {
    /* no body */
  }
  if (!res.ok) throw new Error(data?.error || `Upload failed (${res.status})`)
  return data
}

/**
 * Streaming chat over Server-Sent Events. The backend emits "token" events
 * (JSON-encoded string chunks), then one "done" event; failures arrive as a
 * single "error" event. Resolves when the stream ends; throws early if the
 * request itself is rejected (so the caller can fall back to /api/chat).
 */
export async function streamChat(body, { onToken, onError, signal } = {}) {
  let res
  try {
    res = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { ...authHeaders(), Accept: 'text/event-stream' },
      body: JSON.stringify(body),
      // Hard cap on a whole streamed turn — a stalled stream must not spin forever.
      signal: signal ?? AbortSignal.timeout(180_000),
    })
  } catch (err) {
    throw friendlyNetworkError(err)
  }
  handleUnauthorized(res)
  if (!res.ok || !res.body) {
    let data = null
    try {
      data = await res.json()
    } catch {
      /* not json */
    }
    throw new Error(data?.error || `Request failed (${res.status})`)
  }

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const dispatch = (rawEvent) => {
    let event = 'message'
    const dataLines = []
    for (const line of rawEvent.split('\n')) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      else if (line.startsWith('data:')) dataLines.push(line.slice(5).trimStart())
    }
    if (dataLines.length === 0) return
    let payload = dataLines.join('\n')
    try {
      payload = JSON.parse(payload)
    } catch {
      /* keep raw */
    }
    if (event === 'token') onToken?.(payload)
    else if (event === 'error') onError?.(payload)
  }

  try {
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      let boundary
      while ((boundary = buffer.indexOf('\n\n')) >= 0) {
        const rawEvent = buffer.slice(0, boundary)
        buffer = buffer.slice(boundary + 2)
        if (rawEvent.trim()) dispatch(rawEvent)
      }
    }
  } catch (err) {
    // Mid-stream stall/abort: report it like a server error event so the
    // caller shows a message instead of a forever-blank bubble.
    onError?.(friendlyNetworkError(err).message)
  }
}
