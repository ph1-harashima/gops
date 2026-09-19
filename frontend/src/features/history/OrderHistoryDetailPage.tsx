import { useEffect, useState } from 'react'
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
import Tooltip from '@mui/material/Tooltip'
import IconButton from '@mui/material/IconButton'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import ArrowForwardIcon from '@mui/icons-material/ArrowForward'

import { useOrderHistoryDetail, useOrderEvents, useCompleteEdiInput } from './api'
import {
  useOfficialPoIntegration, useRequestOfficialPoIntegration,
  useConfirmOfficialPoNumber, useGenerateOfficialPoExcel, downloadOfficialPoExcel,
  usePlaceOfficialPoToImportFolder, useConfirmOfficialPoImport,
  useGenerateOfficialPoPdf, downloadOfficialPoPdf,
  useReissueOfficialPo, useOfficialPoRevisionHistory, useRequestCancelOfficialPo, useApproveCancelOfficialPo,
} from './officialPoIntegrationApi'
import { useLegacyPoConcurrency, useCaptureLegacyPoBaseline } from './legacyPoConcurrencyApi'
import { useMailPreview } from './mailPreviewApi'
import { useEmailStatus, useSendEmail } from './emailSendApi'
import { useFulfillment } from './fulfillmentApi'
import {
  useFollowUpCases, useCreateFollowUpCase, useUpdateFollowUpCaseNote,
  useCloseFollowUpCase, usePreviewFollowUpMail, useCreateReorderDraft,
} from './followUpApi'
import { useApprove, useReturnForCorrection } from '../drafts/poPreviewApi'
import { useOrderRevisions, useResponseHistory } from '../supplierResponse/api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { AttentionChips } from '../../shared/components/AttentionChips'
import { Toast } from '../../shared/components/Toast'
import { resolveReturnTo, withBackTo, withReturnTo } from '../../shared/navigation/returnTo'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN } from '../../shared/types/auth'
import type { ApiErrorBody } from '../../shared/types/orderDraft'
import type { OrderHistoryDetail } from '../../shared/types/orderHistory'
import type { OfficialPoIntegration } from '../../shared/types/officialPoIntegration'
import type { OrderEmail } from '../../shared/types/orderEmail'
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
// Phase 7-H (Audit Timeline readability audit): blank means "genuinely no
// value to show" for a Timeline row - both `null` (e.g. a Create event's
// oldValue - nothing existed before) AND `""` (e.g. REMARK_CHANGED clearing
// a Remark to empty text) collapse to the same "not present" case here, so
// neither ever renders as a bare "—" or an empty space next to an Arrow.
function resolveTimelineValue(t: TFunction, fieldName: string | null, value: string | null): string | null {
  if (value === null || value === '') return null
  if (fieldName === 'status') return t(`status:orderStatus.${value}`, { defaultValue: value })
  if (fieldName === 'attentionType') return t(`status:attentionType.${value}`, { defaultValue: value })
  return value
}

/** Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章):
 * parses a comma-separated address input field into a trimmed,
 * non-empty-only array - shared by the To/CC Override fields. */
function splitAddressInput(value: string): string[] {
  return value.split(',').map((s) => s.trim()).filter((s) => s.length > 0)
}

/** BR-04 (docs/gulliver-20260917-confirmed-business-rules.md): domain-only
 * comparison for the "異なるDomainのメールアドレスが含まれる" Warning - case
 * insensitive, never a full-address comparison (the local part legitimately
 * differs address to address). */
function domainOf(email: string): string {
  const at = email.lastIndexOf('@')
  return at === -1 ? email.toLowerCase() : email.slice(at + 1).toLowerCase()
}

/** Addresses (from `actual`) whose Domain does not match ANY of the
 * Master-resolved Domains - never blocks sending (BR-04: "異Domainだから
 * 送信禁止にはしません"), only flags for the final-confirmation Warning. */
function addressesWithUnknownDomain(actual: string[], master: string[]): string[] {
  const masterDomains = new Set(master.map(domainOf))
  return actual.filter((a) => !masterDomains.has(domainOf(a)))
}

function errorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

/** Phase 9-G: "次にすべきこと" - a pure function of already-fetched state
 * (never its own fetch/source of truth). Returns an i18n key under
 * `history:atAGlance.hint.*`, or null when there is nothing actionable
 * right now (either the Order hasn't reached APPROVED yet, or every step
 * this hint tracks is already done). */
function computeNextActionHintKey(
  detail: OrderHistoryDetail,
  integration: OfficialPoIntegration | undefined,
  emailStatus: OrderEmail | undefined,
): string | null {
  if (detail.status !== 'APPROVED') {
    return null
  }
  if (!integration || integration.status === 'NOT_REQUESTED') {
    return 'requestIntegration'
  }
  if (integration.status === 'FAILED') {
    return 'retryPlacement'
  }
  if (integration.status === 'PENDING') {
    // BR-08: the Official PO No. is auto-numbered as soon as G-SYS連携準備
    // creates the Integration Request - there is no longer a distinct
    // "confirm the number" step to hint at.
    return 'generateExcel'
  }

  // From GENERATED onward the Excel exists, so Email Send becomes
  // independently actionable regardless of the G-SYS Import Folder
  // pipeline's own further progress (Send only ever requires the Excel,
  // never a completed Handoff - EmailSendService's own Gate) - prioritized
  // ahead of the Import Folder step below since notifying the manufacturer
  // is typically the more time-sensitive of the two.
  if (detail.resolvedManufacturerChannel === 'EMAIL' && emailStatus?.status !== 'SENT') {
    return 'sendEmail'
  }
  if (detail.resolvedManufacturerChannel === 'EDI' && detail.communicationChannel === 'EDI' && detail.ediStatus !== 'COMPLETED') {
    return 'completeEdiInput'
  }

  if (integration.status === 'GENERATED') {
    return 'placeToImportFolder'
  }
  if (integration.status === 'SUBMITTED') {
    return 'confirmImport'
  }
  return null // CONFIRMED, and Email/EDI (if applicable) already done too.
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
  const theme = useTheme()
  // Mobile Responsive Audit (最重要 - Approval happens on THIS screen once
  // status is PENDING_APPROVAL): the 12-column SKU line table is the
  // densest table in the app - a Horizontal-Scroll Table (MUI TableContainer's
  // default) would technically avoid page overflow but forces sideways
  // scrolling to compare Recommended Qty against Current Stock/Sales/Lead
  // Time, defeating "judge what you're approving on a phone". A Card per
  // SKU (all fields stacked, none hidden) replaces it below `md`; the
  // Approve/Return/Edit action bar becomes sticky so it can never scroll
  // off-screen while reviewing a long line list.
  const isCardLayout = useMediaQuery(theme.breakpoints.down('md'))
  // Phase 6-C (docs/production-ux-workflow-redesign.md 2章/5章): 発注詳細 is
  // the shared landing point after both Demo Send and Supplier Response
  // Confirm now (neither auto-opens the next screen anymore), so it shows
  // whichever one-shot success Message the previous screen handed off via
  // Router state - read once per navigation, not persisted, so a manual
  // reload of this URL correctly shows neither.
  const demoSendSuccess = Boolean((location.state as { demoSendSuccess?: boolean } | null)?.demoSendSuccess)
  // Phase 7-H (EDI発注Workflow Foundation): same one-shot handoff idiom as
  // demoSendSuccess above, for the EDI Send path (PoPreviewPage.handleEdiSend).
  const ediSendSuccess = Boolean((location.state as { ediSendSuccess?: boolean } | null)?.ediSendSuccess)
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
  // Phase 8-M (Global Navigation Audit, Principle D): this screen's own
  // path (with its own returnTo preserved) becomes the returnTo the
  // Fulfillment section's Arrival List link carries, so Arrival List's
  // conditional Back button can return to this Order Detail specifically.
  const ownPath = withReturnTo(`/orders/${orderId}`, returnTo)

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
  const confirmPoNumberMutation = useConfirmOfficialPoNumber(orderId)
  const generateExcelMutation = useGenerateOfficialPoExcel(orderId)
  const generatePdfMutation = useGenerateOfficialPoPdf(orderId)
  const reissueMutation = useReissueOfficialPo(orderId)
  const cancelMutation = useRequestCancelOfficialPo(orderId)
  const approveCancelMutation = useApproveCancelOfficialPo(orderId)
  const { data: revisionHistory } = useOfficialPoRevisionHistory(orderId)
  const placeMutation = usePlaceOfficialPoToImportFolder(orderId)
  const confirmImportMutation = useConfirmOfficialPoImport(orderId)
  const completeEdiInputMutation = useCompleteEdiInput(orderId)
  const captureBaselineMutation = useCaptureLegacyPoBaseline(orderId)
  const mailPreviewMutation = useMailPreview(orderId)
  // Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md 10章):
  // Email To/CC Override - editable fields the user may change before
  // Send, seeded once from the Master-resolved Preview values. "Manually
  // edited" tracks whether the current field content should be sent as an
  // Override, so a Send with untouched fields is never wrongly flagged as
  // an Override at the Backend (EmailSendService's own overrideUsed
  // semantics: null/empty means "no Override").
  const [toOverrideInput, setToOverrideInput] = useState('')
  const [ccOverrideInput, setCcOverrideInput] = useState('')
  const [toManuallyEdited, setToManuallyEdited] = useState(false)
  const [ccManuallyEdited, setCcManuallyEdited] = useState(false)
  // BR-04 (docs/gulliver-20260917-confirmed-business-rules.md): a final
  // send-time confirmation is mandatory - Send no longer fires directly off
  // the button click, it only opens this Dialog.
  const [sendConfirmDialogOpen, setSendConfirmDialogOpen] = useState(false)
  const [recipientFormSeeded, setRecipientFormSeeded] = useState(false)
  const { data: emailStatus } = useEmailStatus(orderId)
  const sendEmailMutation = useSendEmail(orderId)
  const createFollowUpCaseMutation = useCreateFollowUpCase(orderId)
  const updateFollowUpNoteMutation = useUpdateFollowUpCaseNote(orderId)
  const closeFollowUpCaseMutation = useCloseFollowUpCase(orderId)
  const previewFollowUpMailMutation = usePreviewFollowUpMail(orderId)
  const createReorderDraftMutation = useCreateReorderDraft()
  const [approveDialogOpen, setApproveDialogOpen] = useState(false)
  const [returnDialogOpen, setReturnDialogOpen] = useState(false)
  const [returnReason, setReturnReason] = useState('')
  const [requestDialogOpen, setRequestDialogOpen] = useState(false)
  const [reissueDialogOpen, setReissueDialogOpen] = useState(false)
  const [cancelDialogOpen, setCancelDialogOpen] = useState(false)
  const [cancelReason, setCancelReason] = useState('')
  // Phase 9-A: PO Number confirm form - seeded once from the Integration
  // Request the first time it loads (a staff-entered form, not a live
  // server-value display like the rest of this READ ONLY page).
  const [deliveryWeekInput, setDeliveryWeekInput] = useState('')
  const [deliveryDateInput, setDeliveryDateInput] = useState('')
  const [shipViaInput, setShipViaInput] = useState('')
  const [shipTermInput, setShipTermInput] = useState('')
  const [paymentTermInput, setPaymentTermInput] = useState('')
  const [poNoFormSeeded, setPoNoFormSeeded] = useState(false)
  const [downloadError, setDownloadError] = useState(false)
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

  function handleReissue() {
    reissueMutation.mutate(undefined, { onSuccess: () => setReissueDialogOpen(false) })
  }

  function handleCancel() {
    cancelMutation.mutate(cancelReason, {
      onSuccess: () => {
        setCancelDialogOpen(false)
        setCancelReason('')
      },
    })
  }

  function handleApproveCancel() {
    approveCancelMutation.mutate()
  }

  function handleConfirmSendEmail() {
    sendEmailMutation.mutate({
      to: toManuallyEdited ? splitAddressInput(toOverrideInput) : undefined,
      cc: ccManuallyEdited ? splitAddressInput(ccOverrideInput) : undefined,
    }, { onSuccess: () => setSendConfirmDialogOpen(false) })
  }

  function handleCaptureBaseline() {
    captureBaselineMutation.mutate()
  }

  // Phase 9-A: seed the PO Number form once from whatever the Integration
  // Request already carries (a correction re-confirm, or a page reload
  // while still PENDING/GENERATED) - never overwrites what staff is mid-way
  // through typing.
  useEffect(() => {
    if (integration && !poNoFormSeeded && integration.status !== 'NOT_REQUESTED') {
      setDeliveryWeekInput(integration.deliveryWeek ?? '')
      setDeliveryDateInput(integration.deliveryDate ?? '')
      setShipViaInput(integration.shipVia ?? '')
      setShipTermInput(integration.shipTerm ?? '')
      setPaymentTermInput(integration.paymentTerm ?? '')
      setPoNoFormSeeded(true)
    }
  }, [integration, poNoFormSeeded])

  // Gap Analysis C-5: seed the To/CC Override fields once from the first
  // successful Preview's Master-resolved addresses - never overwrites what
  // staff is mid-way editing on a later re-Preview.
  useEffect(() => {
    if (mailPreviewMutation.data && !recipientFormSeeded) {
      setToOverrideInput(mailPreviewMutation.data.to.join(', '))
      setCcOverrideInput(mailPreviewMutation.data.cc.join(', '))
      setRecipientFormSeeded(true)
    }
  }, [mailPreviewMutation.data, recipientFormSeeded])

  function handleConfirmPoNumber() {
    confirmPoNumberMutation.mutate({
      deliveryWeek: deliveryWeekInput.trim() || null,
      deliveryDate: deliveryDateInput.trim() || null,
      shipVia: shipViaInput.trim() || null,
      shipTerm: shipTermInput.trim() || null,
      paymentTerm: paymentTermInput.trim() || null,
    })
  }

  function handleGenerateExcel() {
    generateExcelMutation.mutate()
  }

  function handlePlaceToImportFolder() {
    placeMutation.mutate()
  }

  function handleConfirmImport() {
    confirmImportMutation.mutate()
  }

  function handleDownloadExcel() {
    setDownloadError(false)
    void downloadOfficialPoExcel(orderId).catch(() => setDownloadError(true))
  }

  function handleGeneratePdf() {
    generatePdfMutation.mutate()
  }

  function handleDownloadPdf() {
    setDownloadError(false)
    void downloadOfficialPoPdf(orderId).catch(() => setDownloadError(true))
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
        // Phase 7-H (PO Preview Navigation audit): PoPreviewPage is reached
        // from both here and Order Draft's own "PO プレビュー" button, and
        // previously had no way to tell them apart (its 戻る button always
        // hardcoded the Draft path) - backTo carries THIS screen's own path
        // so Preview's 戻る button returns here instead, while returnTo
        // (attached below, same as every other case) keeps carrying the
        // deeper List chain unchanged.
        return { label: t('goToPreview'), to: withBackTo(`/orders/drafts/${detail.id}/preview`, `/orders/${detail.id}`) }
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

  // Phase 9-G (UI整理 - Production PO Workflow §8): a single computed "次に
  // すべきこと" hint, derived from the same state combination the individual
  // Sections below already display piecemeal - never a new source of truth,
  // purely a read of detail/integration/emailStatus already fetched above.
  // ADMIN-scoped (every step it names is an ADMIN-only Action) - OPERATOR
  // sees no hint here (the Sections themselves already explain why a
  // Button is missing/disabled for them).
  const nextActionHintKey = isAdmin ? computeNextActionHintKey(detail, integration, emailStatus) : null

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={() => navigate(backTarget)}>{t('backToList')}</Button>
        <Typography variant="h5" component="h1">
          {t('detailTitle')} - {detail.prototypePoNo ?? detail.draftNo}
        </Typography>
        {/* Acceptance Fix C-5: the header number (Prototype PO No., e.g.
            PO-DEMO-...) and the G-SYS正式PO連携 section's "正式PO番号" are two
            intentionally distinct identifiers (PrototypePoNoGenerator's own
            Javadoc: "unmistakably distinct from Legacy PO Number... never
            intended to resemble Legacy's own format") - this Chip makes that
            explicit at the one place a user is most likely to mistake the
            header number for the real PO No. */}
        <Tooltip title={t('portalPoNoCaptionTooltip')}>
          <Chip size="small" variant="outlined" label={t('portalPoNoCaption')} data-testid="portal-po-no-caption" />
        </Tooltip>
        <OrderStatusChip status={detail.status} />
        {/* Phase 7-H (EDI発注Workflow Foundation): shown here, not on PO
            Preview (unreachable once past APPROVED - PoPreviewService's own
            pre-existing Status Gate) - Order Detail is the one screen
            reachable for every Status, Send included. */}
        {detail.communicationChannel && (
          <Chip size="small" variant="outlined" label={t(`communicationChannel.${detail.communicationChannel}`)} data-testid="communication-channel-chip" />
        )}
        {/* Phase 9-D: "what SHOULD happen" per the Manufacturer Channel
            Master, shown only before any Send has recorded "what actually
            happened" (communicationChannel above) - avoids showing two
            possibly-conflicting Channel Chips side by side once a real Send
            has already occurred. */}
        {!detail.communicationChannel && detail.resolvedManufacturerChannel && (
          <Chip
            size="small"
            variant="outlined"
            color="info"
            label={t(`officialPoIntegration.resolvedChannelChip.${detail.resolvedManufacturerChannel}`)}
            data-testid="resolved-manufacturer-channel-chip"
          />
        )}
        {/* Gap Analysis §12 (Domestic/Overseas Foundation): display-only Chip
            resolved from the new Portal-only Supplier Region Classification
            Master - never consulted by Recommended Qty, purely informational. */}
        {detail.resolvedRegionClassification && (
          <Chip
            size="small"
            variant="outlined"
            label={t(`supplierRegionClassification:regionClassification.${detail.resolvedRegionClassification}`)}
            data-testid="resolved-region-classification-chip"
          />
        )}
        <AttentionChips attentions={detail.orderAttentions} acknowledgeable />
      </Stack>

      {/* Phase 9-G: "at a glance" strip - composes state the Sections below
          already carry (never a second source of truth), so ADMIN can see
          the whole G-SYS/Email/EDI picture without scrolling through every
          Section. Only shown once there is something to summarize
          (APPROVED or later). */}
      {(detail.status === 'APPROVED' || (integration && integration.status !== 'NOT_REQUESTED')) && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }} data-testid="at-a-glance-panel">
          <Stack direction="row" spacing={3} sx={{ flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
            <Typography variant="body2">
              {t('atAGlance.officialPoNoLabel')}: <strong data-testid="at-a-glance-official-po-no">{integration?.officialPoNo ?? t('atAGlance.unset')}</strong>
            </Typography>
            <Typography variant="body2">
              {t('atAGlance.integrationStatusLabel')}: <strong>{t(`officialPoIntegration.statusLabel.${integration?.status ?? 'NOT_REQUESTED'}`)}</strong>
            </Typography>
            <Typography variant="body2">
              {t('atAGlance.excelLabel')}: <strong>{integration?.excelGenerated ? t('atAGlance.done') : t('atAGlance.notYet')}</strong>
            </Typography>
            <Typography variant="body2">
              {t('atAGlance.channelLabel')}: <strong>{detail.resolvedManufacturerChannel ? t(`communicationChannelValue.${detail.resolvedManufacturerChannel}`) : t('atAGlance.unresolved')}</strong>
            </Typography>
            {detail.resolvedManufacturerChannel === 'EMAIL' && (
              <Typography variant="body2">
                {t('atAGlance.emailLabel')}: <strong>{emailStatus?.status === 'SENT' ? t('atAGlance.done') : emailStatus?.status === 'FAILED' ? t('atAGlance.failed') : t('atAGlance.notYet')}</strong>
              </Typography>
            )}
            {detail.resolvedManufacturerChannel === 'EDI' && detail.communicationChannel === 'EDI' && (
              <Typography variant="body2">
                {t('atAGlance.ediLabel')}: <strong>{t(`ediStatus.status.${detail.ediStatus ?? 'WAITING_INPUT'}`)}</strong>
              </Typography>
            )}
          </Stack>
          {nextActionHintKey && (
            <Alert severity="info" sx={{ mt: 1.5 }} data-testid="next-action-hint">
              {t(`atAGlance.hint.${nextActionHintKey}`)}
            </Alert>
          )}
          {/* Gap Analysis C-3 (docs/gulliver-20260917-phase1-gap-analysis.md
              8章): "自動検知＋人間による再発行" - detected here (Backend's
              isReissueRequired, shared with the Reissue Action's own Gate),
              but NEVER auto-reissued - ADMIN must explicitly click Reissue
              in the Official PO Section below. */}
          {integration?.reissueRequired && (
            <Alert severity="warning" sx={{ mt: 1.5 }} data-testid="reissue-required-banner">
              {t('atAGlance.reissueRequiredMessage')}
            </Alert>
          )}
        </Paper>
      )}

      {/* Phase 7-I (Layout Shift audit): every one of these is a one-shot
          event Message (a just-completed navigation handoff, or a mutation
          result) - none is a standing fact about the Order (those stay
          inline below: pendingApprovalIndicator, official-po-no-unassigned-
          note, fulfillment/concurrency status panels, etc.). Moved to the
          shared Toast so a Save/Approve/Return click doesn't reflow this
          screen's own Action buttons and Fulfillment/Follow-up sections
          beneath it. */}
      <Toast open={demoSendSuccess} severity="success" message={t('demoSendSuccessMessage')} />
      <Toast open={ediSendSuccess} severity="success" message={t('ediSendSuccessMessage')} />
      <Toast open={supplierResponseConfirmSuccess} severity="success" message={t('supplierResponseConfirmSuccessMessage')} />
      <Toast open={approveMutation.isSuccess} severity="success" message={t('approveSuccess')} onClose={() => approveMutation.reset()} />
      <Toast
        open={approveMutation.isError}
        severity="error"
        message={errorCodeOf(approveMutation.error) === 'FORBIDDEN' ? t('errorForbidden') : t('errorGeneric')}
        onClose={() => approveMutation.reset()}
      />
      <Toast open={returnMutation.isSuccess} severity="success" message={t('returnSuccess')} onClose={() => returnMutation.reset()} />
      <Toast
        open={returnMutation.isError}
        severity="error"
        message={
          errorCodeOf(returnMutation.error) === 'RETURN_REASON_REQUIRED' ? t('errorReturnReasonRequired') :
          errorCodeOf(returnMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
          t('errorGeneric')
        }
        onClose={() => returnMutation.reset()}
      />

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

      {/* Phase 9-D: EDI status tracker - only meaningful once this Order was
          actually sent over EDI (communicationChannel === 'EDI'); "入力待ち"
          starts automatically at that Send, "入力完了" is this Section's own
          explicit Action. Never shows a "Send Email" Button for an
          EDI-channel Order (§6's requirement) - that Button lives only in
          the Mail Preview Section below, and only when the resolved Channel
          is EMAIL. */}
      {detail.communicationChannel === 'EDI' && (
        <Paper variant="outlined" sx={{ p: 2, mb: 2 }} data-testid="edi-status-section">
          <Typography variant="subtitle1" gutterBottom>{t('ediStatus.title')}</Typography>
          <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
            <Chip
              size="small"
              color={detail.ediStatus === 'COMPLETED' ? 'success' : 'warning'}
              label={t(`ediStatus.status.${detail.ediStatus ?? 'WAITING_INPUT'}`)}
              data-testid="edi-status-chip"
            />
            {detail.ediStatus === 'COMPLETED' && detail.ediCompletedAt && (
              <Typography variant="caption" color="text.secondary">
                {t('ediStatus.completedByLabel')}: {detail.ediCompletedByDisplayName ?? detail.ediCompletedBy} - {t('ediStatus.completedAtLabel')}: {new Date(detail.ediCompletedAt).toLocaleString('ja-JP')}
              </Typography>
            )}
          </Stack>

          <Toast open={completeEdiInputMutation.isSuccess} severity="success" message={t('ediStatus.completeSuccess')} onClose={() => completeEdiInputMutation.reset()} />
          <Toast
            open={completeEdiInputMutation.isError}
            severity="error"
            testId="edi-complete-error"
            message={t('errorGeneric')}
            onClose={() => completeEdiInputMutation.reset()}
          />

          {detail.ediStatus !== 'COMPLETED' && (
            <Button
              variant="outlined"
              sx={{ mt: 2 }}
              onClick={() => completeEdiInputMutation.mutate()}
              disabled={completeEdiInputMutation.isPending}
              data-testid="edi-complete-button"
            >
              {completeEdiInputMutation.isPending ? <CircularProgress size={20} /> : t('ediStatus.completeButton')}
            </Button>
          )}
        </Paper>
      )}

      {isCardLayout ? (
        <Stack spacing={1.5} data-testid="order-detail-line-cards">
          {detail.details.map((line) => (
            <Card key={line.sku} variant="outlined">
              <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', rowGap: 0.5 }}>
                  <Button
                    size="small"
                    variant="text"
                    sx={{ p: 0, minWidth: 0, textTransform: 'none', fontWeight: 600 }}
                    onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(line.sku)}`, ownPath))}
                    data-testid={`sku-detail-link-${line.sku}`}
                  >
                    {line.sku}
                  </Button>
                  <AttentionChips attentions={line.attentions} acknowledgeable />
                </Stack>
                <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{line.itemName}</Typography>
                <Divider sx={{ mb: 1 }} />
                <Stack spacing={0.75}>
                  {[
                    { label: t('detailTable.recommendedQty'), value: line.recommendedQty },
                    { label: t('detailTable.orderedQty'), value: line.orderedQty },
                    { label: t('detailTable.confirmedQty'), value: line.confirmedQty ?? t('notAvailable') },
                    { label: t('detailTable.requestedDelivery'), value: line.requestedDelivery ?? t('notAvailable') },
                    { label: t('detailTable.confirmedDelivery'), value: line.confirmedDelivery ?? t('notAvailable') },
                    { label: t('detailTable.currentStock'), value: line.currentStock ?? t('notAvailable') },
                    { label: t('detailTable.monthlySales'), value: line.monthlySales ?? t('notAvailable') },
                    { label: t('detailTable.leadTime'), value: line.leadTime ?? t('notAvailable') },
                    { label: t('detailTable.openArrival'), value: line.openArrival ?? t('notAvailable') },
                  ].map((row) => (
                    <Stack key={row.label} direction="row" sx={{ justifyContent: 'space-between' }}>
                      <Typography variant="body2" color="text.secondary">{row.label}</Typography>
                      <Typography variant="body2" sx={{ fontWeight: 500 }}>{row.value}</Typography>
                    </Stack>
                  ))}
                </Stack>
              </CardContent>
            </Card>
          ))}
        </Stack>
      ) : (
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
              {/* Gap Analysis B-1 (docs/gulliver-20260917-phase1-gap-analysis.md
                  5章): the same judgement material the person who placed the
                  order already saw on Candidate List/Draft - live Legacy READ
                  ONLY values, not a new calculation. Appended AFTER the
                  pre-existing columns (never inserted earlier) so no existing
                  column's position shifts - existing E2E (e.g.
                  fulfillment-follow-up-foundation.spec.ts) locates cells by
                  fixed nth() index against this same table. */}
              <TableCell align="right">{t('detailTable.currentStock')}</TableCell>
              <TableCell align="right">{t('detailTable.monthlySales')}</TableCell>
              <TableCell>{t('detailTable.leadTime')}</TableCell>
              <TableCell align="right">{t('detailTable.openArrival')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {detail.details.map((line) => (
              <TableRow key={line.sku} hover>
                <TableCell>
                  {/* Gap Analysis B-2: link to SKU Detail (Arrival予定 and
                      full Stock/Sales history live there), carrying this
                      screen's own path via returnTo - same idiom as
                      Candidate List/Warehouse Stock/Stock-Sales already use
                      (Phase 8-M Principle D), so SKU Detail's 戻る returns
                      here specifically. */}
                  <Button
                    size="small"
                    variant="text"
                    sx={{ p: 0, minWidth: 0, textTransform: 'none' }}
                    onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(line.sku)}`, ownPath))}
                    data-testid={`sku-detail-link-${line.sku}`}
                  >
                    {line.sku}
                  </Button>
                </TableCell>
                <TableCell>{line.itemName}</TableCell>
                <TableCell align="right">{line.recommendedQty}</TableCell>
                <TableCell align="right">{line.orderedQty}</TableCell>
                <TableCell align="right">{line.confirmedQty ?? t('notAvailable')}</TableCell>
                <TableCell>{line.requestedDelivery ?? t('notAvailable')}</TableCell>
                <TableCell>{line.confirmedDelivery ?? t('notAvailable')}</TableCell>
                <TableCell><AttentionChips attentions={line.attentions} acknowledgeable /></TableCell>
                <TableCell align="right">{line.currentStock ?? t('notAvailable')}</TableCell>
                <TableCell align="right">{line.monthlySales ?? t('notAvailable')}</TableCell>
                <TableCell>{line.leadTime ?? t('notAvailable')}</TableCell>
                <TableCell align="right">{line.openArrival ?? t('notAvailable')}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      )}

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
        // Mobile Responsive Audit 最重要: sticky so Approve/Return stay
        // reachable without hunting for them after scrolling through a long
        // SKU line list - never fixed-px, just `position: sticky` against
        // this screen's own scrolling ancestor (the same Box in App.tsx that
        // already hosts every routed page's scroll region).
        <Stack
          direction="row"
          spacing={2}
          sx={{
            mt: 2,
            ...(isCardLayout && {
              position: 'sticky',
              bottom: 0,
              py: 1.5,
              bgcolor: 'background.paper',
              borderTop: 1,
              borderColor: 'divider',
              zIndex: 1,
            }),
          }}
        >
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

          <Toast open={requestIntegrationMutation.isSuccess} severity="success" message={t('officialPoIntegration.requestSuccess')} onClose={() => requestIntegrationMutation.reset()} />
          <Toast
            open={requestIntegrationMutation.isError}
            severity="error"
            message={
              errorCodeOf(requestIntegrationMutation.error) === 'ORDER_NOT_APPROVED' ? t('officialPoIntegration.errorNotApproved') :
              errorCodeOf(requestIntegrationMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
              t('errorGeneric')
            }
            onClose={() => requestIntegrationMutation.reset()}
          />

          {integration && (
            <Stack spacing={1} sx={{ mb: 2 }}>
              <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1, alignItems: 'center' }}>
                <Typography variant="body2" data-testid="official-po-status-label">
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
                  {t('officialPoIntegration.requestedByLabel')}: {integration.requestedByDisplayName ?? integration.requestedBy} - {t('officialPoIntegration.requestedAtLabel')}: {new Date(integration.requestedAt).toLocaleString('ja-JP')}
                </Typography>
              )}
              {integration.preflight && (
                <Box data-testid="preflight-result">
                  <Typography component="div" variant="body2" sx={{ fontWeight: 'bold', mt: 1 }}>
                    {t('officialPoIntegration.lastResultLabel')}:{' '}
                    <Chip
                      size="small"
                      label={t('officialPoIntegration.preflightResult.' + integration.preflight.result)}
                      color={integration.preflight.result === 'BLOCKED' ? 'error'
                        : integration.preflight.result === 'WARNING' ? 'warning' : 'success'}
                    />
                  </Typography>
                  <Stack spacing={0.5} sx={{ mt: 1 }}>
                    {integration.preflight.issues
                      // Gulliver UI最終仕上げ #1: OFFICIAL_PO_NO_NOT_ASSIGNEDはPreflight実行時点
                      // (正式PO番号確定より前)のスナップショットなので、確定後もそのまま表示すると
                      // 画面上部の正式PO番号表示と矛盾する。確定済みならこの注記だけ非表示にする
                      // (他のissueや最終チェック結果Chip自体は従来通り表示 - 表示条件のみの変更)。
                      .filter((issue) => !(issue.code === 'OFFICIAL_PO_NO_NOT_ASSIGNED' && integration.officialPoNo))
                      .map((issue, i) => (
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

          {/* Phase 9-A: PO Number confirm + Excel generation - extends this
              same Section (7-C6's own precedent) rather than adding a new
              one. Editable while PENDING/GENERATED; locked (read-only
              display) once SUBMITTED/CONFIRMED/FAILED. */}
          {integration && integration.status !== 'NOT_REQUESTED' && isAdmin && (
            <Box sx={{ mt: 2 }} data-testid="official-po-number-form">
              <Divider sx={{ mb: 2 }} />
              <Typography variant="subtitle2" gutterBottom>{t('officialPoIntegration.numberFormTitle')}</Typography>

              <Toast
                open={confirmPoNumberMutation.isSuccess}
                severity="success"
                message={t('officialPoIntegration.confirmSuccess')}
                onClose={() => confirmPoNumberMutation.reset()}
              />
              <Toast
                open={confirmPoNumberMutation.isError}
                severity="error"
                testId="official-po-number-confirm-error"
                message={
                  errorCodeOf(confirmPoNumberMutation.error) === 'INVALID_OFFICIAL_PO_NUMBER' ? t('officialPoIntegration.errorInvalidNumber') :
                  errorCodeOf(confirmPoNumberMutation.error) === 'DUPLICATE_OFFICIAL_PO_NUMBER' ? t('officialPoIntegration.errorDuplicateNumber') :
                  errorCodeOf(confirmPoNumberMutation.error) === 'OFFICIAL_PO_ALREADY_SUBMITTED' ? t('officialPoIntegration.errorAlreadySubmitted') :
                  errorCodeOf(confirmPoNumberMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
                  t('errorGeneric')
                }
                onClose={() => confirmPoNumberMutation.reset()}
              />

              {(() => {
                const locked = !['PENDING', 'GENERATED'].includes(integration.status)
                return (
                  <Stack spacing={2}>
                    {locked && (
                      <Alert severity="info" data-testid="official-po-number-locked-note">
                        {t('officialPoIntegration.numberLockedNote')}
                      </Alert>
                    )}
                    <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap', rowGap: 2 }}>
                      <TextField
                        label={t('officialPoIntegration.deliveryWeekLabel')}
                        value={deliveryWeekInput}
                        onChange={(e) => setDeliveryWeekInput(e.target.value)}
                        disabled={locked}
                        slotProps={{ htmlInput: { maxLength: 5 } }}
                        data-testid="official-po-delivery-week-input"
                        size="small"
                      />
                      <TextField
                        label={t('officialPoIntegration.deliveryDateLabel')}
                        value={deliveryDateInput}
                        onChange={(e) => setDeliveryDateInput(e.target.value)}
                        disabled={locked}
                        data-testid="official-po-delivery-date-input"
                        size="small"
                      />
                      <TextField
                        label={t('officialPoIntegration.shipViaLabel')}
                        value={shipViaInput}
                        onChange={(e) => setShipViaInput(e.target.value)}
                        disabled={locked}
                        data-testid="official-po-ship-via-input"
                        size="small"
                      />
                      <TextField
                        label={t('officialPoIntegration.shipTermLabel')}
                        value={shipTermInput}
                        onChange={(e) => setShipTermInput(e.target.value)}
                        disabled={locked}
                        data-testid="official-po-ship-term-input"
                        size="small"
                      />
                      <TextField
                        label={t('officialPoIntegration.paymentTermLabel')}
                        value={paymentTermInput}
                        onChange={(e) => setPaymentTermInput(e.target.value)}
                        disabled={locked}
                        data-testid="official-po-payment-term-input"
                        size="small"
                      />
                    </Stack>
                    {!locked && (
                      <Box>
                        <Button
                          variant="outlined"
                          onClick={handleConfirmPoNumber}
                          disabled={confirmPoNumberMutation.isPending}
                          data-testid="official-po-number-confirm-button"
                        >
                          {confirmPoNumberMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.confirmButton')}
                        </Button>
                      </Box>
                    )}
                  </Stack>
                )
              })()}

              <Divider sx={{ my: 2 }} />
              <Typography variant="subtitle2" gutterBottom>{t('officialPoIntegration.excelSectionTitle')}</Typography>

              <Toast
                open={generateExcelMutation.isSuccess}
                severity="success"
                message={t('officialPoIntegration.generateSuccess')}
                onClose={() => generateExcelMutation.reset()}
              />
              <Toast
                open={generateExcelMutation.isError}
                severity="error"
                testId="official-po-generate-error"
                message={
                  errorCodeOf(generateExcelMutation.error) === 'OFFICIAL_PO_NUMBER_REQUIRED' ? t('officialPoIntegration.errorNumberRequired') :
                  errorCodeOf(generateExcelMutation.error) === 'OFFICIAL_PO_PREFLIGHT_BLOCKED' ? t('officialPoIntegration.errorPreflightBlocked') :
                  errorCodeOf(generateExcelMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
                  t('errorGeneric')
                }
                onClose={() => generateExcelMutation.reset()}
              />
              <Toast open={downloadError} severity="error" message={t('errorGeneric')} onClose={() => setDownloadError(false)} />

              <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
                <Button
                  variant="outlined"
                  onClick={handleGenerateExcel}
                  disabled={generateExcelMutation.isPending || !integration.officialPoNo}
                  data-testid="official-po-generate-button"
                >
                  {generateExcelMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.generateButton')}
                </Button>
                {integration.excelGenerated && (
                  <Button variant="text" onClick={handleDownloadExcel} data-testid="official-po-download-button">
                    {t('officialPoIntegration.downloadExcelButton')}
                  </Button>
                )}
              </Stack>

              {/* Gap Analysis C-1 (docs/gulliver-20260917-phase1-gap-analysis.md
                  7章): "G-OPS Standard Official PO PDF" - Gate mirrors Excel
                  (needs a confirmed PO No.) but is otherwise independent of
                  the Excel/Import Folder Integration Status above. */}
              <Divider sx={{ my: 2 }} />
              <Typography variant="subtitle2" gutterBottom>{t('officialPoIntegration.pdfSectionTitle')}</Typography>
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 1 }}>
                {t('officialPoIntegration.pdfSectionNote')}
              </Typography>

              <Toast
                open={generatePdfMutation.isSuccess}
                severity="success"
                message={t('officialPoIntegration.pdfGenerateSuccess')}
                onClose={() => generatePdfMutation.reset()}
              />
              <Toast
                open={generatePdfMutation.isError}
                severity="error"
                testId="official-po-pdf-generate-error"
                message={
                  errorCodeOf(generatePdfMutation.error) === 'OFFICIAL_PO_NUMBER_REQUIRED' ? t('officialPoIntegration.errorNumberRequired') :
                  errorCodeOf(generatePdfMutation.error) === 'OFFICIAL_PO_PREFLIGHT_BLOCKED' ? t('officialPoIntegration.errorPreflightBlocked') :
                  errorCodeOf(generatePdfMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
                  t('errorGeneric')
                }
                onClose={() => generatePdfMutation.reset()}
              />

              <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 1 }}>
                <Button
                  variant="outlined"
                  onClick={handleGeneratePdf}
                  disabled={generatePdfMutation.isPending || !integration.officialPoNo}
                  data-testid="official-po-pdf-generate-button"
                >
                  {generatePdfMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.pdfGenerateButton')}
                </Button>
                {integration.pdfGenerated && (
                  <Button variant="text" onClick={handleDownloadPdf} data-testid="official-po-pdf-download-button">
                    {t('officialPoIntegration.downloadPdfButton')}
                  </Button>
                )}
              </Stack>

              {/* Phase 9-B: Import Folder Integration - extends this same
                  Excel Section (design doc §7's "Fileを置くだけ"). */}
              {integration.excelGenerated && (
                <Box sx={{ mt: 2 }}>
                  <Divider sx={{ mb: 2 }} />
                  <Typography variant="subtitle2" gutterBottom>{t('officialPoIntegration.placeSectionTitle')}</Typography>

                  <Toast
                    open={placeMutation.isSuccess}
                    severity="success"
                    message={t('officialPoIntegration.placeSuccess')}
                    onClose={() => placeMutation.reset()}
                  />
                  <Toast
                    open={placeMutation.isError}
                    severity="error"
                    testId="official-po-place-error"
                    message={
                      errorCodeOf(placeMutation.error) === 'OFFICIAL_PO_NOT_GENERATED' ? t('officialPoIntegration.errorNotGenerated') :
                      errorCodeOf(placeMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
                      t('errorGeneric')
                    }
                    onClose={() => placeMutation.reset()}
                  />

                  {integration.status === 'FAILED' && (
                    <Alert severity="error" sx={{ mb: 2 }} data-testid="official-po-place-failed-note">
                      {t('officialPoIntegration.placeFailedNote')}
                      {integration.errorMessage ? `: ${integration.errorMessage}` : ''}
                    </Alert>
                  )}
                  {(integration.status === 'SUBMITTED' || integration.status === 'CONFIRMED') && (
                    <Alert severity="success" sx={{ mb: 2 }} data-testid="official-po-placed-note">
                      {t('officialPoIntegration.placedNote')}
                    </Alert>
                  )}

                  <Button
                    variant="outlined"
                    onClick={handlePlaceToImportFolder}
                    disabled={placeMutation.isPending || integration.status === 'SUBMITTED' || integration.status === 'CONFIRMED'}
                    data-testid="official-po-place-button"
                  >
                    {placeMutation.isPending ? <CircularProgress size={20} /> :
                      integration.status === 'FAILED' ? t('officialPoIntegration.retryPlaceButton') :
                      t('officialPoIntegration.placeButton')}
                  </Button>

                  {/* Phase 9-C: G-SYS Import Confirmation - manual, on-demand
                      Legacy READ ONLY check (design doc §9's Success
                      Detection, no background poller). */}
                  {(integration.status === 'SUBMITTED' || integration.status === 'CONFIRMED') && (
                    <Box sx={{ mt: 2 }}>
                      <Divider sx={{ mb: 2 }} />
                      <Typography variant="subtitle2" gutterBottom>{t('officialPoIntegration.confirmImportSectionTitle')}</Typography>

                      {confirmImportMutation.data && !confirmImportMutation.data.matched && (
                        <Alert
                          severity={confirmImportMutation.data.reason === 'MISMATCH' ? 'warning' : 'info'}
                          sx={{ mb: 2 }}
                          data-testid="official-po-import-not-matched"
                        >
                          {t(`officialPoIntegration.importConfirmReason.${confirmImportMutation.data.reason}`)}
                          {confirmImportMutation.data.details.length > 0 && (
                            <Box component="ul" sx={{ mt: 1, mb: 0 }}>
                              {confirmImportMutation.data.details.map((d, i) => (
                                <li key={i}>
                                  {d.skuCode}: {t('officialPoIntegration.expectedQtyLabel')} {d.expectedQty ?? '-'} /{' '}
                                  {t('officialPoIntegration.actualQtyLabel')} {d.actualQty ?? '-'}
                                </li>
                              ))}
                            </Box>
                          )}
                        </Alert>
                      )}
                      {confirmImportMutation.data?.matched && (
                        <Alert severity="success" sx={{ mb: 2 }} data-testid="official-po-import-confirmed-note">
                          {t('officialPoIntegration.importConfirmedNote')}
                        </Alert>
                      )}
                      {integration.status === 'CONFIRMED' && !confirmImportMutation.data && (
                        <Alert severity="success" sx={{ mb: 2 }} data-testid="official-po-import-confirmed-note">
                          {t('officialPoIntegration.importConfirmedNote')}
                        </Alert>
                      )}

                      <Toast
                        open={confirmImportMutation.isError}
                        severity="error"
                        testId="official-po-confirm-import-error"
                        message={
                          errorCodeOf(confirmImportMutation.error) === 'OFFICIAL_PO_NOT_SUBMITTED' ? t('officialPoIntegration.errorNotSubmitted') :
                          errorCodeOf(confirmImportMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
                          t('errorGeneric')
                        }
                        onClose={() => confirmImportMutation.reset()}
                      />

                      {integration.status === 'SUBMITTED' && (
                        <Button
                          variant="outlined"
                          onClick={handleConfirmImport}
                          disabled={confirmImportMutation.isPending}
                          data-testid="official-po-confirm-import-button"
                        >
                          {confirmImportMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.confirmImportButton')}
                        </Button>
                      )}
                    </Box>
                  )}
                </Box>
              )}
            </Box>
          )}

          {/* Phase 7-C6 9章/10章/19章: Excel / Legacy Concurrency Control
              Foundation - extends this same Section rather than adding a new
              one (19章's explicit instruction). Never implies a Legacy WRITE
              (20章's Label wording constraint). */}
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" gutterBottom>{t('legacyPoConcurrency.title')}</Typography>

          <Toast open={captureBaselineMutation.isSuccess} severity="success" message={t('legacyPoConcurrency.captureSuccess')} onClose={() => captureBaselineMutation.reset()} />
          <Toast
            open={captureBaselineMutation.isError}
            severity="error"
            testId="legacy-po-baseline-capture-error"
            message={
              errorCodeOf(captureBaselineMutation.error) === 'OFFICIAL_PO_NOT_LINKED' ? t('legacyPoConcurrency.errorNotLinked') :
              errorCodeOf(captureBaselineMutation.error) === 'LEGACY_PO_NOT_FOUND_FOR_BASELINE' ? t('legacyPoConcurrency.errorPoNotFound') :
              errorCodeOf(captureBaselineMutation.error) === 'INTEGRATION_REQUEST_REQUIRED' ? t('legacyPoConcurrency.errorIntegrationRequestRequired') :
              errorCodeOf(captureBaselineMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
              t('errorGeneric')
            }
            onClose={() => captureBaselineMutation.reset()}
          />

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

          {/* Gap Analysis C-2/C-3 (docs/gulliver-20260917-phase1-gap-analysis.md
              7章/8章): Reissue + Revision History - extends this same Section
              (same convention as Legacy Concurrency above, 19章). */}
          <Divider sx={{ my: 2 }} />
          <Typography variant="subtitle2" gutterBottom>{t('officialPoIntegration.reissueSectionTitle')}</Typography>

          <Toast
            open={reissueMutation.isSuccess}
            severity="success"
            message={t('officialPoIntegration.reissueSuccess')}
            onClose={() => reissueMutation.reset()}
          />
          <Toast
            open={reissueMutation.isError}
            severity="error"
            testId="official-po-reissue-error"
            message={
              errorCodeOf(reissueMutation.error) === 'OFFICIAL_PO_REISSUE_NOT_REQUIRED' ? t('officialPoIntegration.errorReissueNotRequired') :
              errorCodeOf(reissueMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
              t('errorGeneric')
            }
            onClose={() => reissueMutation.reset()}
          />

          <Toast
            open={cancelMutation.isSuccess}
            severity="success"
            message={t('officialPoIntegration.cancelRequestSuccess')}
            onClose={() => cancelMutation.reset()}
          />
          <Toast
            open={cancelMutation.isError}
            severity="error"
            testId="official-po-cancel-error"
            message={
              errorCodeOf(cancelMutation.error) === 'OFFICIAL_PO_CANCEL_NOT_ALLOWED' ? t('officialPoIntegration.errorCancelNotAllowed') :
              errorCodeOf(cancelMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
              t('errorGeneric')
            }
            onClose={() => cancelMutation.reset()}
          />
          <Toast
            open={approveCancelMutation.isSuccess}
            severity="success"
            message={t('officialPoIntegration.cancelApproveSuccess')}
            onClose={() => approveCancelMutation.reset()}
          />
          <Toast
            open={approveCancelMutation.isError}
            severity="error"
            testId="official-po-cancel-approve-error"
            message={
              errorCodeOf(approveCancelMutation.error) === 'OFFICIAL_PO_CANCEL_APPROVAL_NOT_ALLOWED' ? t('officialPoIntegration.errorCancelApprovalNotAllowed') :
              errorCodeOf(approveCancelMutation.error) === 'FORBIDDEN' ? t('errorForbidden') :
              t('errorGeneric')
            }
            onClose={() => approveCancelMutation.reset()}
          />

          {isAdmin && (
            <Stack direction="row" spacing={2} sx={{ mb: 2 }}>
              <Button
                variant="outlined"
                color="warning"
                onClick={() => setReissueDialogOpen(true)}
                disabled={!integration?.reissueRequired || reissueMutation.isPending}
                data-testid="official-po-reissue-button"
              >
                {t('officialPoIntegration.reissueButton')}
              </Button>
              <Button
                variant="outlined"
                color="error"
                onClick={() => setCancelDialogOpen(true)}
                disabled={integration?.lifecycleStatus !== 'ACTIVE' || cancelMutation.isPending}
                data-testid="official-po-cancel-button"
              >
                {t('officialPoIntegration.cancelButton')}
              </Button>
              {/* BR-03 step 2: only enabled once a Cancel Request is
                  actually pending - never a direct ACTIVE -> CANCELLED
                  shortcut. */}
              <Button
                variant="contained"
                color="error"
                onClick={handleApproveCancel}
                disabled={integration?.lifecycleStatus !== 'CANCEL_REQUESTED' || approveCancelMutation.isPending}
                data-testid="official-po-cancel-approve-button"
              >
                {approveCancelMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.cancelApproveButton')}
              </Button>
            </Stack>
          )}

          {integration?.lifecycleStatus === 'CANCEL_REQUESTED' && (
            <Alert severity="warning" sx={{ mb: 2 }} data-testid="official-po-cancel-requested-note">
              {t('officialPoIntegration.cancelRequestedNote')}{integration.lifecycleReason ? `: ${integration.lifecycleReason}` : ''}
            </Alert>
          )}

          {integration?.lifecycleStatus === 'CANCELLED' && (
            <Alert severity="error" sx={{ mb: 2 }} data-testid="official-po-cancelled-note">
              {t('officialPoIntegration.cancelledNote')}{integration.lifecycleReason ? `: ${integration.lifecycleReason}` : ''}
            </Alert>
          )}

          {revisionHistory && revisionHistory.length > 0 && (
            <TableContainer sx={{ mt: 1 }}>
              <Table size="small" data-testid="official-po-revision-history-table">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('officialPoIntegration.revisionHistory.revisionNo')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.createdAt')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.createdBy')}</TableCell>
                    <TableCell>{t('officialPoIntegration.officialPoNoLabel')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.excel')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.pdf')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.integrationStatus')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.lifecycleStatus')}</TableCell>
                    <TableCell>{t('officialPoIntegration.revisionHistory.sendStatus')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {revisionHistory.map((rev) => (
                    <TableRow key={rev.revisionNo} hover data-testid={`revision-history-row-${rev.revisionNo}`}>
                      <TableCell>{String(rev.revisionNo).padStart(3, '0')}</TableCell>
                      <TableCell>{rev.createdAt ? new Date(rev.createdAt).toLocaleString('ja-JP') : t('notAvailable')}</TableCell>
                      <TableCell>{rev.createdByDisplayName ?? rev.createdBy ?? t('notAvailable')}</TableCell>
                      <TableCell>{rev.officialPoNo ?? t('notAvailable')}</TableCell>
                      <TableCell>{rev.excelGenerated ? t('atAGlance.done') : t('atAGlance.notYet')}</TableCell>
                      <TableCell>{rev.pdfGenerated ? t('atAGlance.done') : t('atAGlance.notYet')}</TableCell>
                      <TableCell>{t(`officialPoIntegration.statusLabel.${rev.integrationStatus}`)}</TableCell>
                      <TableCell>
                        <Chip
                          size="small"
                          color={rev.lifecycleStatus === 'ACTIVE' ? 'success' : rev.lifecycleStatus === 'CANCELLED' ? 'error' : 'default'}
                          label={t(`officialPoIntegration.lifecycleStatusLabel.${rev.lifecycleStatus}`)}
                        />
                      </TableCell>
                      <TableCell>{rev.emailSendStatus ? t(`officialPoIntegration.revisionHistory.emailSendStatusValue.${rev.emailSendStatus}`) : t('notAvailable')}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )}
        </Paper>
      )}

      {/* §6 Channel-aware audit fix: an EDI-resolved manufacturer never sees
          Mail Preview/Send as a normal operating path - only a short notice
          pointing at the EDI status tracker Section above. Gated on
          resolvedManufacturerChannel (the Master's own "what SHOULD happen"),
          not communicationChannel (only set after a Send already occurred),
          so the notice also covers the APPROVED-but-not-yet-sent window. */}
      {(detail.status === 'APPROVED' || (integration && integration.status !== 'NOT_REQUESTED'))
        && detail.resolvedManufacturerChannel === 'EDI' && detail.communicationChannel !== 'EDI' && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="edi-channel-notice">
          <Alert severity="info">{t('mailPreview.ediChannelNotice')}</Alert>
        </Paper>
      )}

      {/* Phase 7-C3 9章/11章: Mail Preview only - no Send API exists this
          Phase. Same visibility/co-location as the Integration Section
          above; deliberately separate Label from "Demo Send" (7-C3 11章).
          §6 Channel-aware audit fix: never shown at all once the
          Manufacturer Channel Master has resolved to EDI - the notice Box
          above takes its place so staff are never offered an Email-shaped
          action for an EDI manufacturer. */}
      {(detail.status === 'APPROVED' || (integration && integration.status !== 'NOT_REQUESTED'))
        && detail.resolvedManufacturerChannel !== 'EDI' && (
        <Paper variant="outlined" sx={{ p: 2, mt: 2 }} data-testid="mail-preview-section">
          <Typography variant="subtitle1" gutterBottom>{t('mailPreview.title')}</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>{t('mailPreview.subtitle')}</Typography>

          <Toast
            open={mailPreviewMutation.isError}
            severity="error"
            message={errorCodeOf(mailPreviewMutation.error) === 'FORBIDDEN' ? t('mailPreview.errorForbidden') : t('mailPreview.errorGeneric')}
            onClose={() => mailPreviewMutation.reset()}
          />

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
              {/* Gap Analysis C-5 (docs/gulliver-20260917-phase1-gap-analysis.md
                  10章): To/CC are editable here - the Master自動選択 stays the
                  default (seeded once above), but Send-time Override is
                  allowed. Editing Master Data itself is a completely
                  separate Admin screen/action (10章's explicit separation) -
                  these fields never write back to supplier_contact. */}
              {isAdmin ? (
                <>
                  <TextField
                    label={t('mailPreview.to')}
                    value={toOverrideInput}
                    onChange={(e) => { setToOverrideInput(e.target.value); setToManuallyEdited(true) }}
                    fullWidth
                    size="small"
                    // Acceptance Fix (item 7): once the user actually edits
                    // this field, the helper text must stop claiming it is
                    // still "the auto-selected address" - it now says what
                    // is actually true (this Send only, Master unchanged).
                    helperText={toManuallyEdited ? t('mailPreview.overrideHelperTextEdited') : t('mailPreview.overrideHelperText')}
                    data-testid="mail-send-to-input"
                  />
                  <TextField
                    label={t('mailPreview.cc')}
                    value={ccOverrideInput}
                    onChange={(e) => { setCcOverrideInput(e.target.value); setCcManuallyEdited(true) }}
                    fullWidth
                    size="small"
                    helperText={ccManuallyEdited ? t('mailPreview.overrideHelperTextEdited') : undefined}
                    data-testid="mail-send-cc-input"
                  />
                </>
              ) : (
                <>
                  <Typography variant="body2">{t('mailPreview.to')}: {mailPreviewMutation.data.to.join(', ') || '—'}</Typography>
                  <Typography variant="body2">{t('mailPreview.cc')}: {mailPreviewMutation.data.cc.join(', ') || '—'}</Typography>
                </>
              )}
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
                {' '}{t(mailPreviewMutation.data.attachment.generated ? 'mailPreview.attachmentGenerated' : 'mailPreview.attachmentNotGenerated')}
              </Typography>
            </Stack>
          )}

          {/* Phase 9-E: real Email Send - only ever shown for a resolved
              EMAIL Channel (§6's explicit "同じSend Emailボタンを出さない
              こと" - an EDI-channel Order never sees this, only its own EDI
              status tracker Section above). Sends exactly what Preview
              resolved - no separate compose UI. */}
          {isAdmin && detail.resolvedManufacturerChannel === 'EMAIL' && (
            <Box sx={{ mt: 2 }} data-testid="email-send-section">
              <Divider sx={{ mb: 2 }} />
              <Typography variant="subtitle2" gutterBottom>{t('mailPreview.sendSectionTitle')}</Typography>

              <Toast open={sendEmailMutation.isSuccess} severity="success" message={t('mailPreview.sendSuccess')} onClose={() => sendEmailMutation.reset()} />
              <Toast
                open={sendEmailMutation.isError}
                severity="error"
                testId="email-send-error"
                message={
                  errorCodeOf(sendEmailMutation.error) === 'EMAIL_PREVIEW_BLOCKED' ? t('mailPreview.errorPreviewBlocked') :
                  errorCodeOf(sendEmailMutation.error) === 'EMAIL_ATTACHMENT_NOT_READY' ? t('mailPreview.errorAttachmentNotReady') :
                  errorCodeOf(sendEmailMutation.error) === 'EMAIL_CHANNEL_NOT_APPLICABLE' ? t('mailPreview.errorChannelNotApplicable') :
                  errorCodeOf(sendEmailMutation.error) === 'FORBIDDEN' ? t('mailPreview.errorForbidden') :
                  t('mailPreview.errorGeneric')
                }
                onClose={() => sendEmailMutation.reset()}
              />

              {emailStatus?.status === 'SENT' && (
                <Alert severity="success" sx={{ mb: 2 }} data-testid="email-sent-note">
                  {t('mailPreview.sentNote')} ({emailStatus.sentByDisplayName ?? emailStatus.sentBy} - {emailStatus.sentAt ? new Date(emailStatus.sentAt).toLocaleString('ja-JP') : ''})
                  {/* Gap Analysis C-5: Audit visibility - Master-resolved vs
                      actually-sent addresses are always distinguishable, not
                      only in the Audit Timeline below. */}
                  {emailStatus.recipientOverrideUsed && (
                    <Box sx={{ mt: 0.5 }} data-testid="email-recipient-override-note">
                      <Typography variant="caption" sx={{ display: 'block' }}>
                        {t('mailPreview.overrideUsedNote')}
                      </Typography>
                      <Typography variant="caption" sx={{ display: 'block' }} color="text.secondary">
                        {t('mailPreview.masterAddressesLabel')}: To [{emailStatus.masterTo.join(', ')}] / CC [{emailStatus.masterCc.join(', ') || '—'}]
                      </Typography>
                    </Box>
                  )}
                </Alert>
              )}
              {emailStatus?.status === 'FAILED' && (
                <Alert severity="error" sx={{ mb: 2 }} data-testid="email-failed-note">
                  {t('mailPreview.sendFailedNote')}{emailStatus.errorMessage ? `: ${emailStatus.errorMessage}` : ''}
                </Alert>
              )}

              {emailStatus?.status !== 'SENT' && (
                <Button
                  variant="contained"
                  onClick={() => setSendConfirmDialogOpen(true)}
                  disabled={sendEmailMutation.isPending}
                  data-testid="email-send-button"
                >
                  {sendEmailMutation.isPending ? <CircularProgress size={20} /> :
                    emailStatus?.status === 'FAILED' ? t('mailPreview.retrySendButton') : t('mailPreview.sendButton')}
                </Button>
              )}
            </Box>
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
                    {r.createdByDisplayName ?? r.createdBy} - {new Date(r.createdAt).toLocaleString('ja-JP')}
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
                <Typography variant="body2" color="text.secondary">
                  {t(`responseHistory.status.${h.responseStatus}`, { defaultValue: h.responseStatus })}
                </Typography>
                {h.agreedBy && (
                  <Typography variant="caption" color="text.secondary">
                    {t('responseHistory.agreedBy', { by: h.agreedByDisplayName ?? h.agreedBy })}
                  </Typography>
                )}
                {h.reopenedBy && (
                  <Typography variant="caption" color="text.secondary">
                    {t('responseHistory.reopenedBy', { by: h.reopenedByDisplayName ?? h.reopenedBy })}
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
                {/* Phase 8-G 12章: only rendered when linkState === 'LINKED'
                    (Portal Order <-> Legacy officialPoNo is confirmed, same
                    guard Fulfillment itself uses above) - filters the
                    Arrival List by this PO Number rather than deep-linking a
                    specific Invoice (one PO may have 0/1/many Arrivals). */}
                <Button
                  size="small"
                  onClick={() => navigate(withReturnTo(`/arrivals?poNumber=${encodeURIComponent(fulfillment.officialPoNo ?? '')}`, ownPath))}
                  data-testid="fulfillment-view-arrivals-button"
                >
                  {t('fulfillment.viewArrivalsButton')}
                </Button>
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
          {/* Phase 7-H (Follow-up audit): confirmed via Source
              (FollowUpCaseService.create has no Order-status/officialPoNo/
              Fulfillment-LINKED gate; docs/fulfillment-follow-up-foundation.md
              12章/20章 - OPERATOR/ADMIN双方に意図的に開放) that manual
              creation working even when G-SYS正式PO未連携 is the Foundation's
              own intended design, not a gap - a Follow-up Case is a
              Portal-only record of "問い合わせたい"意図そのもので、Legacy
              Fulfillment実績を読める状態を前提にしていない。Label/Tooltip
              only change - creation条件（Business Rule）は変更していない。 */}
          {/* aria-label is deliberately a short, generic label (t('followUp.createButtonInfoLabel'))
              rather than the full Tooltip text - the Tooltip text itself
              explains "実際のメール送信は行われません" (E2E regression found:
              the full text's own mention of "送信" made this button falsely
              match getByRole('button', {name: /送信/}) assertions elsewhere
              on this screen that check "no Send control exists"). */}
          <Tooltip title={t('followUp.createButtonTooltip')}>
            <IconButton size="small" aria-label={t('followUp.createButtonInfoLabel')}>
              <InfoOutlinedIcon fontSize="small" />
            </IconButton>
          </Tooltip>
          <Button size="small" variant="outlined" onClick={() => openFollowUpDialog()} data-testid="create-follow-up-case-button">
            {t('followUp.createButton')}
          </Button>
        </Stack>

        <Toast open={createFollowUpCaseMutation.isError} severity="error" message={t('followUp.errorGeneric')} onClose={() => createFollowUpCaseMutation.reset()} />
        <Toast open={previewFollowUpMailMutation.isError} severity="error" message={t('followUp.errorGeneric')} onClose={() => previewFollowUpMailMutation.reset()} />

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
                  {c.createdByDisplayName ?? c.createdBy} - {new Date(c.createdAt).toLocaleString('ja-JP')}
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
          {events.map((e, i) => {
            // Phase 7-H (Audit Timeline readability audit): a "—" Before
            // paired with an Arrow read as a value having CHANGED FROM
            // nothing, which is misleading for Create-type events (nothing
            // "changed" - a value was simply set for the first time, e.g.
            // ORDER_DRAFT_CREATED's newValue=draftNo with no real oldValue).
            // The Arrow (an MUI icon, not the "→" character - visually
            // distinct from surrounding text) now renders ONLY when BOTH
            // sides resolve to a genuine value; a single-sided value (Create,
            // or a Business event that only ever records one side) renders
            // alone, with no placeholder dash and no Arrow. Event semantics
            // (eventType/fieldName/oldValue/newValue as stored) are
            // completely unchanged - this only touches how the same data is
            // displayed.
            const oldResolved = resolveTimelineValue(t, e.fieldName, e.oldValue)
            const newResolved = resolveTimelineValue(t, e.fieldName, e.newValue)
            return (
              <Paper key={i} variant="outlined" sx={{ p: 1.5 }} data-testid={`timeline-event-${i}`}>
                <Stack direction="row" spacing={2} sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
                  <Typography variant="body2" sx={{ fontWeight: 'bold', minWidth: 200 }}>
                    {t(`status:eventType.${e.eventType}`, { defaultValue: e.eventType })}
                  </Typography>
                  {(oldResolved !== null || newResolved !== null) && (
                    <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
                      {oldResolved !== null && newResolved !== null ? (
                        <>
                          <Typography variant="body2" color="text.secondary">{oldResolved}</Typography>
                          <ArrowForwardIcon fontSize="inherit" sx={{ color: 'text.secondary' }} data-testid="timeline-arrow-icon" />
                          <Typography variant="body2" color="text.secondary">{newResolved}</Typography>
                        </>
                      ) : (
                        <Typography variant="body2" color="text.secondary">{oldResolved ?? newResolved}</Typography>
                      )}
                    </Stack>
                  )}
                  <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                    {e.performedByDisplayName ?? e.performedBy} - {new Date(e.performedAt).toLocaleString('ja-JP')}
                  </Typography>
                </Stack>
              </Paper>
            )
          })}
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
        fullScreen={isCardLayout}
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
        fullScreen={isCardLayout}
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

      {/* Gap Analysis C-2/C-3: Reissue confirmation - a human-confirmed
          Action (never automatic), mirroring the G-SYS連携準備 dialog above. */}
      <Dialog
        open={reissueDialogOpen}
        fullScreen={isCardLayout}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setReissueDialogOpen(false)
        }}
      >
        <DialogTitle>{t('officialPoIntegration.reissueDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap' }}>{t('officialPoIntegration.reissueDialogBody')}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setReissueDialogOpen(false)} disabled={reissueMutation.isPending}>
            {t('officialPoIntegration.reissueDialogCancel')}
          </Button>
          <Button
            variant="contained"
            color="warning"
            onClick={handleReissue}
            disabled={reissueMutation.isPending}
            data-testid="official-po-reissue-dialog-confirm"
          >
            {reissueMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.reissueDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* Gap Analysis C-4 (docs/gulliver-20260917-phase1-gap-analysis.md
          9章): Cancel - reason is mandatory (Audit Trail: 誰が・いつ・何を・
          なぜ), so this is a Reason-input Dialog rather than a plain confirm. */}
      <Dialog
        open={cancelDialogOpen}
        fullScreen={isCardLayout}
        onClose={(_event, reason) => {
          if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
          setCancelDialogOpen(false)
        }}
      >
        <DialogTitle>{t('officialPoIntegration.cancelDialogTitle')}</DialogTitle>
        <DialogContent>
          <DialogContentText sx={{ whiteSpace: 'pre-wrap', mb: 2 }}>{t('officialPoIntegration.cancelDialogBody')}</DialogContentText>
          <TextField
            label={t('officialPoIntegration.cancelReasonLabel')}
            value={cancelReason}
            onChange={(e) => setCancelReason(e.target.value)}
            fullWidth
            multiline
            minRows={2}
            required
            data-testid="official-po-cancel-reason-input"
          />
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setCancelDialogOpen(false)} disabled={cancelMutation.isPending}>
            {t('officialPoIntegration.cancelDialogCancel')}
          </Button>
          <Button
            variant="contained"
            color="error"
            onClick={handleCancel}
            disabled={cancelMutation.isPending || !cancelReason.trim()}
            data-testid="official-po-cancel-dialog-confirm"
          >
            {cancelMutation.isPending ? <CircularProgress size={20} /> : t('officialPoIntegration.cancelDialogConfirm')}
          </Button>
        </DialogActions>
      </Dialog>

      {/* BR-04 (docs/gulliver-20260917-confirmed-business-rules.md): a final
          send-time confirmation is mandatory before any Manufacturer Send,
          and a Domain Warning (never a block) is shown when a To/CC address
          resolves to a Domain the Master Contact never registered. */}
      {mailPreviewMutation.data && (() => {
        const finalTo = toManuallyEdited ? splitAddressInput(toOverrideInput) : mailPreviewMutation.data.to
        const finalCc = ccManuallyEdited ? splitAddressInput(ccOverrideInput) : mailPreviewMutation.data.cc
        const unknownDomainAddresses = addressesWithUnknownDomain(
          [...finalTo, ...finalCc], [...mailPreviewMutation.data.to, ...mailPreviewMutation.data.cc])
        return (
          <Dialog
            open={sendConfirmDialogOpen}
            fullScreen={isCardLayout}
            onClose={(_event, reason) => {
              if (reason === 'backdropClick' || reason === 'escapeKeyDown') return
              setSendConfirmDialogOpen(false)
            }}
            data-testid="email-send-confirm-dialog"
          >
            <DialogTitle>{t('mailPreview.sendConfirmDialogTitle')}</DialogTitle>
            <DialogContent>
              <Stack spacing={1} sx={{ mt: 1 }}>
                <Typography variant="body2">{t('mailPreview.to')}: {finalTo.join(', ') || '—'}</Typography>
                <Typography variant="body2">{t('mailPreview.cc')}: {finalCc.join(', ') || '—'}</Typography>
                {unknownDomainAddresses.length > 0 && (
                  <Alert severity="warning" data-testid="email-domain-mismatch-warning">
                    {t('mailPreview.domainMismatchWarning', { addresses: unknownDomainAddresses.join(', ') })}
                  </Alert>
                )}
              </Stack>
            </DialogContent>
            <DialogActions>
              <Button onClick={() => setSendConfirmDialogOpen(false)} disabled={sendEmailMutation.isPending}>
                {t('mailPreview.sendConfirmDialogCancel')}
              </Button>
              <Button
                variant="contained"
                onClick={handleConfirmSendEmail}
                disabled={sendEmailMutation.isPending}
                data-testid="email-send-confirm-dialog-confirm"
              >
                {sendEmailMutation.isPending ? <CircularProgress size={20} /> : t('mailPreview.sendConfirmDialogConfirm')}
              </Button>
            </DialogActions>
          </Dialog>
        )
      })()}

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
