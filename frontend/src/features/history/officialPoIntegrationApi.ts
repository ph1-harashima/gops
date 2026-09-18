import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OfficialPoIntegration, OfficialPoImportConfirmationResult, OfficialPoRevisionHistoryEntry } from '../../shared/types/officialPoIntegration'

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
      // Cache Consistency Fix: creates this Order's first Revision History
      // row (same field set - officialPoNo/integrationStatus/lifecycleStatus -
      // Reissue/Cancel already invalidate this key for the same reason).
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
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
      // Cache Consistency Fix: Revision History's own officialPoNo column.
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
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
      // Cache Consistency Fix: Revision History's own excelGenerated/
      // integrationStatus columns.
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
    },
  })
}

/** "Import Folderへ配置" (Phase 9-B). ADMIN only. Idempotent/Retry-safe -
 * a repeat call while already SUBMITTED/CONFIRMED is a no-op; a call while
 * FAILED retries the same stored Excel (never regenerates it). */
async function placeOfficialPoToImportFolder(orderId: number): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.post<OfficialPoIntegration>(`/orders/${orderId}/official-po/place`)
  return data
}

export function usePlaceOfficialPoToImportFolder(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => placeOfficialPoToImportFolder(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
      // Cache Consistency Fix: Revision History's own integrationStatus column.
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
    },
  })
}

/** "G-SYS取込確認" (Phase 9-C). ADMIN only. Strictly READ ONLY on Legacy -
 * never itself writes to G-SYS; only Portal's own Integration Status may
 * advance to CONFIRMED as a result. */
async function confirmOfficialPoImport(orderId: number): Promise<OfficialPoImportConfirmationResult> {
  const { data } = await apiClient.post<OfficialPoImportConfirmationResult>(`/orders/${orderId}/official-po/confirm-import`)
  return data
}

export function useConfirmOfficialPoImport(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => confirmOfficialPoImport(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
      // Cache Consistency Fix: Revision History's own integrationStatus column.
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
    },
  })
}

/** Gap Analysis B-3 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * extracts the file name the Backend actually computed
 * (OfficialPoFileNaming - Supplier/Brand/Date/PO No./Revision) from
 * Content-Disposition, rather than trusting the browser to apply it - a
 * Blob download's `<a download>` attribute always wins over the
 * Content-Disposition header once the bytes have already been fetched as a
 * Blob, so this must be read explicitly or the old hardcoded name would
 * silently keep appearing regardless of what the Backend sends. */
function fileNameFromContentDisposition(contentDisposition: string | undefined, fallback: string): string {
  if (!contentDisposition) return fallback
  const match = /filename="?([^";]+)"?/.exec(contentDisposition)
  return match ? decodeURIComponent(match[1]) : fallback
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
  link.download = fileNameFromContentDisposition(response.headers['content-disposition'], `official-po-${orderId}.xlsx`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}

/** Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md 7章):
 * "G-OPS Standard Official PO PDF" generation. ADMIN only. Always
 * re-generates (no separate PDF state machine to protect - see the
 * Backend's own Javadoc). */
async function generateOfficialPoPdf(orderId: number): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.post<OfficialPoIntegration>(`/orders/${orderId}/official-po/pdf/generate`)
  return data
}

export function useGenerateOfficialPoPdf(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => generateOfficialPoPdf(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
      // Cache Consistency Fix: Revision History's own pdfGenerated column.
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
    },
  })
}

/** Downloads the generated Official PO PDF. ADMIN only. Mirrors
 * {@link downloadOfficialPoExcel}. */
/** Gap Analysis C-2/C-3 (docs/gulliver-20260917-phase1-gap-analysis.md
 * 7章/8章): "Official POを再発行". ADMIN only. Human-confirmed action -
 * refused (409 OFFICIAL_PO_REISSUE_NOT_REQUIRED) unless the Backend's own
 * isReissueRequired detection agrees (same computation the "at a glance"
 * reissueRequired flag uses). */
async function reissueOfficialPo(orderId: number): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.post<OfficialPoIntegration>(`/orders/${orderId}/official-po/reissue`)
  return data
}

export function useReissueOfficialPo(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => reissueOfficialPo(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
      // Cache Consistency Fix: Reissue moves which Revision is "current" for
      // LegacyPoConcurrencyService.compare's own Revision resolution
      // (resolveCurrentRevisionNo) - the old Revision's Baseline result must
      // not keep showing once a new Revision exists.
      void queryClient.invalidateQueries({ queryKey: ['legacy-po-concurrency', orderId] })
    },
  })
}

/** Gap Analysis C-4 (docs/gulliver-20260917-phase1-gap-analysis.md 9章):
 * "Official POをCancel". ADMIN only, reason mandatory. G-OPS-internal
 * Workflow state - never writes to Legacy in any way. */
async function cancelOfficialPo(orderId: number, reason: string): Promise<OfficialPoIntegration> {
  const { data } = await apiClient.post<OfficialPoIntegration>(`/orders/${orderId}/official-po/cancel`, { reason })
  return data
}

export function useCancelOfficialPo(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (reason: string) => cancelOfficialPo(orderId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['official-po-integration', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['official-po-revisions', orderId] })
      void queryClient.invalidateQueries({ queryKey: ['order-events', orderId] })
    },
  })
}

/** Gap Analysis C-2: Revision History - any authenticated user may view it. */
async function fetchRevisionHistory(orderId: number): Promise<OfficialPoRevisionHistoryEntry[]> {
  const { data } = await apiClient.get<OfficialPoRevisionHistoryEntry[]>(`/orders/${orderId}/official-po/revisions`)
  return data
}

export function useOfficialPoRevisionHistory(orderId: number) {
  return useQuery({
    queryKey: ['official-po-revisions', orderId],
    queryFn: () => fetchRevisionHistory(orderId),
    enabled: Number.isFinite(orderId),
  })
}

export async function downloadOfficialPoPdf(orderId: number): Promise<void> {
  const response = await apiClient.get<Blob>(`/orders/${orderId}/official-po/pdf`, { responseType: 'blob' })
  const url = window.URL.createObjectURL(response.data)
  const link = document.createElement('a')
  link.href = url
  link.download = fileNameFromContentDisposition(response.headers['content-disposition'], `official-po-${orderId}.pdf`)
  document.body.appendChild(link)
  link.click()
  link.remove()
  window.URL.revokeObjectURL(url)
}
