// Mirrors backend com.glv.gsysportal.dto.response.SupplierMasterSummaryResponse
// / SupplierMasterDetailResponse (Master Maintenance Hub).

export interface SupplierMasterSummary {
  supplierCode: string
  supplierName: string
  regionClassification: 'DOMESTIC' | 'OVERSEAS' | 'MIXED' | null
  brandCount: number
  contactConfigured: boolean
  channel: 'EMAIL' | 'EDI' | 'MIXED' | null
  officialPoShortCode: string | null
}

export interface SupplierMasterBrandRef {
  brandCode: string
  brandName: string
}

export interface SupplierMasterDetail {
  supplierCode: string
  supplierName: string
  brands: SupplierMasterBrandRef[]
  regionClassification: 'DOMESTIC' | 'OVERSEAS' | 'MIXED' | null
  contactConfigured: boolean
  activeContactCount: number
  channel: 'EMAIL' | 'EDI' | 'MIXED' | null
  officialPoShortCode: string | null
}
