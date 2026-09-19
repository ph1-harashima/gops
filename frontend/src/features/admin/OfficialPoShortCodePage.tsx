import { useMemo, useState } from 'react'
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

import { useOfficialPoShortCodes, useCreateOfficialPoShortCode, useUpdateOfficialPoShortCode } from './officialPoShortCodeApi'
import type { OfficialPoShortCode, OfficialPoShortCodeRequest } from '../../shared/types/officialPoShortCode'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const EMPTY_FORM: OfficialPoShortCodeRequest = {
  codeType: 'SUPPLIER', businessCode: '', shortCode: '', active: true,
}

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

/**
 * BR-08 (docs/gulliver-20260917-confirmed-business-rules.md): Official PO
 * Short Code Master admin screen - mirrors SupplierRegionClassificationPage's
 * own shape exactly (Backend also enforces ADMIN-only).
 *
 * IMPORTANT: G-OPS never generates or infers a Supplier/Brand abbreviation
 * here - this screen only RECORDS the 3-character value Gulliver has
 * already decided (conceptually, in G-SYS Master, which has no such column
 * today - re-confirmed READ ONLY). Official PO No. auto-numbering
 * (OfficialPoNumberGenerator) fails with a clear error until both the
 * Order's Supplier and Brand have an active row here.
 */
interface Props {
  /** Master Maintenance Hub - see SupplierContactPage's own Props doc for
   * the full rationale. Matches on {@code businessCode} (codeType=SUPPLIER
   * rows for this Supplier's own PO Short Code - a Brand's own short code,
   * codeType=BRAND, is a separate concept not scoped to any one Supplier). */
  supplierCodeFilter?: string
}

export function OfficialPoShortCodePage({ supplierCodeFilter }: Props = {}) {
  const { t } = useTranslation(['officialPoShortCode', 'common'])
  const { data, isLoading, isError, refetch } = useOfficialPoShortCodes()
  const createMutation = useCreateOfficialPoShortCode()
  const updateMutation = useUpdateOfficialPoShortCode()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<OfficialPoShortCodeRequest>(EMPTY_FORM)

  // Master Maintenance Hub / IA Audit §8 (Inactive行の扱い): mirrors
  // SupplierContactPage/ManufacturerChannelPage's "Active=Default表示"
  // convention, retrofitted here for consistency.
  const [showInactive, setShowInactive] = useState(false)
  const filtered = useMemo(() => {
    return (data ?? []).filter((c) => {
      if (supplierCodeFilter && !(c.codeType === 'SUPPLIER' && c.businessCode === supplierCodeFilter)) return false
      if (!showInactive && !c.active) return false
      return true
    })
  }, [data, showInactive, supplierCodeFilter])

  function openCreate() {
    setEditingId(null)
    setForm(supplierCodeFilter ? { ...EMPTY_FORM, codeType: 'SUPPLIER', businessCode: supplierCodeFilter } : EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(row: OfficialPoShortCode) {
    setEditingId(row.id)
    setForm({ codeType: row.codeType, businessCode: row.businessCode, shortCode: row.shortCode, active: row.active })
    setDialogOpen(true)
  }

  function handleSave() {
    const request = { ...form, shortCode: form.shortCode.trim().toUpperCase() }
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
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{t('title')}</Typography>
        <Button variant="contained" onClick={openCreate} data-testid="official-po-short-code-create-button">
          {t('createButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('subtitle')}</Typography>

      {data && data.length > 0 && (
        <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
          <FormControlLabel
            control={<Checkbox checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)}
                               data-testid="official-po-short-code-show-inactive" />}
            label={t('common:showInactiveLabel')} />
        </Stack>
      )}

      {data && data.length === 0 && <Alert severity="info">{t('empty')}</Alert>}
      {data && data.length > 0 && filtered.length === 0 && <Alert severity="info">{t('common:noSearchResults')}</Alert>}
      {filtered.length > 0 && (
        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="official-po-short-code-table-container">
          <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
            <TableHead>
              <TableRow>
                <TableCell>{t('table.codeType')}</TableCell>
                <TableCell>{t('table.businessCode')}</TableCell>
                <TableCell>{t('table.shortCode')}</TableCell>
                <TableCell>{t('table.active')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((c) => (
                <TableRow key={c.id} hover data-testid={`official-po-short-code-row-${c.id}`}>
                  <TableCell>{t(`codeType.${c.codeType}`)}</TableCell>
                  <TableCell>{c.businessCode}</TableCell>
                  <TableCell><Chip size="small" label={c.shortCode} /></TableCell>
                  <TableCell>
                    <Chip size="small" color={c.active ? 'success' : 'default'}
                          label={c.active ? t('statusActive') : t('statusInactive')} />
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => openEdit(c)} data-testid={`official-po-short-code-edit-${c.id}`}>
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
            <TextField select label={t('field.codeType')} value={form.codeType}
                       onChange={(e) => setForm({ ...form, codeType: e.target.value as 'SUPPLIER' | 'BRAND' })}
                       size="small" fullWidth data-testid="official-po-short-code-codeType">
              <MenuItem value="SUPPLIER">{t('codeType.SUPPLIER')}</MenuItem>
              <MenuItem value="BRAND">{t('codeType.BRAND')}</MenuItem>
            </TextField>
            <TextField label={t('field.businessCode')} helperText={t('field.businessCodeHelp')} value={form.businessCode}
                       onChange={(e) => setForm({ ...form, businessCode: e.target.value })}
                       size="small" fullWidth data-testid="official-po-short-code-businessCode" />
            <TextField label={t('field.shortCode')} helperText={t('field.shortCodeHelp')} value={form.shortCode}
                       onChange={(e) => setForm({ ...form, shortCode: e.target.value })}
                       slotProps={{ htmlInput: { maxLength: 3 } }}
                       size="small" fullWidth data-testid="official-po-short-code-shortCode" />
            <FormControlLabel
              control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })}
                                 data-testid="official-po-short-code-active" />}
              label={t('field.active')} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={activeMutation.isPending}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={activeMutation.isPending} data-testid="official-po-short-code-save">
            {activeMutation.isPending ? <CircularProgress size={20} /> : t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
