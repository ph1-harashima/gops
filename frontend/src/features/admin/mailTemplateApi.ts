import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../shared/api/client'
import type { MailTemplate, MailTemplateRequest } from '../../shared/types/mailTemplate'

/** Phase 7-C3 12章/13章: ADMIN-only Master screen. */
async function fetchMailTemplates(): Promise<MailTemplate[]> {
  const { data } = await apiClient.get<MailTemplate[]>('/admin/mail-templates')
  return data
}

export function useMailTemplates() {
  return useQuery({
    queryKey: ['mail-templates'],
    queryFn: fetchMailTemplates,
  })
}

async function createMailTemplate(request: MailTemplateRequest): Promise<MailTemplate> {
  const { data } = await apiClient.post<MailTemplate>('/admin/mail-templates', request)
  return data
}

export function useCreateMailTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: createMailTemplate,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['mail-templates'] }),
  })
}

async function updateMailTemplate(id: number, request: MailTemplateRequest): Promise<MailTemplate> {
  const { data } = await apiClient.put<MailTemplate>(`/admin/mail-templates/${id}`, request)
  return data
}

export function useUpdateMailTemplate() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ id, request }: { id: number; request: MailTemplateRequest }) => updateMailTemplate(id, request),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['mail-templates'] }),
  })
}
