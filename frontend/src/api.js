// Tiny fetch wrapper: attaches the login token, parses JSON, and turns
// backend errors ({ "error": "..." }) into thrown Errors with that message.

const TOKEN_KEY = 'sahayak_token'

export const tokenStore = {
  get: () => localStorage.getItem(TOKEN_KEY),
  set: (token) => localStorage.setItem(TOKEN_KEY, token),
  clear: () => localStorage.removeItem(TOKEN_KEY),
}

export async function api(path, { method = 'GET', body } = {}) {
  const headers = { 'Content-Type': 'application/json' }
  const token = tokenStore.get()
  if (token) headers.Authorization = `Bearer ${token}`

  const res = await fetch(`/api${path}`, {
    method,
    headers,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })

  // Session expired or revoked → drop the token and let App show the login screen.
  if (res.status === 401 && token) {
    tokenStore.clear()
    window.dispatchEvent(new Event('sahayak:unauthorized'))
  }

  let data = null
  try {
    data = await res.json()
  } catch {
    /* no body (e.g. 204) */
  }
  if (!res.ok) throw new Error(data?.error || `Request failed (${res.status})`)
  return data
}
