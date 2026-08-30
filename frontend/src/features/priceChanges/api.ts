import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type {
  PriceChangeCandidate,
  PriceChangeSetDetail,
  PriceChangeSetSummary,
} from '../../shared/types/priceChange'

async function fetchList(status?: string): Promise<PriceChangeSetSummary[]> {
  const { data } = await apiClient.get<PriceChangeSetSummary[]>('/price-changes', { params: { status } })
  return data
}

export function usePriceChangeList(status?: string) {
  return useQuery({
    queryKey: ['price-changes', 'list', status ?? null],
    queryFn: () => fetchList(status),
  })
}

async function fetchDetail(id: number): Promise<PriceChangeSetDetail> {
  const { data } = await apiClient.get<PriceChangeSetDetail>(`/price-changes/${id}`)
  return data
}

export function usePriceChangeDetail(id: number) {
  return useQuery({
    queryKey: ['price-changes', 'detail', id],
    queryFn: () => fetchDetail(id),
    enabled: Number.isFinite(id),
  })
}

async function searchCandidates(params: { brandCode?: string; itemGrpCd?: string; keyword?: string }): Promise<PriceChangeCandidate[]> {
  const { data } = await apiClient.get<PriceChangeCandidate[]>('/price-changes/legacy-items', { params })
  return data
}

/** Product Selection search - deliberately NOT auto-run on every keystroke
 * (enabled gate below); callers trigger it explicitly (button click) to
 * avoid hammering the Legacy READ ONLY connection on every character. */
export function usePriceChangeCandidates(params: { brandCode?: string; itemGrpCd?: string; keyword?: string }, enabled: boolean) {
  return useQuery({
    queryKey: ['price-changes', 'candidates', params],
    queryFn: () => searchCandidates(params),
    enabled,
  })
}

async function fetchItemGroups(): Promise<string[]> {
  const { data } = await apiClient.get<string[]>('/price-changes/item-groups')
  return data
}

export function usePriceChangeItemGroups() {
  return useQuery({
    queryKey: ['price-changes', 'item-groups'],
    queryFn: fetchItemGroups,
  })
}

async function createPriceChangeSet(note: string | null): Promise<PriceChangeSetDetail> {
  const { data } = await apiClient.post<PriceChangeSetDetail>('/price-changes', { note })
  return data
}

export function useCreatePriceChangeSet() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createPriceChangeSet,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['price-changes', 'list'] })
    },
  })
}

function invalidateDetail(queryClient: ReturnType<typeof useQueryClient>, id: number) {
  // Same "invalidate and re-GET" idiom as useUpdateDraft - always shows what
  // is actually persisted, never merely what a mutation response echoed.
  void queryClient.invalidateQueries({ queryKey: ['price-changes', 'detail', id] })
  void queryClient.invalidateQueries({ queryKey: ['price-changes', 'list'] })
}

export function useAddPriceChangeDetail(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (itemCd: string) => apiClient.post<PriceChangeSetDetail>(`/price-changes/${id}/details`, { itemCd }).then((r) => r.data),
    onSuccess: () => invalidateDetail(queryClient, id),
  })
}

export function useAddPriceChangeItemGroup(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (itemGrpCd: string) =>
      apiClient.post<PriceChangeSetDetail>(`/price-changes/${id}/details/by-item-group`, { itemGrpCd }).then((r) => r.data),
    onSuccess: () => invalidateDetail(queryClient, id),
  })
}

export function useUpdateProposedPrice(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (args: { detailId: number; proposedPrcSellWTax: number | null }) =>
      apiClient
        .put<PriceChangeSetDetail>(`/price-changes/${id}/details/${args.detailId}/proposed-price`, {
          proposedPrcSellWTax: args.proposedPrcSellWTax,
        })
        .then((r) => r.data),
    onSuccess: () => invalidateDetail(queryClient, id),
  })
}

export function useRemovePriceChangeDetail(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (detailId: number) =>
      apiClient.delete<PriceChangeSetDetail>(`/price-changes/${id}/details/${detailId}`).then((r) => r.data),
    onSuccess: () => invalidateDetail(queryClient, id),
  })
}

export function useUpdatePriceChangeNote(id: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (note: string | null) =>
      apiClient.put<PriceChangeSetDetail>(`/price-changes/${id}/note`, { note }).then((r) => r.data),
    onSuccess: () => invalidateDetail(queryClient, id),
  })
}
