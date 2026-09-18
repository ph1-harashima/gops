import { describe, expect, it, beforeEach } from 'vitest'
import { render, screen, fireEvent, waitFor } from '@testing-library/react'
import { I18nextProvider } from 'react-i18next'

import i18n, { LANGUAGE_STORAGE_KEY } from '../i18n'
import { LanguageSwitcher } from './LanguageSwitcher'

// G-OPS i18n完成 #14: covers §3's two concrete requirements - switching
// actually changes i18n's active language, and the choice survives via
// localStorage (checked here at the storage-write level; the read-on-
// reload half is covered by i18n.persistence.test.ts, which needs a fresh
// module instance to simulate an actual page reload).
describe('LanguageSwitcher', () => {
  beforeEach(async () => {
    window.localStorage.clear()
    await i18n.changeLanguage('ja')
  })

  it('renders one option per supported language, showing each language in its own native name', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <LanguageSwitcher />
      </I18nextProvider>,
    )
    expect(screen.getByTestId('language-switcher-ja')).toHaveTextContent('日本語')
    expect(screen.getByTestId('language-switcher-en')).toHaveTextContent('English')
  })

  it('defaults to Japanese selected', () => {
    render(
      <I18nextProvider i18n={i18n}>
        <LanguageSwitcher />
      </I18nextProvider>,
    )
    expect(screen.getByTestId('language-switcher-ja')).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('language-switcher-en')).toHaveAttribute('aria-pressed', 'false')
  })

  it('clicking English switches i18n.language and persists the choice to localStorage', async () => {
    render(
      <I18nextProvider i18n={i18n}>
        <LanguageSwitcher />
      </I18nextProvider>,
    )
    fireEvent.click(screen.getByTestId('language-switcher-en'))

    await waitFor(() => expect(i18n.resolvedLanguage ?? i18n.language).toBe('en'))
    expect(window.localStorage.getItem(LANGUAGE_STORAGE_KEY)).toBe('en')
    await waitFor(() => expect(screen.getByTestId('language-switcher-en')).toHaveAttribute('aria-pressed', 'true'))
  })

  it('clicking the already-selected language is a no-op (ToggleButtonGroup exclusive mode keeps a selection)', async () => {
    render(
      <I18nextProvider i18n={i18n}>
        <LanguageSwitcher />
      </I18nextProvider>,
    )
    fireEvent.click(screen.getByTestId('language-switcher-ja'))
    await waitFor(() => expect(i18n.resolvedLanguage ?? i18n.language).toBe('ja'))
  })
})
