import { Link } from 'react-router-dom'

const FEATURES = [
  { icon: '🧠', title: 'Eight AI engines', text: 'Claude, ChatGPT, Gemini, Groq and more — bring your own keys, switch per message.' },
  { icon: '🎙️', title: 'Real voice mode', text: 'Talk hands-free like a phone call — it listens, acts, answers out loud, then listens again.' },
  { icon: '📎', title: 'Files & vision', text: 'Attach images and documents; it sees photos, reads PDFs, and answers about them in chat.' },
  { icon: '🌐', title: 'Live internet tools', text: 'Weather anywhere, Wikipedia, and a guarded web-page reader — no extra keys.' },
  { icon: '📌', title: 'Long-term memory', text: 'Say "remember…" once; it personalizes every future conversation.' },
  { icon: '⏰', title: 'Scheduling that survives', text: 'Tasks live in Postgres and run on time even after restarts.' },
  { icon: '🔐', title: 'Private by design', text: 'Accounts, encrypted credentials, per-user isolation enforced in code.' },
]

const STACK = [
  'Spring Boot 3.5', 'Spring AI 1.1', 'Java 21', 'PostgreSQL 16',
  'React 18', 'Vite', 'Web Speech API', 'Docker',
]

const ROADMAP = [
  { when: 'Shipped', what: 'Multi-user accounts, encrypted per-user integrations, scheduler' },
  { when: 'Shipped', what: '8 AI engines with bring-your-own-key and per-message switching' },
  { when: 'Shipped', what: 'Voice mode, web tools, long-term memory, streaming replies' },
  { when: 'Shipped', what: 'Attachments: image vision + PDF/Word/CSV analysis in chat' },
  { when: 'Shipped', what: 'LinkedIn image posts (single & multi-photo, schedulable), GitHub, activity feed' },
  { when: 'Next', what: 'Recurring tasks ("every Monday 9 AM") and approval buttons for risky actions' },
  { when: 'Later', what: 'Server-side voice (works in every browser), Google Calendar, WhatsApp Business' },
]

export default function AboutPage() {
  return (
    <div className="site-page view-in">
      <section className="site-hero">
        <span className="orb big" aria-hidden="true" />
        <h1>
          A personal AI agent that is <span className="grad-text">actually yours</span>.
        </h1>
        <p>
          Sahayak (Hindi: <i>helper</i>) is a self-hosted AI assistant. It chats, speaks, sees
          your images, reads your documents, remembers what matters, and takes real actions —
          email, LinkedIn, GitHub, Telegram, Discord, Slack — through <b>your own</b> accounts,
          on <b>your own</b> server.
        </p>
        <div className="site-hero-actions">
          <Link to="/" className="btn">Try it now</Link>
          <Link to="/services" className="btn ghost">See what it does</Link>
        </div>
      </section>

      <section className="site-section">
        <h2>The vision</h2>
        <div className="card glassy site-vision">
          <p>
            Cloud assistants know everything about you and belong to someone else. Sahayak flips
            that: one person (or a small circle) runs it themselves, brings their own AI keys,
            connects their own apps, and keeps every conversation, note and credential in their
            own database. No middlemen, no data brokers — a Jarvis you can read the source of.
          </p>
        </div>
      </section>

      <section className="site-section">
        <h2>Key features</h2>
        <div className="site-grid">
          {FEATURES.map((f) => (
            <div key={f.title} className="card site-feature">
              <span className="tool-icon" aria-hidden="true">{f.icon}</span>
              <b>{f.title}</b>
              <p>{f.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="site-section">
        <h2>Why people use it</h2>
        <ul className="site-why card">
          <li><b>Ownership</b> — your API keys, your database, your rules. Turn signups off and it's a private butler.</li>
          <li><b>Honesty</b> — it never fakes success: every action reports what really happened, and unsupported platforms say so openly.</li>
          <li><b>One place</b> — chat, voice, reminders, messaging and posting live in a single calm interface.</li>
          <li><b>Hackable</b> — adding a new integration is three small classes; the README shows how.</li>
        </ul>
      </section>

      <section className="site-section">
        <h2>Built with</h2>
        <div className="stack-chips">
          {STACK.map((s) => (
            <span key={s} className="chip">{s}</span>
          ))}
        </div>
      </section>

      <section className="site-section">
        <h2>Roadmap</h2>
        <div className="timeline site-roadmap">
          {ROADMAP.map((r, i) => (
            <div key={i} className="timeline-item">
              <span className={`timeline-dot ${r.when === 'Shipped' ? 'done' : r.when === 'Next' ? 'scheduled' : 'chat'}`} />
              <span className="timeline-text">{r.what}</span>
              <span className={`chip ${r.when === 'Shipped' ? 'ok' : ''}`}>{r.when}</span>
            </div>
          ))}
        </div>
      </section>

      <section className="cta-band card glassy">
        <h2>Your agent is a login away.</h2>
        <Link to="/" className="btn">Open Sahayak</Link>
      </section>
    </div>
  )
}
