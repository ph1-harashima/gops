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
import jaDashboard from './locales/ja/dashboard.json'
import jaSkuDetail from './locales/ja/skuDetail.json'
import enCommon from './locales/en/common.json'
import enStatus from './locales/en/status.json'
import enHistory from './locales/en/history.json'
// Phase 6-A: minimal partial resources - just the new Label keys introduced
// this Phase (docs/production-ux-workflow-redesign.md 6章/13章). No
// Language Selector is implemented yet (lng is fixed to 'ja' below), and
// fallbackLng: 'ja' fills in every other key in these namespaces, so a
// partial file here does not regress anything - it only prepares the two
// new keys for whenever `en` becomes reachable.
import enDrafts from './locales/en/drafts.json'
import enSupplierResponse from './locales/en/supplierResponse.json'

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
      dashboard: jaDashboard,
      skuDetail: jaSkuDetail,
    },
    en: {
      common: enCommon,
      status: enStatus,
      history: enHistory,
      drafts: enDrafts,
      supplierResponse: enSupplierResponse,
    },
  },
  interpolation: {
    escapeValue: false,
  },
})

export default i18n
