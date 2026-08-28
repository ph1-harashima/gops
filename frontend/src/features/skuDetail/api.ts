import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { SkuDetail } from '../../shared/types/skuDetail'

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
