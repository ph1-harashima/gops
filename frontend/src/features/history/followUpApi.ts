import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderDraft } from '../../shared/types/orderDraft'
import type { MailPreview } from '../../shared/types/mailPreview'
import type {
  CloseFollowUpCaseRequest,
  CreateFollowUpCaseRequest,
  CreateReorderDraftRequest,
  FollowUpCase,
  UpdateFollowUpCaseRequest,
} from '../../shared/types/followUp'

async function fetchFollowUpCases(orderId: number): Promise<FollowUpCase[]> {
  const { data } = await apiClient.get<FollowUpCase[]>(`/orders/${orderId}/follow-up-cases`)
  return data
}

export function useFollowUpCases(orderId: number) {
  return useQuery({
    queryKey: ['follow-up-cases', orderId],
    queryFn: () => fetchFollowUpCases(orderId),
    enabled: Number.isFinite(orderId),
  })
}

async function createFollowUpCase(orderId: number, request: CreateFollowUpCaseRequest): Promise<FollowUpCase> {
  const { data } = await apiClient.post<FollowUpCase>(`/orders/${orderId}/follow-up-cases`, request)
  return data
}

/** "問い合わせ対象にする" (Phase 7-C7A 12章) - always explicit, never
 * auto-generated from a Fulfillment calculation. */
export function useCreateFollowUpCase(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateFollowUpCaseRequest) => createFollowUpCase(orderId, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-up-cases', orderId] }),
  })
}

async function updateFollowUpCaseNote(caseId: number, request: UpdateFollowUpCaseRequest): Promise<FollowUpCase> {
  const { data } = await apiClient.put<FollowUpCase>(`/follow-up-cases/${caseId}`, request)
  return data
}

export function useUpdateFollowUpCaseNote(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ caseId, request }: { caseId: number; request: UpdateFollowUpCaseRequest }) =>
      updateFollowUpCaseNote(caseId, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-up-cases', orderId] }),
  })
}

async function closeFollowUpCase(caseId: number, request: CloseFollowUpCaseRequest): Promise<FollowUpCase> {
  const { data } = await apiClient.post<FollowUpCase>(`/follow-up-cases/${caseId}/close`, request)
  return data
}

/** ADMIN only (Backend-enforced). */
export function useCloseFollowUpCase(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ caseId, request }: { caseId: number; request: CloseFollowUpCaseRequest }) =>
      closeFollowUpCase(caseId, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-up-cases', orderId] }),
  })
}

async function previewFollowUpMail(orderId: number, caseId: number): Promise<MailPreview> {
  const { data } = await apiClient.post<MailPreview>(`/orders/${orderId}/follow-up-cases/${caseId}/mail-preview`)
  return data
}

/** Preview only - no Send API exists (7-C7A 13章). A successful (non-BLOCKED)
 * Preview transitions the Case OPEN -> INQUIRY_PREPARED server-side. */
export function usePreviewFollowUpMail(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (caseId: number) => previewFollowUpMail(orderId, caseId),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['follow-up-cases', orderId] }),
  })
}

async function createReorderDraft(caseId: number, request: CreateReorderDraftRequest): Promise<OrderDraft> {
  const { data } = await apiClient.post<OrderDraft>(`/follow-up-cases/${caseId}/reorder-draft`, request)
  return data
}

/** "再発注Draftを作成" (Phase 7-C7A 16章). ADMIN only. Never auto-decides a
 * quantity - the created Draft's Order Qty is whatever the ordinary Create
 * Draft flow computes (Legacy Recommended Qty). */
export function useCreateReorderDraft() {
  return useMutation({
    mutationFn: ({ caseId, request }: { caseId: number; request: CreateReorderDraftRequest }) =>
      createReorderDraft(caseId, request),
  })
}
