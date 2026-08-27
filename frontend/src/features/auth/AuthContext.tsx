import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react'
import type { CurrentUser } from '../../shared/types/auth'
import { fetchCurrentUser, login as loginApi, logout as logoutApi } from './api'

interface AuthState {
  user: CurrentUser | null
  loading: boolean
  login: (username: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

const AuthContext = createContext<AuthState | undefined>(undefined)

/**
 * Every Draft create/read/update call requires a real authenticated
 * portal_user identity (implementation instructions 15章) - this Context is
 * the single place that tracks the session and is checked before any Draft
 * screen renders.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<CurrentUser | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    fetchCurrentUser().then((u) => {
      setUser(u)
      setLoading(false)
    })
  }, [])

  const login = useCallback(async (username: string, password: string) => {
    const u = await loginApi(username, password)
    setUser(u)
  }, [])

  const logout = useCallback(async () => {
    await logoutApi()
    setUser(null)
  }, [])

  return <AuthContext.Provider value={{ user, loading, login, logout }}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth must be used within AuthProvider')
  return ctx
}
