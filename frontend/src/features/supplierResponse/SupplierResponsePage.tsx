import { useEffect, useMemo, useState } from 'react'
import { useLocation, useNavigate, useParams, useSearchParams } from 'react-router-dom'
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
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'
import Tooltip from '@mui/material/Tooltip'

import { useSupplierResponse, useSaveSupplierResponse, useConfirmSupplierResponse } from './api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { AttentionChips } from '../../shared/components/AttentionChips'
import { withReturnTo } from '../../shared/navigation/returnTo'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

interface LineEdit {
  confirmedQty: string // '' = null (not yet answered); otherwise a non-negative integer string
  confirmedDelivery: string
  responseNote: string
}

export function SupplierResponsePage() {
  const { t } = useTranslation(['supplierResponse', 'common', 'status'])
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const navigate = useNavigate()
  const location = useLocation()
  const demoSendSuccess = Boolean((location.state as { demoSendSuccess?: boolean } | null)?.demoSendSuccess)
  const [searchParams] = useSearchParams()
  // Phase 6-A (docs/production-ux-workflow-redesign.md 8章/9章): "戻る" now
  // goes to Order Detail (the order's central screen) instead of PO
  // Preview - a stale pre-send confirmation view is not a natural place to
  // land once a response is already being recorded. Whatever returnTo this
  // screen was given (e.g. from Order History List, via Order Detail) is
  // forwarded along so Order Detail's own "一覧へ戻る" keeps working too.
  // The post-Confirm CTA set (currently just "履歴を見る") is untouched -
  // that full redesign is Phase 6-C, not this Phase.
  const returnTo = searchParams.get('returnTo')
  const backToOrderDetailTarget = withReturnTo(`/orders/${orderId}`, returnTo)

  const { data: response, isLoading, isError, error } = useSupplierResponse(orderId)
  const saveMutation = useSaveSupplierResponse(orderId)
  const confirmMutation = useConfirmSupplierResponse(orderId)

  const [responseDate, setResponseDate] = useState('')
  const [responseNote, setResponseNote] = useState('')
  const [lines, setLines] = useState<Record<number, LineEdit>>({})
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false)

  useEffect(() => {
    if (!response) return
    setResponseDate(response.responseDate ?? '')
    setResponseNote(response.responseNote ?? '')
    setLines(Object.fromEntries(response.details.map((d) => [
      d.detailId,
      {
        confirmedQty: d.confirmedQty === null ? '' : String(d.confirmedQty),
        confirmedDelivery: d.confirmedDelivery ?? '',
        responseNote: d.responseNote ?? '',
      },
    ])))
  }, [response])

  const isEditable = response?.status === 'AWAITING_SUPPLIER'

  const allAnswered = useMemo(
    () => Object.values(lines).every((l) => l.confirmedQty !== ''),
    [lines],
  )

  function handleQtyChange(detailId: number, raw: string) {
    setLines((prev) => ({ ...prev, [detailId]: { ...prev[detailId], confirmedQty: raw } }))
  }
  function handleDeliveryChange(detailId: number, raw: string) {
    setLines((prev) => ({ ...prev, [detailId]: { ...prev[detailId], confirmedDelivery: raw } }))
  }
  function handleNoteChange(detailId: number, raw: string) {
    setLines((prev) => ({ ...prev, [detailId]: { ...prev[detailId], responseNote: raw } }))
  }

  function handleSave() {
    if (!response) return
    saveMutation.mutate({
      responseDate: responseDate || null,
      responseNote,
      details: response.details.map((d) => {
        const edit = lines[d.detailId]
        return {
          detailId: d.detailId,
          confirmedQty: edit.confirmedQty === '' ? null : Number(edit.confirmedQty),
          confirmedDelivery: edit.confirmedDelivery || null,
          responseNote: edit.responseNote,
        }
      }),
    })
  }

  function handleConfirm() {
    confirmMutation.mutate(undefined, { onSuccess: () => setConfirmDialogOpen(false) })
  }

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError || !response) {
    const code = errorCodeOf(error)
    const messageKey =
      code === 'INVALID_ORDER_STATUS' ? 'errorInvalidOrderStatus' :
      code === 'DRAFT_NOT_FOUND' ? 'notFound' :
      'errorGeneric'
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{t(messageKey)}</Alert>
      </Box>
    )
  }

  const saveErrorCode = saveMutation.isError ? errorCodeOf(saveMutation.error) : null
  const confirmErrorCode = confirmMutation.isError ? errorCodeOf(confirmMutation.error) : null

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={() => navigate(backToOrderDetailTarget)}>{t('backToOrderDetail')}</Button>
        <Typography variant="h5" component="h1">
          {t('title')} - {response.prototypePoNo ?? response.draftNo}
        </Typography>
        <OrderStatusChip status={response.status} />
        <AttentionChips attentions={response.orderAttentions} acknowledgeable />
      </Stack>

      {demoSendSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('preview:demoSendSuccess')}</Alert>
      )}
      {saveMutation.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>{t('saveSuccess')}</Alert>}
      {saveErrorCode === 'INVALID_CONFIRMED_QTY' && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('errorGeneric')}</Alert>
      )}
      {saveErrorCode === 'INVALID_STATUS_TRANSITION' && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('errorInvalidStatusTransition')}</Alert>
      )}
      {confirmMutation.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>{t('confirmSuccess')}</Alert>}
      {confirmErrorCode === 'SUPPLIER_RESPONSE_INCOMPLETE' && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('errorIncomplete')}</Alert>
      )}
      {confirmErrorCode === 'INVALID_STATUS_TRANSITION' && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('errorInvalidStatusTransition')}</Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="body2">{t('header.prototypePoNo')}: <strong>{response.prototypePoNo ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('header.supplier')}: <strong>{response.supplierName ?? response.supplierCode}</strong></Typography>
          <Typography variant="body2">{t('header.brand')}: <strong>{response.brandName ?? response.brandCode}</strong></Typography>
          <Typography variant="body2">{t('header.orderDate')}: <strong>{response.orderDate ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('header.totalOrderedQty')}: <strong>{response.totalOrderedQty}</strong></Typography>
          <Typography variant="body2">{t('header.totalAmount')}: <strong>¥{response.totalAmount.toLocaleString()}</strong></Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
          <TextField
            label={t('responseHeader.responseDate')}
            type="date"
            size="small"
            value={responseDate}
            onChange={(e) => setResponseDate(e.target.value)}
            slotProps={{ inputLabel: { shrink: true }, input: { readOnly: !isEditable } }}
          />
        </Stack>
        <TextField
          label={t('responseHeader.responseNote')}
          value={responseNote}
          onChange={(e) => setResponseNote(e.target.value)}
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
              <TableCell>{t('table.sku')}</TableCell>
              <TableCell>{t('table.itemName')}</TableCell>
              <TableCell align="right">{t('table.orderedQty')}</TableCell>
              <TableCell align="right">{t('table.confirmedQty')}</TableCell>
              <TableCell>{t('table.requestedDelivery')}</TableCell>
              <TableCell>{t('table.confirmedDelivery')}</TableCell>
              <TableCell>{t('table.responseNote')}</TableCell>
              <TableCell>{t('table.attention')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {response.details.map((d) => {
              const edit = lines[d.detailId] ?? { confirmedQty: '', confirmedDelivery: '', responseNote: '' }
              return (
                <TableRow key={d.detailId} hover data-testid={`response-row-${d.sku}`}>
                  <TableCell>{d.sku}</TableCell>
                  <TableCell>{d.itemName}</TableCell>
                  <TableCell align="right">{d.orderedQty}</TableCell>
                  <TableCell align="right">
                    <Tooltip title={d.warningCodes.map((w) => t(`status:warningCode.${w}`, { defaultValue: w })).join(', ')}
                             disableHoverListener={d.warningCodes.length === 0}>
                      <TextField
                        type="number"
                        size="small"
                        placeholder={t('unanswered') ?? undefined}
                        value={edit.confirmedQty}
                        color={d.warningCodes.length > 0 ? 'warning' : undefined}
                        onChange={(e) => handleQtyChange(d.detailId, e.target.value)}
                        data-testid={`confirmed-qty-input-${d.sku}`}
                        slotProps={{
                          htmlInput: { min: 0, step: 1, style: { textAlign: 'right', width: 90 } },
                          input: { readOnly: !isEditable },
                        }}
                      />
                    </Tooltip>
                  </TableCell>
                  <TableCell>
                    <TextField
                      type="date"
                      size="small"
                      value={edit.confirmedDelivery}
                      onChange={(e) => handleDeliveryChange(d.detailId, e.target.value)}
                      data-testid={`confirmed-delivery-input-${d.sku}`}
                      slotProps={{ inputLabel: { shrink: true }, input: { readOnly: !isEditable } }}
                    />
                  </TableCell>
                  <TableCell colSpan={1} sx={{ color: 'text.secondary', fontSize: '0.75rem' }}>
                    {d.requestedDelivery ?? '—'}
                  </TableCell>
                  <TableCell>
                    <TextField
                      size="small"
                      value={edit.responseNote}
                      onChange={(e) => handleNoteChange(d.detailId, e.target.value)}
                      slotProps={{ input: { readOnly: !isEditable } }}
                    />
                  </TableCell>
                  <TableCell>
                    <AttentionChips attentions={d.attentions} acknowledgeable />
                  </TableCell>
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </TableContainer>

      <Paper variant="outlined" sx={{ p: 2, mt: 2 }}>
        <Typography variant="subtitle1" gutterBottom>{t('summary.title')}</Typography>
        <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap' }}>
          <Typography variant="body2">{t('summary.answered')}: {response.summary.answeredCount} / {response.summary.totalCount}</Typography>
          <Typography variant="body2">{t('summary.unanswered')}: {response.summary.unansweredCount}</Typography>
          <Typography variant="body2">{t('summary.quantityChanged')}: {response.summary.quantityChangedCount}</Typography>
          <Typography variant="body2">{t('summary.deliveryChanged')}: {response.summary.deliveryChangedCount}</Typography>
          <Typography variant="body2">{t('summary.zeroQty')}: {response.summary.zeroQtyCount}</Typography>
        </Stack>
      </Paper>

      <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
        {isEditable && (
          <Button variant="contained" onClick={handleSave} disabled={saveMutation.isPending} data-testid="save-response-button">
            {saveMutation.isPending ? <CircularProgress size={20} /> : t('saveResponse')}
          </Button>
        )}
        {isEditable && (
          <Button
            variant="outlined"
            onClick={() => setConfirmDialogOpen(true)}
            disabled={!allAnswered || confirmMutation.isPending}
            data-testid="confirm-response-button"
          >
            {t('confirmResponse')}
          </Button>
        )}
        <Button variant="text" onClick={() => navigate(`/orders/${orderId}`)} data-testid="view-history-button">
          {t('viewHistory')}
        </Button>
      </Stack>

      <Dialog open={confirmDialogOpen} onClose={() => setConfirmDialogOpen(false)}>
        <DialogTitle>{t('confirmDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t('confirmDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)} disabled={confirmMutation.isPending}>
            {t('confirmDialogCancel')}
          </Button>
          <Button variant="contained" onClick={handleConfirm} disabled={confirmMutation.isPending} data-testid="confirm-response-dialog-confirm">
            {confirmMutation.isPending ? <CircularProgress size={20} /> : t('confirmDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
