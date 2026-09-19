import { useSearchParams } from 'react-router-dom'

import { OrderCandidateBrandListPage } from './OrderCandidateBrandListPage'
import { CandidateListPage } from './CandidateListPage'

/**
 * Order Candidates Brand Entry (docs/gops-order-candidates-brand-entry-implementation.md):
 * `/candidates` with no Query Parameter at all is the bare top-nav landing -
 * it now shows the Brand List first, never the full SKU Flat List directly.
 * Any Query Parameter (brandCode, recommendedOnly, outOfStockOnly, etc. -
 * every existing Dashboard Deep Link already sets one) still resolves
 * straight to the existing, completely unchanged CandidateListPage.
 *
 * This is the ONLY routing change - CandidateListPage itself is untouched,
 * and every existing Deep Link caller (Dashboard's Brand row / KPI tiles)
 * keeps working exactly as before, since they already pass a Query
 * Parameter.
 */
export function CandidatesEntryPage() {
  const [searchParams] = useSearchParams()
  return searchParams.toString() === '' ? <OrderCandidateBrandListPage /> : <CandidateListPage />
}
