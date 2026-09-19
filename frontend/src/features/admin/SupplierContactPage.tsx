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

import { useSupplierContacts, useCreateSupplierContact, useUpdateSupplierContact } from './supplierContactApi'
import type { SupplierContact, SupplierContactRequest } from '../../shared/types/supplierContact'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const EMPTY_FORM: SupplierContactRequest = {
  supplierCode: '', brandCode: null, contactName: '', email: '',
  contactType: 'TO', language: 'ja', region: null, procurementType: null, primary: false, active: true,
}

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

interface Props {
  /** Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
   * when set (Supplier Settings' own Contacts tab), hard-scopes the table to
   * this exact Supplier and pre-fills it on Create - the Supplier Context a
   * user already selected is never lost/re-searched. Omitted entirely on the
   * existing standalone `/admin/supplier-contacts` route, whose behavior is
   * therefore completely unchanged (Backward Compatibility). */
  supplierCodeFilter?: string
}

/** Phase 7-C3 12章/13章: ADMIN-only Master screen (Backend also enforces
 * this - reaching this page as OPERATOR would just get 403s on every call). */
export function SupplierContactPage({ supplierCodeFilter }: Props = {}) {
  const { t } = useTranslation(['supplierContact', 'common'])
  const { data, isLoading, isError, refetch } = useSupplierContacts()
  const createMutation = useCreateSupplierContact()
  const updateMutation = useUpdateSupplierContact()

  const [dialogOpen, setDialogOpen] = useState(false)
  const [editingId, setEditingId] = useState<number | null>(null)
  const [form, setForm] = useState<SupplierContactRequest>(EMPTY_FORM)

  // Acceptance Fix C-4: same minimal client-side Search/Filter as
  // ManufacturerChannelPage - see that file's own comment for rationale.
  const [search, setSearch] = useState('')
  const [showInactive, setShowInactive] = useState(false)
  const filtered = useMemo(() => {
    const keyword = search.trim().toLowerCase()
    return (data ?? []).filter((c) => {
      if (supplierCodeFilter && c.supplierCode !== supplierCodeFilter) return false
      if (!showInactive && !c.active) return false
      if (!keyword) return true
      return c.supplierCode.toLowerCase().includes(keyword)
        || (c.brandCode ?? '').toLowerCase().includes(keyword)
        || c.contactName.toLowerCase().includes(keyword)
        || c.email.toLowerCase().includes(keyword)
    })
  }, [data, search, showInactive, supplierCodeFilter])
  function clearFilters() {
    setSearch('')
    setShowInactive(false)
  }

  function openCreate() {
    setEditingId(null)
    setForm(supplierCodeFilter ? { ...EMPTY_FORM, supplierCode: supplierCodeFilter } : EMPTY_FORM)
    setDialogOpen(true)
  }

  function openEdit(contact: SupplierContact) {
    setEditingId(contact.id)
    setForm({
      supplierCode: contact.supplierCode, brandCode: contact.brandCode, contactName: contact.contactName,
      email: contact.email, contactType: contact.contactType, language: contact.language,
      region: contact.region, procurementType: contact.procurementType, primary: contact.primary, active: contact.active,
    })
    setDialogOpen(true)
  }

  function handleSave() {
    const request = { ...form, brandCode: form.brandCode || null, region: form.region || null, procurementType: form.procurementType || null }
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
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ mb: 1, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{t('title')}</Typography>
        <Button variant="contained" onClick={openCreate} data-testid="supplier-contact-create-button">
          {t('createButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('subtitle')}</Typography>

      {data && data.length > 0 && (
        <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
          <TextField size="small" label={t('common:searchLabel')} value={search}
                     onChange={(e) => setSearch(e.target.value)}
                     data-testid="supplier-contact-search" sx={{ minWidth: 240 }} />
          <FormControlLabel
            control={<Checkbox checked={showInactive} onChange={(e) => setShowInactive(e.target.checked)}
                               data-testid="supplier-contact-show-inactive" />}
            label={t('common:showInactiveLabel')} />
          <Button size="small" onClick={clearFilters} data-testid="supplier-contact-clear-filters">{t('common:clearFilters')}</Button>
        </Stack>
      )}

      {data && data.length === 0 && <Alert severity="info">{t('empty')}</Alert>}
      {data && data.length > 0 && filtered.length === 0 && <Alert severity="info">{t('common:noSearchResults')}</Alert>}
      {filtered.length > 0 && (
        <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="supplier-contact-table-container">
          <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
            <TableHead>
              <TableRow>
                <TableCell>{t('table.supplierCode')}</TableCell>
                <TableCell>{t('table.brandCode')}</TableCell>
                <TableCell>{t('table.contactName')}</TableCell>
                <TableCell>{t('table.email')}</TableCell>
                <TableCell>{t('table.contactType')}</TableCell>
                <TableCell>{t('table.language')}</TableCell>
                <TableCell>{t('table.primary')}</TableCell>
                <TableCell>{t('table.active')}</TableCell>
                <TableCell />
              </TableRow>
            </TableHead>
            <TableBody>
              {filtered.map((c) => (
                <TableRow key={c.id} hover data-testid={`supplier-contact-row-${c.id}`}>
                  <TableCell>{c.supplierCode}</TableCell>
                  <TableCell>{c.brandCode ?? '—'}</TableCell>
                  <TableCell>{c.contactName}</TableCell>
                  <TableCell>{c.email}</TableCell>
                  <TableCell>{t(`contactType.${c.contactType}`)}</TableCell>
                  <TableCell>{t(`language.${c.language}`)}</TableCell>
                  <TableCell>{c.primary ? '✓' : ''}</TableCell>
                  <TableCell>
                    <Chip size="small" color={c.active ? 'success' : 'default'}
                          label={c.active ? t('statusActive') : t('statusInactive')} />
                  </TableCell>
                  <TableCell>
                    <Button size="small" onClick={() => openEdit(c)} data-testid={`supplier-contact-edit-${c.id}`}>
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
                       size="small" fullWidth data-testid="supplier-contact-supplierCode" />
            <TextField label={t('field.brandCode')} helperText={t('field.brandCodeHelp')} value={form.brandCode ?? ''}
                       onChange={(e) => setForm({ ...form, brandCode: e.target.value })}
                       size="small" fullWidth data-testid="supplier-contact-brandCode" />
            <TextField label={t('field.contactName')} value={form.contactName}
                       onChange={(e) => setForm({ ...form, contactName: e.target.value })}
                       size="small" fullWidth data-testid="supplier-contact-contactName" />
            <TextField label={t('field.email')} value={form.email}
                       onChange={(e) => setForm({ ...form, email: e.target.value })}
                       size="small" fullWidth data-testid="supplier-contact-email" />
            <TextField select label={t('field.contactType')} value={form.contactType}
                       onChange={(e) => setForm({ ...form, contactType: e.target.value })}
                       size="small" fullWidth data-testid="supplier-contact-contactType">
              <MenuItem value="TO">{t('contactType.TO')}</MenuItem>
              <MenuItem value="CC">{t('contactType.CC')}</MenuItem>
            </TextField>
            <TextField select label={t('field.language')} value={form.language}
                       onChange={(e) => setForm({ ...form, language: e.target.value })}
                       size="small" fullWidth data-testid="supplier-contact-language">
              <MenuItem value="ja">{t('language.ja')}</MenuItem>
              <MenuItem value="en">{t('language.en')}</MenuItem>
            </TextField>
            <FormControlLabel
              control={<Checkbox checked={form.primary} onChange={(e) => setForm({ ...form, primary: e.target.checked })} />}
              label={t('field.primary')} />
            <FormControlLabel
              control={<Checkbox checked={form.active} onChange={(e) => setForm({ ...form, active: e.target.checked })}
                                 data-testid="supplier-contact-active" />}
              label={t('field.active')} />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)} disabled={activeMutation.isPending}>{t('cancel')}</Button>
          <Button variant="contained" onClick={handleSave} disabled={activeMutation.isPending} data-testid="supplier-contact-save">
            {activeMutation.isPending ? <CircularProgress size={20} /> : t('save')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
