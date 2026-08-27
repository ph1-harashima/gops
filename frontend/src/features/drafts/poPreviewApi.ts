import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderStatusChange, PoPreview } from '../../shared/types/poPreview'

async function fetchPreview(draftId: number): Promise<PoPreview> {
  // POST, not GET (implementation instructions 2章's literal endpoint) - the
  // Backend re-runs real Validation on every call, it is not a pure fetch.
  const { data } = await apiClient.post<PoPreview>(`/orders/drafts/${draftId}/preview`)
  return data
}

export function usePoPreview(draftId: number) {
  return useQuery({
    queryKey: ['po-preview', draftId],
    queryFn: () => fetchPreview(draftId),
    enabled: Number.isFinite(draftId),
    retry: false,
  })
}

async function confirmOrder(draftId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/drafts/${draftId}/confirm`)
  return data
}

export function useConfirmOrder(draftId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => confirmOrder(draftId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['po-preview', draftId] })
      void queryClient.invalidateQueries({ queryKey: ['order-draft', draftId] })
    },
  })
}

async function returnToDraft(draftId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${draftId}/return-to-draft`)
  return data
}

export function useReturnToDraft(draftId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => returnToDraft(draftId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['po-preview', draftId] })
      void queryClient.invalidateQueries({ queryKey: ['order-draft', draftId] })
    },
  })
}

/** READY_TO_ORDER -> SENT -> AWAITING_SUPPLIER (implementation instructions
 * 3章). No real email is sent - this call never touches any mail transport. */
async function demoSend(draftId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${draftId}/demo-send`)
  return data
}

export function useDemoSend(draftId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => demoSend(draftId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['po-preview', draftId] })
      void queryClient.invalidateQueries({ queryKey: ['order-draft', draftId] })
    },
  })
}
