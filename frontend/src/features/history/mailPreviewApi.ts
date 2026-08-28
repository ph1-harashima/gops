import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { MailPreview } from '../../shared/types/mailPreview'

/** Phase 7-C3 9章: POST, not GET - the Backend re-resolves Contact/Template/
 * Admin-CC against current data on every call (mirrors PoPreviewController's
 * own "not a pure fetch" reasoning). No Send API exists this Phase. */
async function fetchMailPreview(orderId: number): Promise<MailPreview> {
  const { data } = await apiClient.post<MailPreview>(`/orders/${orderId}/mail-preview`)
  return data
}

export function useMailPreview(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => fetchMailPreview(orderId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] }),
  })
}
