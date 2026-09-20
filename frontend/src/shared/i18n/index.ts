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
import jaSupplierContact from './locales/ja/supplierContact.json'
import jaMailTemplate from './locales/ja/mailTemplate.json'
import jaManufacturerChannel from './locales/ja/manufacturerChannel.json'
import jaSupplierRegionClassification from './locales/ja/supplierRegionClassification.json'
import jaPriceChanges from './locales/ja/priceChanges.json'
import jaArrivals from './locales/ja/arrivals.json'
import jaWarehouseStock from './locales/ja/warehouseStock.json'
import jaStockSales from './locales/ja/stockSales.json'
import jaMailSettings from './locales/ja/mailSettings.json'
import jaOfficialPoShortCode from './locales/ja/officialPoShortCode.json'
import jaSupplierMaster from './locales/ja/supplierMaster.json'
import jaRestockExpectation from './locales/ja/restockExpectation.json'
import enCommon from './locales/en/common.json'
import enStatus from './locales/en/status.json'
import enAuth from './locales/en/auth.json'
import enHistory from './locales/en/history.json'
import enSkuDetail from './locales/en/skuDetail.json'
import enSupplierContact from './locales/en/supplierContact.json'
import enMailTemplate from './locales/en/mailTemplate.json'
import enManufacturerChannel from './locales/en/manufacturerChannel.json'
import enSupplierRegionClassification from './locales/en/supplierRegionClassification.json'
import enDrafts from './locales/en/drafts.json'
import enSupplierResponse from './locales/en/supplierResponse.json'
import enCandidates from './locales/en/candidates.json'
import enPreview from './locales/en/preview.json'
import enDashboard from './locales/en/dashboard.json'
import enPriceChanges from './locales/en/priceChanges.json'
import enArrivals from './locales/en/arrivals.json'
import enWarehouseStock from './locales/en/warehouseStock.json'
import enStockSales from './locales/en/stockSales.json'
import enMailSettings from './locales/en/mailSettings.json'
import enOfficialPoShortCode from './locales/en/officialPoShortCode.json'
import enSupplierMaster from './locales/en/supplierMaster.json'
import enRestockExpectation from './locales/en/restockExpectation.json'

// G-OPS i18n完成 #3: Language Switcher実装に伴い、UI言語の永続化キー。
// Mail Template側の`language`フィールド(DB値、メーカー/Supplierごとの
// メール送信言語設定)とは完全に別物 - このキーはブラウザのUI表示言語のみを
// 保持し、Backend/DBの言語設定には一切関与しない(§7の分離要件)。
export const LANGUAGE_STORAGE_KEY = 'gsys-ui-language'
export const SUPPORTED_LANGUAGES = ['ja', 'en'] as const
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]

function readStoredLanguage(): SupportedLanguage {
  try {
    const stored = window.localStorage.getItem(LANGUAGE_STORAGE_KEY)
    if (stored != null && (SUPPORTED_LANGUAGES as readonly string[]).includes(stored)) {
      return stored as SupportedLanguage
    }
  } catch {
    // localStorage unavailable (e.g. private browsing) - fall through to default.
  }
  return 'ja'
}

// Japanese is the Default display language (要件§2), but the actual initial
// `lng` now resolves from localStorage (readStoredLanguage()) so a Language
// Switcher choice survives page reload (§3). fallbackLng stays 'ja' as a
// safety net for any future namespace/key that is momentarily incomplete in
// one language - never a substitute for translating a key (both languages
// are now fully populated, see 完了報告 §4). Display text is never
// hardcoded into React components - it always comes from these resource
// files (Requirements MD 26章 principle 21).
void i18n.use(initReactI18next).init({
  lng: readStoredLanguage(),
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
      supplierContact: jaSupplierContact,
      mailTemplate: jaMailTemplate,
      manufacturerChannel: jaManufacturerChannel,
      supplierRegionClassification: jaSupplierRegionClassification,
      priceChanges: jaPriceChanges,
      arrivals: jaArrivals,
      warehouseStock: jaWarehouseStock,
      stockSales: jaStockSales,
      mailSettings: jaMailSettings,
      officialPoShortCode: jaOfficialPoShortCode,
      supplierMaster: jaSupplierMaster,
      restockExpectation: jaRestockExpectation,
    },
    en: {
      common: enCommon,
      status: enStatus,
      auth: enAuth,
      history: enHistory,
      skuDetail: enSkuDetail,
      drafts: enDrafts,
      supplierResponse: enSupplierResponse,
      candidates: enCandidates,
      preview: enPreview,
      dashboard: enDashboard,
      supplierContact: enSupplierContact,
      mailTemplate: enMailTemplate,
      manufacturerChannel: enManufacturerChannel,
      supplierRegionClassification: enSupplierRegionClassification,
      priceChanges: enPriceChanges,
      arrivals: enArrivals,
      warehouseStock: enWarehouseStock,
      stockSales: enStockSales,
      mailSettings: enMailSettings,
      officialPoShortCode: enOfficialPoShortCode,
      supplierMaster: enSupplierMaster,
      restockExpectation: enRestockExpectation,
    },
  },
  interpolation: {
    escapeValue: false,
  },
})

// Keep localStorage in sync whenever the language actually changes (covers
// both the Language Switcher's own i18n.changeLanguage() call and any other
// caller), so a reload always restores the last-chosen UI language (§3).
i18n.on('languageChanged', (lng) => {
  try {
    if ((SUPPORTED_LANGUAGES as readonly string[]).includes(lng)) {
      window.localStorage.setItem(LANGUAGE_STORAGE_KEY, lng)
    }
  } catch {
    // localStorage unavailable - the in-memory language still applies for this session.
  }
})

// Keep <html lang> in sync with the active UI language - correct HTML
// semantics/screen-reader pronunciation, independent of the Mail Template
// language (§7's separation is unaffected: this only touches the document
// element, never anything sent to the Backend). index.html ships a static
// lang="ja" default; this corrects it once i18n resolves the stored/actual
// language, and again on every subsequent switch.
try {
  document.documentElement.lang = i18n.resolvedLanguage ?? i18n.language
} catch {
  // document unavailable (e.g. non-DOM test environment) - safe to skip.
}
i18n.on('languageChanged', (lng) => {
  try {
    document.documentElement.lang = lng
  } catch {
    // document unavailable - safe to skip.
  }
})

export default i18n
