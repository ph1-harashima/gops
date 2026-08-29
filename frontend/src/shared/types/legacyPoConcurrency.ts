// Mirrors backend com.glv.gsysportal.dto.response.LegacyPoConcurrencyResponse /
// LegacyPoDiffEntry / LegacyPoBaselineResponse (Phase 7-C6).

export interface LegacyPoDiffEntry {
  field: string
  skuCode: string | null
  baselineValue: string | null
  currentValue: string | null
  diffType: string
}

export interface LegacyPoConcurrency {
  orderId: number
  officialPoNo: string | null
  result: string
  baselineRevisionNo: number | null
  baselineFingerprint: string | null
  capturedBy: string | null
  capturedAt: string | null
  diffs: LegacyPoDiffEntry[]
}

export interface LegacyPoBaseline {
  orderId: number
  revisionNo: number
  officialPoNo: string
  fingerprint: string
  capturedBy: string
  capturedAt: string
}
