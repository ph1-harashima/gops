import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderCandidate, OrderCandidateFilter } from '../../shared/types/orderCandidate'
import type { PageResponse } from '../../shared/types/pagination'

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
