import { useEffect, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import TablePagination from '@mui/material/TablePagination'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Chip from '@mui/material/Chip'
import Tooltip from '@mui/material/Tooltip'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'

import { useOrderHistory } from './api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import type { OrderHistoryFilter } from './api'

// Phase 7-C1 6章: READY_TO_ORDER dropped from the Filter options - no
// current Order can hold that Status anymore (V8 migrated every existing
// row to APPROVED, and no code writes it going forward). It stays
// translatable in status.json only for historical Audit Timeline entries.
const STATUS_OPTIONS = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'AWAITING_SUPPLIER', 'SUPPLIER_CONFIRMED']
// Phase 7-H (Order List search/filter audit): orderNoKeyword/itemKeyword/
// updatedFrom/updatedTo added the same way as the existing 3 - real Backend
// query params (OrderHistoryService.list), not a client-only display Filter.
const FILTER_PARAMS = ['supplierCode', 'brandCode', 'status', 'orderNoKeyword', 'itemKeyword', 'updatedFrom', 'updatedTo'] as const
const DEFAULT_PAGE_SIZE = 20

/** Read-only glance badges for the list view (no Acknowledge action here -
 * that lives on Order History Detail / Supplier Response, implementation
 * instructions Step 5 2章). Unlike AttentionChips this renders from plain
 * type strings (OrderHistorySummaryResponse.activeAttentionTypes), not
 * id-bearing AttentionSummary objects. */
function AttentionTypeBadges({ types }: { types: string[] }) {
  const { t } = useTranslation('status')
  if (types.length === 0) return null
  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
      {types.map((type) => (
        <Chip key={type} size="small" color={type === 'PARTIAL_CONFIRMATION' ? 'info' : 'warning'}
              label={t(`attentionType.${type}`, { defaultValue: type })} />
      ))}
    </Stack>
  )
}

/** Requirements MD 31.2: History画面は原則READ ONLY. Row click navigates to
 * detail; no inline editing exists on this screen (implementation
 * instructions 27章). */
export function OrderHistoryListPage() {
  const { t } = useTranslation(['history', 'common', 'status'])
  const navigate = useNavigate()
  // Phase 6-A (docs/production-ux-workflow-redesign.md 6.2章): same
  // URL-as-single-source-of-truth fix as Candidate List - Dashboard's
  // ?brandCode=...&status=... deep-link (Step 5 3章) is read live from
  // searchParams on every render, not just once at mount, and every Filter
  // change below writes straight back to the URL.
  const [searchParams, setSearchParams] = useSearchParams()
  const filter: OrderHistoryFilter = {
    supplierCode: searchParams.get('supplierCode') ?? undefined,
    brandCode: searchParams.get('brandCode') ?? undefined,
    status: searchParams.get('status') ?? undefined,
    orderNoKeyword: searchParams.get('orderNoKeyword') ?? undefined,
    itemKeyword: searchParams.get('itemKeyword') ?? undefined,
    updatedFrom: searchParams.get('updatedFrom') ?? undefined,
    updatedTo: searchParams.get('updatedTo') ?? undefined,
  }
  // Phase 7-H: same "local input state, commit to the URL on blur/Enter"
  // idiom as CandidateListPage's Keyword field - typing a keyword must not
  // fire a fresh Backend request on every keystroke.
  const [orderNoKeywordInput, setOrderNoKeywordInput] = useState(filter.orderNoKeyword ?? '')
  const [itemKeywordInput, setItemKeywordInput] = useState(filter.itemKeyword ?? '')

  useEffect(() => {
    setOrderNoKeywordInput(searchParams.get('orderNoKeyword') ?? '')
    setItemKeywordInput(searchParams.get('itemKeyword') ?? '')
  }, [searchParams])

  function updateFilter(patch: Partial<OrderHistoryFilter>) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        const merged = { ...filter, ...patch }
        for (const key of FILTER_PARAMS) {
          const value = merged[key]
          if (value) next.set(key, value)
          else next.delete(key)
        }
        next.set('page', '0') // any Filter change resets to page 1
        return next
      },
      { replace: true },
    )
  }

  // Phase 6-D (docs/production-ux-workflow-redesign.md 6章/11章): Dashboard's
  // 要確認 KPI counts orders with an active Attention. Phase 8-J 3章: now a
  // real Backend query param (hasAttention, see OrderHistoryService.list) -
  // was a client-side display filter over the full fetched set until this
  // Phase, which would only have filtered the current page once List itself
  // became Backend-paginated below.
  const hasAttentionOnly = searchParams.get('hasAttention') === 'true'

  function toggleHasAttentionOnly(checked: boolean) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (checked) next.set('hasAttention', 'true')
        else next.delete('hasAttention')
        next.set('page', '0')
        return next
      },
      { replace: true },
    )
  }

  const page = Number(searchParams.get('page') ?? '0')
  const size = Number(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE))

  function changePage(newPage: number) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set('page', String(newPage))
        return next
      },
      { replace: true },
    )
  }

  function changeSize(newSize: number) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        next.set('size', String(newSize))
        next.set('page', '0')
        return next
      },
      { replace: true },
    )
  }

  const listPath = listReturnTo('/orders/history', searchParams)

  // Phase 8-J 3章/4章: Backend-paginated + Backend-filtered (including
  // hasAttentionOnly above) - this screen no longer fetches the whole
  // portal_order table then filters/slices in the browser.
  const { data, isLoading, isError, refetch } = useOrderHistory({ ...filter, hasAttentionOnly }, page, size)

  return (
    // Phase 7-F Header/List UX Audit (Sticky Table Header): see the same
    // structural note in CandidateListPage.tsx - TableContainer needs an
    // intentional bounded height (flex:1/overflow:auto below) to actually
    // be the scrolling ancestor `stickyHeader` sticks within; its default
    // `overflow-x: auto` alone claims that role without ever scrolling.
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('listTitle')}
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        {/* Phase 8-J 3章/4章: supplier/brand switched from a <select> whose
            options were derived from the (now Backend-paginated, no longer
            full-table) fetched rows - options built from a single page would
            silently miss values on other pages - to free-text input, the
            SAME convention ArrivalListPage/WarehouseStockListPage/
            StockSalesListPage already use for their own Backend-paginated
            code filters. */}
        <TextField
          size="small"
          label={t('filter.supplier')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.supplierCode ?? ''}
          onBlur={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
          data-testid="order-history-filter-supplier"
        />
        <TextField
          size="small"
          label={t('filter.brand')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.brandCode ?? ''}
          onBlur={(e) => updateFilter({ brandCode: e.target.value || undefined })}
          data-testid="order-history-filter-brand"
        />
        <TextField
          select
          size="small"
          label={t('filter.status')}
          sx={{ minWidth: 200 }}
          value={filter.status ?? ''}
          onChange={(e) => updateFilter({ status: e.target.value || undefined })}
        >
          <MenuItem value="">{t('filter.all')}</MenuItem>
          {STATUS_OPTIONS.map((s) => (
            <MenuItem key={s} value={s}>{t(`status:orderStatus.${s}`)}</MenuItem>
          ))}
        </TextField>
        <TextField
          size="small"
          label={t('filter.orderNoKeyword')}
          placeholder={t('filter.orderNoKeywordPlaceholder') ?? undefined}
          sx={{ minWidth: 220 }}
          value={orderNoKeywordInput}
          onChange={(e) => setOrderNoKeywordInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && updateFilter({ orderNoKeyword: orderNoKeywordInput || undefined })}
          onBlur={() => updateFilter({ orderNoKeyword: orderNoKeywordInput || undefined })}
          data-testid="order-no-keyword-input"
        />
        <TextField
          size="small"
          label={t('filter.itemKeyword')}
          placeholder={t('filter.itemKeywordPlaceholder') ?? undefined}
          sx={{ minWidth: 220 }}
          value={itemKeywordInput}
          onChange={(e) => setItemKeywordInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && updateFilter({ itemKeyword: itemKeywordInput || undefined })}
          onBlur={() => updateFilter({ itemKeyword: itemKeywordInput || undefined })}
          data-testid="item-keyword-input"
        />
        <TextField
          label={t('filter.updatedFrom')}
          type="date"
          size="small"
          value={filter.updatedFrom ?? ''}
          onChange={(e) => updateFilter({ updatedFrom: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
          data-testid="updated-from-input"
        />
        <TextField
          label={t('filter.updatedTo')}
          type="date"
          size="small"
          value={filter.updatedTo ?? ''}
          onChange={(e) => updateFilter({ updatedTo: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
          data-testid="updated-to-input"
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={hasAttentionOnly}
              onChange={(e) => toggleHasAttentionOnly(e.target.checked)}
              data-testid="has-attention-only-checkbox"
            />
          }
          label={t('filter.hasAttentionOnly')}
        />
      </Stack>

      {isLoading && (
        <Stack direction="row" spacing={1} sx={{ my: 4, alignItems: 'center' }}>
          <CircularProgress size={20} />
          <Typography>{t('common:loading')}</Typography>
        </Stack>
      )}

      {isError && (
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>{t('common:retry')}</Button>} sx={{ my: 2 }}>
          {t('common:errorGeneric')}
        </Alert>
      )}

      {!isLoading && !isError && data && data.content.length === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>{t('empty')}</Alert>
      )}

      {!isLoading && !isError && data && data.content.length > 0 && (
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('resultCount', { count: data.totalElements })}
          </Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="order-history-table-container">
            <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  {/* Phase 1 Final Cleanup (Order History Number Model Audit):
                      Portal管理番号 (Portal-internal tracking) and 正式PO番号
                      (Official PO No. - the Excel/PDF/Manufacturer Send
                      source of truth) are now shown as two distinct columns,
                      reusing the same terms/tooltip already established on
                      Order Detail (portalPoNoCaption) rather than the
                      previously-ambiguous single "PO No." column. */}
                  <TableCell>
                    <Tooltip title={t('portalPoNoCaptionTooltip')}>
                      <span>{t('portalPoNoCaption')}</span>
                    </Tooltip>
                  </TableCell>
                  <TableCell>{t('table.officialPoNo')}</TableCell>
                  <TableCell>{t('officialPoIntegration.revisionLabel')}</TableCell>
                  <TableCell>{t('table.orderDate')}</TableCell>
                  <TableCell>{t('table.supplier')}</TableCell>
                  <TableCell>{t('table.brand')}</TableCell>
                  <TableCell align="right">{t('table.skuCount')}</TableCell>
                  <TableCell align="right">{t('table.totalOrderedQty')}</TableCell>
                  <TableCell align="right">{t('table.totalAmount')}</TableCell>
                  <TableCell>{t('table.status')}</TableCell>
                  <TableCell>{t('table.attention')}</TableCell>
                  <TableCell>{t('table.updatedAt')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.map((row) => (
                  <TableRow
                    key={row.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(withReturnTo(`/orders/${row.id}`, listPath))}
                  >
                    <TableCell data-testid="order-history-management-no">{row.prototypePoNo ?? row.draftNo}</TableCell>
                    <TableCell data-testid="order-history-official-po-no">
                      {row.officialPoNo ?? t('officialPoIntegration.officialPoNoUnassigned')}
                    </TableCell>
                    <TableCell data-testid="order-history-revision">
                      {row.revisionNo ?? '—'}
                    </TableCell>
                    <TableCell>{row.orderDate ?? '—'}</TableCell>
                    <TableCell>{row.supplierName ?? row.supplierCode}</TableCell>
                    <TableCell>{row.brandName ?? row.brandCode}</TableCell>
                    <TableCell align="right">{row.skuCount}</TableCell>
                    <TableCell align="right">{row.totalOrderedQty}</TableCell>
                    <TableCell align="right">¥{row.totalAmount.toLocaleString()}</TableCell>
                    <TableCell><OrderStatusChip status={row.status} /></TableCell>
                    <TableCell><AttentionTypeBadges types={row.activeAttentionTypes} /></TableCell>
                    <TableCell>{new Date(row.updatedAt).toLocaleString('ja-JP')}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          <TablePagination
            component="div"
            count={data.totalElements}
            page={data.page}
            rowsPerPage={data.size}
            rowsPerPageOptions={[10, 20, 50, 100]}
            onPageChange={(_e, newPage) => changePage(newPage)}
            onRowsPerPageChange={(e) => changeSize(Number(e.target.value))}
            labelRowsPerPage={t('common:rowsPerPage', { defaultValue: 'Rows per page:' })}
            data-testid="order-history-pagination"
          />
        </Box>
      )}
    </Box>
  )
}
