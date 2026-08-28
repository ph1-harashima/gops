import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { Dashboard } from '../../shared/types/dashboard'

async function fetchDashboard(): Promise<Dashboard> {
  const { data } = await apiClient.get<Dashboard>('/dashboard')
  return data
}

export function useDashboard() {
  return useQuery({
    queryKey: ['dashboard'],
    queryFn: fetchDashboard,
  })
}
