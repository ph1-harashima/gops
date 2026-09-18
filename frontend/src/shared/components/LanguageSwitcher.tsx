import ToggleButton from '@mui/material/ToggleButton'
import ToggleButtonGroup from '@mui/material/ToggleButtonGroup'
import { useTranslation } from 'react-i18next'

import { SUPPORTED_LANGUAGES, type SupportedLanguage } from '../i18n'

// G-OPS i18n完成 #3: Language Switcher. Each option deliberately shows its
// OWN language's native name ("日本語"/"English"), not a translated label -
// this is the one place in the app where showing text via t() would defeat
// the switcher's own purpose: a user who currently cannot read the active
// UI language still needs to recognize their own language's name in order
// to select it (e.g. a Japanese-default screen rendering "日本語 / 英語"
// would give an English-speaking user no legible way to find "English").
// This is therefore an intentional exception to "every user-facing string
// goes through i18n", not a missed hardcode - see 完了報告 §11.
const LANGUAGE_NATIVE_NAMES: Record<SupportedLanguage, string> = {
  ja: '日本語',
  en: 'English',
}

export function LanguageSwitcher() {
  const { i18n } = useTranslation()
  const current = (i18n.resolvedLanguage ?? i18n.language) as SupportedLanguage

  return (
    <ToggleButtonGroup
      size="small"
      exclusive
      value={current}
      onChange={(_e, next: SupportedLanguage | null) => {
        if (next) void i18n.changeLanguage(next)
      }}
      aria-label="Language / 言語"
      data-testid="language-switcher"
    >
      {SUPPORTED_LANGUAGES.map((lng) => (
        <ToggleButton
          key={lng}
          value={lng}
          data-testid={`language-switcher-${lng}`}
          sx={{ px: 1.25, py: 0.25, fontSize: '0.75rem', textTransform: 'none', lineHeight: 1.4 }}
        >
          {LANGUAGE_NATIVE_NAMES[lng]}
        </ToggleButton>
      ))}
    </ToggleButtonGroup>
  )
}
