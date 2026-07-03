import { useState } from 'react'
import { Link } from 'react-router-dom'
import { api, tokenStore } from '../api.js'
import { SparkIcon } from './Icons.jsx'

const FEATURES = [
  'Talk by voice or text — it answers, acts, and remembers',
  'Live weather, Wikipedia and web lookups built in',
  'Email, LinkedIn, Telegram, Discord & Slack — your own accounts',
  'Schedule anything: "post this tomorrow at 6 PM"',
]

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
      <div className="auth-hero">
        <div className="side-brand">
          <span className="orb big" aria-hidden="true" />
          <span className="wordmark xl">Sahayak</span>
        </div>
        <h1>
          Your personal AI agent.
          <br />
          <span className="grad-text">Self-hosted. Actually yours.</span>
        </h1>
        <ul className="auth-features">
          {FEATURES.map((f) => (
            <li key={f}>
              <SparkIcon /> {f}
            </li>
          ))}
        </ul>
      </div>

      <div className="auth-card card">
        <div className="tabs">
          <button
            className={mode === 'login' ? 'tab active' : 'tab'}
            onClick={() => {
              setMode('login')
              setError('')
            }}
          >
            Log in
          </button>
          <button
            className={mode === 'register' ? 'tab active' : 'tab'}
            onClick={() => {
              setMode('register')
              setError('')
            }}
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
                placeholder="Prabal"
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

          <button className="btn wide" type="submit" disabled={busy}>
            {busy ? 'One moment…' : mode === 'login' ? 'Log in' : 'Create account'}
          </button>
        </form>

        <nav className="auth-site-links" aria-label="About this project">
          <Link to="/about">About</Link>
          <Link to="/services">Services</Link>
          <Link to="/contact">Contact</Link>
        </nav>
      </div>
    </div>
  )
}
