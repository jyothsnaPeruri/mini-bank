import { useEffect, useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router'
import { ApiError, warmUpServer } from '../api'
import { DEMO_EMAIL, DEMO_PASSWORD, useAuth } from '../useAuth'
import { Alert } from '../components/Alert'
import { Logo } from '../components/Logo'
import { ServerWakeNotice } from '../components/ServerWakeNotice'
import type { User } from '../types'

type Mode = 'signin' | 'register'

const GITHUB_URL = 'https://github.com/jyothsnaPeruri/mini-bank'

export function LoginPage() {
  const { user, login, register } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [mode, setMode] = useState<Mode>('signin')
  const [fullName, setFullName] = useState('')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState<ApiError | null>(null)

  // Start waking the free-tier server while the visitor reads the page
  useEffect(warmUpServer, [])

  if (user) return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/'} replace />

  const goHome = (signedIn: User) => {
    const from = (location.state as { from?: string } | null)?.from
    navigate(signedIn.role === 'ADMIN' ? '/admin' : (from ?? '/'), { replace: true })
  }

  const run = async (action: () => Promise<User>) => {
    setSubmitting(true)
    setError(null)
    try {
      goHome(await action())
    } catch (err) {
      setError(err as ApiError)
      setSubmitting(false)
    }
  }

  const onSubmit = (event: FormEvent) => {
    event.preventDefault()
    run(() => (mode === 'signin' ? login(email, password) : register(fullName, email, password)))
  }

  const switchMode = (next: Mode) => {
    setMode(next)
    setError(null)
  }

  const fieldError = (field: string) => error?.fieldErrors[field]

  return (
    <div className="auth-page">
      <section className="auth-hero">
        <Logo />
        <h1>Banking basics, built properly.</h1>
        <p className="auth-hero-lead">
          A full-stack demo bank: open accounts, move money between them and pay other customers, with a real ledger
          behind every dollar.
        </p>
        <ul className="auth-points">
          <li>
            <strong>Transfers can't half-happen.</strong> Each one is a single database transaction that locks both
            accounts.
          </li>
          <li>
            <strong>Double-clicks are safe.</strong> Idempotency keys make sure a retried payment is only sent once.
          </li>
          <li>
            <strong>Secure by default.</strong> JWT sign-in, BCrypt passwords and role-based admin access.
          </li>
        </ul>
        <p className="auth-stack">Spring Boot · Spring Security · PostgreSQL · React · TypeScript</p>
      </section>

      <section className="auth-panel">
        <ServerWakeNotice />
        <div className="card auth-card">
          <div className="tabs" role="tablist">
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'signin'}
              className={mode === 'signin' ? 'tab active' : 'tab'}
              onClick={() => switchMode('signin')}
            >
              Sign in
            </button>
            <button
              type="button"
              role="tab"
              aria-selected={mode === 'register'}
              className={mode === 'register' ? 'tab active' : 'tab'}
              onClick={() => switchMode('register')}
            >
              Create account
            </button>
          </div>

          <button
            type="button"
            className="btn btn-demo"
            disabled={submitting}
            onClick={() => run(() => login(DEMO_EMAIL, DEMO_PASSWORD))}
          >
            Try the demo account
            <span className="btn-demo-sub">No sign-up needed · sample data included</span>
          </button>

          <div className="divider">
            <span>or {mode === 'signin' ? 'sign in with email' : 'create your own'}</span>
          </div>

          {error && Object.keys(error.fieldErrors).length === 0 && <Alert>{error.message}</Alert>}

          <form onSubmit={onSubmit} noValidate>
            {mode === 'register' && (
              <div className="field">
                <label htmlFor="fullName">Full name</label>
                <input
                  id="fullName"
                  value={fullName}
                  onChange={(e) => setFullName(e.target.value)}
                  autoComplete="name"
                  aria-invalid={!!fieldError('fullName')}
                />
                {fieldError('fullName') && <p className="field-error">{fieldError('fullName')}</p>}
              </div>
            )}
            <div className="field">
              <label htmlFor="email">Email</label>
              <input
                id="email"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                aria-invalid={!!fieldError('email')}
              />
              {fieldError('email') && <p className="field-error">{fieldError('email')}</p>}
            </div>
            <div className="field">
              <label htmlFor="password">Password</label>
              <input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete={mode === 'signin' ? 'current-password' : 'new-password'}
                aria-invalid={!!fieldError('password')}
              />
              {fieldError('password') ? (
                <p className="field-error">{fieldError('password')}</p>
              ) : (
                mode === 'register' && <p className="field-hint">At least 8 characters</p>
              )}
            </div>
            <button type="submit" className="btn btn-primary btn-block" disabled={submitting}>
              {submitting ? 'Please wait…' : mode === 'signin' ? 'Sign in' : 'Create account'}
            </button>
            {mode === 'register' && (
              <p className="field-hint center">New accounts start with $1,000 of demo money.</p>
            )}
          </form>
        </div>
        <p className="auth-footer">
          Built by Jyothsna Peruri ·{' '}
          <a href={GITHUB_URL} target="_blank" rel="noreferrer">
            View source on GitHub
          </a>
        </p>
      </section>
    </div>
  )
}
