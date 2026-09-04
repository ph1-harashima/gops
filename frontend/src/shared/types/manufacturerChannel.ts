// Mirrors backend com.glv.gsysportal.dto.request.ManufacturerChannelRequest
// / dto.response.ManufacturerChannelResponse (Phase 9-D).

export interface ManufacturerChannelRequest {
  supplierCode: string
  brandCode: string | null
  channel: 'EMAIL' | 'EDI'
  active: boolean
}

export interface ManufacturerChannel {
  id: number
  supplierCode: string
  brandCode: string | null
  channel: 'EMAIL' | 'EDI'
  active: boolean
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}
