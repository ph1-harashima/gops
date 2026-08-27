import { apiClient } from '../../shared/api/client'
import type { CurrentUser } from '../../shared/types/auth'

/** GET /api/auth/me - 401 (not a thrown error the caller needs to unwrap
 * specially) simply means "not logged in", so callers treat a rejected
 * promise as "no session" rather than a fatal error. */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  try {
    const { data } = await apiClient.get<CurrentUser>('/auth/me')
    return data
  } catch {
    return null
  }
}

export async function login(username: string, password: string): Promise<CurrentUser> {
  const params = new URLSearchParams()
  params.set('username', username)
  params.set('password', password)
  const { data } = await apiClient.post<CurrentUser>('/auth/login', params, {
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  })
  return data
}

export async function logout(): Promise<void> {
  await apiClient.post('/auth/logout')
}
