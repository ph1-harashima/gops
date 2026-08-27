import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// G-SYS Online Ordering Prototype - New Portal Frontend.
// Talks ONLY to New Service API (com.glv.gsysportal backend), never to
// Legacy or Prototype databases directly (Technical Design 1章/26章).
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
