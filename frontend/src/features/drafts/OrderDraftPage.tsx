import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TextField from '@mui/material/TextField'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Tooltip from '@mui/material/Tooltip'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Divider from '@mui/material/Divider'

import { useOrderDraft, useUpdateDraft } from './api'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { resolveReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

/** Mirrors OrderDraftService.warningCodes on the backend, for immediate
 * feedback while editing - the authoritative value is always whatever the
 * server returns after Save / re-GET. */
function hasSignificantDeviation(recommendedQty: number, orderQty: number): boolean {
  if (recommendedQty > 0) {
    const ratio = orderQty / recommendedQty
    return ratio < 0.5 || ratio > 1.5
  }
  return orderQty > 0
}

export function OrderDraftPage() {
  const { t } = useTranslation(['drafts', 'common', 'status'])
  const { id } = useParams<{ id: string }>()
  const draftId = Number(id)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const returnTo = searchParams.get('returnTo')
  // Phase 6-A bug fix (docs/production-ux-workflow-redesign.md 2章/13章 #1):
  // this button used to be labeled "一覧へ戻る" but actually navigated to
  // Dashboard regardless. It now genuinely returns to the Candidate List
  // that created this Draft, Filter state included, via returnTo - and
  // falls back to the unfiltered list when the Draft was opened without one
  // (e.g. a bookmarked/typed URL) rather than guessing at a business context
  // it can't know (5章 "Contextが不明なら安全なFallback").
  const backTarget = resolveReturnTo(returnTo, '/candidates')

  const { data: draft, isLoading, isError } = useOrderDraft(draftId)
  const updateMutation = useUpdateDraft(draftId)

  const [orderDate, setOrderDate] = useState('')
  const [requestedDelivery, setRequestedDelivery] = useState('')
  const [remark, setRemark] = useState('')
  const [orderQtyById, setOrderQtyById] = useState<Record<number, number>>({})

  // Reset local edit state whenever a fresh Draft is loaded (initial load,
  // or after Save triggers the re-GET per Requirements MD 13章).
  useEffect(() => {
    if (!draft) return
    setOrderDate(draft.orderDate ?? '')
    setRequestedDelivery(draft.requestedDelivery ?? '')
    setRemark(draft.remark ?? '')
    setOrderQtyById(Object.fromEntries(draft.details.map((d) => [d.id, d.orderQty])))
  }, [draft])

  const isDirty = useMemo(() => {
    if (!draft) return false
    if ((draft.orderDate ?? '') !== orderDate) return true
    if ((draft.requestedDelivery ?? '') !== requestedDelivery) return true
    if ((draft.remark ?? '') !== remark) return true
    return draft.details.some((d) => orderQtyById[d.id] !== d.orderQty)
  }, [draft, orderDate, requestedDelivery, remark, orderQtyById])

  // Unsaved Changes warning (implementation instructions 12章): tab
  // close/refresh is covered by beforeunload; in-app "Back" navigation is
  // covered by the confirm() in handleBack below (this app uses a plain
  // BrowserRouter, not a data router, so useBlocker is not available).
  useEffect(() => {
    function handler(e: BeforeUnloadEvent) {
      if (isDirty) {
        e.preventDefault()
      }
    }
    window.addEventListener('beforeunload', handler)
    return () => window.removeEventListener('beforeunload', handler)
  }, [isDirty])

  function handleBack() {
    if (isDirty && !window.confirm(t('drafts:unsavedChangesConfirm'))) {
      return
    }
    navigate(backTarget)
  }

  function handleQtyChange(detailId: number, raw: string) {
    const value = raw === '' ? 0 : Number(raw)
    if (!Number.isFinite(value)) return
    setOrderQtyById((prev) => ({ ...prev, [detailId]: value }))
  }

  const summary = useMemo(() => {
    if (!draft) return { totalQty: 0, totalAmount: 0 }
    let totalQty = 0
    let totalAmount = 0
    for (const d of draft.details) {
      const qty = orderQtyById[d.id] ?? d.orderQty
      totalQty += qty
      totalAmount += (d.unitPrice ?? 0) * qty
    }
    return { totalQty, totalAmount }
  }, [draft, orderQtyById])

  const invalidQty = useMemo(
    () => Object.values(orderQtyById).some((v) => !Number.isInteger(v) || v < 0),
    [orderQtyById],
  )

  async function handleSave() {
    if (!draft) return
    updateMutation.mutate({
      orderDate: orderDate || null,
      requestedDelivery: requestedDelivery || null,
      remark,
      details: draft.details.map((d) => ({ detailId: d.id, orderQty: orderQtyById[d.id] ?? d.orderQty })),
    })
  }

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError || !draft) {
    return (
      <Alert severity="error" sx={{ m: 3 }}>
        {t('drafts:notFound')}
      </Alert>
    )
  }

  const saveErrorCode =
    updateMutation.isError && axios.isAxiosError<ApiErrorBody>(updateMutation.error)
      ? updateMutation.error.response?.data?.errorCode
      : null

  // Implementation instructions 16章: Save Draft (PUT) is Backend-rejected
  // (409 ORDER_NOT_EDITABLE) unless Status = DRAFT - mirrored here so the
  // Frontend also read-only's the form, not just relying on the Backend
  // guard to reject an attempted Save.
  const isEditable = draft.status === 'DRAFT'

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={handleBack}>{t('drafts:backToCandidates')}</Button>
        <Typography variant="h5" component="h1">
          {t('drafts:title')} - {draft.draftNo}
        </Typography>
        <OrderStatusChip status={draft.status} />
        <DataSourceBadge dataSource={draft.dataSource} />
        {draft.prototypePoNo && (
          <Typography variant="body2" color="text.secondary">
            {t('drafts:prototypePoNo')}: {draft.prototypePoNo}
          </Typography>
        )}
      </Stack>

      {updateMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('drafts:saveSuccess')}
        </Alert>
      )}
      {saveErrorCode && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('drafts:saveFailed', { code: saveErrorCode })}
        </Alert>
      )}
      {isDirty && (
        <Alert severity="warning" sx={{ mb: 2 }}>
          {t('drafts:unsavedChangesBanner')}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <TextField
            label={t('drafts:supplier')}
            value={draft.supplierName ?? draft.supplierCode ?? ''}
            slotProps={{ input: { readOnly: true } }}
            size="small"
            sx={{ minWidth: 220 }}
          />
          <TextField
            label={t('drafts:brand')}
            value={draft.brandName ?? draft.brandCode ?? ''}
            slotProps={{ input: { readOnly: true } }}
            size="small"
            sx={{ minWidth: 200 }}
          />
          <TextField
            label={t('drafts:orderDate')}
            type="date"
            size="small"
            value={orderDate}
            onChange={(e) => setOrderDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true }, input: { readOnly: !isEditable } }}
          />
          <TextField
            label={t('drafts:requestedDelivery')}
            type="date"
            size="small"
            value={requestedDelivery}
            onChange={(e) => setRequestedDelivery(e.target.value)}
            slotProps={{ inputLabel: { shrink: true }, input: { readOnly: !isEditable } }}
          />
        </Stack>
        <TextField
          label={t('drafts:remark')}
          value={remark}
          onChange={(e) => setRemark(e.target.value)}
          multiline
          minRows={2}
          fullWidth
          size="small"
          sx={{ mt: 2 }}
          slotProps={{ input: { readOnly: !isEditable } }}
        />
      </Paper>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('drafts:table.sku')}</TableCell>
              <TableCell>{t('drafts:table.itemName')}</TableCell>
              <TableCell align="right">{t('drafts:table.currentStock')}</TableCell>
              <TableCell align="right">{t('drafts:table.leadTime')}</TableCell>
              <TableCell align="right">{t('drafts:table.recommendedQty')}</TableCell>
              <TableCell align="right">{t('drafts:table.orderQty')}</TableCell>
              <TableCell align="right">{t('drafts:table.unitPrice')}</TableCell>
              <TableCell align="right">{t('drafts:table.amount')}</TableCell>
              <TableCell>{t('drafts:table.itemStatus')}</TableCell>
              <TableCell>{t('drafts:table.warning')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {draft.details.map((d) => {
              const qty = orderQtyById[d.id] ?? d.orderQty
              const warn = hasSignificantDeviation(d.recommendedQty, qty)
              const invalid = !Number.isInteger(qty) || qty < 0
              return (
                <TableRow key={d.id} hover data-testid={`draft-row-${d.sku}`}>
                  <TableCell>{d.sku}</TableCell>
                  <TableCell>{d.itemName}</TableCell>
                  <TableCell align="right">{d.currentStock ?? t('drafts:notAvailable')}</TableCell>
                  <TableCell align="right">{d.leadTime ?? t('drafts:notAvailable')}</TableCell>
                  <TableCell align="right">{d.recommendedQty}</TableCell>
                  <TableCell align="right">
                    <TextField
                      type="number"
                      size="small"
                      value={qty}
                      onChange={(e) => handleQtyChange(d.id, e.target.value)}
                      error={invalid}
                      data-testid={`order-qty-input-${d.sku}`}
                      slotProps={{
                        htmlInput: { min: 0, step: 1, style: { textAlign: 'right', width: 80 } },
                        input: { readOnly: !isEditable },
                      }}
                    />
                  </TableCell>
                  <TableCell align="right">{d.unitPrice != null ? `¥${d.unitPrice.toLocaleString()}` : t('drafts:notAvailable')}</TableCell>
                  <TableCell align="right">¥{((d.unitPrice ?? 0) * qty).toLocaleString()}</TableCell>
                  <TableCell>
                    <ItemStatusChip status={d.itemStatus} />
                  </TableCell>
                  <TableCell>
                    {warn && (
                      <Tooltip title={t('drafts:warningOrderQtyDiffers')}>
                        <Chip size="small" color="warning" label={t('drafts:warning')} />
                      </Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <Paper variant="outlined" sx={{ p: 2, mt: 2 }}>
        <Stack direction="row" spacing={4} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
          <Typography variant="subtitle1">
            {t('drafts:summary.totalQty')}: <strong>{summary.totalQty}</strong>
          </Typography>
          <Typography variant="subtitle1">
            {t('drafts:summary.totalAmount')}: <strong>¥{summary.totalAmount.toLocaleString()}</strong>
          </Typography>
          <Divider orientation="vertical" flexItem />
          {isEditable && (
            <Button
              variant="contained"
              onClick={handleSave}
              disabled={!isDirty || invalidQty || updateMutation.isPending}
              data-testid="save-draft-button"
            >
              {updateMutation.isPending ? <CircularProgress size={20} /> : t('drafts:saveDraft')}
            </Button>
          )}
          <Button
            variant="outlined"
            onClick={() => navigate(withReturnTo(`/orders/drafts/${draftId}/preview`, returnTo))}
            data-testid="go-to-preview-button"
          >
            {t('drafts:preview')}
          </Button>
        </Stack>
      </Paper>
    </Box>
  )
}
