import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

// G-OPS i18n完成 #14: Vitest for unit/component tests only (i18n resource
// parity, LanguageSwitcher, persistence) - kept as its own config file,
// separate from vite.config.ts, so the existing dev server / production
// build pipeline is untouched. Playwright (`npm run test:e2e`) remains the
// tool for full end-to-end business scenarios; this config never runs
// against a real Backend/Postgres and never touches Business Logic.
export default defineConfig({
  plugins: [react()],
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: ['./src/test/setupTests.ts'],
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
