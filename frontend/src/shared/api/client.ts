import axios from 'axios'

// Sole entry point to the backend. The Frontend never talks to Legacy or
// Prototype databases directly (Technical Design 1章). /api is proxied to
// the New Service API by Vite in dev (see vite.config.ts) and would be
// served from the same origin in a production build.
export const apiClient = axios.create({
  baseURL: '/api',
  // Spring Security Session Form Login (portal_user) relies on the JSESSIONID
  // cookie (Technical Design 7章) - always send/accept it, even though the
  // dev proxy already makes this same-origin from the browser's perspective.
  withCredentials: true,
})
