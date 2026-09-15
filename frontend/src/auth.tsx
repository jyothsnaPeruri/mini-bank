import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { api, configureApi } from './api'
import type { AuthResponse, User } from './types'
import { AuthContext, type AuthContextValue } from './useAuth'

interface Session {
  token: string
  user: User
}

const STORAGE_KEY = 'minibank.session'

function isExpired(token: string) {
  try {
    const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')))
    return typeof payload.exp !== 'number' || payload.exp * 1000 < Date.now()
  } catch {
    return true
  }
}

function loadSession(): Session | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY)
    if (!stored) return null
    const session = JSON.parse(stored) as Session
    return isExpired(session.token) ? null : session
  } catch {
    return null
  }
}

function saveSession(session: Session | null) {
  try {
    if (session) localStorage.setItem(STORAGE_KEY, JSON.stringify(session))
    else localStorage.removeItem(STORAGE_KEY)
  } catch {
    // Storage can be unavailable (private browsing); the session then lasts until the tab closes
  }
}

// Module-level so the API client can read the token synchronously, even in the first render's effects
let currentToken: string | null = null
let signOut: () => void = () => {}
configureApi({ getToken: () => currentToken, onUnauthorized: () => signOut() })

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(() => {
    const loaded = loadSession()
    currentToken = loaded?.token ?? null
    return loaded
  })

  const updateSession = useCallback((next: Session | null) => {
    currentToken = next?.token ?? null
    saveSession(next)
    setSession(next)
  }, [])

  const logout = useCallback(() => updateSession(null), [updateSession])

  // If the server rejects the token (expired, or the server restarted), send the user back to sign in
  useEffect(() => {
    signOut = logout
  }, [logout])

  const value = useMemo<AuthContextValue>(
    () => ({
      user: session?.user ?? null,
      login: async (email, password) => {
        const response = await api<AuthResponse>('/api/auth/login', { method: 'POST', body: { email, password } })
        updateSession(response)
        return response.user
      },
      register: async (fullName, email, password) => {
        const response = await api<AuthResponse>('/api/auth/register', {
          method: 'POST',
          body: { fullName, email, password },
        })
        updateSession(response)
        return response.user
      },
      logout,
    }),
    [session, updateSession, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
