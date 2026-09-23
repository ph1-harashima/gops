import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { CreateDraftRequest, OrderDraft, UpdateDraftRequest } from '../../shared/types/orderDraft'

async function fetchDraft(id: number): Promise<OrderDraft> {
  const { data } = await apiClient.get<OrderDraft>(`/orders/drafts/${id}`)
  return data
}

export function useOrderDraft(id: number) {
  return useQuery({
    queryKey: ['order-draft', id],
    queryFn: () => fetchDraft(id),
    enabled: Number.isFinite(id),
  })
}

async function createDraft(request: CreateDraftRequest): Promise<OrderDraft> {
  const { data } = await apiClient.post<OrderDraft>('/orders/drafts', request)
  return data
}

export function useCreateDraft() {
  return useMutation({ mutationFn: createDraft })
}

async function updateDraft(id: number, request: UpdateDraftRequest): Promise<OrderDraft> {
  const { data } = await apiClient.put<OrderDraft>(`/orders/drafts/${id}`, request)
  return data
}

export function useUpdateDraft(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: UpdateDraftRequest) => updateDraft(id, request),
    onSuccess: () => {
      // Requirements MD 13章: "保存後に再GETして、DB保存値と画面値が一致する
      // ことを確認可能にする" - deliberately invalidate (not just write the
      // PUT response into cache) so the screen re-fetches via GET and always
      // shows what is actually persisted, not merely what the PUT echoed.
      void queryClient.invalidateQueries({ queryKey: ['order-draft', id] })
    },
  })
}

/** G-OPS Operational Workflow Realignment Phase F §16: soft delete,
 * DRAFT-only, no-downstream-process-started only - the Backend
 * (OrderDraftService.deleteDraft) is the sole authority on the guard;
 * this call surfaces its 409 DRAFT_DELETION_NOT_ALLOWED as-is. */
async function deleteDraft(id: number): Promise<void> {
  await apiClient.delete(`/orders/drafts/${id}`)
}

export function useDeleteDraft(id: number) {
  return useMutation({ mutationFn: () => deleteDraft(id) })
}
