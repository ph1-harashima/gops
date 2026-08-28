import { defineConfig, devices } from '@playwright/test'

/**
 * Step 5 7章: single Core Demo Scenario E2E test.
 *
 * Assumes the Backend (http://localhost:8080) and Frontend dev server
 * (http://localhost:5173) are already running against the local Prototype
 * PostgreSQL / Legacy Demo MySQL (Safety Guard applies to the Backend
 * process itself, same as any other manual run - this config does not
 * start or manage either server, matching the "no accidental prod/shared
 * target" principle used throughout Step 5).
 *
 * Selector policy (Step 5 8章 finding): coordinate-based clicks are a known
 * automation-tool artifact unrelated to the app. This suite exclusively
 * uses data-testid / role / label locators, never raw coordinates.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 60_000,
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
})
