// Mirrors backend com.glv.gsysportal.dto.response.AttentionSummary /
// AttentionResponse.
export interface AttentionSummary {
  id: number
  attentionType: string
}

export interface Attention {
  id: number
  portalOrderId: number
  portalOrderDetailId: number | null
  attentionType: string
  isActive: boolean
  detectedAt: string
  resolvedAt: string | null
  acknowledgedBy: string | null
  acknowledgedAt: string | null
}
