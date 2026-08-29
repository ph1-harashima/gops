import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { Fulfillment } from '../../shared/types/fulfillment'

/** Phase 7-C7A 4章: READ ONLY - Legacy TR_PO/TR_PO_DTL/TR_INV/TR_INV_DTL,
 * never written to. */
async function fetchFulfillment(orderId: number): Promise<Fulfillment> {
  const { data } = await apiClient.get<Fulfillment>(`/orders/${orderId}/fulfillment`)
  return data
}

export function useFulfillment(orderId: number) {
  return useQuery({
    queryKey: ['fulfillment', orderId],
    queryFn: () => fetchFulfillment(orderId),
    enabled: Number.isFinite(orderId),
  })
}
