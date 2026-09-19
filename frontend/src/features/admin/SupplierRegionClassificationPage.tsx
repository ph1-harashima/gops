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

import { useSupplierRegionClassifications, useCreateSupplierRegionClassification, useUpdateSupplierRegionClassification } from './supplierRegionClassificationApi'
import type { SupplierRegionClassification, SupplierRegionClassificationRequest } from '../../shared/types/supplierRegionClassification'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const EMPTY_FORM: SupplierRegionClassificationRequest = {
  supplierCode: '', brandCode: null, regionClassification: 'DOMESTIC', active: true,
}

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

interface Props {
  /** Master Maintenance Hub - see SupplierContactPage's own Props doc for
   * the full rationale (identical pattern). */
  supplierCodeFilter?: string
}

/**
 * Gap Analysis §12 (docs/gulliver-20260917-phase1-gap-analysis.md 12章):
 * Domestic/Overseas Foundation admin screen - mirrors ManufacturerChannelPage's
 * own shape exactly (Backend also enforces ADMIN-only).
 *
 * IMPORTANT: this classification is a NEW, Portal-only, ADMIN-set Working
 * Assumption - Legacy has no such field for Supplier at all (re-confirmed
 * READ ONLY, see the Backend Entity's own Javadoc). It is never consulted
 * by the Recommended Qty calculation (発注数量計算式) - display only.
 */
export function SupplierRegionClassificationPage({ supplierCodeFilter }: Props = {}) {
  const { t } = useTranslation(['supplierRegionClassification', 'common'])
  const { data, isLoading, isError, refetch } = useSupplierRegionClassifications()
  const createMutation = useCreateSupplierRegionClassification()
  const updateMutation = useUpdateSupplierRegionClassification()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<SupplierRegionClassificationRequest>(EMPTY_FORM)

  // Master Maintenance Hub / IA Audit §8 (Inactive行の扱い): mirrors
  // SupplierContactPage/ManufacturerChannelPage's own "Active=Default表示"
  // convention, retrofitted here for consistency (this screen previously had
  // no Filter at all and showed every row unconditionally).
  const [showInactive, setShowInactive] = useState(false)
  const filtered = useMemo(() => {
    return (data ?? []).filter((c) => {
      if (supplierCodeFilter && c.supplierCode !== supplierCodeFilter) return false
      if (!showInactive && !c.active) return false
      return true
    })
  }, [data, showInactive, supplierCodeFilter])

  function openCreate() {
    setEditingId(null)
    setForm(supplierCodeFilter ? { ...EMPTY_FORM, supplierCode: supplierCodeFilter } : EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(row: SupplierRegionClassification) {
    setEditingId(row.id)
    setForm({ supplierCode: row.supplierCode, brandCode: row.brandCode, regionClassification: row.regionClassification, active: row.active })
    setDialogOpen(true)
  }

  function handleSave() {
    const request = { ...form, brandCode: form.brandCode || null }
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
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{t('title')}</Typography>
        <Button variant="contained" onClick={openCreate} data-testid="supplier-region-classification-create-button">
          {t('createButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('subtitle')}</Typography>

      {data && data.length > 0 && (
        <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
          <FormControlLabel
            control={<Checkbox checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)}
                               data-testid="supplier-region-classification-show-inactive" />}
            label={t('common:showInactiveLabel')} />
        </Stack>
      )}

      {data && data.length === 0 && <Alert severity="info">{t('empty')}</Alert>}
      {data && data.length > 0 && filtered.length === 0 && <Alert severity="info">{t('common:noSearchResults')}</Alert>}
      {filtered.length > 0 && (
        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="supplier-region-classification-table-container">
          <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
            <TableHead>
              <TableRow>
                <TableCell>{t('table.supplierCode')}</TableCell>
                <TableCell>{t('table.brandCode')}</TableCell>
                <TableCell>{t('table.regionClassification')}</TableCell>
                <TableCell>{t('table.active')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((c) => (
                <TableRow key={c.id} hover data-testid={`supplier-region-classification-row-${c.id}`}>
                  <TableCell>{c.supplierCode}</TableCell>
                  <TableCell>{c.brandCode ?? '—'}</TableCell>
                  <TableCell>
                    <Chip size="small" label={t(`regionClassification.${c.regionClassification}`)} />
                  </TableCell>
                  <TableCell>
                    <Chip size="small" color={c.active ? 'success' : 'default'}
                          label={c.active ? t('statusActive') : t('statusInactive')} />
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => openEdit(c)} data-testid={`supplier-region-classification-edit-${c.id}`}>
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
            <TextField label={t('field.supplierCode')} value={form.supplierCode}
                       onChange={(e) => setForm({ ...form, supplierCode: e.target.value })}
                       size="small" fullWidth data-testid="supplier-region-classification-supplierCode" />
            <TextField label={t('field.brandCode')} helperText={t('field.brandCodeHelp')} value={form.brandCode ?? ''}
                       onChange={(e) => setForm({ ...form, brandCode: e.target.value })}
                       size="small" fullWidth data-testid="supplier-region-classification-brandCode" />
            <TextField select label={t('field.regionClassification')} value={form.regionClassification}
                       onChange={(e) => setForm({ ...form, regionClassification: e.target.value as 'DOMESTIC' | 'OVERSEAS' })}
                       size="small" fullWidth data-testid="supplier-region-classification-regionClassification">
              <MenuItem value="DOMESTIC">{t('regionClassification.DOMESTIC')}</MenuItem>
              <MenuItem value="OVERSEAS">{t('regionClassification.OVERSEAS')}</MenuItem>
            </TextField>
            <FormControlLabel
              control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })}
                                 data-testid="supplier-region-classification-active" />}
              label={t('field.active')} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={activeMutation.isPending}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={activeMutation.isPending} data-testid="supplier-region-classification-save">
            {activeMutation.isPending ? <CircularProgress size={20} /> : t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
