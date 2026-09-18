import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { SupplierRegionClassification, SupplierRegionClassificationRequest } from '../../shared/types/supplierRegionClassification'

/** Gap Analysis §12: ADMIN-only Master screen (Backend enforces via
 * @PreAuthorize on every one of these endpoints, both read and write). */
async function fetchRegionClassifications(): Promise<SupplierRegionClassification[]> {
  const { data } = await apiClient.get<SupplierRegionClassification[]>('/admin/supplier-region-classifications')
  return data
}

export function useSupplierRegionClassifications() {
  return useQuery({
    queryKey: ['supplier-region-classifications'],
    queryFn: fetchRegionClassifications,
  })
}

async function createRegionClassification(request: SupplierRegionClassificationRequest): Promise<SupplierRegionClassification> {
  const { data } = await apiClient.post<SupplierRegionClassification>('/admin/supplier-region-classifications', request)
  return data
}

export function useCreateSupplierRegionClassification() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createRegionClassification,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['supplier-region-classifications'] }),
  })
}

async function updateRegionClassification(id: number, request: SupplierRegionClassificationRequest): Promise<SupplierRegionClassification> {
  const { data } = await apiClient.put<SupplierRegionClassification>(`/admin/supplier-region-classifications/${id}`, request)
  return data
}

export function useUpdateSupplierRegionClassification() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: SupplierRegionClassificationRequest }) => updateRegionClassification(id, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['supplier-region-classifications'] }),
  })
}
