import { useState } from 'react'
import { api, tokenStore } from '../api.js'

export default function Login({ onLogin }) {
  const [mode, setMode] = useState('login')
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const submit = async (e) => {
    e.preventDefault()
    if (busy) return
    setError('')
    setBusy(true)
    try {
      const path = mode === 'login' ? '/auth/login' : '/auth/register'
      const body = mode === 'login' ? { email, password } : { name, email, password }
      const res = await api(path, { method: 'POST', body })
      tokenStore.set(res.token)
      onLogin(res.user)
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="auth">
      <div className="auth-card">
        <div className="brand auth-brand">
          <span className="wordmark">Sahayak</span>
          <span className="tagline">ops agent</span>
        </div>
        <p className="auth-blurb">
          Your personal AI agent: it chats, sends email from your account, posts on your
          LinkedIn, and runs tasks at the time you pick.
        </p>

        <div className="tabs">
          <button
            className={mode === 'login' ? 'tab active' : 'tab'}
            onClick={() => { setMode('login'); setError('') }}
          >
            Log in
          </button>
          <button
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => { setMode('register'); setError('') }}
          >
            Create account
          </button>
        </div>

        <form onSubmit={submit}>
          {mode === 'register' && (
            <label className="field">
              Your name
              <input
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="Abhishek"
                maxLength={60}
                required
              />
            </label>
          )}
          <label className="field">
            Email
            <input
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="you@example.com"
              required
            />
          </label>
          <label className="field">
            Password {mode === 'register' && <span className="hint-inline">(8+ characters)</span>}
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              minLength={mode === 'register' ? 8 : undefined}
              required
            />
          </label>

          {error && <p className="error-text">{error}</p>}

          <button className="send wide" type="submit" disabled={busy}>
            {busy ? 'One moment…' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
        </form>
      </div>
    </div>
  )
}
