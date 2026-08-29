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

import Chip from '@mui/material/Chip'
import MenuItem from '@mui/material/MenuItem'

import { useOrderHistoryDetail, useOrderEvents } from './api'
import { useOfficialPoIntegration, useRequestOfficialPoIntegration } from './officialPoIntegrationApi'
import { useLegacyPoConcurrency, useCaptureLegacyPoBaseline } from './legacyPoConcurrencyApi'
import { useMailPreview } from './mailPreviewApi'
import { useFulfillment } from './fulfillmentApi'
import {
  useFollowUpCases, useCreateFollowUpCase, useUpdateFollowUpCaseNote,
  useCloseFollowUpCase, usePreviewFollowUpMail, useCreateReorderDraft,
} from './followUpApi'
import { useApprove, useReturnForCorrection } from '../drafts/poPreviewApi'
import { useOrderRevisions, useResponseHistory } from '../supplierResponse/api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { AttentionChips } from '../../shared/components/AttentionChips'
import { resolveReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN } from '../../shared/types/auth'
import type { ApiErrorBody } from '../../shared/types/orderDraft'
import type { TFunction } from 'i18next'

const FOLLOW_UP_REASONS = ['DELIVERY_OVERDUE', 'PARTIAL_DELIVERY', 'NO_ARRIVAL', 'QUANTITY_DIFFERENCE', 'OTHER']

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
  const { data: integration } = useOfficialPoIntegration(orderId)
  const { data: concurrency } = useLegacyPoConcurrency(orderId)
  const { data: revisions } = useOrderRevisions(orderId)
  const { data: responseHistory } = useResponseHistory(orderId)
  const { data: fulfillment } = useFulfillment(orderId)
  const { data: followUpCases } = useFollowUpCases(orderId)

  const { user } = useAuth()
  const isAdmin = user?.role === ROLE_ADMIN
  const approveMutation = useApprove(orderId)
  const returnMutation = useReturnForCorrection(orderId)
  const requestIntegrationMutation = useRequestOfficialPoIntegration(orderId)
  const captureBaselineMutation = useCaptureLegacyPoBaseline(orderId)
  const mailPreviewMutation = useMailPreview(orderId)
  const createFollowUpCaseMutation = useCreateFollowUpCase(orderId)
  const updateFollowUpNoteMutation = useUpdateFollowUpCaseNote(orderId)
  const closeFollowUpCaseMutation = useCloseFollowUpCase(orderId)
  const previewFollowUpMailMutation = usePreviewFollowUpMail(orderId)
  const createReorderDraftMutation = useCreateReorderDraft()
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [returnDialogOpen, setReturnDialogOpen] = useState(false)
  const [returnReason, setReturnReason] = useState('')
  const [requestDialogOpen, setRequestDialogOpen] = useState(false)
  const [followUpDialogOpen, setFollowUpDialogOpen] = useState(false)
  const [followUpSku, setFollowUpSku] = useState('')
  const [followUpReason, setFollowUpReason] = useState('OTHER')
  const [followUpNote, setFollowUpNote] = useState('')
  const [previewingCaseId, setPreviewingCaseId] = useState<number | null>(null)
  const [reorderDialogCaseId, setReorderDialogCaseId] = useState<number | null>(null)
  const [reorderReason, setReorderReason] = useState('')
  const [editNoteCaseId, setEditNoteCaseId] = useState<number | null>(null)
  const [editNoteValue, setEditNoteValue] = useState('')

  function handleRequestIntegration() {
    requestIntegrationMutation.mutate(undefined, { onSuccess: () => setRequestDialogOpen(false) })
  }

  function handleCaptureBaseline() {
    captureBaselineMutation.mutate()
  }

  function openFollowUpDialog(sku?: string) {
    setFollowUpSku(sku ?? '')
    setFollowUpReason('OTHER')
    setFollowUpNote('')
    setFollowUpDialogOpen(true)
  }

  function handleCreateFollowUpCase() {
    createFollowUpCaseMutation.mutate(
      { skuCode: followUpSku || null, reason: followUpReason, note: followUpNote || null },
      { onSuccess: () => setFollowUpDialogOpen(false) },
    )
  }

  function handleCreateReorderDraft() {
    const followUpCase = followUpCases?.find((c) => c.id === reorderDialogCaseId)
    if (!followUpCase) return
    createReorderDraftMutation.mutate(
      { caseId: followUpCase.id, request: { skus: followUpCase.skuCode ? [followUpCase.skuCode] : [], reorderReason: reorderReason || null } },
      {
        onSuccess: (created) => {
          setReorderDialogCaseId(null)
          setReorderReason('')
          navigate(`/orders/drafts/${created.id}`)
        },
      },
    )
  }

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
      case 'AGREED':
        // 確認 (review) - same screen, read-only once Confirmed
        // (SupplierResponsePage.isEditable already gates on Status). AGREED
        // is where the Agreement/Reopen Actions themselves live (7-C5 21章).
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

      {/* Phase 7-C2A 13章: shown once APPROVED (where ADMIN can start it) or
          once a Request already exists (any later Status - the Section stays
          visible so its history remains visible even after Demo Send moves
          the Order on, since Demo Send and this Foundation are deliberately
          independent, 7-C2A 15章). */}
      {(detail.status === 'APPROVED' || (integration && integration.status !== 'NOT_REQUESTED')) && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="official-po-integration-section">
          <Typography variant="subtitle1" gutterBottom>{t('officialPoIntegration.title')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
            {t('officialPoIntegration.subtitle')}
          </Typography>

          {requestIntegrationMutation.isSuccess && (
            <Alert severity="success" sx={{ mb: 2 }}>{t('officialPoIntegration.requestSuccess')}</Alert>
          )}
          {requestIntegrationMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {(() => {
                const code = errorCodeOf(requestIntegrationMutation.error)
                if (code === 'ORDER_NOT_APPROVED') return t('officialPoIntegration.errorNotApproved')
                if (code === 'FORBIDDEN') return t('errorForbidden')
                return t('errorGeneric')
              })()}
            </Alert>
          )}

          {integration && (
            <Stack spacing={1} sx={{ mb: 2 }}>
              <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
                <Typography variant="body2">
                  {t('officialPoIntegration.statusLabel.' + integration.status)}
                </Typography>
                <Typography variant="body2">
                  {t('officialPoIntegration.officialPoNoLabel')}:{' '}
                  <strong data-testid="official-po-no">
                    {integration.officialPoNo ?? t('officialPoIntegration.officialPoNoUnassigned')}
                  </strong>
                </Typography>
                {integration.status !== 'NOT_REQUESTED' && (
                  <Typography variant="body2">
                    {t('officialPoIntegration.revisionLabel')}: {integration.revisionNo}
                  </Typography>
                )}
              </Stack>
              {!integration.officialPoNo && integration.status !== 'NOT_REQUESTED' && (
                <Alert severity="info" data-testid="official-po-no-unassigned-note">
                  {t('officialPoIntegration.officialPoNoUnassignedNote')}
                </Alert>
              )}
              {integration.requestedAt && (
                <Typography variant="caption" color="text.secondary">
                  {t('officialPoIntegration.requestedByLabel')}: {integration.requestedBy} - {t('officialPoIntegration.requestedAtLabel')}: {new Date(integration.requestedAt).toLocaleString('ja-JP')}
                </Typography>
              )}
              {integration.preflight && (
                <Box data-testid="preflight-result">
                  <Typography variant="body2" sx={{ fontWeight: 'bold', mt: 1 }}>
                    {t('officialPoIntegration.lastResultLabel')}:{' '}
                    <Chip
                      size="small"
                      label={t('officialPoIntegration.preflightResult.' + integration.preflight.result)}
                      color={integration.preflight.result === 'BLOCKED' ? 'error'
                        : integration.preflight.result === 'WARNING' ? 'warning' : 'success'}
                    />
                  </Typography>
                  <Stack spacing={0.5} sx={{ mt: 1 }}>
                    {integration.preflight.issues.map((issue, i) => (
                      <Typography key={i} variant="body2" color="text.secondary">
                        {t(`officialPoIntegration.preflightIssue.${issue.code}`, { defaultValue: issue.code })}
                        {issue.skuCode ? ` (${issue.skuCode})` : ''}
                      </Typography>
                    ))}
                  </Stack>
                </Box>
              )}
            </Stack>
          )}

          {detail.status === 'APPROVED' && isAdmin && (
            <Button
              variant="outlined"
              onClick={() => setRequestDialogOpen(true)}
              disabled={requestIntegrationMutation.isPending}
              data-testid="official-po-request-button"
            >
              {requestIntegrationMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.requestButton')}
            </Button>
          )}

          {/* Phase 7-C6 9章/10章/19章: Excel / Legacy Concurrency Control
              Foundation - extends this same Section rather than adding a new
              one (19章's explicit instruction). Never implies a Legacy WRITE
              (20章's Label wording constraint). */}
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" gutterBottom>{t('legacyPoConcurrency.title')}</Typography>

          {captureBaselineMutation.isSuccess && (
            <Alert severity="success" sx={{ mb: 2 }}>{t('legacyPoConcurrency.captureSuccess')}</Alert>
          )}
          {captureBaselineMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }} data-testid="legacy-po-baseline-capture-error">
              {(() => {
                const code = errorCodeOf(captureBaselineMutation.error)
                if (code === 'OFFICIAL_PO_NOT_LINKED') return t('legacyPoConcurrency.errorNotLinked')
                if (code === 'LEGACY_PO_NOT_FOUND_FOR_BASELINE') return t('legacyPoConcurrency.errorPoNotFound')
                if (code === 'INTEGRATION_REQUEST_REQUIRED') return t('legacyPoConcurrency.errorIntegrationRequestRequired')
                if (code === 'FORBIDDEN') return t('errorForbidden')
                return t('errorGeneric')
              })()}
            </Alert>
          )}

          {concurrency && (
            <Stack spacing={1} sx={{ mb: 2 }} data-testid="legacy-po-concurrency-section">
              {concurrency.result === 'NOT_LINKED' && (
                <Alert severity="info" data-testid="concurrency-not-linked">{t('legacyPoConcurrency.notLinked')}</Alert>
              )}
              {concurrency.result === 'PO_NOT_FOUND' && (
                <Alert severity="warning" data-testid="concurrency-po-not-found">{t('legacyPoConcurrency.poNotFound')}</Alert>
              )}
              {concurrency.result === 'NOT_BASELINED' && (
                <Alert severity="info" data-testid="concurrency-not-baselined">{t('legacyPoConcurrency.notBaselined')}</Alert>
              )}
              {concurrency.result === 'UNCHANGED' && (
                <Alert severity="success" data-testid="concurrency-unchanged">{t('legacyPoConcurrency.unchanged')}</Alert>
              )}
              {concurrency.result === 'CHANGED' && (
                <Box data-testid="concurrency-changed">
                  <Alert severity="warning" sx={{ mb: 1 }}>{t('legacyPoConcurrency.changed')}</Alert>
                  <TableContainer>
                    <Table size="small">
                      <TableHead>
                        <TableRow>
                          <TableCell>{t('legacyPoConcurrency.diffTable.field')}</TableCell>
                          <TableCell>{t('legacyPoConcurrency.diffTable.sku')}</TableCell>
                          <TableCell>{t('legacyPoConcurrency.diffTable.baselineValue')}</TableCell>
                          <TableCell>{t('legacyPoConcurrency.diffTable.currentValue')}</TableCell>
                          <TableCell>{t('legacyPoConcurrency.diffTable.diffType')}</TableCell>
                        </TableRow>
                      </TableHead>
                      <TableBody>
                        {concurrency.diffs.map((d, i) => (
                          <TableRow key={i} data-testid={`concurrency-diff-row-${i}`}>
                            <TableCell>{d.field}</TableCell>
                            <TableCell>{d.skuCode ?? t('notAvailable')}</TableCell>
                            <TableCell>{d.baselineValue ?? t('notAvailable')}</TableCell>
                            <TableCell>{d.currentValue ?? t('notAvailable')}</TableCell>
                            <TableCell>{t('legacyPoConcurrency.diffType.' + d.diffType)}</TableCell>
                          </TableRow>
                        ))}
                      </TableBody>
                    </Table>
                  </TableContainer>
                </Box>
              )}
              {concurrency.capturedAt && (
                <Typography variant="caption" color="text.secondary">
                  {t('legacyPoConcurrency.baselineCapturedLabel')}: {concurrency.capturedBy} -{' '}
                  {new Date(concurrency.capturedAt).toLocaleString('ja-JP')}
                </Typography>
              )}
            </Stack>
          )}

          {isAdmin && (
            <Button
              variant="outlined"
              onClick={handleCaptureBaseline}
              disabled={captureBaselineMutation.isPending}
              data-testid="legacy-po-baseline-capture-button"
            >
              {captureBaselineMutation.isPending ? <CircularProgress size={20} /> : t('legacyPoConcurrency.captureButton')}
            </Button>
          )}
        </Paper>
      )}

      {/* Phase 7-C3 9章/11章: Mail Preview only - no Send API exists this
          Phase. Same visibility/co-location as the Integration Section
          above; deliberately separate Label from "Demo Send" (7-C3 11章). */}
      {(detail.status === 'APPROVED' || (integration && integration.status !== 'NOT_REQUESTED')) && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="mail-preview-section">
          <Typography variant="subtitle1" gutterBottom>{t('mailPreview.title')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('mailPreview.subtitle')}</Typography>

          {mailPreviewMutation.isError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              {(() => {
                const code = errorCodeOf(mailPreviewMutation.error)
                if (code === 'FORBIDDEN') return t('mailPreview.errorForbidden')
                return t('mailPreview.errorGeneric')
              })()}
            </Alert>
          )}

          <Button
            variant="outlined"
            onClick={() => mailPreviewMutation.mutate()}
            disabled={mailPreviewMutation.isPending}
            data-testid="mail-preview-button"
          >
            {mailPreviewMutation.isPending ? <CircularProgress size={20} /> : t('mailPreview.previewButton')}
          </Button>

          {mailPreviewMutation.data && (
            <Stack spacing={1} sx={{ mt: 2 }} data-testid="mail-preview-result">
              {mailPreviewMutation.data.issues.length > 0 && (
                <Stack spacing={0.5}>
                  {mailPreviewMutation.data.issues.map((issue, i) => (
                    <Alert key={i} severity={issue.severity === 'BLOCKED' ? 'error' : 'warning'}>
                      {t(`mailPreview.issue.${issue.code}`, { defaultValue: issue.code })}
                    </Alert>
                  ))}
                </Stack>
              )}
              <Typography variant="body2">{t('mailPreview.from')}: {mailPreviewMutation.data.from}</Typography>
              <Typography variant="body2">{t('mailPreview.to')}: {mailPreviewMutation.data.to.join(', ') || '—'}</Typography>
              <Typography variant="body2">{t('mailPreview.cc')}: {mailPreviewMutation.data.cc.join(', ') || '—'}</Typography>
              {mailPreviewMutation.data.subject ? (
                <>
                  <Typography variant="body2">{t('mailPreview.subject')}: <strong>{mailPreviewMutation.data.subject}</strong></Typography>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>
                    {t('mailPreview.body')}:{'\n'}{mailPreviewMutation.data.body}
                  </Typography>
                </>
              ) : (
                <Alert severity="info">{t('mailPreview.blockedNotice')}</Alert>
              )}
              <Typography variant="body2" color="text.secondary">
                {t('mailPreview.attachment')}: {mailPreviewMutation.data.attachment.fileName ?? '—'}
                {' '}{t('mailPreview.attachmentNotGenerated')}
              </Typography>
            </Stack>
          )}
        </Paper>
      )}

      {/* Phase 7-C5 19章/20章: browsable Revision History (Rev1, Rev2, ...) -
          only rendered once an Order has actually been sent at least once
          (a never-sent Order has no Revision yet). */}
      {revisions && revisions.length > 0 && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="revision-history-section">
          <Typography variant="subtitle1" gutterBottom>{t('revisionHistory.title')}</Typography>
          <Stack spacing={2}>
            {revisions.map((r) => (
              <Box key={r.revisionId} data-testid={`revision-row-${r.revisionNo}`}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="body2" sx={{ fontWeight: 'bold' }}>
                    {t('revisionHistory.revisionLabel', { no: r.revisionNo })}
                  </Typography>
                  <Chip size="small" label={t(`revisionHistory.type.${r.revisionType}`)} />
                  <Typography variant="caption" color="text.secondary">
                    {r.createdBy} - {new Date(r.createdAt).toLocaleString('ja-JP')}
                  </Typography>
                </Stack>
                {r.reason && (
                  <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
                    {t('revisionHistory.reasonLabel')}: {r.reason}
                  </Typography>
                )}
                <Stack direction="row" spacing={3} sx={{ mt: 0.5, flexWrap: 'wrap' }}>
                  {r.lines.map((line) => (
                    <Typography key={line.skuCode} variant="caption" color="text.secondary">
                      {line.skuCode}: {line.orderedQty}
                    </Typography>
                  ))}
                </Stack>
              </Box>
            ))}
          </Stack>
        </Paper>
      )}

      {/* Phase 7-C5 20章: browsable Response History (Response1, Response2, ...). */}
      {responseHistory && responseHistory.length > 0 && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="order-detail-response-history-section">
          <Typography variant="subtitle1" gutterBottom>{t('responseHistory.title')}</Typography>
          <Stack spacing={1}>
            {responseHistory.map((h) => (
              <Stack key={h.responseId} direction="row" spacing={2} sx={{ alignItems: 'center' }}
                     data-testid={`order-detail-response-history-row-${h.revisionNo}`}>
                <Typography variant="body2">
                  {t('responseHistory.revisionLabel', { no: h.revisionNo })}
                  {h.isCurrent ? ` (${t('responseHistory.current')})` : ''}
                </Typography>
                <Typography variant="body2" color="text.secondary">{h.responseStatus}</Typography>
                {h.agreedBy && (
                  <Typography variant="caption" color="text.secondary">
                    {t('responseHistory.agreedBy', { by: h.agreedBy })}
                  </Typography>
                )}
                {h.reopenedBy && (
                  <Typography variant="caption" color="text.secondary">
                    {t('responseHistory.reopenedBy', { by: h.reopenedBy })}
                  </Typography>
                )}
              </Stack>
            ))}
          </Stack>
        </Paper>
      )}

      {/* Phase 7-C7A 4章/6章: G-SYS入荷状況 - always rendered (once loaded),
          even when NOT_LINKED, so that state is explicit rather than a
          misleading "0件"/"未納" (4章's explicit instruction). */}
      {fulfillment && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="fulfillment-section">
          <Typography variant="subtitle1" gutterBottom>{t('fulfillment.title')}</Typography>
          {fulfillment.linkState === 'NOT_LINKED' && (
            <Alert severity="info" data-testid="fulfillment-not-linked">{t('fulfillment.notLinked')}</Alert>
          )}
          {fulfillment.linkState === 'PO_NOT_FOUND' && (
            <Alert severity="warning" data-testid="fulfillment-po-not-found">{t('fulfillment.poNotFound')}</Alert>
          )}
          {fulfillment.linkState === 'LINKED' && (
            <>
              <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1, alignItems: 'center', mb: 1 }}>
                <Typography variant="body2">
                  {t('fulfillment.officialPoNoLabel')}: <strong>{fulfillment.officialPoNo}</strong>
                </Typography>
                <Chip
                  size="small"
                  label={t(`fulfillment.status.${fulfillment.fulfillmentStatus}`)}
                  color={fulfillment.fulfillmentStatus === 'FULFILLED' ? 'success'
                    : fulfillment.fulfillmentStatus === 'PARTIAL' ? 'warning' : 'default'}
                  data-testid="fulfillment-status-chip"
                />
              </Stack>
              <TableContainer>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>{t('fulfillment.table.sku')}</TableCell>
                      <TableCell>{t('fulfillment.table.itemName')}</TableCell>
                      <TableCell align="right">{t('fulfillment.table.ordered')}</TableCell>
                      <TableCell align="right">{t('fulfillment.table.invoiced')}</TableCell>
                      <TableCell align="right">{t('fulfillment.table.stockIn')}</TableCell>
                      <TableCell align="right">{t('fulfillment.table.outstanding')}</TableCell>
                      <TableCell>{t('fulfillment.table.status')}</TableCell>
                      <TableCell />
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {fulfillment.lines.map((line) => (
                      <TableRow key={line.skuCode} hover data-testid={`fulfillment-line-${line.skuCode}`}>
                        <TableCell>{line.skuCode}</TableCell>
                        <TableCell>{line.itemName}</TableCell>
                        <TableCell align="right">{line.orderedQty}</TableCell>
                        <TableCell align="right">{line.invoicedQty}</TableCell>
                        <TableCell align="right">{line.stockInQty}</TableCell>
                        <TableCell align="right">{line.outstandingQty}</TableCell>
                        <TableCell>{t(`fulfillment.status.${line.lineStatus}`)}</TableCell>
                        <TableCell>
                          <Button size="small" onClick={() => openFollowUpDialog(line.skuCode)}
                                  data-testid={`fulfillment-follow-up-button-${line.skuCode}`}>
                            {t('followUp.createButtonShort')}
                          </Button>
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </TableContainer>
            </>
          )}
        </Paper>
      )}

      {/* Phase 7-C7A 9章/12章/18章: 問い合わせ (Follow-up Case) - always
          Portal-only, never auto-generated. */}
      <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="follow-up-section">
        <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
          <Typography variant="subtitle1">{t('followUp.title')}</Typography>
          <Button size="small" variant="outlined" onClick={() => openFollowUpDialog()} data-testid="create-follow-up-case-button">
            {t('followUp.createButton')}
          </Button>
        </Stack>

        {createFollowUpCaseMutation.isError && (
          <Alert severity="error" sx={{ mb: 1 }}>{t('followUp.errorGeneric')}</Alert>
        )}
        {previewFollowUpMailMutation.isError && (
          <Alert severity="error" sx={{ mb: 1 }}>{t('followUp.errorGeneric')}</Alert>
        )}

        {(!followUpCases || followUpCases.length === 0) && (
          <Alert severity="info">{t('followUp.empty')}</Alert>
        )}

        <Stack spacing={1.5}>
          {followUpCases?.map((c) => (
            <Paper key={c.id} variant="outlined" sx={{ p: 1.5 }} data-testid={`follow-up-case-${c.id}`}>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                <Chip size="small" label={t(`followUp.status.${c.status}`)}
                      color={c.status === 'CLOSED' ? 'default' : c.status === 'INQUIRY_PREPARED' ? 'info' : 'warning'} />
                <Typography variant="body2">{t(`followUp.reason.${c.reason}`)}</Typography>
                {c.skuCode && <Typography variant="body2" color="text.secondary">SKU: {c.skuCode}</Typography>}
                <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                  {c.createdBy} - {new Date(c.createdAt).toLocaleString('ja-JP')}
                </Typography>
              </Stack>
              {c.note && <Typography variant="body2" sx={{ mt: 0.5 }}>{c.note}</Typography>}

              {c.status !== 'CLOSED' && (
                <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
                  <Button size="small" variant="outlined"
                          onClick={() => { setPreviewingCaseId(c.id); previewFollowUpMailMutation.mutate(c.id) }}
                          disabled={previewFollowUpMailMutation.isPending}
                          data-testid={`follow-up-mail-preview-button-${c.id}`}>
                    {t('followUp.previewButton')}
                  </Button>
                  <Button size="small" variant="outlined"
                          onClick={() => { setEditNoteCaseId(c.id); setEditNoteValue(c.note ?? '') }}
                          data-testid={`follow-up-edit-note-button-${c.id}`}>
                    {t('followUp.editNoteButton')}
                  </Button>
                  {isAdmin && (
                    <>
                      <Button size="small" variant="outlined" color="error"
                              onClick={() => closeFollowUpCaseMutation.mutate({ caseId: c.id, request: {} })}
                              disabled={closeFollowUpCaseMutation.isPending}
                              data-testid={`follow-up-close-button-${c.id}`}>
                        {t('followUp.closeButton')}
                      </Button>
                      <Button size="small" variant="outlined"
                              onClick={() => { setReorderDialogCaseId(c.id); setReorderReason('') }}
                              data-testid={`follow-up-reorder-button-${c.id}`}>
                        {t('followUp.reorderButton')}
                      </Button>
                    </>
                  )}
                </Stack>
              )}

              {previewingCaseId === c.id && previewFollowUpMailMutation.data && (
                <Stack spacing={0.5} sx={{ mt: 1 }} data-testid={`follow-up-mail-preview-result-${c.id}`}>
                  {previewFollowUpMailMutation.data.issues.map((issue, i) => (
                    <Alert key={i} severity={issue.severity === 'BLOCKED' ? 'error' : 'warning'}>
                      {t(`mailPreview.issue.${issue.code}`, { defaultValue: issue.code })}
                    </Alert>
                  ))}
                  {previewFollowUpMailMutation.data.subject && (
                    <>
                      <Typography variant="body2">{t('mailPreview.subject')}: <strong>{previewFollowUpMailMutation.data.subject}</strong></Typography>
                      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{previewFollowUpMailMutation.data.body}</Typography>
                    </>
                  )}
                </Stack>
              )}
            </Paper>
          ))}
        </Stack>
      </Paper>

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

      {/* Phase 7-E Section 2 audit: same backdropClick/escapeKeyDown hardening
          as the Supplier Response Confirm Dialog fix, applied here and to
          Return/Official-PO-Request/Reorder below - all four confirm a
          Workflow Status transition or an equivalent one-shot business
          action, the same Dialog class the original bug was found in.
          Follow-up Create and Edit-Note below are left as plain data-entry
          forms (like the Master Maintenance dialogs) - not touched. */}
      <Dialog
        open={approveDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setApproveDialogOpen(false)
        }}
      >
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

      <Dialog
        open={returnDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setReturnDialogOpen(false)
        }}
      >
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

      <Dialog
        open={requestDialogOpen}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setRequestDialogOpen(false)
        }}
      >
        <DialogTitle>{t('officialPoIntegration.requestDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('officialPoIntegration.requestDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setRequestDialogOpen(false)} disabled={requestIntegrationMutation.isPending}>
            {t('officialPoIntegration.requestDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleRequestIntegration}
            disabled={requestIntegrationMutation.isPending}
            data-testid="official-po-request-dialog-confirm"
          >
            {requestIntegrationMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.requestDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={followUpDialogOpen} onClose={() => setFollowUpDialogOpen(false)}>
        <DialogTitle>{t('followUp.createDialogTitle')}</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 1, minWidth: 320 }}>
            <TextField
              select
              label={t('followUp.reasonLabel')}
              value={followUpReason}
              onChange={(e) => setFollowUpReason(e.target.value)}
              data-testid="follow-up-reason-select"
            >
              {FOLLOW_UP_REASONS.map((reason) => (
                <MenuItem key={reason} value={reason}>{t(`followUp.reason.${reason}`)}</MenuItem>
              ))}
            </TextField>
            <TextField
              label={t('followUp.skuLabel')}
              value={followUpSku}
              onChange={(e) => setFollowUpSku(e.target.value)}
              helperText={t('followUp.skuHelperText')}
              data-testid="follow-up-sku-input"
            />
            <TextField
              label={t('followUp.noteLabel')}
              value={followUpNote}
              onChange={(e) => setFollowUpNote(e.target.value)}
              multiline
              minRows={2}
              data-testid="follow-up-note-input"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setFollowUpDialogOpen(false)} disabled={createFollowUpCaseMutation.isPending}>
            {t('followUp.createDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleCreateFollowUpCase}
            disabled={createFollowUpCaseMutation.isPending}
            data-testid="follow-up-create-dialog-confirm"
          >
            {createFollowUpCaseMutation.isPending ? <CircularProgress size={20} /> : t('followUp.createDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog open={editNoteCaseId !== null} onClose={() => setEditNoteCaseId(null)}>
        <DialogTitle>{t('followUp.editNoteDialogTitle')}</DialogTitle>
        <DialogContent>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            sx={{ mt: 1, minWidth: 320 }}
            value={editNoteValue}
            onChange={(e) => setEditNoteValue(e.target.value)}
            data-testid="follow-up-edit-note-input"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setEditNoteCaseId(null)} disabled={updateFollowUpNoteMutation.isPending}>
            {t('followUp.createDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={() => {
              if (editNoteCaseId === null) return
              updateFollowUpNoteMutation.mutate(
                { caseId: editNoteCaseId, request: { note: editNoteValue || null } },
                { onSuccess: () => setEditNoteCaseId(null) },
              )
            }}
            disabled={updateFollowUpNoteMutation.isPending}
            data-testid="follow-up-edit-note-dialog-confirm"
          >
            {updateFollowUpNoteMutation.isPending ? <CircularProgress size={20} /> : t('followUp.editNoteDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      <Dialog
        open={reorderDialogCaseId !== null}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setReorderDialogCaseId(null)
        }}
      >
        <DialogTitle>{t('followUp.reorderDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>{t('followUp.reorderDialogBody')}</DialogContentText>
          <TextField
            autoFocus
            fullWidth
            multiline
            minRows={2}
            label={t('followUp.reorderReasonLabel')}
            value={reorderReason}
            onChange={(e) => setReorderReason(e.target.value)}
            data-testid="follow-up-reorder-reason-input"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReorderDialogCaseId(null)} disabled={createReorderDraftMutation.isPending}>
            {t('followUp.createDialogCancel')}
          </Button>
          <Button
            variant="contained"
            onClick={handleCreateReorderDraft}
            disabled={createReorderDraftMutation.isPending}
            data-testid="follow-up-reorder-dialog-confirm"
          >
            {createReorderDraftMutation.isPending ? <CircularProgress size={20} /> : t('followUp.reorderDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  )
}
