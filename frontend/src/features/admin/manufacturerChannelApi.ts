import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { ManufacturerChannel, ManufacturerChannelRequest } from '../../shared/types/manufacturerChannel'

/** Phase 9-D: ADMIN-only Master screen (Backend enforces via @PreAuthorize
 * on every one of these endpoints, both read and write). */
async function fetchManufacturerChannels(): Promise<ManufacturerChannel[]> {
  const { data } = await apiClient.get<ManufacturerChannel[]>('/admin/manufacturer-channels')
  return data
}

export function useManufacturerChannels() {
  return useQuery({
    queryKey: ['manufacturer-channels'],
    queryFn: fetchManufacturerChannels,
  })
}

async function createManufacturerChannel(request: ManufacturerChannelRequest): Promise<ManufacturerChannel> {
  const { data } = await apiClient.post<ManufacturerChannel>('/admin/manufacturer-channels', request)
  return data
}

export function useCreateManufacturerChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createManufacturerChannel,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['manufacturer-channels'] }),
  })
}

async function updateManufacturerChannel(id: number, request: ManufacturerChannelRequest): Promise<ManufacturerChannel> {
  const { data } = await apiClient.put<ManufacturerChannel>(`/admin/manufacturer-channels/${id}`, request)
  return data
}

export function useUpdateManufacturerChannel() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: ManufacturerChannelRequest }) => updateManufacturerChannel(id, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['manufacturer-channels'] }),
  })
}
