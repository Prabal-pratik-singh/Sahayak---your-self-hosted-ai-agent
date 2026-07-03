import { useEffect } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router-dom'

export const GITHUB_URL = 'https://github.com/Prabal-pratik-singh/Sahayak---your-self-hosted-ai-agent'
// TODO(Prabal): replace with your personal LinkedIn profile URL
export const LINKEDIN_URL = 'https://www.linkedin.com/in/'

const TITLES = {
  '/about': 'About — Sahayak',
  '/services': 'Services — Sahayak',
  '/contact': 'Contact — Sahayak',
}

/** Shared shell for the public marketing pages: top nav + footer. */
export default function PublicLayout() {
  const location = useLocation()

  // The app applies theme/accent from settings; do the same out here so the
  // marketing pages match what the user chose inside the app.
  useEffect(() => {
    try {
      const s = JSON.parse(localStorage.getItem('sahayak_settings') || '{}')
      const theme =
        s.theme === 'system' || !s.theme
          ? window.matchMedia?.('(prefers-color-scheme: light)').matches
            ? 'light'
            : 'dark'
          : s.theme
      document.documentElement.dataset.theme = theme
      if (s.accent) document.documentElement.dataset.accent = s.accent
    } catch {
      /* defaults apply */
    }
  }, [])

  useEffect(() => {
    document.title = TITLES[location.pathname] || 'Sahayak — personal AI agent'
    window.scrollTo?.(0, 0)
  }, [location.pathname])

  return (
    <div className="site">
      <header className="site-nav">
        <Link to="/" className="side-brand" aria-label="Sahayak home">
          <span className="orb" aria-hidden="true" />
          <span className="wordmark">Sahayak</span>
        </Link>
        <nav className="site-links" aria-label="Site">
          <NavLink to="/about">About</NavLink>
          <NavLink to="/services">Services</NavLink>
          <NavLink to="/contact">Contact</NavLink>
        </nav>
        <Link to="/" className="btn site-cta">
          Open the app
        </Link>
      </header>

      <main className="site-main" key={location.pathname}>
        <Outlet />
      </main>

      <footer className="site-footer">
        <div className="site-footer-inner">
          <div className="side-brand">
            <span className="orb" aria-hidden="true" />
            <span className="wordmark">Sahayak</span>
          </div>
          <p className="site-footer-line">
            Self-hosted personal AI agent · your keys, your server, your data.
          </p>
          <div className="site-links">
            <Link to="/about">About</Link>
            <Link to="/services">Services</Link>
            <Link to="/contact">Contact</Link>
            <a href={GITHUB_URL} target="_blank" rel="noopener noreferrer">
              GitHub
            </a>
          </div>
        </div>
      </footer>
    </div>
  )
}
