import { useState } from 'react'
import { GITHUB_URL, LINKEDIN_URL } from './PublicLayout.jsx'

// Real backend: POST /api/contact stores the message in the database.

export default function ContactPage() {
  const [form, setForm] = useState({ name: '', email: '', message: '' })
  const [errors, setErrors] = useState({})
  const [busy, setBusy] = useState(false)
  const [sent, setSent] = useState(false)
  const [serverError, setServerError] = useState('')

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }))
    setErrors((er) => ({ ...er, [key]: undefined }))
  }

  const validate = () => {
    const er = {}
    if (!form.name.trim()) er.name = 'Please tell us your name.'
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(form.email.trim())) er.email = 'That email does not look right.'
    if (form.message.trim().length < 10) er.message = 'A few more words, please (at least 10 characters).'
    setErrors(er)
    return Object.keys(er).length === 0
  }

  const submit = async (e) => {
    e.preventDefault()
    if (busy || !validate()) return
    setBusy(true)
    setServerError('')
    try {
      const res = await fetch('/api/contact', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          name: form.name.trim(),
          email: form.email.trim(),
          message: form.message.trim(),
        }),
        signal: AbortSignal.timeout(20_000),
      })
      const data = await res.json().catch(() => null)
      if (!res.ok) throw new Error(data?.error || `Request failed (${res.status})`)
      setSent(true)
    } catch (err) {
      setServerError(
        err?.name === 'TimeoutError'
          ? 'The server took too long. Please try again.'
          : err.message,
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="site-page view-in">
      <section className="site-hero">
        <h1>
          Say <span className="grad-text">hello</span>.
        </h1>
        <p>Questions, ideas, bug reports — they all land in Sahayak's own database.</p>
      </section>

      <section className="site-section contact-grid">
        <div className="card contact-card">
          {sent ? (
            <div className="contact-success" role="status">
              <span className="orb big" aria-hidden="true" />
              <h2>Message received ✓</h2>
              <p>Thanks, {form.name.split(' ')[0] || 'friend'} — it's saved and will be read soon.</p>
              <button
                className="btn ghost"
                onClick={() => {
                  setSent(false)
                  setForm({ name: '', email: '', message: '' })
                }}
              >
                Send another
              </button>
            </div>
          ) : (
            <form onSubmit={submit} noValidate>
              <label className="field">
                Your name
                <input value={form.name} onChange={set('name')} maxLength={60} placeholder="Prabal" required />
                {errors.name && <span className="error-text">{errors.name}</span>}
              </label>
              <label className="field">
                Email
                <input type="email" value={form.email} onChange={set('email')} maxLength={190} placeholder="you@example.com" required />
                {errors.email && <span className="error-text">{errors.email}</span>}
              </label>
              <label className="field">
                Message
                <textarea
                  className="input contact-textarea"
                  value={form.message}
                  onChange={set('message')}
                  maxLength={2000}
                  rows={6}
                  placeholder="What's on your mind?"
                  required
                />
                {errors.message && <span className="error-text">{errors.message}</span>}
              </label>
              {serverError && <p className="error-text" role="alert">{serverError}</p>}
              <button className="btn wide" type="submit" disabled={busy}>
                {busy ? 'Sending…' : 'Send message'}
              </button>
            </form>
          )}
        </div>

        <div className="contact-side">
          <div className="card">
            <div className="card-title">Elsewhere</div>
            <a className="row-item" href={GITHUB_URL} target="_blank" rel="noopener noreferrer">
              <span className="tool-icon" aria-hidden="true">🐙</span>
              <span className="row-text">GitHub — source code &amp; issues</span>
            </a>
            <a className="row-item" href={LINKEDIN_URL} target="_blank" rel="noopener noreferrer">
              <span className="tool-icon" aria-hidden="true">💼</span>
              <span className="row-text">LinkedIn — Prabal Pratik Singh</span>
            </a>
          </div>
          <div className="card">
            <div className="card-title">Self-hosting?</div>
            <p className="empty-line">
              The README covers setup end to end. Found a bug? A GitHub issue with steps to
              reproduce is the fastest route to a fix.
            </p>
          </div>
        </div>
      </section>
    </div>
  )
}
