import { useApp } from '../App.jsx'

// Launcher: every real capability, one click away. Clicking drops a ready
// prompt into a fresh chat — no fake tools, these all exist server-side.

const TOOLS = [
  { icon: '🌦️', name: 'Live weather', desc: 'Current conditions + 2-day forecast, anywhere', prompt: "What's the weather in " },
  { icon: '📚', name: 'Wikipedia', desc: 'Facts and background on anything', prompt: 'Look up on Wikipedia: ' },
  { icon: '🔗', name: 'Read a page', desc: 'Fetches a public URL and reads it for you', prompt: 'Read this page and summarize it: ' },
  { icon: '🧠', name: 'Remember', desc: 'Long-term notes it keeps about you', prompt: 'Remember that ' },
  { icon: '🗒️', name: 'My notes', desc: 'See everything it remembers', prompt: 'What notes have you saved about me?' },
  { icon: '⏰', name: 'Schedule', desc: 'Run anything later — it survives restarts', prompt: 'Tomorrow at 9 AM, ' },
  { icon: '✉️', name: 'Email', desc: 'Send from your own mailbox (connect first)', prompt: 'Send an email to ' },
  { icon: '💼', name: 'LinkedIn', desc: 'Draft & publish posts as you (connect first)', prompt: 'Draft a LinkedIn post about ' },
  { icon: '📨', name: 'Telegram', desc: 'Message your Telegram chat (connect first)', prompt: 'Send this on Telegram: ' },
  { icon: '🎮', name: 'Discord', desc: 'Post to your channel (connect first)', prompt: 'Post this on Discord: ' },
  { icon: '💬', name: 'Slack', desc: 'Post to your channel (connect first)', prompt: 'Post this on Slack: ' },
  { icon: '🗓️', name: 'Summarize my day', desc: 'Tasks + reminders in one answer', prompt: 'List my scheduled tasks and summarize what is coming up.' },
]

export default function ToolsView() {
  const { setPrefill, newChat } = useApp()

  const launch = async (tool) => {
    setPrefill(tool.prompt)
    await newChat()
  }

  return (
    <div className="tools view-in">
      <div className="tool-grid">
        {TOOLS.map((t) => (
          <button key={t.name} className="card tool-card" onClick={() => launch(t)}>
            <span className="tool-icon">{t.icon}</span>
            <b>{t.name}</b>
            <p>{t.desc}</p>
          </button>
        ))}
      </div>
    </div>
  )
}
