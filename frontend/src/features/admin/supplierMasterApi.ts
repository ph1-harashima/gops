import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { SupplierMasterDetail, SupplierMasterSummary } from '../../shared/types/supplierMaster'
import type { PageResponse } from '../../shared/types/pagination'

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * READ ONLY - Create/Update for Contact/Channel/Region/PO Code all remain on
 * their own existing API hooks (supplierContactApi.ts etc.), unchanged.
 *
 * Stage 5H Systematic Performance Remediation (RC-J, docs/real-data-audit/
 * gops-stage5h-systematic-performance-remediation.md): now Backend-paginated
 * - same {@link PageResponse} envelope every other paginated List screen
 * (Candidate List, Stock/Sales List) already uses.
 */
async function fetchSuppliers(page: number, size: number): Promise<PageResponse<SupplierMasterSummary>> {
  const { data } = await apiClient.get<PageResponse<SupplierMasterSummary>>('/admin/suppliers', {
    params: { page, size },
  })
  return data
}

export function useSupplierMasterList(page: number, size: number) {
  return useQuery({
    queryKey: ['supplier-master-list', page, size],
    queryFn: () => fetchSuppliers(page, size),
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
