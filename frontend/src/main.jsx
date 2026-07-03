import React, { lazy, Suspense } from 'react'
import ReactDOM from 'react-dom/client'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import App from './App.jsx'
import PublicLayout from './site/PublicLayout.jsx'
import './index.css'

// Marketing pages load on demand; the app itself stays the fast path.
const AboutPage = lazy(() => import('./site/AboutPage.jsx'))
const ServicesPage = lazy(() => import('./site/ServicesPage.jsx'))
const ContactPage = lazy(() => import('./site/ContactPage.jsx'))

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <BrowserRouter>
      <Routes>
        <Route element={<PublicLayout />}>
          <Route
            path="/about"
            element={
              <Suspense fallback={null}>
                <AboutPage />
              </Suspense>
            }
          />
          <Route
            path="/services"
            element={
              <Suspense fallback={null}>
                <ServicesPage />
              </Suspense>
            }
          />
          <Route
            path="/contact"
            element={
              <Suspense fallback={null}>
                <ContactPage />
              </Suspense>
            }
          />
        </Route>
        {/* The app itself (login + dashboard) stays at the root. */}
        <Route path="/*" element={<App />} />
      </Routes>
    </BrowserRouter>
  </React.StrictMode>,
)
