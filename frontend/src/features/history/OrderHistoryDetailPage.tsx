import { useState } from 'react'
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
import Divider from '@mui/material/Divider'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'

import { useOrderHistoryDetail, useOrderEvents } from './api'
import { useApprove, useReturnForCorrection } from '../drafts/poPreviewApi'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { AttentionChips } from '../../shared/components/AttentionChips'
import { resolveReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN } from '../../shared/types/auth'
import type { ApiErrorBody } from '../../shared/types/orderDraft'
import type { TFunction } from 'i18next'

/** Audit Timeline i18n audit: oldValue/newValue are raw strings on the wire
 * for every event type (implementation instructions - internal Code exposure
 * audit). Only two (fieldName, value) shapes are actually Codes that need
 * translating - "status" (Workflow Status: DRAFT/READY_TO_ORDER/...) and
 * "attentionType" (QUANTITY_CHANGED/DELIVERY_CHANGED/PARTIAL_CONFIRMATION/...)
 * - both verified against the actual AuditEvent construction sites in
 * OrderStatusTransitionService/SupplierResponseService/AttentionService.
 * Every other fieldName (orderQty/confirmedQty/orderDate/remark/
 * confirmedDelivery/prototypePoNo/draftNo/...) is a genuine data value
 * (a number, a date, free text, an identifier) and must never be routed
 * through a Code i18n table - it is rendered as-is. */
function resolveTimelineValue(t: TFunction, fieldName: string | null, value: string | null): string | null {
  if (value === null) return null
  if (fieldName === 'status') return t(`status:orderStatus.${value}`, { defaultValue: value })
  if (fieldName === 'attentionType') return t(`status:attentionType.${value}`, { defaultValue: value })
  return value
}

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

/** READ ONLY (Requirements MD 31.2) - no Save/Edit control anywhere on this
 * screen. Edits go through the Draft / Supplier Response screens only,
 * reached via the Link buttons below. */
export function OrderHistoryDetailPage() {
  const { t } = useTranslation(['history', 'common', 'status'])
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const navigate = useNavigate()
  const location = useLocation()
  // Phase 6-C (docs/production-ux-workflow-redesign.md 2章/5章): 発注詳細 is
  // the shared landing point after both Demo Send and Supplier Response
  // Confirm now (neither auto-opens the next screen anymore), so it shows
  // whichever one-shot success Message the previous screen handed off via
  // Router state - read once per navigation, not persisted, so a manual
  // reload of this URL correctly shows neither.
  const demoSendSuccess = Boolean((location.state as { demoSendSuccess?: boolean } | null)?.demoSendSuccess)
  const supplierResponseConfirmSuccess = Boolean(
    (location.state as { supplierResponseConfirmSuccess?: boolean } | null)?.supplierResponseConfirmSuccess,
  )
  const [searchParams] = useSearchParams()
  // Phase 6-A (docs/production-ux-workflow-redesign.md 6.3章): restore the
  // originating List's own Filter state on "戻る", falling back to the
  // unfiltered list when opened without a returnTo (bookmark, direct link
  // from Supplier Response's 履歴を見る, etc). Forwarded onward to Preview /
  // Supplier Response so the chain survives further hops too.
  const returnTo = searchParams.get('returnTo')
  const backTarget = resolveReturnTo(returnTo, '/orders/history')

  const { data: detail, isLoading, isError } = useOrderHistoryDetail(orderId)
  const { data: events, isLoading: eventsLoading } = useOrderEvents(orderId)

  const { user } = useAuth()
  const isAdmin = user?.role === ROLE_ADMIN
  const approveMutation = useApprove(orderId)
  const returnMutation = useReturnForCorrection(orderId)
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [returnDialogOpen, setReturnDialogOpen] = useState(false)
  const [returnReason, setReturnReason] = useState('')

  function handleApprove() {
    approveMutation.mutate(undefined, { onSuccess: () => setApproveDialogOpen(false) })
  }

  function handleReturnForCorrection() {
    returnMutation.mutate(returnReason, {
      onSuccess: () => {
        setReturnDialogOpen(false)
        setReturnReason('')
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

  if (isError || !detail) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{t('notFound')}</Alert>
      </Box>
    )
  }

  // Phase 6-B (docs/production-ux-workflow-redesign.md 4章/5章): 発注詳細 is
  // this order's business Hub - exactly one primary Action, chosen by its
  // current Status, reusing the existing Draft/Preview/Supplier Response
  // routes as-is (no new Business Logic, no new Route). SENT is never a
  // resting Status (OrderStatusTransitionService - Demo Send never leaves an
  // Order sitting in SENT), so it deliberately has no case here.
  // Phase 7-C1 15章/16章: PENDING_APPROVAL is deliberately NOT handled here
  // (it has role-dependent, multi-Action UI - ADMIN sees 承認/修正/差し戻し,
  // OPERATOR sees a read-only indicator - rendered separately below).
  // APPROVED replaces the old READY_TO_ORDER case (V8 migrated every
  // existing Order to APPROVED; no Order can hold READY_TO_ORDER anymore).
  const primaryAction = (() => {
    switch (detail.status) {
      case 'DRAFT':
        return { label: t('goToDraftEdit'), to: `/orders/drafts/${detail.id}` }
      case 'APPROVED':
        return { label: t('goToPreview'), to: `/orders/drafts/${detail.id}/preview` }
      case 'AWAITING_SUPPLIER':
        // 入力 (input) - a Response is still owed.
        return { label: t('goToSupplierResponseInput'), to: `/orders/${detail.id}/supplier-response` }
      case 'SUPPLIER_CONFIRMED':
        // 確認 (review) - same screen, read-only once Confirmed
        // (SupplierResponsePage.isEditable already gates on Status).
        return { label: t('goToSupplierResponseReview'), to: `/orders/${detail.id}/supplier-response` }
      default:
        return null
    }
  })()

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={() => navigate(backTarget)}>{t('backToList')}</Button>
        <Typography variant="h5" component="h1">
          {t('detailTitle')} - {detail.prototypePoNo ?? detail.draftNo}
        </Typography>
        <OrderStatusChip status={detail.status} />
        <AttentionChips attentions={detail.orderAttentions} acknowledgeable />
      </Stack>

      {demoSendSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('demoSendSuccessMessage')}</Alert>
      )}
      {supplierResponseConfirmSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('supplierResponseConfirmSuccessMessage')}</Alert>
      )}
      {approveMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('approveSuccess')}</Alert>
      )}
      {approveMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(() => {
            const code = errorCodeOf(approveMutation.error)
            if (code === 'FORBIDDEN') return t('errorForbidden')
            return t('errorGeneric')
          })()}
        </Alert>
      )}
      {returnMutation.isSuccess && (
        <Alert severity="success" sx={{ mb: 2 }}>{t('returnSuccess')}</Alert>
      )}
      {returnMutation.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {(() => {
            const code = errorCodeOf(returnMutation.error)
            if (code === 'RETURN_REASON_REQUIRED') return t('errorReturnReasonRequired')
            if (code === 'FORBIDDEN') return t('errorForbidden')
            return t('errorGeneric')
          })()}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="body2">{t('detail.supplier')}: <strong>{detail.supplierName ?? detail.supplierCode}</strong></Typography>
          <Typography variant="body2">{t('detail.brand')}: <strong>{detail.brandName ?? detail.brandCode}</strong></Typography>
          <Typography variant="body2">{t('detail.orderDate')}: <strong>{detail.orderDate ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('detail.requestedDelivery')}: <strong>{detail.requestedDelivery ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('detail.currency')}: <strong>{detail.currency ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('detail.totalQty')}: <strong>{detail.totalQty}</strong></Typography>
          <Typography variant="body2">{t('detail.totalAmount')}: <strong>¥{detail.totalAmount.toLocaleString()}</strong></Typography>
        </Stack>
        {detail.remark && <Typography variant="body2" sx={{ mt: 1 }}>{t('detail.remark')}: {detail.remark}</Typography>}
      </Paper>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('detailTable.sku')}</TableCell>
              <TableCell>{t('detailTable.itemName')}</TableCell>
              <TableCell align="right">{t('detailTable.recommendedQty')}</TableCell>
              <TableCell align="right">{t('detailTable.orderedQty')}</TableCell>
              <TableCell align="right">{t('detailTable.confirmedQty')}</TableCell>
              <TableCell>{t('detailTable.requestedDelivery')}</TableCell>
              <TableCell>{t('detailTable.confirmedDelivery')}</TableCell>
              <TableCell>{t('detailTable.attention')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {detail.details.map((line) => (
              <TableRow key={line.sku} hover>
                <TableCell>{line.sku}</TableCell>
                <TableCell>{line.itemName}</TableCell>
                <TableCell align="right">{line.recommendedQty}</TableCell>
                <TableCell align="right">{line.orderedQty}</TableCell>
                <TableCell align="right">{line.confirmedQty ?? t('notAvailable')}</TableCell>
                <TableCell>{line.requestedDelivery ?? t('notAvailable')}</TableCell>
                <TableCell>{line.confirmedDelivery ?? t('notAvailable')}</TableCell>
                <TableCell><AttentionChips attentions={line.attentions} acknowledgeable /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      {primaryAction && (
        <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
          <Button
            variant="outlined"
            onClick={() => navigate(withReturnTo(primaryAction.to, returnTo))}
            data-testid="order-detail-primary-action"
          >
            {primaryAction.label}
          </Button>
        </Stack>
      )}

      {/* Phase 7-C1 15章: PENDING_APPROVAL's Action set depends on Role -
          ADMIN gets 修正/承認/差し戻し, OPERATOR gets a read-only indicator.
          The Backend enforces this regardless (approve/return-for-correction
          are @PreAuthorize("hasRole('ADMIN')")) - this is UX convenience,
          not the access control (17章). */}
      {detail.status === 'PENDING_APPROVAL' && isAdmin && (
        <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
          <Button
            variant="outlined"
            onClick={() => navigate(withReturnTo(`/orders/drafts/${detail.id}`, returnTo))}
            data-testid="order-detail-edit-button"
          >
            {t('editDraft')}
          </Button>
          <Button
            variant="contained"
            color="primary"
            onClick={() => setApproveDialogOpen(true)}
            disabled={approveMutation.isPending}
            data-testid="order-detail-approve-button"
          >
            {approveMutation.isPending ? <CircularProgress size={20} /> : t('approve')}
          </Button>
          <Button
            variant="outlined"
            color="error"
            onClick={() => setReturnDialogOpen(true)}
            disabled={returnMutation.isPending}
            data-testid="order-detail-return-button"
          >
            {returnMutation.isPending ? <CircularProgress size={20} /> : t('returnForCorrection')}
          </Button>
        </Stack>
      )}
      {detail.status === 'PENDING_APPROVAL' && !isAdmin && (
        <Alert severity="info" sx={{ mt: 2 }} data-testid="pending-approval-indicator">
          {t('pendingApprovalIndicator')}
        </Alert>
      )}

      <Divider sx={{ my: 3 }} />

      <Typography variant="h6" gutterBottom>{t('timelineTitle')}</Typography>
      {eventsLoading && <CircularProgress size={20} />}
      {!eventsLoading && (!events || events.length === 0) && (
        <Alert severity="info">{t('timelineEmpty')}</Alert>
      )}
      {!eventsLoading && events && events.length > 0 && (
        <Stack spacing={1}>
          {events.map((e, i) => (
            <Paper key={i} variant="outlined" sx={{ p: 1.5 }}>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ fontWeight: 'bold', minWidth: 200 }}>
                  {t(`status:eventType.${e.eventType}`, { defaultValue: e.eventType })}
                </Typography>
                {(e.oldValue !== null || e.newValue !== null) && (
                  <Typography variant="body2" color="text.secondary">
                    {t('timelineFieldOldNew', {
                      old: resolveTimelineValue(t, e.fieldName, e.oldValue) ?? t('notAvailable'),
                      new: resolveTimelineValue(t, e.fieldName, e.newValue) ?? t('notAvailable'),
                    })}
                  </Typography>
                )}
                <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                  {e.performedByDisplayName ?? e.performedBy} - {new Date(e.performedAt).toLocaleString('ja-JP')}
                </Typography>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}

      <Dialog open={approveDialogOpen} onClose={() => setApproveDialogOpen(false)}>
        <DialogTitle>{t('approveDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('approveDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setApproveDialogOpen(false)} disabled={approveMutation.isPending}>
            {t('approveDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleApprove}
            disabled={approveMutation.isPending}
            data-testid="approve-dialog-confirm"
          >
            {approveMutation.isPending ? <CircularProgress size={20} /> : t('approveDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={returnDialogOpen} onClose={() => setReturnDialogOpen(false)}>
        <DialogTitle>{t('returnDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>{t('returnDialogBody')}</DialogContentText>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label={t('returnReasonInputLabel')}
            value={returnReason}
            onChange={(e) => setReturnReason(e.target.value)}
            data-testid="return-reason-input"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReturnDialogOpen(false)} disabled={returnMutation.isPending}>
            {t('returnDialogCancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReturnForCorrection}
            disabled={returnMutation.isPending || returnReason.trim() === ''}
            data-testid="return-dialog-confirm"
          >
            {returnMutation.isPending ? <CircularProgress size={20} /> : t('returnDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
