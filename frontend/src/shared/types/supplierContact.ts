// Mirrors backend com.glv.gsysportal.dto.response.SupplierContactResponse /
// dto.request.SupplierContactRequest (Phase 7-C3).
export interface SupplierContact {
  id: number
  supplierCode: string
  brandCode: string | null
  contactName: string
  email: string
  contactType: string
  language: string
  region: string | null
  procurementType: string | null
  primary: boolean
  active: boolean
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}

export interface SupplierContactRequest {
  supplierCode: string
  brandCode: string | null
  contactName: string
  email: string
  contactType: string
  language: string
  region: string | null
  procurementType: string | null
  primary: boolean
  active: boolean
}
