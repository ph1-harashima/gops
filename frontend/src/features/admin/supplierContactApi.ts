import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { SupplierContact, SupplierContactRequest } from '../../shared/types/supplierContact'

/** Phase 7-C3 12章/13章: ADMIN-only Master screen (Backend enforces via
 * @PreAuthorize on every one of these endpoints, both read and write). */
async function fetchSupplierContacts(): Promise<SupplierContact[]> {
  const { data } = await apiClient.get<SupplierContact[]>('/admin/supplier-contacts')
  return data
}

export function useSupplierContacts() {
  return useQuery({
    queryKey: ['supplier-contacts'],
    queryFn: fetchSupplierContacts,
  })
}

async function createSupplierContact(request: SupplierContactRequest): Promise<SupplierContact> {
  const { data } = await apiClient.post<SupplierContact>('/admin/supplier-contacts', request)
  return data
}

export function useCreateSupplierContact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createSupplierContact,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['supplier-contacts'] }),
  })
}

async function updateSupplierContact(id: number, request: SupplierContactRequest): Promise<SupplierContact> {
  const { data } = await apiClient.put<SupplierContact>(`/admin/supplier-contacts/${id}`, request)
  return data
}

export function useUpdateSupplierContact() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: SupplierContactRequest }) => updateSupplierContact(id, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['supplier-contacts'] }),
  })
}
