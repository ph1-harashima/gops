import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { SkuDetail } from '../../shared/types/skuDetail'
import type { RestockExpectation, RestockExpectationInput } from '../../shared/types/restockExpectation'

async function fetchSkuDetail(sku: string): Promise<SkuDetail> {
  const { data } = await apiClient.get<SkuDetail>(`/items/${encodeURIComponent(sku)}/ordering-context`)
  return data
}

export function useSkuDetail(sku: string) {
  return useQuery({
    queryKey: ['sku-detail', sku],
    queryFn: () => fetchSkuDetail(sku),
    enabled: Boolean(sku),
  })
}

/** Post-Freeze Business Refinement (re-audit doc §9/§10-3) - a separate
 * call from useSkuDetail's own Legacy-only READ, matching this app's
 * existing "Order Detail + separate Official PO Integration call" idiom
 * rather than folding a Portal-owned concern into a Legacy-Read-only DTO. */
async function fetchRestockExpectation(sku: string): Promise<RestockExpectation> {
  const { data } = await apiClient.get<RestockExpectation>(`/items/${encodeURIComponent(sku)}/restock-expectation`)
  return data
}

export function useRestockExpectation(sku: string) {
  return useQuery({
    queryKey: ['restock-expectation', sku],
    queryFn: () => fetchRestockExpectation(sku),
    enabled: Boolean(sku),
  })
}

async function updateRestockExpectation(sku: string, input: RestockExpectationInput): Promise<RestockExpectation> {
  const { data } = await apiClient.put<RestockExpectation>(`/items/${encodeURIComponent(sku)}/restock-expectation`, input)
  return data
}

export function useUpdateRestockExpectation(sku: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: RestockExpectationInput) => updateRestockExpectation(sku, input),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['restock-expectation', sku] })
      // The Candidate List/Order History/Stock-Sales Lists all embed this
      // same SKU's restock info - invalidate broadly rather than trying to
      // patch every possibly-cached page individually.
      void queryClient.invalidateQueries({ queryKey: ['order-candidates'] })
      void queryClient.invalidateQueries({ queryKey: ['stock-sales'] })
    },
  })
}
