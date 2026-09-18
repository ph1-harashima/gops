import { useRef, useState, type DragEvent } from 'react'
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

// Phase 7-F Header/List UX Audit (Mail Template Variable UX): business users
// should never need to see/type {{supplierName}} syntax directly - these
// Chips insert the exact same token the Backend's MailTemplateRenderer
// already resolves (SubjectTemplate/BodyTemplate storage format is
// unchanged, MailPreviewService's variable Map keys are unchanged - purely
// an input-affordance change, not a new template variable or Business Rule).
const VARIABLES: { key: string; labelKey: string }[] = [
  { key: 'supplierName', labelKey: 'variable.supplierName' },
  { key: 'contactName', labelKey: 'variable.contactName' },
  { key: 'poNo', labelKey: 'variable.poNo' },
  { key: 'orderDate', labelKey: 'variable.orderDate' },
  { key: 'requestedDelivery', labelKey: 'variable.requestedDelivery' },
  { key: 'senderName', labelKey: 'variable.senderName' },
  { key: 'senderEmail', labelKey: 'variable.senderEmail' },
  { key: 'revisionNo', labelKey: 'variable.revisionNo' },
]

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

  // Phase 7-F Header/List UX Audit (Mail Template Variable UX): tracks which
  // field a Variable Chip click should insert into (whichever was last
  // focused - Click-to-insert always works this way, per-field Drag&Drop
  // handlers below target their own field directly instead).
  const [activeField, setActiveField] = useState<'subject' | 'body'>('subject')
  const subjectInputRef = useRef<HTMLInputElement | null>(null)
  const bodyInputRef = useRef<HTMLTextAreaElement | null>(null)

  function insertVariable(key: string, targetField?: 'subject' | 'body') {
    const field = targetField ?? activeField
    const token = `{{${key}}}`
    const el = field === 'subject' ? subjectInputRef.current : bodyInputRef.current
    const currentValue = field === 'subject' ? form.subjectTemplate : form.bodyTemplate
    const start = el?.selectionStart ?? currentValue.length
    const end = el?.selectionEnd ?? currentValue.length
    const nextValue = currentValue.slice(0, start) + token + currentValue.slice(end)
    setForm((prev) => ({ ...prev, [field === 'subject' ? 'subjectTemplate' : 'bodyTemplate']: nextValue }))
    const cursorPos = start + token.length
    // Cursor restore must wait for the controlled TextField to actually
    // re-render with nextValue - doing it synchronously would still see the
    // OLD value's (shorter) length and clamp the position incorrectly.
    requestAnimationFrame(() => {
      el?.focus()
      el?.setSelectionRange(cursorPos, cursorPos)
    })
  }

  function handleVariableDrop(e: DragEvent<HTMLElement>, field: 'subject' | 'body') {
    e.preventDefault()
    const key = e.dataTransfer.getData('text/plain')
    if (key) insertVariable(key, field)
  }

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
    // Phase 7-F Header/List UX Audit (Sticky Table Header): see the same
    // structural note in CandidateListPage.tsx.
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{t('title')}</Typography>
        <Button variant="contained" onClick={openCreate} data-testid="mail-template-create-button">
          {t('createButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('subtitle')}</Typography>

      {data && data.length === 0 && <Alert severity="info">{t('empty')}</Alert>}
      {data && data.length > 0 && (
        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="mail-template-table-container">
          <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
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
            <TextField label={t('field.templateName')} value={form.templateName}
                       onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, templateName: v })) }}
                       size="small" fullWidth data-testid="mail-template-templateName" />
            {/* Gulliver UI最終安定化 #1: templateType/language(Select)は、MUIのSelectが
                内部でonChangeを二重にforwardする経路を持つ(handleChange->handleChange -
                実測スタックトレースで確認済み)。setForm({...form, ...})という古いclosure
                ベースの更新だと、二重発火時に両方が同じ古いformから新オブジェクトを計算し、
                Reactの再レンダーと競合して「Maximum update depth exceeded」を引き起こしうる
                (insertVariable()は元々関数型updater setForm(prev=>...)を使っており、過去2回の
                発生ともこの関数型updater経路では一度も再現していない - 唯一のクローズド差分)。
                このFormの全onChangeを関数型updaterへ統一し、この再入時の競合クラスを解消する。
                UIの見た目・挙動・保存データ形式は一切変更しない。 */}
            <TextField select label={t('field.templateType')} value={form.templateType}
                       onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, templateType: v })) }}
                       size="small" fullWidth data-testid="mail-template-templateType">
              {TEMPLATE_TYPES.map((type) => (
                <MenuItem key={type} value={type}>{t(`templateType.${type}`)}</MenuItem>
              ))}
            </TextField>
            <TextField label={t('field.supplierCode')} helperText={t('field.supplierCodeHelp')} value={form.supplierCode ?? ''}
                       onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, supplierCode: v })) }}
                       size="small" fullWidth data-testid="mail-template-supplierCode" />
            <TextField label={t('field.brandCode')} value={form.brandCode ?? ''}
                       onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, brandCode: v })) }}
                       size="small" fullWidth data-testid="mail-template-brandCode" />
            <TextField select label={t('field.language')} value={form.language}
                       onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, language: v })) }}
                       size="small" fullWidth data-testid="mail-template-language">
              <MenuItem value="ja">{t('languageOption.ja')}</MenuItem>
              <MenuItem value="en">{t('languageOption.en')}</MenuItem>
            </TextField>
            {/* Phase 7-F Header/List UX Audit (Mail Template Variable UX):
                business users click (or drag) a Chip instead of typing/
                remembering {{supplierName}}-style syntax. Click always
                inserts into whichever of Subject/Body was last focused
                (activeField); each field's own onDrop targets itself
                directly - both paths call the same insertVariable() and
                write the exact same {{key}} token the Backend already
                resolves, so nothing about the stored/rendered format
                changes. */}
            <Box>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 0.5 }}>
                {t('variablesInsertHelp')}
              </Typography>
              <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }} data-testid="mail-template-variable-chips">
                {VARIABLES.map((v) => (
                  <Chip
                    key={v.key}
                    size="small"
                    variant="outlined"
                    label={t(v.labelKey)}
                    clickable
                    draggable
                    onDragStart={(e) => e.dataTransfer.setData('text/plain', v.key)}
                    onClick={() => insertVariable(v.key)}
                    data-testid={`mail-template-variable-${v.key}`}
                  />
                ))}
              </Stack>
            </Box>
            <TextField
              label={t('field.subjectTemplate')}
              value={form.subjectTemplate}
              onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, subjectTemplate: v })) }}
              onFocus={() => setActiveField('subject')}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => handleVariableDrop(e, 'subject')}
              size="small"
              fullWidth
              data-testid="mail-template-subjectTemplate"
              slotProps={{ htmlInput: { ref: subjectInputRef } }}
            />
            <TextField
              label={t('field.bodyTemplate')}
              value={form.bodyTemplate}
              onChange={(e) => { const v = e.target.value; setForm((prev) => ({ ...prev, bodyTemplate: v })) }}
              onFocus={() => setActiveField('body')}
              onDragOver={(e) => e.preventDefault()}
              onDrop={(e) => handleVariableDrop(e, 'body')}
              size="small"
              fullWidth
              multiline
              minRows={4}
              data-testid="mail-template-bodyTemplate"
              slotProps={{ htmlInput: { ref: bodyInputRef } }}
            />
            <Typography variant="caption" color="text.secondary">{t('variablesHelp')}</Typography>
            <FormControlLabel
              control={<Checkbox checked={form.active} onChange={(e) => { const v = e.target.checked; setForm((prev) => ({ ...prev, active: v })) }}
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
