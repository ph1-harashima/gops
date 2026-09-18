import { describe, expect, it, beforeEach, vi } from 'vitest'

// G-OPS i18n完成 #14 (§3 persistence requirement, §14 persistence test):
// `../i18n/index.ts` reads localStorage exactly once, at module-init time
// (readStoredLanguage()), so simulating "reload the page after picking
// English" means re-importing a FRESH copy of the module - vi.resetModules()
// plus a dynamic import is the only way to re-run that init logic within
// one test file, since a normal top-level import is cached/shared across
// every test in the whole run.
const STORAGE_KEY = 'gsys-ui-language'

describe('UI language persistence', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.resetModules()
  })

  it('defaults to Japanese when localStorage has nothing stored yet', async () => {
    const { default: i18n } = await import('./index')
    expect(i18n.resolvedLanguage ?? i18n.language).toBe('ja')
  })

  it('restores English on init when localStorage already holds "en" (simulates reload after switching)', async () => {
    window.localStorage.setItem(STORAGE_KEY, 'en')
    const { default: i18n } = await import('./index')
    expect(i18n.resolvedLanguage ?? i18n.language).toBe('en')
  })

  it('falls back to Japanese for an unsupported/corrupted stored value rather than failing to load', async () => {
    window.localStorage.setItem(STORAGE_KEY, 'fr')
    const { default: i18n } = await import('./index')
    expect(i18n.resolvedLanguage ?? i18n.language).toBe('ja')
  })

  it('writes the chosen language back to localStorage whenever it changes, so the next init reads it', async () => {
    const { default: i18n } = await import('./index')
    await i18n.changeLanguage('en')
    expect(window.localStorage.getItem(STORAGE_KEY)).toBe('en')

    vi.resetModules()
    const { default: reloaded } = await import('./index')
    expect(reloaded.resolvedLanguage ?? reloaded.language).toBe('en')
  })
})
