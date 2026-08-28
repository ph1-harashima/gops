import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderStatusChange } from '../../shared/types/poPreview'
import type {
  CreateRevisionRequest,
  OrderRevisionSummary,
  SaveSupplierResponseRequest,
  SupplierResponse,
  SupplierResponseHistoryEntry,
} from '../../shared/types/supplierResponse'

async function fetchSupplierResponse(orderId: number): Promise<SupplierResponse> {
  const { data } = await apiClient.get<SupplierResponse>(`/orders/${orderId}/supplier-response`)
  return data
}

export function useSupplierResponse(orderId: number) {
  return useQuery({
    queryKey: ['supplier-response', orderId],
    queryFn: () => fetchSupplierResponse(orderId),
    enabled: Number.isFinite(orderId),
  })
}

async function saveSupplierResponse(orderId: number, request: SaveSupplierResponseRequest): Promise<SupplierResponse> {
  const { data } = await apiClient.put<SupplierResponse>(`/orders/${orderId}/supplier-response`, request)
  return data
}

export function useSaveSupplierResponse(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: SaveSupplierResponseRequest) => saveSupplierResponse(orderId, request),
    onSuccess: (data) => {
      // Requirements MD 19章: re-GET after save so the screen always shows
      // what the server actually persisted - write the PUT response
      // straight into the cache (it IS the authoritative post-save state,
      // same shape as GET) rather than issuing a second round-trip.
      queryClient.setQueryData(['supplier-response', orderId], data)
    },
  })
}

async function confirmSupplierResponse(orderId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${orderId}/supplier-response/confirm`)
  return data
}

export function useConfirmSupplierResponse(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => confirmSupplierResponse(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['supplier-response', orderId] })
    },
  })
}

// --- Phase 7-C5: Supplier Response Revision / Agreement Workflow ---

async function agreeResponse(orderId: number, responseId: number, forceAgree: boolean): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(
    `/orders/${orderId}/responses/${responseId}/agree`, { forceAgree })
  return data
}

/** SUPPLIER_CONFIRMED -> AGREED (7-C5 11章). ADMIN only (Backend-enforced). */
export function useAgreeResponse(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ responseId, forceAgree }: { responseId: number; forceAgree: boolean }) =>
      agreeResponse(orderId, responseId, forceAgree),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['supplier-response', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-responses', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}

async function reopenAgreement(orderId: number, responseId: number, reason: string): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(
    `/orders/${orderId}/responses/${responseId}/reopen`, { reason })
  return data
}

/** AGREED -> SUPPLIER_CONFIRMED with a mandatory reason (7-C5 18章). ADMIN only. */
export function useReopenAgreement(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ responseId, reason }: { responseId: number; reason: string }) =>
      reopenAgreement(orderId, responseId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['supplier-response', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-responses', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}

async function createRevision(orderId: number, request: CreateRevisionRequest): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${orderId}/revisions`, request)
  return data
}

/** "修正版を作成": SUPPLIER_CONFIRMED -> DRAFT (7-C5 13章). ADMIN only. */
export function useCreateRevision(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: CreateRevisionRequest) => createRevision(orderId, request),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['supplier-response', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-revisions', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-responses', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-history-detail', orderId] })
    },
  })
}

async function fetchResponseHistory(orderId: number): Promise<SupplierResponseHistoryEntry[]> {
  const { data } = await apiClient.get<SupplierResponseHistoryEntry[]>(`/orders/${orderId}/responses`)
  return data
}

export function useResponseHistory(orderId: number) {
  return useQuery({
    queryKey: ['order-responses', orderId],
    queryFn: () => fetchResponseHistory(orderId),
    enabled: Number.isFinite(orderId),
  })
}

async function fetchResponseByRevision(orderId: number, revisionNo: number): Promise<SupplierResponse> {
  const { data } = await apiClient.get<SupplierResponse>(`/orders/${orderId}/responses/by-revision/${revisionNo}`)
  return data
}

/** Past, READ ONLY Response for a specific Revision (7-C5 21章). */
export function useResponseByRevision(orderId: number, revisionNo: number | null) {
  return useQuery({
    queryKey: ['supplier-response-history', orderId, revisionNo],
    queryFn: () => fetchResponseByRevision(orderId, revisionNo as number),
    enabled: Number.isFinite(orderId) && revisionNo !== null,
  })
}

async function fetchRevisionHistory(orderId: number): Promise<OrderRevisionSummary[]> {
  const { data } = await apiClient.get<OrderRevisionSummary[]>(`/orders/${orderId}/revisions`)
  return data
}

export function useOrderRevisions(orderId: number) {
  return useQuery({
    queryKey: ['order-revisions', orderId],
    queryFn: () => fetchRevisionHistory(orderId),
    enabled: Number.isFinite(orderId),
  })
}
