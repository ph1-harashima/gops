import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OfficialPoIntegration } from '../../shared/types/officialPoIntegration'

/** Phase 7-C2A 13章: Order Detail's "G-SYS正式PO連携" Section. Any
 * authenticated user may view it. */
async function fetchIntegration(orderId: number): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.get<OfficialPoIntegration>(`/orders/${orderId}/official-po`)
  return data
}

export function useOfficialPoIntegration(orderId: number) {
  return useQuery({
    queryKey: ['official-po-integration', orderId],
    queryFn: () => fetchIntegration(orderId),
    enabled: Number.isFinite(orderId),
  })
}

/** "G-SYS連携準備" (7-C2A 14章). ADMIN only (enforced Backend-side) - creates
 * (or, on repeat calls, re-preflights) the Integration Request. Never writes
 * to Legacy in any way. */
async function requestIntegration(orderId: number): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.post<OfficialPoIntegration>(`/orders/${orderId}/official-po/request`)
  return data
}

export function useRequestOfficialPoIntegration(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => requestIntegration(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}
