import { useEffect, useState } from 'react'
import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
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
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import TextField from '@mui/material/TextField'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Divider from '@mui/material/Divider'

import axios from 'axios'

import MenuItem from '@mui/material/MenuItem'

import { useSkuDetail, useRestockExpectation, useUpdateRestockExpectation } from './api'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'
import { StockJudgementChip } from '../../shared/components/StockJudgementChip'
import { RestockLabel } from '../../shared/components/RestockLabel'
import { StockoutStatusChip } from '../../shared/components/StockoutStatusChip'
import { ManufacturerConfirmationCaption } from '../../shared/components/ManufacturerConfirmationCaption'
import { RestockConflictWarning } from '../../shared/components/RestockConflictWarning'
import { ManufacturerStockoutHistoryDialog } from '../../shared/components/ManufacturerStockoutHistoryDialog'
import { computeStockJudgement } from '../../shared/domain/stockJudgement'
import { resolveReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import { Toast } from '../../shared/components/Toast'
import type { ApiErrorBody } from '../../shared/types/orderDraft'
import type { ContactMethod, StockoutStatus } from '../../shared/types/restockExpectation'

function restockErrorCodeOf(error: unknown): string | null {
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    return error.response?.data?.errorCode ?? null
  }
  return null
}

/**
 * Post-Freeze Business Refinement (re-audit doc §9/§10-3) - the SKU Detail
 * Edit form for Type C Manual Expected Restock. Deliberately never shows
 * the words "Legacy"/"Portal"/"TR_ARR" - only the ja/en business terms
 * (入荷予定日 vs 再入荷予定日 vs 再入荷予定：未定) RestockLabel already uses
 * everywhere else, so an Approver/Operator reads one consistent vocabulary
 * regardless of which screen they're on.
 *
 * Prefills from `manualDate`/`manualUnknown` (the raw Manual record), never
 * from the merged `date`/`source` - those reflect Legacy once an open
 * Arrival exists, and prefilling from them would silently overwrite the
 * real Manual value with Legacy's own date on the next Save.
 */
function RestockExpectationEditSection({ sku }: { sku: string }) {
  const { t } = useTranslation('restockExpectation')
  const { data, isLoading } = useRestockExpectation(sku)
  const updateMutation = useUpdateRestockExpectation(sku)

  const [dateInput, setDateInput] = useState('')
  const [unknownInput, setUnknownInput] = useState(false)
  const [memoInput, setMemoInput] = useState('')
  const [statusInput, setStatusInput] = useState<StockoutStatus | ''>('')
  const [shortageQtyInput, setShortageQtyInput] = useState('')
  const [receivedDateInput, setReceivedDateInput] = useState('')
  const [contactMethodInput, setContactMethodInput] = useState<ContactMethod | ''>('')
  const [historyOpen, setHistoryOpen] = useState(false)

  // Re-sync local form state whenever a fresh fetch/save lands - never on
  // every render, so mid-edit keystrokes aren't clobbered by a background
  // refetch.
  useEffect(() => {
    if (!data) return
    setDateInput(data.manualDate ?? '')
    setUnknownInput(data.manualUnknown)
    setMemoInput(data.manualMemo ?? '')
    setStatusInput(data.stockoutStatus ?? '')
    setShortageQtyInput(data.shortageQty != null ? String(data.shortageQty) : '')
    setReceivedDateInput(data.informationReceivedDate ?? '')
    setContactMethodInput(data.contactMethod ?? '')
  }, [data])

  if (isLoading || !data) {
    return null
  }

  function handleSave() {
    updateMutation.mutate({
      expectedRestockDate: unknownInput ? null : (dateInput || null),
      unknown: unknownInput,
      memo: memoInput || null,
      stockoutStatus: statusInput || null,
      shortageQty: shortageQtyInput === '' ? null : Number(shortageQtyInput),
      informationReceivedDate: receivedDateInput || null,
      contactMethod: contactMethodInput || null,
    })
  }

  // requirements doc §23: Legacy's own value and the Manufacturer's own
  // account are shown as two SEPARATE lines whenever either exists -
  // neither is allowed to fully hide the other, with a Warning marker when
  // they actively disagree (hasConflict).
  const hasManufacturerRestockInfo = data.manualDate != null || data.manualUnknown
  const hasNothing = data.legacyDate == null && !hasManufacturerRestockInfo && !data.stockoutStatus

  return (
    <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }} data-testid="sku-restock-section">
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
        <Typography variant="subtitle1" gutterBottom>{t('editSectionTitle')}</Typography>
        <Button size="small" onClick={() => setHistoryOpen(true)} data-testid="sku-restock-view-history-button">
          {t('viewHistoryButton')}
        </Button>
      </Stack>
      <Stack spacing={0.5} sx={{ mb: 1.5 }}>
        {hasNothing && <Typography variant="body2" color="text.secondary">{t('noneDisplay')}</Typography>}
        {data.legacyDate != null && (
          <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center' }}>
            <RestockLabel source="LEGACY_EXPECTED_ARRIVAL" date={data.legacyDate} />
            {data.hasConflict && <RestockConflictWarning />}
          </Stack>
        )}
        {data.legacyDate != null && (
          <Alert severity="info" sx={{ mt: 0.5 }} data-testid="restock-legacy-readonly-note">
            {t('legacyReadOnlyNote')}
          </Alert>
        )}
        {hasManufacturerRestockInfo && (
          <RestockLabel source={data.manualUnknown ? 'PORTAL_MANUAL_UNKNOWN' : 'PORTAL_MANUAL'} date={data.manualDate} />
        )}
        {data.stockoutStatus && (
          <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
            <StockoutStatusChip status={data.stockoutStatus} />
            {data.shortageQty != null && (
              <Typography variant="body2" color="text.secondary">{t('shortageQtyLabel')}: {data.shortageQty}</Typography>
            )}
          </Stack>
        )}
        <ManufacturerConfirmationCaption informationReceivedDate={data.informationReceivedDate} contactMethod={data.contactMethod} />
      </Stack>
      <Divider sx={{ mb: 1.5 }} />
      <Stack spacing={1.5}>
        <TextField
          select
          label={t('stockoutStatusLabel')}
          size="small"
          value={statusInput}
          onChange={(e) => setStatusInput(e.target.value as StockoutStatus | '')}
          data-testid="sku-restock-status-select"
        >
          <MenuItem value="">{t('stockoutStatusNoneOption')}</MenuItem>
          <MenuItem value="STOCKOUT">{t('stockoutStatus.STOCKOUT')}</MenuItem>
          <MenuItem value="LONG_TERM_STOCKOUT">{t('stockoutStatus.LONG_TERM_STOCKOUT')}</MenuItem>
          <MenuItem value="RESOLVED">{t('stockoutStatus.RESOLVED')}</MenuItem>
        </TextField>
        <TextField
          label={t('dateLabel')}
          type="date"
          size="small"
          value={dateInput}
          disabled={unknownInput}
          onChange={(e) => setDateInput(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          data-testid="sku-restock-date-input"
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={unknownInput}
              onChange={(e) => {
                setUnknownInput(e.target.checked)
                if (e.target.checked) setDateInput('')
              }}
              data-testid="sku-restock-unknown-checkbox"
            />
          }
          label={t('unknownCheckboxLabel')}
        />
        <TextField
          label={t('shortageQtyLabel')}
          placeholder={t('shortageQtyPlaceholder') ?? undefined}
          type="number"
          size="small"
          value={shortageQtyInput}
          onChange={(e) => setShortageQtyInput(e.target.value)}
          slotProps={{ htmlInput: { min: 0 } }}
          data-testid="sku-restock-shortage-qty-input"
        />
        <TextField
          label={t('informationReceivedDateLabel')}
          type="date"
          size="small"
          value={receivedDateInput}
          onChange={(e) => setReceivedDateInput(e.target.value)}
          slotProps={{ inputLabel: { shrink: true } }}
          data-testid="sku-restock-received-date-input"
        />
        <TextField
          select
          label={t('contactMethodLabel')}
          size="small"
          value={contactMethodInput}
          onChange={(e) => setContactMethodInput(e.target.value as ContactMethod | '')}
          data-testid="sku-restock-contact-method-select"
        >
          <MenuItem value="">{t('contactMethodNoneOption')}</MenuItem>
          <MenuItem value="PHONE">{t('contactMethod.PHONE')}</MenuItem>
          <MenuItem value="EMAIL">{t('contactMethod.EMAIL')}</MenuItem>
          <MenuItem value="ORDER_RESPONSE">{t('contactMethod.ORDER_RESPONSE')}</MenuItem>
          <MenuItem value="OTHER">{t('contactMethod.OTHER')}</MenuItem>
        </TextField>
        <TextField
          label={t('memoLabel')}
          size="small"
          multiline
          minRows={2}
          value={memoInput}
          onChange={(e) => setMemoInput(e.target.value)}
          data-testid="sku-restock-memo-input"
        />
        {data.manualUpdatedBy && (
          <Typography variant="caption" color="text.secondary">
            {t('updatedByLabel')}: {data.manualUpdatedBy} ({t('updatedAtLabel')}: {data.manualUpdatedAt ? new Date(data.manualUpdatedAt).toLocaleString('ja-JP') : '—'})
          </Typography>
        )}
        <Box>
          <Button
            variant="contained"
            size="small"
            onClick={handleSave}
            disabled={updateMutation.isPending}
            data-testid="sku-restock-save-button"
          >
            {t('saveButton')}
          </Button>
        </Box>
      </Stack>
      <Toast open={updateMutation.isSuccess} severity="success" message={t('saveSuccess')} onClose={() => updateMutation.reset()} />
      <Toast
        open={updateMutation.isError}
        severity="error"
        message={restockErrorCodeOf(updateMutation.error) === 'INVALID_SKU_EXPECTED_RESTOCK' ? t('errorInvalid') : t('errorGeneric')}
        onClose={() => updateMutation.reset()}
      />
      <ManufacturerStockoutHistoryDialog sku={sku} open={historyOpen} onClose={() => setHistoryOpen(false)} />
    </Paper>
  )
}

/** Phase 8-J 9章/10章: rate is a plain fraction (e.g. 0.3521) as returned by
 * MarginCalculator.profitRateSell - displayed as a percentage here, no
 * rounding/threshold logic beyond the 2-decimal display format. */
function formatMarginRate(rate: number | null, notAvailable: string): string {
  if (rate == null) return notAvailable
  return `${(rate * 100).toFixed(2)}%`
}

/**
 * SKU Detail (implementation instructions Step 5 4章). READ ONLY reference
 * screen for order judgement - deliberately no Sales Trend chart, no
 * 直近30/60/90日 or 前月/前々月 breakdown anywhere on this page (Legacy only
 * exposes one current-month figure - Phase 0.5 audit finding).
 */
export function SkuDetailPage() {
  const { t } = useTranslation(['skuDetail', 'common', 'status'])
  const { sku } = useParams<{ sku: string }>()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  // Phase 6-A: Candidate List embeds its own current URL (Filter state
  // included) as ?returnTo=... when linking here - restore that exact List
  // state on "戻る" instead of always landing on the unfiltered list
  // (docs/production-ux-workflow-redesign.md 6.3章).
  const returnTo = searchParams.get('returnTo')
  const backTarget = resolveReturnTo(returnTo, '/candidates')
  // Phase 8-M (Global Navigation Audit, Principle D): SKU Detail has 3
  // possible entry points (Candidate List / Warehouse Stock Drawer /
  // Stock-Sales Drawer) - the Label must reflect the actual origin instead
  // of always assuming Candidate List (the same "戻る hardcoded a stale
  // caller" pitfall Phase 7-H fixed for PoPreviewPage).
  // Gap Analysis B-2 (docs/gulliver-20260917-phase1-gap-analysis.md 5章):
  // 4th entry point - Order Detail (承認画面) linking here to let the
  // approver check current Stock/Sales/Arrival for a specific line.
  const backLabel = backTarget.startsWith('/warehouse-stock')
    ? t('backToWarehouseStock')
    : backTarget.startsWith('/stock-sales')
      ? t('backToStockSales')
      : backTarget.startsWith('/orders/')
        ? t('backToOrderDetail')
        : t('back')
  // Phase 8-M (Global Navigation Audit, Principle D): this screen's own
  // current path (with its own returnTo preserved) becomes the returnTo
  // Arrival List carries, so Arrival List's conditional Back button returns
  // here specifically rather than dropping the user at the plain List.
  const ownPath = withReturnTo(`/items/${encodeURIComponent(sku ?? '')}`, returnTo)
  const { data, isLoading, isError } = useSkuDetail(sku ?? '')

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError || !data) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{t('notFound')}</Alert>
      </Box>
    )
  }

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Typography variant="h5" component="h1">{data.sku} - {data.itemName}</Typography>
        <ItemStatusChip status={data.itemStatus} />
        <DataSourceBadge dataSource={data.dataSource} />
        <Box sx={{ flexGrow: 1 }} />
        {/* Phase 8-J 6章: this SKU used only as a search condition on the
            Arrival List (same officialPoNo-as-search-condition pattern
            Order Detail's Fulfillment section already uses to reach
            /arrivals?poNumber=...) - never a "this Warehouse Stock/Arrival
            came from this SKU" Transaction Trace.
            Phase 8-M (Global Navigation Audit, Principle D): DOES thread
            returnTo=ownPath now, so Arrival List's conditional Back button
            can return to this SKU Detail screen specifically. */}
        <Button
          size="small"
          variant="outlined"
          onClick={() => navigate(withReturnTo(`/arrivals?skuKeyword=${encodeURIComponent(data.sku)}`, ownPath))}
          data-testid="sku-detail-view-arrivals-button"
        >
          {t('viewArrivals')}
        </Button>
        {/* Phase 8-M (Global Navigation Audit, Principle E/§5): Back button
            moved to the rightmost position (same convention as every other
            Detail screen fixed this Phase - Arrival/PriceChange/Order
            Detail) instead of its previous leftmost placement, and Label is
            now Context-aware rather than always assuming Candidate List. */}
        <Button size="small" onClick={() => navigate(backTarget)} data-testid="back-to-sku-detail-origin">
          {backLabel}
        </Button>
      </Stack>

      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
          <Typography variant="subtitle1" gutterBottom>{t('section.product')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">{t('field.brand')}: {data.brandName ?? data.brandCode ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.supplier')}: {data.supplierName ?? data.supplierCode ?? t('notAvailable')}</Typography>
          </Stack>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
          <Typography variant="subtitle1" gutterBottom>{t('section.inventory')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">{t('field.currentStock')}: {data.currentStock ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.safetyStock')}: {data.safetyStock ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.openPo')}: {data.openPo ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.openArrival')}: {data.openArrival ?? t('notAvailable')}</Typography>
            <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }}>
              <Typography variant="body2">{t('field.stockJudgement')}:</Typography>
              {/* Phase 7-G: SkuDetailResponse keeps openPo/openArrival as two
                  separate raw fields (SkuDetailService.java), but
                  OrderCandidateResponse - what DashboardService's
                  isLongTermOutOfStock Predicate and the Candidate List's
                  in-page Filter/Badge actually read as "openPo" - is
                  row.openPo()+row.openArrival() already summed
                  (OrderCandidateService.toResponse, sum()). Passing raw
                  data.openPo alone here would silently diverge from that
                  Predicate for any SKU with openPo==0 but openArrival>0 -
                  confirmed via Source, not assumption - so the two are added
                  back together here to keep this screen's Badge consistent
                  with the Candidate List/Dashboard Badge for the same SKU. */}
              <StockJudgementChip judgement={computeStockJudgement(data.currentStock, (data.openPo ?? 0) + (data.openArrival ?? 0))} />
            </Stack>
          </Stack>
        </Paper>

        <RestockExpectationEditSection sku={data.sku} />

        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
          <Typography variant="subtitle1" gutterBottom>{t('section.ordering')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">{t('field.monthlySales')}: {data.monthlySales ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.leadTime')}: {data.leadTime ?? t('notAvailable')}</Typography>
            <Typography variant="body2">
              {t('field.recommendedQty')}: <strong>{data.recommendedQty ?? t('notAvailable')}</strong>
            </Typography>
            <Typography variant="body2">
              {t('field.unitPrice')}: {data.unitPrice != null ? `¥${data.unitPrice.toLocaleString()}` : t('notAvailable')}
            </Typography>
          </Stack>
        </Paper>

        {/* Phase 8-J 9章/10章: reuses Price Change Foundation's
            MarginCalculator - a THEORETICAL reference figure, never Actual
            Gross Profit (that requires Transaction-level Invoice data, out
            of Source scope). Kept as its own clearly-labeled Paper rather
            than folded into 発注情報, so it reads as a distinct, separately
            sourced reference value rather than an Ordering decision input. */}
        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }} data-testid="sku-detail-margin-section">
          <Typography variant="subtitle1" gutterBottom>{t('section.margin')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">
              {t('field.theoreticalMarginRate')}: <strong>{formatMarginRate(data.theoreticalMarginRate, t('notAvailable'))}</strong>
            </Typography>
            <Typography variant="body2">
              {t('field.theoreticalMarginAmount')}: {data.theoreticalMarginAmount != null ? `¥${data.theoreticalMarginAmount.toLocaleString()}` : t('notAvailable')}
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {t('field.marginDisclaimer')}
            </Typography>
          </Stack>
        </Paper>
      </Stack>

      <Typography variant="h6" sx={{ mt: 3 }} gutterBottom>{t('section.history')}</Typography>
      {data.poHistory.length === 0 ? (
        <Alert severity="info">{t('historyEmpty')}</Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('historyTable.poNo')}</TableCell>
                <TableCell>{t('historyTable.orderDate')}</TableCell>
                <TableCell>{t('historyTable.supplier')}</TableCell>
                <TableCell align="right">{t('historyTable.qty')}</TableCell>
                <TableCell align="right">{t('historyTable.unitPrice')}</TableCell>
                <TableCell>{t('historyTable.status')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.poHistory.map((line, i) => (
                <TableRow key={`${line.poNo}-${i}`} hover>
                  <TableCell>{line.poNo}</TableCell>
                  <TableCell>{line.orderDate ?? t('notAvailable')}</TableCell>
                  <TableCell>{line.supplierName ?? line.supplierCode ?? t('notAvailable')}</TableCell>
                  <TableCell align="right">{line.qty ?? t('notAvailable')}</TableCell>
                  <TableCell align="right">{line.unitPrice != null ? `¥${line.unitPrice.toLocaleString()}` : t('notAvailable')}</TableCell>
                  <TableCell>{line.status ?? t('notAvailable')}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  )
}
