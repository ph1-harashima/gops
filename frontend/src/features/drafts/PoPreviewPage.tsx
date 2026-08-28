import { useState } from 'react'
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
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Divider from '@mui/material/Divider'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'

import { usePoPreview, useConfirmOrder, useReturnToDraft, useDemoSend } from './poPreviewApi'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { withReturnTo } from '../../shared/navigation/returnTo'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const KNOWN_ERROR_CODES = new Set([
  'NO_ORDERABLE_ITEMS',
  'MISSING_UNIT_PRICE',
  'INVALID_ORDER_STATUS',
  'INVALID_STATUS_TRANSITION',
  'DRAFT_NOT_FOUND',
])

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const code = error.response?.data?.errorCode
    return code ?? null
  }
  return null
}

export function PoPreviewPage() {
  const { t } = useTranslation(['preview', 'common'])
  const { id } = useParams<{ id: string }>()
  const draftId = Number(id)
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  // Phase 6-A: Draft forwards its own returnTo (ultimately the Candidate
  // List's Filter state) here - Preview just keeps carrying it along so it
  // survives the Preview<->Draft round trip and reaches Order Detail /
  // Supplier Response too (docs/production-ux-workflow-redesign.md 6.3章/
  // 9章). Demo Send's own post-success navigation is unchanged this Phase
  // (that redesign is Phase 6-C, out of scope here).
  const returnTo = searchParams.get('returnTo')
  const draftPath = withReturnTo(`/orders/drafts/${draftId}`, returnTo)

  const { data: preview, isLoading, isError, error, refetch } = usePoPreview(draftId)
  const confirmMutation = useConfirmOrder(draftId)
  const returnMutation = useReturnToDraft(draftId)
  const demoSendMutation = useDemoSend(draftId)
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false)
  const [demoSendDialogOpen, setDemoSendDialogOpen] = useState(false)

  function handleEditOrder() {
    if (preview?.status === 'DRAFT') {
      navigate(draftPath)
      return
    }
    // READY_TO_ORDER -> DRAFT via Return to Draft, then go edit
    // (implementation instructions 11章/12章/13章: "発注内容を修正" both
    // unlocks editing and navigates there).
    returnMutation.mutate(undefined, {
      onSuccess: () => navigate(draftPath),
    })
  }

  function handleConfirmOrder() {
    confirmMutation.mutate(undefined, {
      onSuccess: () => setConfirmDialogOpen(false),
    })
  }

  function handleDemoSend() {
    demoSendMutation.mutate(undefined, {
      onSuccess: () => {
        setDemoSendDialogOpen(false)
        // Phase 6-C (docs/production-ux-workflow-redesign.md 0章/2章):
        // "送信する" and "後日回答を登録する" are separate Business Tasks -
        // this no longer auto-opens Supplier Response. AWAITING_SUPPLIER is
        // also outside Preview's own Status scope (DRAFT/READY_TO_ORDER
        // only - implementation instructions 1章), so this screen can't just
        // stay and re-fetch either; 発注詳細 (Order Detail) is the shared
        // landing point for every Status, Send included.
        //
        // The inbound returnTo (if any) is deliberately NOT forwarded here:
        // it usually points back to the Candidate List that started this
        // Draft, and "送信済みなのに発注候補一覧へ戻る" would be an
        // unnatural business context after a completed Send (6-C 8章). The
        // returnTo chain is reset to the Order List's own AWAITING_SUPPLIER
        // bucket instead - the order's new home now that it has been sent.
        navigate(withReturnTo(`/orders/${draftId}`, '/orders/history?status=AWAITING_SUPPLIER'), {
          state: { demoSendSuccess: true },
        })
      },
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

  if (isError || !preview) {
    const code = errorCodeOf(error)
    const messageKey =
      code === 'NO_ORDERABLE_ITEMS' ? 'errorNoOrderableItems' :
      code === 'MISSING_UNIT_PRICE' ? 'errorMissingUnitPrice' :
      code === 'INVALID_ORDER_STATUS' ? 'errorInvalidOrderStatus' :
      code === 'DRAFT_NOT_FOUND' ? 'notFound' :
      'errorGeneric'
    return (
      <Box sx={{ p: 3 }}>
        <Button onClick={() => navigate(draftPath)} sx={{ mb: 2 }}>
          {t('back')}
        </Button>
        <Alert
          severity="error"
          action={code && !KNOWN_ERROR_CODES.has(code) ? (
            <Button color="inherit" size="small" onClick={() => refetch()}>
              {t('common:retry')}
            </Button>
          ) : undefined}
        >
          {t(messageKey)}
        </Alert>
      </Box>
    )
  }

  const returnErrorCode = returnMutation.isError ? errorCodeOf(returnMutation.error) : null

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={() => navigate(draftPath)}>{t('back')}</Button>
        <Typography variant="h5" component="h1">
          {t('title')} - {preview.draftNo}
        </Typography>
        <OrderStatusChip status={preview.status} />
        <Chip size="small" variant="outlined" color="info" label={t('demoModeChip')} />
      </Stack>

      {confirmMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('confirmSuccess')}
        </Alert>
      )}
      {confirmMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(() => {
            const code = errorCodeOf(confirmMutation.error)
            if (code === 'NO_ORDERABLE_ITEMS') return t('errorNoOrderableItems')
            if (code === 'MISSING_UNIT_PRICE') return t('errorMissingUnitPrice')
            if (code === 'INVALID_STATUS_TRANSITION') return t('errorInvalidStatusTransition')
            return t('errorGeneric')
          })()}
        </Alert>
      )}
      {returnErrorCode && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('returnToDraftFailed')}
        </Alert>
      )}
      {demoSendMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>
          {t('demoSendSuccess')}
        </Alert>
      )}
      {demoSendMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(() => {
            const code = errorCodeOf(demoSendMutation.error)
            if (code === 'INVALID_STATUS_TRANSITION') return t('errorInvalidStatusTransition')
            return t('demoSendFailed')
          })()}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="body2">{t('supplier')}: <strong>{preview.supplierName ?? preview.supplierCode}</strong></Typography>
          <Typography variant="body2">{t('brand')}: <strong>{preview.brandName ?? preview.brandCode}</strong></Typography>
          <Typography variant="body2">{t('orderDate')}: <strong>{preview.orderDate ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('requestedDelivery')}: <strong>{preview.requestedDelivery ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('currency')}: <strong>{preview.currency ?? '—'}</strong></Typography>
          <Typography variant="body2">
            {t('prototypePoNo')}: <strong>{preview.prototypePoNo ?? t('prototypePoNoUnassigned')}</strong>
          </Typography>
        </Stack>
        {preview.remark && (
          <Typography variant="body2" sx={{ mt: 1 }}>{t('remark')}: {preview.remark}</Typography>
        )}
      </Paper>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('table.sku')}</TableCell>
              <TableCell>{t('table.itemName')}</TableCell>
              <TableCell align="right">{t('table.orderQty')}</TableCell>
              <TableCell align="right">{t('table.unitPrice')}</TableCell>
              <TableCell align="right">{t('table.amount')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {preview.details.map((d) => (
              <TableRow key={d.lineNo} hover>
                <TableCell>{d.sku}</TableCell>
                <TableCell>{d.itemName}</TableCell>
                <TableCell align="right">{d.orderQty}</TableCell>
                <TableCell align="right">{d.unitPrice != null ? `¥${d.unitPrice.toLocaleString()}` : '—'}</TableCell>
                <TableCell align="right">¥{d.amount.toLocaleString()}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Paper variant="outlined" sx={{ p: 2, mt: 2 }}>
        <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap' }}>
          <Typography variant="subtitle1">{t('summary.skuCount')}: <strong>{preview.summary.skuCount}</strong></Typography>
          <Typography variant="subtitle1">{t('summary.totalQty')}: <strong>{preview.summary.totalQty}</strong></Typography>
          <Typography variant="subtitle1">
            {t('summary.totalAmount')}: <strong>¥{preview.summary.totalAmount.toLocaleString()}</strong>
          </Typography>
        </Stack>
      </Paper>

      <Paper variant="outlined" sx={{ p: 2, mt: 2 }}>
        <Typography variant="subtitle1" gutterBottom>{t('manufacturerCommunication.title')}</Typography>
        <Alert severity="info" sx={{ mb: 2 }}>{t('manufacturerCommunication.demoNotice')}</Alert>
        <Stack spacing={1}>
          <Typography variant="body2">{t('manufacturerCommunication.to')}: {preview.manufacturerCommunication.to}</Typography>
          <Typography variant="body2">{t('manufacturerCommunication.cc')}: {preview.manufacturerCommunication.cc}</Typography>
          <Typography variant="body2">{t('manufacturerCommunication.subject')}: {preview.manufacturerCommunication.subject}</Typography>
          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
            {t('manufacturerCommunication.body')}:{'\n'}{preview.manufacturerCommunication.body}
          </Typography>
          <Typography variant="body2">
            {t('manufacturerCommunication.attachment')}: {preview.manufacturerCommunication.attachment}
          </Typography>
        </Stack>
      </Paper>

      <Divider sx={{ my: 2 }} />

      <Stack direction="row" spacing={2}>
        {(preview.status === 'DRAFT' || preview.status === 'READY_TO_ORDER') && (
          <Button variant="outlined" onClick={handleEditOrder} disabled={returnMutation.isPending}>
            {returnMutation.isPending ? <CircularProgress size={20} /> : t('editOrder')}
          </Button>
        )}
        {preview.status === 'DRAFT' && (
          <Button
            variant="contained"
            onClick={() => setConfirmDialogOpen(true)}
            disabled={confirmMutation.isPending}
            data-testid="confirm-order-button"
          >
            {t('confirmOrder')}
          </Button>
        )}
        {preview.status === 'READY_TO_ORDER' && (
          <Button
            variant="contained"
            color="primary"
            onClick={() => setDemoSendDialogOpen(true)}
            disabled={demoSendMutation.isPending}
            data-testid="demo-send-button"
          >
            {demoSendMutation.isPending ? <CircularProgress size={20} /> : t('demoSend')}
          </Button>
        )}
        {(preview.status === 'AWAITING_SUPPLIER' || preview.status === 'SUPPLIER_CONFIRMED') && (
          <Button
            variant="contained"
            onClick={() => navigate(withReturnTo(`/orders/${draftId}/supplier-response`, returnTo))}
          >
            {t('goToSupplierResponse')}
          </Button>
        )}
      </Stack>

      <Dialog open={confirmDialogOpen} onClose={() => setConfirmDialogOpen(false)}>
        <DialogTitle>{t('confirmDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('confirmDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setConfirmDialogOpen(false)} disabled={confirmMutation.isPending}>
            {t('confirmDialogCancel')}
          </Button>
          <Button variant="contained" onClick={handleConfirmOrder} disabled={confirmMutation.isPending} data-testid="confirm-order-dialog-confirm">
            {confirmMutation.isPending ? <CircularProgress size={20} /> : t('confirmDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={demoSendDialogOpen} onClose={() => setDemoSendDialogOpen(false)}>
        <DialogTitle>{t('demoSendDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('demoSendDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDemoSendDialogOpen(false)} disabled={demoSendMutation.isPending}>
            {t('demoSendDialogCancel')}
          </Button>
          <Button variant="contained" onClick={handleDemoSend} disabled={demoSendMutation.isPending} data-testid="demo-send-dialog-confirm">
            {demoSendMutation.isPending ? <CircularProgress size={20} /> : t('demoSendDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
