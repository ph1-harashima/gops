// Mirrors backend com.glv.gsysportal.dto.response.OrderEmailResponse (Phase 9-E).

export interface OrderEmail {
  orderId: number
  revisionNo: number
  status: 'SENT' | 'FAILED' | null
  to: string[]
  cc: string[]
  subject: string | null
  sentAt: string | null
  sentBy: string | null
  errorCode: string | null
  errorMessage: string | null
  retryCount: number
}
