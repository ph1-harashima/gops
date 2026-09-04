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

export interface ConfirmOfficialPoNumberInput {
  officialPoNo: string
  deliveryWeek: string | null
  deliveryDate: string | null
  shipVia: string | null
  shipTerm: string | null
  paymentTerm: string | null
}

/** "PO番号入力/確定UI" (Phase 9-A). ADMIN only (enforced Backend-side) -
 * editable while PENDING/GENERATED, locked once SUBMITTED (409
 * OFFICIAL_PO_ALREADY_SUBMITTED). */
async function confirmOfficialPoNumber(orderId: number, input: ConfirmOfficialPoNumberInput): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.put<OfficialPoIntegration>(`/orders/${orderId}/official-po/number`, input)
  return data
}

export function useConfirmOfficialPoNumber(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ConfirmOfficialPoNumberInput) => confirmOfficialPoNumber(orderId, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}

/** Official PO Excel generation (Phase 9-A). ADMIN only. Idempotent - a
 * repeat call while already GENERATED (or later) just returns current
 * state. */
async function generateOfficialPoExcel(orderId: number): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.post<OfficialPoIntegration>(`/orders/${orderId}/official-po/generate`)
  return data
}

export function useGenerateOfficialPoExcel(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => generateOfficialPoExcel(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}

/** Downloads the generated Official PO Excel (Phase 9-A). ADMIN only.
 * Triggers a browser file-save rather than returning JSON, so this is a
 * plain function (not a React Query hook) called directly from a click
 * handler. */
export async function downloadOfficialPoExcel(orderId: number): Promise<void> {
  const response = await apiClient.get<Blob>(`/orders/${orderId}/official-po/excel`, { responseType: 'blob' })
  const url = window.URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = `official-po-${orderId}.xlsx`
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}
