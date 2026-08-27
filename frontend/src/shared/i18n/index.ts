import i18n from 'i18next'
import { initReactI18next } from 'react-i18next'

import jaCommon from './locales/ja/common.json'
import jaCandidates from './locales/ja/candidates.json'
import jaStatus from './locales/ja/status.json'
import jaAuth from './locales/ja/auth.json'
import jaDrafts from './locales/ja/drafts.json'
import jaPreview from './locales/ja/preview.json'
import jaSupplierResponse from './locales/ja/supplierResponse.json'
import jaHistory from './locales/ja/history.json'
import enCommon from './locales/en/common.json'

// Japanese is the initial display language (Requirements MD 28.1).
// Display text is never hardcoded into React components - it always comes
// from these resource files (Requirements MD 26章 principle 21).
void i18n.use(initReactI18next).init({
  lng: 'ja',
  fallbackLng: 'ja',
  resources: {
    ja: {
      common: jaCommon,
      candidates: jaCandidates,
      status: jaStatus,
      auth: jaAuth,
      drafts: jaDrafts,
      preview: jaPreview,
      supplierResponse: jaSupplierResponse,
      history: jaHistory,
    },
    en: {
      common: enCommon,
    },
  },
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
