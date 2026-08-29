import { useMemo } from 'react'
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
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import Chip from '@mui/material/Chip'
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
const FILTER_PARAMS = ['supplierCode', 'brandCode', 'status'] as const

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
  }

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
        return next
      },
      { replace: true },
    )
  }

  // Phase 6-D (docs/production-ux-workflow-redesign.md 6章/11章): Dashboard's
  // 要確認 KPI counts orders with an active Attention over the SAME
  // unfiltered order set this screen already fetches - activeAttentionTypes
  // is already on every row (OrderHistorySummaryResponse), so this is a
  // pure client-side display filter, not a new Backend/API Attention Filter.
  const hasAttentionOnly = searchParams.get('hasAttention') === 'true'

  function toggleHasAttentionOnly(checked: boolean) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (checked) next.set('hasAttention', 'true')
        else next.delete('hasAttention')
        return next
      },
      { replace: true },
    )
  }

  const listPath = listReturnTo('/orders/history', searchParams)

  const { data, isLoading, isError, refetch } = useOrderHistory(filter)

  const visibleData = useMemo(() => {
    if (!data) return data
    return hasAttentionOnly ? data.filter((row) => row.activeAttentionTypes.length > 0) : data
  }, [data, hasAttentionOnly])

  const supplierOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of data ?? []) {
      if (row.supplierCode) map.set(row.supplierCode, row.supplierName ?? row.supplierCode)
    }
    return Array.from(map.entries())
  }, [data])

  const brandOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of data ?? []) {
      if (row.brandCode) map.set(row.brandCode, row.brandName ?? row.brandCode)
    }
    return Array.from(map.entries())
  }, [data])

  return (
    // Phase 7-F Header/List UX Audit (Sticky Table Header): see the same
    // structural note in CandidateListPage.tsx - TableContainer needs an
    // intentional bounded height (flex:1/overflow:auto below) to actually
    // be the scrolling ancestor `stickyHeader` sticks within; its default
    // `overflow-x: auto` alone claims that role without ever scrolling.
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('listTitle')}
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <TextField
          select
          size="small"
          label={t('filter.supplier')}
          sx={{ minWidth: 200 }}
          value={filter.supplierCode ?? ''}
          onChange={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
        >
          <MenuItem value="">{t('filter.all')}</MenuItem>
          {supplierOptions.map(([code, name]) => (
            <MenuItem key={code} value={code}>{name}</MenuItem>
          ))}
        </TextField>
        <TextField
          select
          size="small"
          label={t('filter.brand')}
          sx={{ minWidth: 200 }}
          value={filter.brandCode ?? ''}
          onChange={(e) => updateFilter({ brandCode: e.target.value || undefined })}
        >
          <MenuItem value="">{t('filter.all')}</MenuItem>
          {brandOptions.map(([code, name]) => (
            <MenuItem key={code} value={code}>{name}</MenuItem>
          ))}
        </TextField>
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

      {!isLoading && !isError && visibleData && visibleData.length === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>{t('empty')}</Alert>
      )}

      {!isLoading && !isError && visibleData && visibleData.length > 0 && (
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('resultCount', { count: visibleData.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 0 }} data-testid="order-history-table-container">
            <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('table.poNo')}</TableCell>
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
                {visibleData.map((row) => (
                  <TableRow
                    key={row.id}
                    hover
                    sx={{ cursor: 'pointer' }}
                    onClick={() => navigate(withReturnTo(`/orders/${row.id}`, listPath))}
                  >
                    <TableCell>{row.prototypePoNo ?? row.draftNo}</TableCell>
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
        </Box>
      )}
    </Box>
  )
}
