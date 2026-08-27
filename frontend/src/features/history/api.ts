import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { AuditEventItem, OrderHistoryDetail, OrderHistorySummary } from '../../shared/types/orderHistory'

export interface OrderHistoryFilter {
  supplierCode?: string
  brandCode?: string
  status?: string
}

async function fetchHistory(filter: OrderHistoryFilter): Promise<OrderHistorySummary[]> {
  const { data } = await apiClient.get<OrderHistorySummary[]>('/orders/history', {
    params: {
      supplierCode: filter.supplierCode || undefined,
      brandCode: filter.brandCode || undefined,
      status: filter.status || undefined,
    },
  })
  return data
}

export function useOrderHistory(filter: OrderHistoryFilter) {
  return useQuery({
    queryKey: ['order-history', filter],
    queryFn: () => fetchHistory(filter),
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
