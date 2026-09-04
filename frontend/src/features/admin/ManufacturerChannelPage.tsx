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

import { useManufacturerChannels, useCreateManufacturerChannel, useUpdateManufacturerChannel } from './manufacturerChannelApi'
import type { ManufacturerChannel, ManufacturerChannelRequest } from '../../shared/types/manufacturerChannel'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const EMPTY_FORM: ManufacturerChannelRequest = {
  supplierCode: '', brandCode: null, channel: 'EMAIL', active: true,
}

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

/** Phase 9-D: ADMIN-only Master screen - mirrors SupplierContactPage's own
 * shape exactly (Backend also enforces this - reaching this page as
 * OPERATOR would just get 403s on every call). */
export function ManufacturerChannelPage() {
  const { t } = useTranslation(['manufacturerChannel', 'common'])
  const { data, isLoading, isError, refetch } = useManufacturerChannels()
  const createMutation = useCreateManufacturerChannel()
  const updateMutation = useUpdateManufacturerChannel()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<ManufacturerChannelRequest>(EMPTY_FORM)

  function openCreate() {
    setEditingId(null)
    setForm(EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(channel: ManufacturerChannel) {
    setEditingId(channel.id)
    setForm({ supplierCode: channel.supplierCode, brandCode: channel.brandCode, channel: channel.channel, active: channel.active })
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
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{t('title')}</Typography>
        <Button variant="contained" onClick={openCreate} data-testid="manufacturer-channel-create-button">
          {t('createButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('subtitle')}</Typography>

      {data && data.length === 0 && <Alert severity="info">{t('empty')}</Alert>}
      {data && data.length > 0 && (
        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="manufacturer-channel-table-container">
          <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
            <TableHead>
              <TableRow>
                <TableCell>{t('table.supplierCode')}</TableCell>
                <TableCell>{t('table.brandCode')}</TableCell>
                <TableCell>{t('table.channel')}</TableCell>
                <TableCell>{t('table.active')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {data.map((c) => (
                <TableRow key={c.id} hover data-testid={`manufacturer-channel-row-${c.id}`}>
                  <TableCell>{c.supplierCode}</TableCell>
                  <TableCell>{c.brandCode ?? '—'}</TableCell>
                  <TableCell>
                    <Chip size="small" label={t(`channel.${c.channel}`)} />
                  </TableCell>
                  <TableCell>
                    <Chip size="small" color={c.active ? 'success' : 'default'}
                          label={c.active ? t('statusActive') : t('statusInactive')} />
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => openEdit(c)} data-testid={`manufacturer-channel-edit-${c.id}`}>
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
                       size="small" fullWidth data-testid="manufacturer-channel-supplierCode" />
            <TextField label={t('field.brandCode')} helperText={t('field.brandCodeHelp')} value={form.brandCode ?? ''}
                       onChange={(e) => setForm({ ...form, brandCode: e.target.value })}
                       size="small" fullWidth data-testid="manufacturer-channel-brandCode" />
            <TextField select label={t('field.channel')} value={form.channel}
                       onChange={(e) => setForm({ ...form, channel: e.target.value as 'EMAIL' | 'EDI' })}
                       size="small" fullWidth data-testid="manufacturer-channel-channel">
              <MenuItem value="EMAIL">{t('channel.EMAIL')}</MenuItem>
              <MenuItem value="EDI">{t('channel.EDI')}</MenuItem>
            </TextField>
            <FormControlLabel
              control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })}
                                 data-testid="manufacturer-channel-active" />}
              label={t('field.active')} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={activeMutation.isPending}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={activeMutation.isPending} data-testid="manufacturer-channel-save">
            {activeMutation.isPending ? <CircularProgress size={20} /> : t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
