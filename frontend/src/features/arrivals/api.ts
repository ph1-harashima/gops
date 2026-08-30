import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { ArrivalDetail, ArrivalSummary } from '../../shared/types/arrival'
import type { PageResponse } from '../../shared/types/pagination'

export interface ArrivalListFilter {
  supplierCode?: string
  brandCode?: string
  poNumber?: string
  invoiceNumber?: string
  skuKeyword?: string
  arrivalDateFrom?: string
  arrivalDateTo?: string
}

async function fetchList(filter: ArrivalListFilter, page: number, size: number): Promise<PageResponse<ArrivalSummary>> {
  const { data } = await apiClient.get<PageResponse<ArrivalSummary>>('/arrivals', {
    params: {
      supplierCode: filter.supplierCode || undefined,
      brandCode: filter.brandCode || undefined,
      poNumber: filter.poNumber || undefined,
      invoiceNumber: filter.invoiceNumber || undefined,
      skuKeyword: filter.skuKeyword || undefined,
      arrivalDateFrom: filter.arrivalDateFrom || undefined,
      arrivalDateTo: filter.arrivalDateTo || undefined,
      page,
      size,
    },
  })
  return data
}

// Phase 8-G 5章/15章: Backend-paginated + Backend-filtered - the Frontend
// never fetches all Arrivals and filters/paginates client-side (same
// principle as PriceChangeSetLineTable's own comment on why calculation
// columns are never re-derived client-side).
export function useArrivalList(filter: ArrivalListFilter, page: number, size: number) {
  return useQuery({
    queryKey: ['arrivals', 'list', filter, page, size],
    queryFn: () => fetchList(filter, page, size),
  })
}

async function fetchDetail(supplierCode: string, poNumber: string, invoiceNumber: string): Promise<ArrivalDetail> {
  const { data } = await apiClient.get<ArrivalDetail>(
    `/arrivals/${encodeURIComponent(supplierCode)}/${encodeURIComponent(poNumber)}/${encodeURIComponent(invoiceNumber)}`,
  )
  return data
}

export function useArrivalDetail(supplierCode: string, poNumber: string, invoiceNumber: string) {
  return useQuery({
    queryKey: ['arrivals', 'detail', supplierCode, poNumber, invoiceNumber],
    queryFn: () => fetchDetail(supplierCode, poNumber, invoiceNumber),
    enabled: Boolean(supplierCode && poNumber && invoiceNumber),
  })
}
