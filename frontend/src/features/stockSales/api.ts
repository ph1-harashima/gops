import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { StockSalesSummary } from '../../shared/types/stockSales'
import type { PageResponse } from '../../shared/types/pagination'

export interface StockSalesListFilter {
  skuKeyword?: string
  brandCode?: string
  supplierCode?: string
  minStock?: number
  maxStock?: number
  minSales?: number
  maxSales?: number
}

async function fetchList(filter: StockSalesListFilter, page: number, size: number): Promise<PageResponse<StockSalesSummary>> {
  const { data } = await apiClient.get<PageResponse<StockSalesSummary>>('/stock-sales', {
    params: {
      skuKeyword: filter.skuKeyword || undefined,
      brandCode: filter.brandCode || undefined,
      supplierCode: filter.supplierCode || undefined,
      minStock: filter.minStock ?? undefined,
      maxStock: filter.maxStock ?? undefined,
      minSales: filter.minSales ?? undefined,
      maxSales: filter.maxSales ?? undefined,
      page,
      size,
    },
  })
  return data
}

// Phase 8-H 6章: Backend-paginated + Backend-filtered - never a Frontend
// fetch-all, same principle as useArrivalList/useWarehouseStockList.
export function useStockSalesList(filter: StockSalesListFilter, page: number, size: number) {
  return useQuery({
    queryKey: ['stock-sales', 'list', filter, page, size],
    queryFn: () => fetchList(filter, page, size),
  })
}

async function fetchDetail(sku: string): Promise<StockSalesSummary> {
  const { data } = await apiClient.get<StockSalesSummary>(`/stock-sales/${encodeURIComponent(sku)}`)
  return data
}

export function useStockSalesDetail(sku: string | null) {
  return useQuery({
    queryKey: ['stock-sales', 'detail', sku],
    queryFn: () => fetchDetail(sku as string),
    enabled: sku != null,
  })
}
