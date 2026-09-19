import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { SupplierMasterDetail, SupplierMasterSummary } from '../../shared/types/supplierMaster'

/** Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * READ ONLY - Create/Update for Contact/Channel/Region/PO Code all remain on
 * their own existing API hooks (supplierContactApi.ts etc.), unchanged. */
async function fetchSuppliers(): Promise<SupplierMasterSummary[]> {
  const { data } = await apiClient.get<SupplierMasterSummary[]>('/admin/suppliers')
  return data
}

export function useSupplierMasterList() {
  return useQuery({
    queryKey: ['supplier-master-list'],
    queryFn: fetchSuppliers,
  })
}

async function fetchSupplier(supplierCode: string): Promise<SupplierMasterDetail> {
  const { data } = await apiClient.get<SupplierMasterDetail>(`/admin/suppliers/${encodeURIComponent(supplierCode)}`)
  return data
}

export function useSupplierMasterDetail(supplierCode: string) {
  return useQuery({
    queryKey: ['supplier-master-detail', supplierCode],
    queryFn: () => fetchSupplier(supplierCode),
  })
}
