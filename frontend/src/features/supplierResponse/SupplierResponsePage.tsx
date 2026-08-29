import { useEffect, useState } from 'react'
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
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogContentText from '@mui/material/DialogContentText'
import DialogActions from '@mui/material/DialogActions'
import Tooltip from '@mui/material/Tooltip'
import MenuItem from '@mui/material/MenuItem'
import FormControlLabel from '@mui/material/FormControlLabel'
import Checkbox from '@mui/material/Checkbox'

import {
  useSupplierResponse, useSaveSupplierResponse, useConfirmSupplierResponse,
  useAgreeResponse, useReopenAgreement, useCreateRevision, useResponseHistory,
} from './api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { AttentionChips } from '../../shared/components/AttentionChips'
import { withReturnTo } from '../../shared/navigation/returnTo'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN } from '../../shared/types/auth'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

/** Phase 7-C5 7章: candidate values, official per-value business definition
 * remains [TBD - CUSTOMER REVIEW] (24章). '' (unselected) is rendered
 * separately (t('unanswered')-equivalent), never added to this list. */
const SUPPLY_STATUS_OPTIONS = ['AVAILABLE', 'OUT_OF_STOCK', 'LONG_TERM_OUT_OF_STOCK', 'DISCONTINUED', 'WAITING_FOR_ARRIVAL', 'UNKNOWN']

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
  supplyStatus: string // '' = leave unchanged / not yet selected (never auto-inferred, 7-C5 7章)
}

export function SupplierResponsePage() {
  const { t } = useTranslation(['supplierResponse', 'common', 'status'])
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const navigate = useNavigate()
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
  const { data: responseHistory } = useResponseHistory(orderId)
  const saveMutation = useSaveSupplierResponse(orderId)
  const confirmMutation = useConfirmSupplierResponse(orderId)
  const agreeMutation = useAgreeResponse(orderId)
  const reopenMutation = useReopenAgreement(orderId)
  const createRevisionMutation = useCreateRevision(orderId)

  const { user } = useAuth()
  const isAdmin = user?.role === ROLE_ADMIN

  const [responseDate, setResponseDate] = useState('')
  const [responseNote, setResponseNote] = useState('')
  const [lines, setLines] = useState<Record<number, LineEdit>>({})
  const [confirmDialogOpen, setConfirmDialogOpen] = useState(false)
  const [agreeDialogOpen, setAgreeDialogOpen] = useState(false)
  const [forceAgree, setForceAgree] = useState(false)
  const [reopenDialogOpen, setReopenDialogOpen] = useState(false)
  const [reopenReason, setReopenReason] = useState('')
  const [revisionDialogOpen, setRevisionDialogOpen] = useState(false)
  const [revisionReason, setRevisionReason] = useState('')
  const [applyConfirmedValues, setApplyConfirmedValues] = useState(false)

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
        supplyStatus: d.supplyStatus ?? '',
      },
    ])))
  }, [response])

  const isEditable = response?.status === 'AWAITING_SUPPLIER'

  // Bug fix (found via live demo testing, PO-DEMO-20260829-0001): this MUST
  // be derived from the server-persisted summary, never from unsaved local
  // `lines` edit state. Filling in every field locally (without clicking
  // "回答を保存" first) used to make the Confirm button clickable even
  // though the Backend still held the old (unanswered) values - clicking it
  // then failed with SUPPLIER_RESPONSE_INCOMPLETE, and nothing ever cleared
  // that stale confirmMutation error afterward (see the useEffect below), so
  // the error banner kept contradicting an already-correct summary even
  // after a subsequent successful Save. Gating on
  // response.summary.unansweredCount instead makes the button reflect only
  // what is actually saved - exactly "全SKUについて回答数量が保存済みなら
  // 確定できる".
  const canConfirm = response ? response.summary.unansweredCount === 0 : false

  // Defensive fix for the same bug: whenever the server response is
  // replaced (a fresh GET, or the authoritative post-Save state written
  // into the cache), any earlier Confirm failure is no longer necessarily
  // still true - drop it rather than let a stale SUPPLIER_RESPONSE_INCOMPLETE
  // Alert linger on screen after the underlying data has since become fully
  // answered.
  useEffect(() => {
    confirmMutation.reset()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [response])

  // Phase 7-C5 11章: Agreement's forceAgree checkbox must appear whenever ANY
  // ACTIVE Attention exists - order-level (response.orderAttentions) OR
  // line-level (each detail's own attentions, e.g. QUANTITY_CHANGED/
  // DELIVERY_CHANGED/SUPPLY_STATUS_CHANGED) - matching exactly what the
  // Backend's agree() checks (orderAttentionRepository...findByPortalOrderIdAndActiveTrue,
  // which is not scoped to order-level-only).
  const hasAnyActiveAttention = Boolean(
    response && (response.orderAttentions.length > 0 || response.details.some((d) => d.attentions.length > 0)),
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
  function handleSupplyStatusChange(detailId: number, raw: string) {
    setLines((prev) => ({ ...prev, [detailId]: { ...prev[detailId], supplyStatus: raw } }))
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
          // '' means "leave unchanged" (Backend convention, 7-C5 7章) - never
          // send an empty string as if it were a real selection.
          supplyStatus: edit.supplyStatus === '' ? null : edit.supplyStatus,
        }
      }),
    })
  }

  function handleAgree() {
    if (!response) return
    agreeMutation.mutate({ responseId: response.responseId, forceAgree }, {
      onSuccess: () => {
        setAgreeDialogOpen(false)
        setForceAgree(false)
      },
    })
  }

  function handleReopen() {
    if (!response) return
    reopenMutation.mutate({ responseId: response.responseId, reason: reopenReason }, {
      onSuccess: () => {
        setReopenDialogOpen(false)
        setReopenReason('')
      },
    })
  }

  function handleCreateRevision() {
    createRevisionMutation.mutate({ reason: revisionReason, applyConfirmedValues }, {
      onSuccess: () => {
        setRevisionDialogOpen(false)
        setRevisionReason('')
        setApplyConfirmedValues(false)
        navigate(withReturnTo(`/orders/drafts/${orderId}`, returnTo))
      },
    })
  }

  function handleConfirm() {
    confirmMutation.mutate(undefined, {
      onSuccess: () => {
        setConfirmDialogOpen(false)
        // Phase 6-C (docs/production-ux-workflow-redesign.md 4章): 発注詳細
        // is this order's landing point after Confirm too, not just after
        // Send - Recommended/Ordered/Confirmed Qty, Attention, and 操作履歴
        // all live there. The existing returnTo (usually the Order List
        // this screen was reached from, e.g. Status=AWAITING_SUPPLIER) is
        // forwarded as-is - unlike Demo Send, there is no "wrong business
        // context" concern here, so no reset is needed.
        navigate(withReturnTo(`/orders/${orderId}`, returnTo), {
          state: { supplierResponseConfirmSuccess: true },
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

      {saveMutation.isSuccess && <Alert severity="success" sx={{ mb: 2 }}>{t('saveSuccess')}</Alert>}
      {saveErrorCode === 'INVALID_CONFIRMED_QTY' && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('errorGeneric')}</Alert>
      )}
      {saveErrorCode === 'INVALID_STATUS_TRANSITION' && (
        <Alert severity="error" sx={{ mb: 2 }}>{t('errorInvalidStatusTransition')}</Alert>
      )}
      {/* Phase 6-E: confirmMutation.isSuccess Alert removed - handleConfirm's
          onSuccess always navigate()s to 発注詳細 in the same synchronous
          callback (Phase 6-C), so this component unmounts before isSuccess
          can ever render true here. Confirmed via source. The actually-
          visible equivalent Message now lives on 発注詳細
          (OrderHistoryDetailPage's supplierResponseConfirmSuccessMessage).
          confirmErrorCode Alerts below are unaffected - only onSuccess
          navigates. */}
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
              <TableCell>{t('table.supplyStatus')}</TableCell>
              <TableCell>{t('table.attention')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {response.details.map((d) => {
              const edit = lines[d.detailId] ?? { confirmedQty: '', confirmedDelivery: '', responseNote: '', supplyStatus: '' }
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
                    {isEditable ? (
                      <TextField
                        select
                        size="small"
                        value={edit.supplyStatus}
                        onChange={(e) => handleSupplyStatusChange(d.detailId, e.target.value)}
                        data-testid={`supply-status-select-${d.sku}`}
                        sx={{ minWidth: 160 }}
                      >
                        <MenuItem value="">{t('unanswered')}</MenuItem>
                        {SUPPLY_STATUS_OPTIONS.map((option) => (
                          <MenuItem key={option} value={option}>
                            {t(`status:supplyStatus.${option}`, { defaultValue: option })}
                          </MenuItem>
                        ))}
                      </TextField>
                    ) : (
                      <Typography variant="body2">
                        {d.supplyStatus
                          ? t(`status:supplyStatus.${d.supplyStatus}`, { defaultValue: d.supplyStatus })
                          : t('unanswered')}
                      </Typography>
                    )}
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

      {/* Phase 7-C5 8章: structured Order Revision vs Response comparison -
          distinct from Attention (which tracks whether a person has reviewed
          a difference, not the difference itself). */}
      {response.differences.length > 0 && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="response-differences-section">
          <Typography variant="subtitle1" gutterBottom>{t('differences.title')}</Typography>
          <Stack spacing={0.5}>
            {response.differences.map((diff, i) => (
              <Typography key={i} variant="body2" data-testid={`difference-${diff.skuCode}-${diff.type}`}>
                {diff.skuCode} - {t(`differences.type.${diff.type}`)}:{' '}
                {diff.orderedValue ?? '—'} → {diff.confirmedValue ?? t('unanswered')}
              </Typography>
            ))}
          </Stack>
        </Paper>
      )}

      {/* Phase 7-C5 11章/18章: Agreement - explicit Business Action, never
          implied by Confirm. ADMIN only (Backend-enforced; hidden here for
          OPERATOR as UX convenience only, 7-C5 15章/17章's established
          precedent). */}
      {response.status === 'SUPPLIER_CONFIRMED' && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="agreement-section">
          <Typography variant="subtitle1" gutterBottom>{t('agreement.title')}</Typography>
          {agreeMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {(() => {
                const code = errorCodeOf(agreeMutation.error)
                if (code === 'UNACKNOWLEDGED_ATTENTION') return t('agreement.errorUnacknowledgedAttention')
                if (code === 'FORBIDDEN') return t('agreement.errorForbidden')
                return t('agreement.errorGeneric')
              })()}
            </Alert>
          )}
          {createRevisionMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {(() => {
                const code = errorCodeOf(createRevisionMutation.error)
                if (code === 'REVISION_REASON_REQUIRED') return t('revision.errorReasonRequired')
                if (code === 'FORBIDDEN') return t('agreement.errorForbidden')
                return t('agreement.errorGeneric')
              })()}
            </Alert>
          )}
          {isAdmin ? (
            <Stack direction="row" spacing={2}>
              <Button
                variant="contained"
                onClick={() => setAgreeDialogOpen(true)}
                disabled={agreeMutation.isPending}
                data-testid="agree-button"
              >
                {t('agreement.agreeButton')}
              </Button>
              <Button
                variant="outlined"
                onClick={() => setRevisionDialogOpen(true)}
                disabled={createRevisionMutation.isPending}
                data-testid="create-revision-button"
              >
                {t('revision.createButton')}
              </Button>
            </Stack>
          ) : (
            <Alert severity="info">{t('agreement.adminOnlyIndicator')}</Alert>
          )}
        </Paper>
      )}

      {response.status === 'AGREED' && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="agreed-section">
          <Typography variant="subtitle1" gutterBottom>{t('agreement.title')}</Typography>
          <Typography variant="body2" data-testid="agreed-info">
            {t('agreement.agreedInfo', { by: response.agreedBy, at: response.agreedAt ? new Date(response.agreedAt).toLocaleString('ja-JP') : '' })}
          </Typography>
          {isAdmin ? (
            <Button
              variant="outlined"
              color="error"
              sx={{ mt: 2 }}
              onClick={() => setReopenDialogOpen(true)}
              disabled={reopenMutation.isPending}
              data-testid="reopen-button"
            >
              {t('agreement.reopenButton')}
            </Button>
          ) : (
            <Alert severity="info" sx={{ mt: 2 }}>{t('agreement.adminOnlyIndicator')}</Alert>
          )}
        </Paper>
      )}

      {/* Phase 7-C5 20章/21章: Response History (Response1, Response2, ...) -
          past rows are READ ONLY, only the current one is ever editable. */}
      {responseHistory && responseHistory.length > 1 && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="response-history-section">
          <Typography variant="subtitle1" gutterBottom>{t('history.title')}</Typography>
          <Stack spacing={1}>
            {responseHistory.map((h) => (
              <Stack key={h.responseId} direction="row" spacing={2} sx={{ alignItems: 'center' }}
                     data-testid={`response-history-row-${h.revisionNo}`}>
                <Typography variant="body2">
                  {t('history.revisionLabel', { no: h.revisionNo })}
                  {h.isCurrent ? ` (${t('history.current')})` : ''}
                </Typography>
                <Typography variant="body2" color="text.secondary">{h.responseStatus}</Typography>
                {h.agreedBy && <Typography variant="caption" color="text.secondary">{t('history.agreedBy', { by: h.agreedBy })}</Typography>}
              </Stack>
            ))}
          </Stack>
        </Paper>
      )}

      {/* Phase 6-C (docs/production-ux-workflow-redesign.md 6章): CTA set is
          Status-driven now - AWAITING_SUPPLIER gets 回答を保存/メーカー回答を確定
          (plus the 発注詳細へ戻る button already at the top of the page);
          SUPPLIER_CONFIRMED is 原則READ ONLY with only that same 戻る button,
          so no bottom row renders at all. The former standalone "履歴を見る"
          button is gone - Confirm now lands on 発注詳細 itself, and 戻る
          already goes there too, so it was a redundant third path. */}
      {isEditable && (
        <Stack direction="row" spacing={2} sx={{ mt: 3 }}>
          <Button variant="contained" onClick={handleSave} disabled={saveMutation.isPending} data-testid="save-response-button">
            {saveMutation.isPending ? <CircularProgress size={20} /> : t('saveResponse')}
          </Button>
          <Button
            variant="outlined"
            onClick={() => setConfirmDialogOpen(true)}
            disabled={!canConfirm || confirmMutation.isPending}
            data-testid="confirm-response-button"
          >
            {t('confirmResponse')}
          </Button>
        </Stack>
      )}

      {/* Bug fix (found via live demo testing on a Revision 2 order): a plain
          `onClose={() => setConfirmDialogOpen(false)}` also fires on a
          backdrop click or Escape - MUI's default dismiss paths for ANY
          Dialog. Root-caused live: the very first attempt to click
          "メーカー回答を確定" landed fractionally outside the dialog's own
          button (a coordinate/timing mismatch plausible under real,
          higher-latency input such as a remote desktop session), and that
          single miss-click was enough to hit the Backdrop and silently
          dismiss the Dialog via this exact onClose path - with the
          Backend/API layer never touched at all (confirmed: zero network
          requests fired), exactly matching the reported "確認Dialogが一瞬
          表示されたが、ユーザー操作なしで自動的に消えた" symptom. This is
          NOT related to the previous Phase's confirmMutation.reset()
          useEffect fix (verified live: the Dialog survives repeated
          `response` cache replacements without a real Cancel/Confirm click).
          For this "確定後は編集できません" irreversible confirmation, only an
          explicit Cancel or Confirm click should ever close it - so
          `reason` is inspected and both dismiss-by-accident paths are
          ignored here. */}
      <Dialog
        open={confirmDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setConfirmDialogOpen(false)
        }}
      >
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

      <Dialog open={agreeDialogOpen} onClose={() => setAgreeDialogOpen(false)}>
        <DialogTitle>{t('agreement.agreeDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap', mb: 1 }}>{t('agreement.agreeDialogBody')}</DialogContentText>
          {hasAnyActiveAttention && (
            <FormControlLabel
              control={<Checkbox checked={forceAgree} onChange={(e) => setForceAgree(e.target.checked)} data-testid="force-agree-checkbox" />}
              label={t('agreement.forceAgreeLabel')}
            />
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setAgreeDialogOpen(false)} disabled={agreeMutation.isPending}>
            {t('agreement.agreeDialogCancel')}
          </Button>
          <Button variant="contained" onClick={handleAgree} disabled={agreeMutation.isPending} data-testid="agree-dialog-confirm">
            {agreeMutation.isPending ? <CircularProgress size={20} /> : t('agreement.agreeDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={reopenDialogOpen} onClose={() => setReopenDialogOpen(false)}>
        <DialogTitle>{t('agreement.reopenDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>{t('agreement.reopenDialogBody')}</DialogContentText>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label={t('agreement.reopenReasonInputLabel')}
            value={reopenReason}
            onChange={(e) => setReopenReason(e.target.value)}
            data-testid="reopen-reason-input"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReopenDialogOpen(false)} disabled={reopenMutation.isPending}>
            {t('agreement.reopenDialogCancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleReopen}
            disabled={reopenMutation.isPending || reopenReason.trim() === ''}
            data-testid="reopen-dialog-confirm"
          >
            {reopenMutation.isPending ? <CircularProgress size={20} /> : t('agreement.reopenDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={revisionDialogOpen} onClose={() => setRevisionDialogOpen(false)}>
        <DialogTitle>{t('revision.createDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>{t('revision.createDialogBody')}</DialogContentText>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label={t('revision.reasonInputLabel')}
            value={revisionReason}
            onChange={(e) => setRevisionReason(e.target.value)}
            data-testid="revision-reason-input"
            sx={{ mb: 1 }}
          />
          <FormControlLabel
            control={<Checkbox checked={applyConfirmedValues} onChange={(e) => setApplyConfirmedValues(e.target.checked)} data-testid="apply-confirmed-values-checkbox" />}
            label={t('revision.applyConfirmedValuesLabel')}
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRevisionDialogOpen(false)} disabled={createRevisionMutation.isPending}>
            {t('revision.createDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleCreateRevision}
            disabled={createRevisionMutation.isPending || revisionReason.trim() === ''}
            data-testid="revision-dialog-confirm"
          >
            {createRevisionMutation.isPending ? <CircularProgress size={20} /> : t('revision.createDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
