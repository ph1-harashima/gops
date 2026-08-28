import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogActions from '@mui/material/DialogActions'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'

import { useMailTemplates, useCreateMailTemplate, useUpdateMailTemplate } from './mailTemplateApi'
import type { MailTemplate, MailTemplateRequest } from '../../shared/types/mailTemplate'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const EMPTY_FORM: MailTemplateRequest = {
  templateName: '', templateType: 'PURCHASE_ORDER', supplierCode: null, brandCode: null,
  language: 'ja', subjectTemplate: '', bodyTemplate: '', attachmentType: null, active: true,
}

const TEMPLATE_TYPES = ['PURCHASE_ORDER', 'PURCHASE_ORDER_REVISION', 'FOLLOW_UP', 'CANCELLATION']

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

/** Phase 7-C3 12章/13章: ADMIN-only Master screen. */
export function MailTemplatePage() {
  const { t } = useTranslation(['mailTemplate', 'common'])
  const { data, isLoading, isError, refetch } = useMailTemplates()
  const createMutation = useCreateMailTemplate()
  const updateMutation = useUpdateMailTemplate()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<MailTemplateRequest>(EMPTY_FORM)

  function openCreate() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(template: MailTemplate) {
    setEditingId(template.id)
    setForm({
      templateName: template.templateName, templateType: template.templateType,
      supplierCode: template.supplierCode, brandCode: template.brandCode, language: template.language,
      subjectTemplate: template.subjectTemplate, bodyTemplate: template.bodyTemplate,
      attachmentType: template.attachmentType, active: template.active,
    })
    setDialogOpen(true)
  }

  function handleSave() {
    const request = { ...form, supplierCode: form.supplierCode || null, brandCode: form.brandCode || null, attachmentType: form.attachmentType || null }
    const onSuccess = () => setDialogOpen(false)
    if (editingId != null) {
      updateMutation.mutate({ id: editingId, request }, { onSuccess })
    } else {
      createMutation.mutate(request, { onSuccess })
    }
  }

  const activeMutation = editingId != null ? updateMutation : createMutation

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>{t('common:retry')}</Button>}>
          {t('common:errorGeneric')}
        </Alert>
      </Box>
    )
  }

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{t('title')}</Typography>
        <Button variant="contained" onClick={openCreate} data-testid="mail-template-create-button">
          {t('createButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('subtitle')}</Typography>

      {data && data.length === 0 && <Alert severity="info">{t('empty')}</Alert>}
      {data && data.length > 0 && (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('table.templateName')}</TableCell>
                <TableCell>{t('table.templateType')}</TableCell>
                <TableCell>{t('table.supplierCode')}</TableCell>
                <TableCell>{t('table.brandCode')}</TableCell>
                <TableCell>{t('table.language')}</TableCell>
                <TableCell>{t('table.active')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((tpl) => (
                <TableRow key={tpl.id} hover data-testid={`mail-template-row-${tpl.id}`}>
                  <TableCell>{tpl.templateName}</TableCell>
                  <TableCell>{t(`templateType.${tpl.templateType}`)}</TableCell>
                  <TableCell>{tpl.supplierCode ?? '—'}</TableCell>
                  <TableCell>{tpl.brandCode ?? '—'}</TableCell>
                  <TableCell>{tpl.language}</TableCell>
                  <TableCell>
                    <Chip size="small" color={tpl.active ? 'success' : 'default'}
                          label={tpl.active ? t('statusActive') : t('statusInactive')} />
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => openEdit(tpl)} data-testid={`mail-template-edit-${tpl.id}`}>
                      {t('editButton')}
                    </Button>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}

      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} maxWidth="sm" fullWidth>
        <DialogTitle>{editingId != null ? t('dialogTitleEdit') : t('dialogTitleCreate')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1 }}>
            {activeMutation.isError && (
              <Alert severity="error">
                {t(`error.${errorCodeOf(activeMutation.error) ?? 'GENERIC'}`, { defaultValue: t('error.GENERIC') })}
              </Alert>
            )}
            <Typography variant="caption" color="text.secondary">{t('variablesHelp')}</Typography>
            <TextField label={t('field.templateName')} value={form.templateName}
                       onChange={(e) => setForm({ ...form, templateName: e.target.value })}
                       size="small" fullWidth data-testid="mail-template-templateName" />
            <TextField select label={t('field.templateType')} value={form.templateType}
                       onChange={(e) => setForm({ ...form, templateType: e.target.value })}
                       size="small" fullWidth data-testid="mail-template-templateType">
              {TEMPLATE_TYPES.map((type) => (
                <MenuItem key={type} value={type}>{t(`templateType.${type}`)}</MenuItem>
              ))}
            </TextField>
            <TextField label={t('field.supplierCode')} helperText={t('field.supplierCodeHelp')} value={form.supplierCode ?? ''}
                       onChange={(e) => setForm({ ...form, supplierCode: e.target.value })}
                       size="small" fullWidth data-testid="mail-template-supplierCode" />
            <TextField label={t('field.brandCode')} value={form.brandCode ?? ''}
                       onChange={(e) => setForm({ ...form, brandCode: e.target.value })}
                       size="small" fullWidth data-testid="mail-template-brandCode" />
            <TextField select label={t('field.language')} value={form.language}
                       onChange={(e) => setForm({ ...form, language: e.target.value })}
                       size="small" fullWidth data-testid="mail-template-language">
              <MenuItem value="ja">日本語</MenuItem>
              <MenuItem value="en">English</MenuItem>
            </TextField>
            <TextField label={t('field.subjectTemplate')} value={form.subjectTemplate}
                       onChange={(e) => setForm({ ...form, subjectTemplate: e.target.value })}
                       size="small" fullWidth data-testid="mail-template-subjectTemplate" />
            <TextField label={t('field.bodyTemplate')} value={form.bodyTemplate}
                       onChange={(e) => setForm({ ...form, bodyTemplate: e.target.value })}
                       size="small" fullWidth multiline minRows={4} data-testid="mail-template-bodyTemplate" />
            <FormControlLabel
              control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })}
                                 data-testid="mail-template-active" />}
              label={t('field.active')} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={activeMutation.isPending}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={activeMutation.isPending} data-testid="mail-template-save">
            {activeMutation.isPending ? <CircularProgress size={20} /> : t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
