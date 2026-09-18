import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderEmail } from '../../shared/types/orderEmail'

/** Phase 9-E: current Email Send state - any authenticated user (same
 * visibility as every other read endpoint). */
async function fetchEmailStatus(orderId: number): Promise<OrderEmail> {
  const { data } = await apiClient.get<OrderEmail>(`/orders/${orderId}/email`)
  return data
}

export function useEmailStatus(orderId: number) {
  return useQuery({
    queryKey: ['order-email', orderId],
    queryFn: () => fetchEmailStatus(orderId),
    enabled: Number.isFinite(orderId),
  })
}

/** Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章):
 * optional per-Send To/CC Override - omitted/undefined means "use the
 * Master-resolved addresses as-is" (pre-C-5 behavior, unchanged). */
export interface SendEmailOverride {
  to?: string[]
  cc?: string[]
}

/** "送信" (Phase 9-E). ADMIN only. Sends exactly what Mail Preview
 * resolved, unless an Override is supplied. Idempotent/Retry-safe - a
 * repeat call while already SENT is a no-op; a call while FAILED retries. */
async function sendEmail(orderId: number, override?: SendEmailOverride): Promise<OrderEmail> {
  const { data } = await apiClient.post<OrderEmail>(`/orders/${orderId}/email/send`, override ?? {})
  return data
}

export function useSendEmail(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (override?: SendEmailOverride) => sendEmail(orderId, override),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['order-email', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
      // Cache Consistency Fix: Revision History's own 送信状況 column
      // (OfficialPoRevisionHistoryEntry.emailSendStatus) is fetched via this
      // separate query - without invalidating it too, the table kept
      // showing the pre-Send value until an unrelated refetch/reload.
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
    },
  })
}
