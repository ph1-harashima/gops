import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { OrderStatusChange } from '../../shared/types/poPreview'
import type { SaveSupplierResponseRequest, SupplierResponse } from '../../shared/types/supplierResponse'

async function fetchSupplierResponse(orderId: number): Promise<SupplierResponse> {
  const { data } = await apiClient.get<SupplierResponse>(`/orders/${orderId}/supplier-response`)
  return data
}

export function useSupplierResponse(orderId: number) {
  return useQuery({
    queryKey: ['supplier-response', orderId],
    queryFn: () => fetchSupplierResponse(orderId),
    enabled: Number.isFinite(orderId),
  })
}

async function saveSupplierResponse(orderId: number, request: SaveSupplierResponseRequest): Promise<SupplierResponse> {
  const { data } = await apiClient.put<SupplierResponse>(`/orders/${orderId}/supplier-response`, request)
  return data
}

export function useSaveSupplierResponse(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (request: SaveSupplierResponseRequest) => saveSupplierResponse(orderId, request),
    onSuccess: (data) => {
      // Requirements MD 19章: re-GET after save so the screen always shows
      // what the server actually persisted - write the PUT response
      // straight into the cache (it IS the authoritative post-save state,
      // same shape as GET) rather than issuing a second round-trip.
      queryClient.setQueryData(['supplier-response', orderId], data)
    },
  })
}

async function confirmSupplierResponse(orderId: number): Promise<OrderStatusChange> {
  const { data } = await apiClient.post<OrderStatusChange>(`/orders/${orderId}/supplier-response/confirm`)
  return data
}

export function useConfirmSupplierResponse(orderId: number) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => confirmSupplierResponse(orderId),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['supplier-response', orderId] })
    },
  })
}
