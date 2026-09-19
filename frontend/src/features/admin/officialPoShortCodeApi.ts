import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OfficialPoShortCode, OfficialPoShortCodeRequest } from '../../shared/types/officialPoShortCode'

/** BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): ADMIN-only
 * Master screen (Backend enforces via @PreAuthorize on every endpoint, both
 * read and write) - registers the 3-character Supplier/Brand abbreviation
 * Gulliver has decided, so OfficialPoNumberGenerator can compose the
 * auto-numbered Official PO No. without ever inventing one itself. */
async function fetchShortCodes(): Promise<OfficialPoShortCode[]> {
  const { data } = await apiClient.get<OfficialPoShortCode[]>('/admin/official-po-short-codes')
  return data
}

export function useOfficialPoShortCodes() {
  return useQuery({
    queryKey: ['official-po-short-codes'],
    queryFn: fetchShortCodes,
  })
}

async function createShortCode(request: OfficialPoShortCodeRequest): Promise<OfficialPoShortCode> {
  const { data } = await apiClient.post<OfficialPoShortCode>('/admin/official-po-short-codes', request)
  return data
}

export function useCreateOfficialPoShortCode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createShortCode,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['official-po-short-codes'] }),
  })
}

async function updateShortCode(id: number, request: OfficialPoShortCodeRequest): Promise<OfficialPoShortCode> {
  const { data } = await apiClient.put<OfficialPoShortCode>(`/admin/official-po-short-codes/${id}`, request)
  return data
}

export function useUpdateOfficialPoShortCode() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: OfficialPoShortCodeRequest }) => updateShortCode(id, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['official-po-short-codes'] }),
  })
}
