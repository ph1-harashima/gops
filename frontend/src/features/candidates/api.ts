import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderCandidate, OrderCandidateFilter } from '../../shared/types/orderCandidate'
import type { PageResponse } from '../../shared/types/pagination'
import type { DashboardBrandRow } from '../../shared/types/dashboard'

// Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
// gops-stage4-targeted-real-data-remediation.md): Backend-paginated, same
// page/size contract as useStockSalesList - GET /api/order-candidates
// previously returned every matching row in one response, which a
// confirmed real Brand with 18,596 SKUs would make unusable.
async function fetchOrderCandidates(filter: OrderCandidateFilter, page: number, size: number): Promise<PageResponse<OrderCandidate>> {
  const { data } = await apiClient.get<PageResponse<OrderCandidate>>('/order-candidates', {
    params: {
      brandCode: filter.brandCode || undefined,
      supplierCode: filter.supplierCode || undefined,
      keyword: filter.keyword || undefined,
      page,
      size,
    },
  })
  return data
}

export function useOrderCandidates(filter: OrderCandidateFilter, page: number, size: number) {
  return useQuery({
    queryKey: ['order-candidates', filter, page, size],
    queryFn: () => fetchOrderCandidates(filter, page, size),
  })
}

// Stage 5E Targeted Remediation (RC-B, docs/real-data-audit/
// gops-stage5e-targeted-remediation.md): lightweight Brand code->name
// lookup, replacing CandidateListPage's previous useDashboard() call -
// Stage 5D confirmed that pulled in Dashboard's entire (at the time,
// catastrophically slow) candidate-count computation in the background on
// every Candidate List visit, just to resolve one Filter Chip's label.
export interface BrandSummary {
  brandCode: string
  brandName: string
}

async function fetchBrandNames(): Promise<BrandSummary[]> {
  const { data } = await apiClient.get<BrandSummary[]>('/brands')
  return data
}

export function useBrandNames() {
  return useQuery({
    queryKey: ['brands'],
    queryFn: fetchBrandNames,
  })
}

/**
 * Candidate Brand List UX improvement (OrderCandidateBrandListPage's own
 * Brand entry screen): keyword (Brand Code/Name) search and the
 * candidateCount>0 default filter both run server-side
 * (GET /api/order-candidates/brands, backed by
 * DashboardService.findBrandRowsForCandidateEntry) - same principle as
 * useBrandNames() above (Stage 5E RC-B): never fetch the full Dashboard
 * payload just to filter it client-side. Same DashboardBrandRow shape the
 * Dashboard page's own Brand Breakdown table already uses.
 */
async function fetchOrderCandidateBrands(keyword: string, includeZeroCandidates: boolean): Promise<DashboardBrandRow[]> {
  const { data } = await apiClient.get<DashboardBrandRow[]>('/order-candidates/brands', {
    params: {
      keyword: keyword || undefined,
      includeZeroCandidates,
    },
  })
  return data
}

export function useOrderCandidateBrands(keyword: string, includeZeroCandidates: boolean) {
  return useQuery({
    queryKey: ['order-candidate-brands', keyword, includeZeroCandidates],
    queryFn: () => fetchOrderCandidateBrands(keyword, includeZeroCandidates),
  })
}
