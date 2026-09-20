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
}

export interface RestockExpectationInput {
  expectedRestockDate: string | null
  unknown: boolean
  memo: string | null
}
