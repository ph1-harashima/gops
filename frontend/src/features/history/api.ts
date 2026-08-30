import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { AuditEventItem, OrderHistoryDetail, OrderHistorySummary } from '../../shared/types/orderHistory'
import type { PageResponse } from '../../shared/types/pagination'

export interface OrderHistoryFilter {
  supplierCode?: string
  brandCode?: string
  status?: string
  // Phase 7-H (Order List search/filter audit): same Backend query param
  // pattern as supplierCode/brandCode/status above - Backend does the
  // actual filtering (OrderHistoryService.list), not a Frontend fetch-all +
  // client filter, consistent with the existing 3.
  orderNoKeyword?: string
  itemKeyword?: string
  updatedFrom?: string
  updatedTo?: string
  // Phase 8-J 3章: was a Frontend-only display filter (over the full
  // fetched set) until this Phase - now a real Backend query param, the
  // SAME move orderNoKeyword/itemKeyword/updatedFrom/updatedTo already made
  // in Phase 7-H, required once List itself became Backend-paginated (a
  // per-page-only client filter would otherwise silently under-count).
  hasAttentionOnly?: boolean
}

async function fetchHistory(filter: OrderHistoryFilter, page: number, size: number): Promise<PageResponse<OrderHistorySummary>> {
  const { data } = await apiClient.get<PageResponse<OrderHistorySummary>>('/orders/history', {
    params: {
      supplierCode: filter.supplierCode || undefined,
      brandCode: filter.brandCode || undefined,
      status: filter.status || undefined,
      orderNoKeyword: filter.orderNoKeyword || undefined,
      itemKeyword: filter.itemKeyword || undefined,
      updatedFrom: filter.updatedFrom || undefined,
      updatedTo: filter.updatedTo || undefined,
      hasAttention: filter.hasAttentionOnly || undefined,
      page,
      size,
    },
  })
  return data
}

// Phase 8-J 3章/4章: Backend-paginated + Backend-filtered, matching
// useArrivalList/useWarehouseStockList/useStockSalesList - never a Frontend
// fetch-all.
export function useOrderHistory(filter: OrderHistoryFilter, page: number, size: number) {
  return useQuery({
    queryKey: ['order-history', filter, page, size],
    queryFn: () => fetchHistory(filter, page, size),
  })
}

async function fetchHistoryDetail(id: number): Promise<OrderHistoryDetail> {
  const { data } = await apiClient.get<OrderHistoryDetail>(`/orders/${id}`)
  return data
}

export function useOrderHistoryDetail(id: number) {
  return useQuery({
    queryKey: ['order-history-detail', id],
    queryFn: () => fetchHistoryDetail(id),
    enabled: Number.isFinite(id),
  })
}

async function fetchOrderEvents(id: number): Promise<AuditEventItem[]> {
  const { data } = await apiClient.get<AuditEventItem[]>(`/orders/${id}/events`)
  return data
}

export function useOrderEvents(id: number) {
  return useQuery({
    queryKey: ['order-events', id],
    queryFn: () => fetchOrderEvents(id),
    enabled: Number.isFinite(id),
  })
}
