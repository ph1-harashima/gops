import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderCandidate, OrderCandidateFilter } from '../../shared/types/orderCandidate'

async function fetchOrderCandidates(filter: OrderCandidateFilter): Promise<OrderCandidate[]> {
  const { data } = await apiClient.get<OrderCandidate[]>('/order-candidates', {
    params: {
      brandCode: filter.brandCode || undefined,
      supplierCode: filter.supplierCode || undefined,
      keyword: filter.keyword || undefined,
    },
  })
  return data
}

export function useOrderCandidates(filter: OrderCandidateFilter) {
  return useQuery({
    queryKey: ['order-candidates', filter],
    queryFn: () => fetchOrderCandidates(filter),
  })
}
