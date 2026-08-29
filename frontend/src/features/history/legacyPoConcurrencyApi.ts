import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { LegacyPoBaseline, LegacyPoConcurrency } from '../../shared/types/legacyPoConcurrency'

/** Phase 7-C6 10章: "G-SYSとの差異を確認" - any authenticated user. */
async function fetchConcurrency(orderId: number): Promise<LegacyPoConcurrency> {
  const { data } = await apiClient.get<LegacyPoConcurrency>(`/orders/${orderId}/official-po/concurrency`)
  return data
}

export function useLegacyPoConcurrency(orderId: number) {
  return useQuery({
    queryKey: ['legacy-po-concurrency', orderId],
    queryFn: () => fetchConcurrency(orderId),
    enabled: Number.isFinite(orderId),
  })
}

/** "G-SYS現在状態を基準として記録" (7-C6 9章/20章). ADMIN only (enforced
 * Backend-side). Never writes to Legacy in any way. */
async function captureBaseline(orderId: number): Promise<LegacyPoBaseline> {
  const { data } = await apiClient.post<LegacyPoBaseline>(`/orders/${orderId}/official-po/baseline`)
  return data
}

export function useCaptureLegacyPoBaseline(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => captureBaseline(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['legacy-po-concurrency', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}
