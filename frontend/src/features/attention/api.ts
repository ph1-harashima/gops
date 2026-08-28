import { useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { Attention } from '../../shared/types/attention'

async function acknowledgeAttention(id: number): Promise<Attention> {
  const { data } = await apiClient.post<Attention>(`/attentions/${id}/acknowledge`)
  return data
}

/** Implementation instructions Step 5 2章. Invalidates every screen that
 * might show this Attention (History list/detail, Supplier Response) -
 * simpler and safer than trying to track exactly which queries reference a
 * given Attention id. */
export function useAcknowledgeAttention() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => acknowledgeAttention(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['order-history'] })
      void queryClient.invalidateQueries({ queryKey: ['order-history-detail'] })
      void queryClient.invalidateQueries({ queryKey: ['order-events'] })
      void queryClient.invalidateQueries({ queryKey: ['supplier-response'] })
      void queryClient.invalidateQueries({ queryKey: ['dashboard'] })
    },
  })
}
