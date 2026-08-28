import { useMemo, useState } from 'react'
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
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'

import { useOrderHistory } from './api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import type { OrderHistoryFilter } from './api'

const STATUS_OPTIONS = ['DRAFT', 'READY_TO_ORDER', 'AWAITING_SUPPLIER', 'SUPPLIER_CONFIRMED']

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
  // Dashboard deep-links here with ?brandCode=...&status=... (implementation
  // instructions Step 5 3章) - only ever read once as the initial filter.
  const [searchParams] = useSearchParams()
  const [filter, setFilter] = useState<OrderHistoryFilter>({
    brandCode: searchParams.get('brandCode') ?? undefined,
    status: searchParams.get('status') ?? undefined,
  })

  const { data, isLoading, isError, refetch } = useOrderHistory(filter)

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
    <Box sx={{ p: 3 }}>
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
          onChange={(e) => setFilter((prev) => ({ ...prev, supplierCode: e.target.value || undefined }))}
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
          onChange={(e) => setFilter((prev) => ({ ...prev, brandCode: e.target.value || undefined }))}
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
          onChange={(e) => setFilter((prev) => ({ ...prev, status: e.target.value || undefined }))}
        >
          <MenuItem value="">{t('filter.all')}</MenuItem>
          {STATUS_OPTIONS.map((s) => (
            <MenuItem key={s} value={s}>{t(`status:orderStatus.${s}`)}</MenuItem>
          ))}
        </TextField>
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

      {!isLoading && !isError && data && data.length === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>{t('empty')}</Alert>
      )}

      {!isLoading && !isError && data && data.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('resultCount', { count: data.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" stickyHeader>
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
                {data.map((row) => (
                  <TableRow key={row.id} hover sx={{ cursor: 'pointer' }} onClick={() => navigate(`/orders/${row.id}`)}>
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
        </>
      )}
    </Box>
  )
}
