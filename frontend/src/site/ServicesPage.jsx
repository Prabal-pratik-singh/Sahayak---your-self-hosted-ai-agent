import { Link } from 'react-router-dom'

// Every card is honest: "Live" ships today, "Coming soon" is roadmap.
const SERVICES = [
  {
    icon: '💬', title: 'AI Chat', status: 'Live',
    text: 'Streaming conversations with 8 engines — Claude, ChatGPT, Gemini, Groq, GitHub Models, Cerebras, Mistral, OpenRouter. Bring your own keys or use the server\'s; switch brains per message.',
  },
  {
    icon: '⚙️', title: 'Task Automation', status: 'Live',
    text: '"Post this tomorrow at 6 PM." Orders become scheduled tasks that run on time — visible on a board and a calendar, cancellable anytime.',
  },
  {
    icon: '🎙️', title: 'Voice Assistant', status: 'Live',
    text: 'Push-to-talk in chat, or a hands-free call: it listens, replies out loud, then listens again — like a phone call, no wake word needed.',
  },
  {
    icon: '🖼️', title: 'Image Understanding', status: 'Live',
    text: 'Attach a photo and ask "what does it say?" Vision-capable engines read it as a real image — and if your current engine can\'t see, the message auto-routes to one that can, with an honest note.',
  },
  {
    icon: '📄', title: 'Document Analysis', status: 'Live',
    text: 'Attach a PDF, Word, text, markdown or CSV file and ask anything — the text is extracted server-side (Apache Tika) and summarized or answered right in chat.',
  },
  {
    icon: '🌐', title: 'Internet Lookups', status: 'Live',
    text: 'Live weather for any place, Wikipedia knowledge, and a safety-guarded reader for public web pages — built in, no extra keys.',
  },
  {
    icon: '⏰', title: 'Scheduling & Reminders', status: 'Live',
    text: 'One-time reminders and future actions stored in Postgres. If the server restarts, your schedule does not care.',
  },
  {
    icon: '✉️', title: 'Email Assistance', status: 'Live',
    text: 'Drafts with you in chat, then sends from your own mailbox (Gmail, Outlook, Zoho — any SMTP). Always confirms before sending.',
  },
  {
    icon: '📣', title: 'Social & Messaging', status: 'Live',
    text: 'Publish LinkedIn posts as you — text, one image, or a multi-photo post, now or scheduled — and send to Telegram, Discord and Slack. WhatsApp / Instagram / X are blocked by their platform APIs; we say so instead of faking it.',
  },
  {
    icon: '🐙', title: 'GitHub', status: 'Live',
    text: 'Connect with one click, then create issues, list your repositories and search issues straight from chat.',
  },
  {
    icon: '🧠', title: 'Personal Memory', status: 'Live',
    text: '"Remember that I prefer short replies." Saved as notes, applied to every future conversation, fully editable.',
  },
  {
    icon: '🔁', title: 'Recurring Automations', status: 'Coming soon',
    text: '"Every Monday at 9 AM, send my week plan to Telegram." Cron-style repetition on top of the existing scheduler.',
  },
]

export default function ServicesPage() {
  return (
    <div className="site-page view-in">
      <section className="site-hero">
        <h1>
          One agent, <span className="grad-text">many hands</span>.
        </h1>
        <p>
          Everything below ships in the box — the only setup is your AI key and, for actions,
          connecting your own accounts on the Integrations page.
        </p>
      </section>

      <section className="site-section">
        <div className="site-grid services">
          {SERVICES.map((s) => (
            <div key={s.title} className="card site-feature">
              <div className="svc-head">
                <span className="tool-icon" aria-hidden="true">{s.icon}</span>
                <span className={`chip ${s.status === 'Live' ? 'ok' : ''}`}>{s.status}</span>
              </div>
              <b>{s.title}</b>
              <p>{s.text}</p>
            </div>
          ))}
        </div>
      </section>

      <section className="site-section">
        <h2>How a request flows</h2>
        <div className="card glassy site-flow">
          <span>You ask — by text or voice</span>
          <span aria-hidden="true">→</span>
          <span>Your chosen AI plans &amp; picks tools</span>
          <span aria-hidden="true">→</span>
          <span>Tools act on <b>your</b> accounts only</span>
          <span aria-hidden="true">→</span>
          <span>You get an honest result</span>
        </div>
      </section>

      <section className="cta-band card glassy">
        <h2>See it work in two minutes.</h2>
        <Link to="/" className="btn">Open Sahayak</Link>
      </section>
    </div>
  )
}
