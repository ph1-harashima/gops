// Mirrors backend com.glv.gsysportal.dto.response.PoPreviewResponse /
// PoPreviewDetailResponse / PoPreviewSummaryResponse /
// ManufacturerCommunicationResponse. Deliberately has NO recommendedQty /
// currentStock / safetyStock / etc. fields at all - the Backend DTO itself
// excludes them (implementation instructions 5章), so there is nothing to
// accidentally render here even by mistake.
export interface PoPreviewDetail {
  lineNo: number
  sku: string
  itemName: string
  orderQty: number
  unitPrice: number | null
  amount: number
}

export interface PoPreviewSummary {
  skuCount: number
  totalQty: number
  totalAmount: number
}

export interface ManufacturerCommunication {
  to: string
  cc: string
  subject: string
  body: string
  attachment: string
}

export interface PoPreview {
  draftId: number
  draftNo: string
  prototypePoNo: string | null
  // Post-Freeze Visual Walkthrough Findings Fix (Finding #1,
  // docs/gops-visual-walkthrough-findings-fix.md): 正式PO番号 - null until
  // assigned. Distinct from prototypePoNo (Portal管理番号) - never conflate
  // the two on this screen (or anywhere else).
  officialPoNo: string | null
  supplierCode: string | null
  supplierName: string | null
  brandCode: string | null
  brandName: string | null
  orderDate: string | null
  requestedDelivery: string | null
  currency: string | null
  remark: string | null
  status: string
  details: PoPreviewDetail[]
  summary: PoPreviewSummary
  manufacturerCommunication: ManufacturerCommunication
  demoMode: boolean
  // Phase 7-H (EDI発注Workflow Foundation): null until the first Send (either
  // channel) - 'EMAIL' (demoSend) or 'EDI' (recordEdiSend/ediSend). Independent
  // of `status` - see PortalOrder.communicationChannel's Javadoc.
  communicationChannel: 'EMAIL' | 'EDI' | null
  // Phase 9-D's Manufacturer Channel Master resolved value - "what SHOULD
  // happen" per the Master (independent of communicationChannel above,
  // which only reflects a Send that already happened). null = unresolved
  // (no Master row yet) - both Send/Record actions stay visible in that
  // case, same fallback as Order Detail's own gating.
  resolvedManufacturerChannel: 'EMAIL' | 'EDI' | null
}

/** Mirrors OrderStatusChangeResponse (Confirm Order / Return to Draft). */
export interface OrderStatusChange {
  id: number
  draftNo: string
  prototypePoNo: string | null
  status: string
  updatedBy: string
  updatedAt: string
}
