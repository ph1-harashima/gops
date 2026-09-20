// Mirrors backend com.glv.gsysportal.dto.response.SkuRestockExpectationResponse
// (Post-Freeze Business Refinement, docs/gops-20260917-business-requirements-re-audit.md
// §8 Display Priority). LEGACY_EXPECTED_ARRIVAL always wins when present -
// the Frontend never re-implements this priority rule, it only renders
// whichever `source`/`date` the Backend already resolved.
export type RestockSource =
  | 'LEGACY_EXPECTED_ARRIVAL'
  | 'PORTAL_MANUAL'
  | 'PORTAL_MANUAL_UNKNOWN'
  | 'NONE'

// Post-Freeze Business Refinement 2
// (docs/gops-manufacturer-stockout-information-management.md §4) - what the
// Manufacturer has told us, independent of Legacy's own System Information
// (§3). null means "not yet classified" (Implementation 1 record, or a
// record that never set a status).
export type StockoutStatus = 'STOCKOUT' | 'LONG_TERM_STOCKOUT' | 'RESOLVED'

export type ContactMethod = 'PHONE' | 'EMAIL' | 'ORDER_RESPONSE' | 'OTHER'

export interface RestockExpectation {
  skuCode: string
  source: RestockSource
  date: string | null
  manualMemo: string | null
  manualUpdatedBy: string | null
  manualUpdatedAt: string | null
  // Distinct from `date`/`source` above: the actual Portal Manual record's
  // own date/未定 flag, exposed even while LEGACY_EXPECTED_ARRIVAL is
  // winning display priority - the Edit form prefills from these, never
  // from the merged `date`, so editing while Legacy is displayed can't
  // silently overwrite the real Manual value with Legacy's date.
  manualDate: string | null
  manualUnknown: boolean
  // Post-Freeze Business Refinement 2 - Manufacturer Stockout Information,
  // always populated regardless of `source`/`date` above (§3: System
  // Information and Manufacturer Information are distinct data sources,
  // never merged into one). `legacyDate` is `date`'s raw Legacy-only
  // counterpart (explicit, since `date` reflects the merge). `hasConflict`
  // is true only when Legacy has an incoming Arrival AND the Manufacturer
  // is still telling us STOCKOUT/LONG_TERM_STOCKOUT (§23) - screens must
  // show both values, never silently pick one.
  legacyDate: string | null
  stockoutStatus: StockoutStatus | null
  shortageQty: number | null
  informationReceivedDate: string | null
  contactMethod: ContactMethod | null
  hasConflict: boolean
}

export interface RestockExpectationInput {
  expectedRestockDate: string | null
  unknown: boolean
  memo: string | null
  stockoutStatus: StockoutStatus | null
  shortageQty: number | null
  informationReceivedDate: string | null
  contactMethod: ContactMethod | null
}

// Post-Freeze Business Refinement 2 §11/§21 - one Business-facing History
// entry (always a full snapshot, never a diff).
export interface RestockExpectationHistoryEntry {
  stockoutStatus: StockoutStatus | null
  expectedRestockDate: string | null
  unknown: boolean
  shortageQty: number | null
  informationReceivedDate: string | null
  contactMethod: ContactMethod | null
  memo: string | null
  recordedBy: string
  recordedAt: string
}
