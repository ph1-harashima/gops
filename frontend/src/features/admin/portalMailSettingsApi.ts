import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'

/** Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
 * Default CC Foundation - prefill only, never an enforced "always CC" rule. */
export interface PortalMailSettings {
  defaultCc: string[]
  updatedBy: string | null
  updatedAt: string | null
}

async function fetchSettings(): Promise<PortalMailSettings> {
  const { data } = await apiClient.get<PortalMailSettings>('/admin/mail-settings')
  return data
}

export function usePortalMailSettings() {
  return useQuery({ queryKey: ['portal-mail-settings'], queryFn: fetchSettings })
}

async function updateSettings(defaultCc: string[]): Promise<PortalMailSettings> {
  const { data } = await apiClient.put<PortalMailSettings>('/admin/mail-settings', { defaultCc })
  return data
}

export function useUpdatePortalMailSettings() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (defaultCc: string[]) => updateSettings(defaultCc),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['portal-mail-settings'] })
    },
  })
}
