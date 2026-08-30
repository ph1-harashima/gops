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

/** Phase 7-C1: invalidates every query a Workflow Status change can affect -
 * this Order's own Preview/Draft/Detail/Timeline, the List it appears in,
 * and the Dashboard's counts (draftCount/pendingApprovalCount/
 * awaitingSupplierCount all move on submit/approve/return). Shared by
 * submit/approve/return-for-correction below since all three are
 * Workflow Status transitions with the same blast radius. */
function invalidateOrderQueries(queryClient: ReturnType<typeof useQueryClient>, orderId: number) {
  void queryClient.invalidateQueries({ queryKey: ['po-preview', orderId] })
  void queryClient.invalidateQueries({ queryKey: ['order-draft', orderId] })
  void queryClient.invalidateQueries({ queryKey: ['order-history-detail', orderId] })
  void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
  void queryClient.invalidateQueries({ queryKey: ['order-history'] })
  void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
}

/** DRAFT -> PENDING_APPROVAL (Phase 7-C1 8章). Rejected 403/FORBIDDEN unless
 * the caller is the Draft's own creator or an ADMIN (OrderStatusTransitionService.submitForApproval). */
async function submitForApproval(orderId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${orderId}/submit-for-approval`)
  return data
}

export function useSubmitForApproval(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => submitForApproval(orderId),
    onSuccess: () => invalidateOrderQueries(queryClient, orderId),
  })
}

/** PENDING_APPROVAL -> APPROVED (Phase 7-C1 9章/10章). ADMIN only - Backend
 * enforces via @PreAuthorize, this is not merely hidden by the UI. */
async function approve(orderId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${orderId}/approve`)
  return data
}

export function useApprove(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => approve(orderId),
    onSuccess: () => invalidateOrderQueries(queryClient, orderId),
  })
}

/** PENDING_APPROVAL -> DRAFT (Phase 7-C1 12章). ADMIN only; reason is
 * mandatory Backend-side (400 RETURN_REASON_REQUIRED if blank). */
async function returnForCorrection(orderId: number, reason: string): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${orderId}/return-for-correction`, { reason })
  return data
}

export function useReturnForCorrection(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (reason: string) => returnForCorrection(orderId, reason),
    onSuccess: () => invalidateOrderQueries(queryClient, orderId),
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

/** Phase 7-H (EDI発注Workflow Foundation): same APPROVED -> SENT ->
 * AWAITING_SUPPLIER transition as demoSend, for a Supplier ordered from over
 * their own EDI system - see OrderStatusTransitionService.recordEdiSend's
 * Javadoc. No real EDI file/connection is ever involved. */
async function ediSend(draftId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${draftId}/edi-send`)
  return data
}

export function useEdiSend(draftId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => ediSend(draftId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['po-preview', draftId] })
      void queryClient.invalidateQueries({ queryKey: ['order-draft', draftId] })
    },
  })
}
