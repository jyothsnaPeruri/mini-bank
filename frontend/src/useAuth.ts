import { createContext, useContext } from 'react'
import type { User } from './types'

export interface AuthContextValue {
  user: User | null
  login: (email: string, password: string) => Promise<User>
  register: (fullName: string, email: string, password: string) => Promise<User>
  logout: () => void
}

export const DEMO_EMAIL = 'demo@minibank.dev'
export const DEMO_PASSWORD = 'Demo@1234'

export const AuthContext = createContext<AuthContextValue | null>(null)

export function useAuth() {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used inside <AuthProvider>')
  return context
}
