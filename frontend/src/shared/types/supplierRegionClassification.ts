// Mirrors backend com.glv.gsysportal.dto.request.SupplierRegionClassificationRequest
// / dto.response.SupplierRegionClassificationResponse (Gap Analysis §12).

export interface SupplierRegionClassificationRequest {
  supplierCode: string
  brandCode: string | null
  regionClassification: 'DOMESTIC' | 'OVERSEAS'
  active: boolean
}

export interface SupplierRegionClassification {
  id: number
  supplierCode: string
  brandCode: string | null
  regionClassification: 'DOMESTIC' | 'OVERSEAS'
  active: boolean
  createdBy: string
  createdAt: string
  updatedBy: string
  updatedAt: string
}
