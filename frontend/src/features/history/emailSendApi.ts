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

/** "送信" (Phase 9-E). ADMIN only. Sends exactly what Mail Preview
 * resolved. Idempotent/Retry-safe - a repeat call while already SENT is a
 * no-op; a call while FAILED retries. */
async function sendEmail(orderId: number): Promise<OrderEmail> {
  const { data } = await apiClient.post<OrderEmail>(`/orders/${orderId}/email/send`)
  return data
}

export function useSendEmail(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => sendEmail(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['order-email', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}
