import { useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Divider from '@mui/material/Divider'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogActions from '@mui/material/DialogActions'

import {
  usePriceChangeDetail,
  usePriceChangeCandidates,
  usePriceChangeItemGroups,
  useAddPriceChangeDetail,
  useAddPriceChangeItemGroup,
  useUpdateProposedPrice,
  useRemovePriceChangeDetail,
  useUpdatePriceChangeNote,
} from './api'
import { PriceChangeLineTable } from './PriceChangeLineTable'
import { Toast } from '../../shared/components/Toast'
import { PRICE_CHANGE_STATUS_DRAFT } from '../../shared/types/priceChange'

/**
 * Price Change Create/Edit (target-price-change-workflow.md 15章) - folds
 * Product Selection + Price Impact Review into this single screen as
 * in-page sections rather than separate screens (Order Draft's own
 * precedent). Only reachable for a DRAFT Change Set - a non-DRAFT id
 * redirects to the read-only Detail screen.
 */
export function PriceChangeEditPage() {
  const { t } = useTranslation(['priceChanges', 'common'])
  const navigate = useNavigate()
  const params = useParams<{ id: string }>()
  const id = Number(params.id)

  const { data, isLoading, isError, refetch } = usePriceChangeDetail(id)
  const { data: itemGroups } = usePriceChangeItemGroups()

  const [brandCode, setBrandCode] = useState('')
  const [itemGrpCd, setItemGrpCd] = useState('')
  const [keyword, setKeyword] = useState('')
  const [searchArmed, setSearchArmed] = useState(false)
  const searchParams = useMemo(
    () => ({ brandCode: brandCode || undefined, itemGrpCd: itemGrpCd || undefined, keyword: keyword || undefined }),
    [brandCode, itemGrpCd, keyword],
  )
  const { data: candidates, isFetching: candidatesLoading } = usePriceChangeCandidates(searchParams, searchArmed)

  const [noteDraft, setNoteDraft] = useState<string | null>(null)
  const [removeTarget, setRemoveTarget] = useState<{ detailId: number; itemCd: string } | null>(null)
  const [toast, setToast] = useState<{ severity: 'success' | 'error'; message: string } | null>(null)

  const addDetailMutation = useAddPriceChangeDetail(id)
  const addItemGroupMutation = useAddPriceChangeItemGroup(id)
  const updatePriceMutation = useUpdateProposedPrice(id)
  const removeMutation = useRemovePriceChangeDetail(id)
  const updateNoteMutation = useUpdatePriceChangeNote(id)

  const existingSkus = useMemo(() => new Set((data?.details ?? []).map((d) => d.itemCd)), [data])

  function handleAdd(itemCd: string) {
    addDetailMutation.mutate(itemCd, {
      onSuccess: () => setToast({ severity: 'success', message: t('priceChanges:addSuccess') }),
      onError: () => setToast({ severity: 'error', message: t('priceChanges:saveFailed') }),
    })
  }

  function handleAddGroup() {
    if (!itemGrpCd) return
    addItemGroupMutation.mutate(itemGrpCd, {
      onSuccess: () => setToast({ severity: 'success', message: t('priceChanges:addSuccess') }),
      onError: () => setToast({ severity: 'error', message: t('priceChanges:saveFailed') }),
    })
  }

  function handleProposedPriceChange(detailId: number, value: number | null) {
    updatePriceMutation.mutate(
      { detailId, proposedPrcSellWTax: value },
      {
        onSuccess: () => setToast({ severity: 'success', message: t('priceChanges:saveSuccess') }),
        onError: () => setToast({ severity: 'error', message: t('priceChanges:saveFailed') }),
      },
    )
  }

  function confirmRemove() {
    if (!removeTarget) return
    removeMutation.mutate(removeTarget.detailId, {
      onSuccess: () => setToast({ severity: 'success', message: t('priceChanges:removeSuccess') }),
      onError: () => setToast({ severity: 'error', message: t('priceChanges:saveFailed') }),
    })
    setRemoveTarget(null)
  }

  function saveNote() {
    if (noteDraft === null) return
    updateNoteMutation.mutate(noteDraft, {
      onSuccess: () => {
        setToast({ severity: 'success', message: t('priceChanges:saveSuccess') })
        setNoteDraft(null)
      },
      onError: () => setToast({ severity: 'error', message: t('priceChanges:saveFailed') }),
    })
  }

  if (isLoading) {
    return (
      <Box sx={{ p: 3 }}>
        <CircularProgress size={20} />
      </Box>
    )
  }

  if (isError || !data) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" action={<Button onClick={() => refetch()}>{t('common:retry')}</Button>}>
          {t('common:errorGeneric')}
        </Alert>
      </Box>
    )
  }

  if (data.status !== PRICE_CHANGE_STATUS_DRAFT) {
    navigate(`/price-changes/${id}`, { replace: true })
    return null
  }

  return (
    <Box sx={{ p: 3, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" component="h1">
          {t('priceChanges:editTitle')} #{data.id}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        <Button size="small" onClick={() => navigate('/price-changes')} data-testid="back-to-price-change-list">
          {t('priceChanges:backToList')}
        </Button>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
          <TextField
            label={t('priceChanges:noteLabel')}
            size="small"
            fullWidth
            value={noteDraft ?? data.note ?? ''}
            onChange={(e) => setNoteDraft(e.target.value)}
            data-testid="price-change-note-input"
          />
          <Button
            size="small"
            variant="outlined"
            onClick={saveNote}
            disabled={noteDraft === null || updateNoteMutation.isPending}
            data-testid="save-note-button"
          >
            {t('priceChanges:noteSave')}
          </Button>
        </Stack>
      </Paper>

      <Typography variant="h6" gutterBottom>
        {t('priceChanges:productSelection.title')}
      </Typography>
      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', gap: 2, alignItems: 'center', mb: 2 }}>
          <TextField
            select
            size="small"
            label={t('priceChanges:productSelection.itemGroup')}
            sx={{ minWidth: 200 }}
            value={itemGrpCd}
            onChange={(e) => setItemGrpCd(e.target.value)}
            data-testid="item-group-select"
          >
            <MenuItem value="">{t('priceChanges:productSelection.allItemGroups')}</MenuItem>
            {(itemGroups ?? []).map((g) => (
              <MenuItem key={g} value={g}>
                {g}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            size="small"
            label={t('priceChanges:productSelection.brand')}
            sx={{ minWidth: 160 }}
            value={brandCode}
            onChange={(e) => setBrandCode(e.target.value)}
          />
          <TextField
            size="small"
            label={t('priceChanges:productSelection.keyword')}
            sx={{ minWidth: 220 }}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && setSearchArmed(true)}
            data-testid="candidate-keyword-input"
          />
          <Button variant="outlined" size="small" onClick={() => setSearchArmed(true)} data-testid="search-candidates-button">
            {t('priceChanges:productSelection.search')}
          </Button>
          <Button
            variant="outlined"
            size="small"
            disabled={!itemGrpCd || addItemGroupMutation.isPending}
            onClick={handleAddGroup}
            data-testid="add-item-group-button"
          >
            {t('priceChanges:productSelection.addGroupButton')}
          </Button>
        </Stack>

        {candidatesLoading && <CircularProgress size={20} />}

        {!candidatesLoading && searchArmed && candidates && (
          <>
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              {t('priceChanges:productSelection.resultCount', { count: candidates.length })}
            </Typography>
            <TableContainer sx={{ maxHeight: 300 }} data-testid="candidate-search-results">
              <Table size="small" stickyHeader>
                <TableHead>
                  <TableRow>
                    <TableCell>{t('priceChanges:lineTable.sku')}</TableCell>
                    <TableCell>{t('priceChanges:lineTable.itemName')}</TableCell>
                    <TableCell>{t('priceChanges:lineTable.brand')}</TableCell>
                    <TableCell>{t('priceChanges:lineTable.itemGroup')}</TableCell>
                    <TableCell align="right">{t('priceChanges:lineTable.currentPrice')}</TableCell>
                    <TableCell align="center"> </TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {candidates.map((c) => (
                    <TableRow key={c.itemCd} hover data-testid={`candidate-search-row-${c.itemCd}`}>
                      <TableCell>{c.itemCd}</TableCell>
                      <TableCell>{c.itemName ?? t('priceChanges:notAvailable')}</TableCell>
                      <TableCell>{c.brandName ?? c.brandCode ?? t('priceChanges:notAvailable')}</TableCell>
                      <TableCell>{c.itemGrpCd ?? t('priceChanges:notAvailable')}</TableCell>
                      <TableCell align="right">
                        {c.prcSellWTax != null ? `¥${c.prcSellWTax.toLocaleString()}` : t('priceChanges:notAvailable')}
                      </TableCell>
                      <TableCell align="center">
                        {existingSkus.has(c.itemCd) ? (
                          <Typography variant="caption" color="text.secondary">
                            {t('priceChanges:productSelection.alreadyAdded')}
                          </Typography>
                        ) : (
                          <Button size="small" onClick={() => handleAdd(c.itemCd)} data-testid={`add-candidate-${c.itemCd}`}>
                            {t('priceChanges:productSelection.addButton')}
                          </Button>
                        )}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
        {!searchArmed && (
          <Typography variant="body2" color="text.secondary">
            {t('priceChanges:productSelection.searchPrompt')}
          </Typography>
        )}
      </Paper>

      <Divider sx={{ my: 3 }} />

      <Typography variant="h6" gutterBottom>
        {t('priceChanges:detailsCount', { count: data.details.length })}
      </Typography>
      {data.details.length === 0 ? (
        <Alert severity="info">{t('priceChanges:detailsEmpty')}</Alert>
      ) : (
        <PriceChangeLineTable
          lines={data.details}
          editable
          onProposedPriceChange={handleProposedPriceChange}
          onRemove={(detailId) => {
            const line = data.details.find((d) => d.detailId === detailId)
            if (line) setRemoveTarget({ detailId, itemCd: line.itemCd })
          }}
        />
      )}

      <Toast open={toast != null} severity={toast?.severity ?? 'success'} message={toast?.message ?? ''} onClose={() => setToast(null)} />

      <Dialog open={removeTarget != null} onClose={() => setRemoveTarget(null)}>
        <DialogTitle>{t('priceChanges:confirmRemoveTitle')}</DialogTitle>
        <DialogContent>{t('priceChanges:confirmRemoveMessage')}</DialogContent>
        <DialogActions>
          <Button onClick={() => setRemoveTarget(null)}>{t('priceChanges:cancel')}</Button>
          <Button color="error" onClick={confirmRemove} data-testid="confirm-remove-detail-dialog">
            {t('priceChanges:lineTable.remove')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
