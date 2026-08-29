// Mirrors backend com.glv.gsysportal.dto.response.FulfillmentView /
// FulfillmentLineView (Phase 7-C7A). READ ONLY - Legacy TR_PO/TR_PO_DTL/
// TR_INV/TR_INV_DTL, never written to.
export interface FulfillmentLine {
  skuCode: string
  itemName: string
  orderedQty: number
  invoicedQty: number
  stockInQty: number
  outstandingQty: number
  lineStatus: 'OPEN' | 'PARTIAL' | 'FULFILLED'
}

export interface Fulfillment {
  orderId: number
  officialPoNo: string | null
  linkState: 'NOT_LINKED' | 'PO_NOT_FOUND' | 'LINKED'
  fulfillmentStatus: 'OPEN' | 'PARTIAL' | 'FULFILLED' | null
  lines: FulfillmentLine[]
}
