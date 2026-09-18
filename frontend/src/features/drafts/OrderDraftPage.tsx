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
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'

import { useOrderDraft, useUpdateDraft } from './api'
import { useSubmitForApproval } from './poPreviewApi'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { Toast } from '../../shared/components/Toast'
import { resolveReturnTo, withBackTo, withReturnTo } from '../../shared/navigation/returnTo'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN } from '../../shared/types/auth'
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
  const submitMutation = useSubmitForApproval(draftId)
  const { user } = useAuth()
  const isAdmin = user?.role === ROLE_ADMIN

  const [orderDate, setOrderDate] = useState('')
  const [requestedDelivery, setRequestedDelivery] = useState('')
  const [remark, setRemark] = useState('')
  const [orderQtyById, setOrderQtyById] = useState<Record<number, number>>({})
  const [submitDialogOpen, setSubmitDialogOpen] = useState(false)
  // Acceptance Fix (item 8, Toast/Banner重複): Save's own onSuccess fires
  // the instant the PUT resolves, but isDirty only clears once the
  // Requirements MD 13章 re-GET below actually lands and resyncs local
  // state - a real (if brief) async gap in which the persistent
  // "unsaved-changes-toast" Banner and the one-shot "保存しました。" success
  // Toast contradicted each other on screen. This flag suppresses ONLY the
  // Banner for that gap; it never skips or fakes the re-GET itself; the
  // Banner's usual isDirty-driven display comes right back once the
  // resynced Draft doesn't match (e.g. the user typed something else
  // in the meantime).
  const [justSaved, setJustSaved] = useState(false)

  // Reset local edit state whenever a fresh Draft is loaded (initial load,
  // or after Save triggers the re-GET per Requirements MD 13章).
  useEffect(() => {
    if (!draft) return
    setOrderDate(draft.orderDate ?? '')
    setRequestedDelivery(draft.requestedDelivery ?? '')
    setRemark(draft.remark ?? '')
    setOrderQtyById(Object.fromEntries(draft.details.map((d) => [d.id, d.orderQty])))
    setJustSaved(false)
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
    }, {
      onSuccess: () => setJustSaved(true),
    })
  }

  function handleSubmitForApproval() {
    submitMutation.mutate(undefined, {
      onSuccess: () => {
        setSubmitDialogOpen(false)
        // Phase 7-C1 15章: 発注詳細 is the shared landing point after a
        // Workflow Status change, same pattern as Demo Send (PoPreviewPage).
        navigate(withReturnTo(`/orders/${draftId}`, returnTo))
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
  const submitErrorCode =
    submitMutation.isError && axios.isAxiosError<ApiErrorBody>(submitMutation.error)
      ? submitMutation.error.response?.data?.errorCode
      : null

  // Phase 7-C1 4章/13章: mirrors OrderDraftPersistenceService.update()'s
  // ownership/role matrix - DRAFT is editable by its own creator or any
  // ADMIN, PENDING_APPROVAL is ADMIN-only (the edit-and-approve path), any
  // other Status is read-only. This is a UX convenience only; the Backend
  // enforces the actual rule regardless (17章 - Frontend hiding alone is
  // never the access control).
  const isEditable =
    draft.status === 'DRAFT' ? (isAdmin || draft.createdBy === user?.username) :
    draft.status === 'PENDING_APPROVAL' ? isAdmin :
    false
  const canSubmitForApproval = draft.status === 'DRAFT' && (isAdmin || draft.createdBy === user?.username)

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

      {/* Phase 7-C1 12章: the most prominent Alert on this screen when
          present - OPERATOR must not miss why their Draft came back. Only
          rendered while status is DRAFT and not yet resubmitted
          (OrderDraftService.resolveReturnReason on the Backend already
          clears it the moment SUBMITTED_FOR_APPROVAL fires again). */}
      {draft.returnReason && (
        <Alert severity="warning" sx={{ mb: 2 }} data-testid="return-reason-banner">
          <strong>{t('drafts:returnedBanner')}</strong>
          {' - '}
          {t('drafts:returnedReasonLabel')}: {draft.returnReason}
        </Alert>
      )}
      {draft.status === 'PENDING_APPROVAL' && !isAdmin && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('drafts:pendingApprovalNotice')}
        </Alert>
      )}
      {/* Phase 7-I (Layout Shift audit): these 5 were previously inline
          <Alert>s mounted directly above the Number Input Table below -
          confirmed Root Cause of the reported bug (isDirty toggling true on
          the very first click of the qty stepper pushed the Table, and the
          mouse pointer with it, down out from under the ▲ the user was still
          clicking). All 5 are TRANSIENT (a one-shot mutation result, or a
          live edit-session state bounded to "until Saved/Loaded") per this
          audit's own dividing line - moved to the shared Toast (Snackbar,
          fixed-position, never affects document flow). returnReason banner/
          pendingApprovalNotice above are deliberately NOT converted - they
          are standing Business state (loaded once with the Draft, not
          toggled by in-progress editing). */}
      <Toast
        open={updateMutation.isSuccess}
        severity="success"
        message={t('drafts:saveSuccess')}
        onClose={() => updateMutation.reset()}
        testId="save-success-toast"
      />
      <Toast
        open={Boolean(saveErrorCode)}
        severity="error"
        message={t('drafts:saveFailed', { code: saveErrorCode })}
        onClose={() => updateMutation.reset()}
        testId="save-error-toast"
      />
      <Toast
        open={submitMutation.isSuccess}
        severity="success"
        message={t('drafts:submitSuccess')}
        onClose={() => submitMutation.reset()}
      />
      <Toast
        open={Boolean(submitErrorCode)}
        severity="error"
        message={
          submitErrorCode === 'NO_ORDERABLE_ITEMS' ? t('drafts:errorNoOrderableItems') :
          submitErrorCode === 'FORBIDDEN' ? t('drafts:errorForbidden') :
          t('drafts:errorGeneric')
        }
        onClose={() => submitMutation.reset()}
      />
      <Toast
        open={isDirty && !justSaved}
        severity="warning"
        message={t('drafts:unsavedChangesBanner')}
        autoHideDuration={null}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'left' }}
        testId="unsaved-changes-toast"
      />

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
          {/* Phase 7-H (希望納期 required-ness audit): confirmed via Source
              that Legacy's Official PO Excel Import DOES require a delivery
              value (PrOfficialPoImportBatch - ERR_MSG_CELL_REQUIRED if
              blank), but that Gate applies to a Legacy-side Import step this
              Prototype never actually calls (no Official PO Import folder
              writes, per this engagement's standing constraint) - Source
              does NOT show any Portal-side Draft/Approval/Demo Send stage
              requiring it today (OrderDraftPersistenceService/
              OrderStatusTransitionService have zero validation on it).
              Marked 任意 here to reflect that CONFIRMED-optional state
              honestly, not left unlabeled - Business Rule NOT changed;
              customer-review-decision-package.md D-9相当のCUSTOMER REVIEW
              opens on whether Portal should require it earlier (see
              completion report). */}
          <TextField
            label={t('drafts:requestedDelivery')}
            type="date"
            size="small"
            value={requestedDelivery}
            onChange={(e) => setRequestedDelivery(e.target.value)}
            slotProps={{ inputLabel: { shrink: true }, input: { readOnly: !isEditable } }}
            helperText={isEditable ? t('drafts:requestedDeliveryOptionalHint') : undefined}
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
            onClick={() => navigate(withBackTo(withReturnTo(`/orders/drafts/${draftId}/preview`, returnTo), `/orders/drafts/${draftId}`))}
            data-testid="go-to-preview-button"
          >
            {t('drafts:preview')}
          </Button>
          {/* Phase 7-C1 8章: DRAFT -> PENDING_APPROVAL. Only the Draft's own
              creator or an ADMIN can submit (OrderStatusTransitionService.
              submitForApproval's ownership check) - hidden rather than
              shown-then-403'd for the common case, though the Backend
              enforces this regardless (17章).
              Phase 7-E Section 2/3 audit: submitForApproval transitions the
              already-PERSISTED Draft's Status only - it does not carry any
              Qty/Date/Remark payload of its own. Without this isDirty guard,
              a user who edits a value on screen and clicks 承認申請 without
              an intervening Save would silently submit the OLD saved values
              (matching what the Backend actually has), not what is currently
              typed - the same "local unsaved state vs Server state" gap that
              caused the Supplier Response confirmation bug, just on this
              screen's own Next action instead of a stale error message. The
              existing unsavedChangesBanner Alert above already explains why,
              same as the Save button's own isDirty-independent disable. */}
          {canSubmitForApproval && (
            <Button
              variant="contained"
              onClick={() => setSubmitDialogOpen(true)}
              disabled={submitMutation.isPending || isDirty}
              data-testid="submit-for-approval-button"
            >
              {submitMutation.isPending ? <CircularProgress size={20} /> : t('drafts:submitForApproval')}
            </Button>
          )}
        </Stack>
      </Paper>

      {/* Phase 7-E Section 2 audit: same backdropClick/escapeKeyDown hardening
          as the Supplier Response Confirm Dialog fix (found via live
          reproduction on that screen) - applied here for consistency since
          this is the same kind of "confirm a Workflow Status transition"
          Dialog, not a plain data-entry form. */}
      <Dialog
        open={submitDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setSubmitDialogOpen(false)
        }}
      >
        <DialogTitle>{t('drafts:submitDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('drafts:submitDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setSubmitDialogOpen(false)} disabled={submitMutation.isPending}>
            {t('drafts:submitDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleSubmitForApproval}
            disabled={submitMutation.isPending}
            data-testid="submit-for-approval-dialog-confirm"
          >
            {submitMutation.isPending ? <CircularProgress size={20} /> : t('drafts:submitDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
