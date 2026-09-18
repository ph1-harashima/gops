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

import { usePoPreview, useReturnToDraft, useDemoSend, useEdiSend } from './poPreviewApi'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { Toast } from '../../shared/components/Toast'
import { resolveBackTo, withReturnTo } from '../../shared/navigation/returnTo'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN } from '../../shared/types/auth'
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
  // editPath: "発注内容を修正" always means "go edit the Draft", regardless
  // of which screen linked to this Preview - unaffected by backTo below.
  const editPath = withReturnTo(`/orders/drafts/${draftId}`, returnTo)
  // Phase 7-H (PO Preview Navigation audit): 戻る previously always reused
  // editPath (the Draft screen), even when Order Detail was the actual
  // caller (発注一覧 -> 発注詳細 -> PO Preview -> "戻る" landed on the Draft,
  // not back on 発注詳細 - confirmed Root Cause via Source, not assumption).
  // backTo (shared/navigation/returnTo.ts) now carries whichever screen
  // actually linked here; a missing backTo (a stale/bookmarked Preview URL
  // from before this fix) falls back to the old Draft-path behavior.
  const backTo = resolveBackTo(searchParams.get('backTo'), `/orders/drafts/${draftId}`)
  const backPath = withReturnTo(backTo, returnTo)
  const backLabel = backTo.startsWith('/orders/drafts/') ? t('backToDraft') : t('backToOrderDetail')

  const { user } = useAuth()
  const isAdmin = user?.role === ROLE_ADMIN

  const { data: preview, isLoading, isError, error, refetch } = usePoPreview(draftId)
  const returnMutation = useReturnToDraft(draftId)
  const demoSendMutation = useDemoSend(draftId)
  const ediSendMutation = useEdiSend(draftId)
  const [demoSendDialogOpen, setDemoSendDialogOpen] = useState(false)
  const [ediSendDialogOpen, setEdiSendDialogOpen] = useState(false)

  // Phase 7-C1 16章: the old DRAFT->READY_TO_ORDER "確定" transition no
  // longer exists - "発注内容を修正" is now purely a navigation aid, not a
  // Workflow Status change, for DRAFT and PENDING_APPROVAL (both are
  // editable in place server-side, per role - OrderDraftPersistenceService's
  // ownership/role matrix is the actual gate; this screen just routes
  // there). Only APPROVED still needs an explicit un-approve first
  // (return-to-draft, ADMIN-only) before the Draft screen becomes editable.
  function handleEditOrder() {
    if (preview?.status === 'APPROVED') {
      returnMutation.mutate(undefined, {
        onSuccess: () => navigate(editPath),
      })
      return
    }
    navigate(editPath)
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

  /** Phase 7-H (EDI発注Workflow Foundation): same shape as handleDemoSend -
   * a distinct Business Action (recordEdiSend, not demoSend), landing on the
   * same 発注詳細 as every other Send, with its own one-shot Message
   * (ediSendSuccess, not demoSendSuccess) so 発注詳細 can say which Channel
   * was actually used. */
  function handleEdiSend() {
    ediSendMutation.mutate(undefined, {
      onSuccess: () => {
        setEdiSendDialogOpen(false)
        navigate(withReturnTo(`/orders/${draftId}`, '/orders/history?status=AWAITING_SUPPLIER'), {
          state: { ediSendSuccess: true },
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
        <Button onClick={() => navigate(backPath)} sx={{ mb: 2 }}>
          {backLabel}
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
        <Button onClick={() => navigate(backPath)}>{backLabel}</Button>
        <Typography variant="h5" component="h1">
          {t('title')} - {preview.draftNo}
        </Typography>
        <OrderStatusChip status={preview.status} />
        <Chip size="small" variant="outlined" color="info" label={t('demoModeChip')} />
        {/* Phase 7-H (EDI発注Workflow Foundation): shown only once a Send has
            actually happened (communicationChannel is null before that) -
            makes "どのChannelで発注したか" visible without touching
            OrderStatusChip's own Status semantics. */}
        {preview.communicationChannel && (
          <Chip size="small" variant="outlined" label={t(`communicationChannel.${preview.communicationChannel}`)} data-testid="communication-channel-chip" />
        )}
      </Stack>

      {/* Phase 7-I (Layout Shift audit): both are one-shot mutation-result
          errors, previously inline <Alert>s mounted directly above the
          summary Paper/Table below - moved to the shared Toast so they
          never push that content (fixed-position Snackbar). */}
      <Toast
        open={Boolean(returnErrorCode)}
        severity="error"
        message={t('returnToDraftFailed')}
        onClose={() => returnMutation.reset()}
      />
      {/* Phase 6-E (docs/production-ux-workflow-redesign.md 8章): a
          demoSendMutation.isSuccess Alert used to render here, but
          handleDemoSend's onSuccess always navigate()s away in the same
          synchronous callback (Phase 6-C) - this component unmounts before
          isSuccess can ever be observed true on a render. Confirmed via
          source (not guessed) and removed; the equivalent, actually-visible
          Message now lives on 発注詳細 (OrderHistoryDetailPage's
          demoSendSuccessMessage). demoSendMutation.isError below is
          unaffected - only onSuccess navigates, so the Error Toast still
          renders normally when Demo Send fails. */}
      <Toast
        open={demoSendMutation.isError}
        severity="error"
        message={
          errorCodeOf(demoSendMutation.error) === 'INVALID_STATUS_TRANSITION'
            ? t('errorInvalidStatusTransition')
            : t('demoSendFailed')
        }
        onClose={() => demoSendMutation.reset()}
      />
      <Toast
        open={ediSendMutation.isError}
        severity="error"
        message={
          errorCodeOf(ediSendMutation.error) === 'INVALID_STATUS_TRANSITION'
            ? t('errorInvalidStatusTransition')
            : t('ediSendFailed')
        }
        onClose={() => ediSendMutation.reset()}
      />

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
        {/* Phase 7-C1 16章: DRAFT/PENDING_APPROVAL are directly editable via
            the Draft screen without any Status change (server-side
            ownership/role matrix is the real gate); APPROVED still needs an
            explicit un-approve first, which is ADMIN-only - hidden for
            OPERATOR rather than shown-then-403'd, since return-to-draft is
            an irreversible-feeling "undo an ADMIN decision" action, not a
            routine edit request (17章's Backend enforcement still applies
            regardless: ReturnToDraftController is @PreAuthorize-guarded). */}
        {(preview.status === 'DRAFT' || preview.status === 'PENDING_APPROVAL' || (preview.status === 'APPROVED' && isAdmin)) && (
          <Button variant="outlined" onClick={handleEditOrder} disabled={returnMutation.isPending} data-testid="edit-order-button">
            {returnMutation.isPending ? <CircularProgress size={20} /> : t('editOrder')}
          </Button>
        )}
        {/* §6/§7 Channel-aware audit fix: gated on resolvedManufacturerChannel
            (Manufacturer Channel Master's "what SHOULD happen") so an
            EDI-resolved manufacturer never sees the Email action here.
            Unresolved (no Master row yet, null) still shows BOTH actions -
            preserves the pre-Phase-9-D fallback (edi-workflow-foundation.spec.ts
            Scenario G/H's own explicit "both visible" assumption for an
            unconfigured Supplier) rather than guessing which one applies. */}
        {preview.status === 'APPROVED' && preview.resolvedManufacturerChannel !== 'EDI' && (
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
        {preview.status === 'APPROVED' && preview.resolvedManufacturerChannel !== 'EMAIL' && (
          <Button
            variant="outlined"
            onClick={() => setEdiSendDialogOpen(true)}
            disabled={ediSendMutation.isPending}
            data-testid="edi-send-button"
          >
            {ediSendMutation.isPending ? <CircularProgress size={20} /> : t('ediSend')}
          </Button>
        )}
        {/* Phase 6-E (docs/production-ux-workflow-redesign.md 6章): a stale
            Preview URL (reached via Browser Back/Forward or a bookmark from
            before Send) can still be viewed after the order has moved past
            READY_TO_ORDER - this is a fallback recovery path, not the
            primary flow (発注詳細 owns that now, Phase 6-B/6-C). Its Label
            now matches 発注詳細's own AWAITING_SUPPLIER/SUPPLIER_CONFIRMED
            wording (入力 vs 確認) instead of a single generic "確認する" for
            both, which had drifted out of sync with that split (Label
            Consistency audit). */}
        {preview.status === 'AWAITING_SUPPLIER' && (
          <Button
            variant="contained"
            onClick={() => navigate(withReturnTo(`/orders/${draftId}/supplier-response`, returnTo))}
          >
            {t('goToSupplierResponseInput')}
          </Button>
        )}
        {preview.status === 'SUPPLIER_CONFIRMED' && (
          <Button
            variant="contained"
            onClick={() => navigate(withReturnTo(`/orders/${draftId}/supplier-response`, returnTo))}
          >
            {t('goToSupplierResponseReview')}
          </Button>
        )}
      </Stack>

      {/* Phase 7-E Section 2 audit: same backdropClick/escapeKeyDown hardening
          as the Supplier Response Confirm Dialog fix - Demo Send is an
          equivalent "confirm a Workflow Status transition" Dialog. */}
      <Dialog
        open={demoSendDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setDemoSendDialogOpen(false)
        }}
      >
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

      <Dialog
        open={ediSendDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setEdiSendDialogOpen(false)
        }}
      >
        <DialogTitle>{t('ediSendDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('ediSendDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEdiSendDialogOpen(false)} disabled={ediSendMutation.isPending}>
            {t('ediSendDialogCancel')}
          </Button>
          <Button variant="contained" onClick={handleEdiSend} disabled={ediSendMutation.isPending} data-testid="edi-send-dialog-confirm">
            {ediSendMutation.isPending ? <CircularProgress size={20} /> : t('ediSendDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
