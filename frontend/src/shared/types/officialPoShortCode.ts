// Mirrors backend com.glv.gsysportal.dto.request.OfficialPoShortCodeRequest
// / dto.response.OfficialPoShortCodeResponse (BR-08).

export interface OfficialPoShortCodeRequest {
  codeType: 'SUPPLIER' | 'BRAND'
  businessCode: string
  shortCode: string
  active: boolean
}

export interface OfficialPoShortCode {
  id: number
  codeType: 'SUPPLIER' | 'BRAND'
  businessCode: string
  shortCode: string
  active: boolean
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}
