import { useQuery } from '@tanstack/react-query'
import { apiClient } from './client'

/** BR-06 (docs/gulliver-20260917-confirmed-business-rules.md): Demo Send is
 * a Local/Demo/Test-only Test Helper Flow, never a Production Business
 * Function - the Frontend asks the Backend whether to show it, rather than
 * guessing the environment client-side. */
export interface FeatureFlags {
  demoSendEnabled: boolean
}

async function fetchFeatureFlags(): Promise<FeatureFlags> {
  const { data } = await apiClient.get<FeatureFlags>('/system/feature-flags')
  return data
}

export function useFeatureFlags() {
  return useQuery({
    queryKey: ['feature-flags'],
    queryFn: fetchFeatureFlags,
    // This never changes within a running deployment (it is read straight
    // from Spring config at startup) - no need to ever refetch it.
    staleTime: Infinity,
  })
}
