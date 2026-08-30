import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { WarehouseStockDetail, WarehouseStockSummary } from '../../shared/types/warehouseStock'
import type { PageResponse } from '../../shared/types/pagination'

export interface WarehouseStockListFilter {
  skuKeyword?: string
  brandCode?: string
  warehouseCode?: string
  minQty?: number
  maxQty?: number
}

async function fetchList(filter: WarehouseStockListFilter, page: number, size: number): Promise<PageResponse<WarehouseStockSummary>> {
  const { data } = await apiClient.get<PageResponse<WarehouseStockSummary>>('/warehouse-stock', {
    params: {
      skuKeyword: filter.skuKeyword || undefined,
      brandCode: filter.brandCode || undefined,
      warehouseCode: filter.warehouseCode || undefined,
      minQty: filter.minQty ?? undefined,
      maxQty: filter.maxQty ?? undefined,
      page,
      size,
    },
  })
  return data
}

// Phase 8-G 9章/15章: Backend-paginated + Backend-filtered, same principle
// as useArrivalList.
export function useWarehouseStockList(filter: WarehouseStockListFilter, page: number, size: number) {
  return useQuery({
    queryKey: ['warehouse-stock', 'list', filter, page, size],
    queryFn: () => fetchList(filter, page, size),
  })
}

async function fetchDetail(sku: string): Promise<WarehouseStockDetail> {
  const { data } = await apiClient.get<WarehouseStockDetail>(`/warehouse-stock/${encodeURIComponent(sku)}`)
  return data
}

// Phase 8-G 10章: no dedicated Route - the caller (WarehouseStockListPage's
// Drawer) fetches this only when a row is expanded.
export function useWarehouseStockDetail(sku: string | null) {
  return useQuery({
    queryKey: ['warehouse-stock', 'detail', sku],
    queryFn: () => fetchDetail(sku as string),
    enabled: sku != null,
  })
}
