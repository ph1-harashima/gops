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
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'

import { useArrivalList } from './api'
import type { ArrivalListFilter } from './api'
import { isSafeInternalPath, listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'

const FILTER_PARAMS = ['supplierCode', 'brandCode', 'poNumber', 'invoiceNumber', 'skuKeyword', 'arrivalDateFrom', 'arrivalDateTo'] as const
const DEFAULT_PAGE_SIZE = 20

function qty(value: number | null): string {
  return value == null ? '—' : String(value)
}

/**
 * Arrival List (Phase 8-G 5章) - "入荷確認". Every Filter param and the Page
 * itself is Backend-driven (useArrivalList calls GET /api/arrivals with
 * page/size query params) - this component never fetches everything and
 * filters/paginates in the browser (15章's explicit instruction).
 */
export function ArrivalListPage() {
  const { t } = useTranslation(['arrivals', 'common'])
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()

  const filter: ArrivalListFilter = {
    supplierCode: searchParams.get('supplierCode') ?? undefined,
    brandCode: searchParams.get('brandCode') ?? undefined,
    poNumber: searchParams.get('poNumber') ?? undefined,
    invoiceNumber: searchParams.get('invoiceNumber') ?? undefined,
    skuKeyword: searchParams.get('skuKeyword') ?? undefined,
    arrivalDateFrom: searchParams.get('arrivalDateFrom') ?? undefined,
    arrivalDateTo: searchParams.get('arrivalDateTo') ?? undefined,
  }
  const page = Number(searchParams.get('page') ?? '0')
  const size = Number(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE))

  // Phase 8-M (Global Navigation Audit): Arrival List can be reached both as
  // a top-level Nav destination (no incoming returnTo - Direct Access/Nav
  // fallback per Principle E) and from SKU Detail / Order Detail's
  // Fulfillment section (with an incoming returnTo). Its own current URL
  // (Filters+Page included) becomes the returnTo carried into Arrival Detail,
  // same convention as the other List screens (reused as-is).
  const incomingReturnTo = searchParams.get('returnTo')
  const ownBackTarget = incomingReturnTo && isSafeInternalPath(incomingReturnTo) ? incomingReturnTo : null
  const listPath = listReturnTo('/arrivals', searchParams)

  function updateFilter(patch: Partial<ArrivalListFilter>) {
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

  const { data, isLoading, isError, refetch } = useArrivalList(filter, page, size)

  return (
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" component="h1" gutterBottom sx={{ mb: 0 }}>
          {t('listTitle')}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {ownBackTarget && (
          <Button size="small" onClick={() => navigate(ownBackTarget)} data-testid="back-to-arrival-origin">
            {t('back')}
          </Button>
        )}
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <TextField
          size="small"
          label={t('filter.supplierCode')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.supplierCode ?? ''}
          onBlur={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
          data-testid="arrival-filter-supplier"
        />
        <TextField
          size="small"
          label={t('filter.brandCode')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.brandCode ?? ''}
          onBlur={(e) => updateFilter({ brandCode: e.target.value || undefined })}
          data-testid="arrival-filter-brand"
        />
        <TextField
          size="small"
          label={t('filter.poNumber')}
          sx={{ minWidth: 180 }}
          defaultValue={filter.poNumber ?? ''}
          onBlur={(e) => updateFilter({ poNumber: e.target.value || undefined })}
          data-testid="arrival-filter-po-number"
        />
        <TextField
          size="small"
          label={t('filter.invoiceNumber')}
          sx={{ minWidth: 180 }}
          defaultValue={filter.invoiceNumber ?? ''}
          onBlur={(e) => updateFilter({ invoiceNumber: e.target.value || undefined })}
          data-testid="arrival-filter-invoice-number"
        />
        <TextField
          size="small"
          label={t('filter.skuKeyword')}
          sx={{ minWidth: 180 }}
          defaultValue={filter.skuKeyword ?? ''}
          onBlur={(e) => updateFilter({ skuKeyword: e.target.value || undefined })}
          data-testid="arrival-filter-sku"
        />
        <TextField
          label={t('filter.arrivalDateFrom')}
          type="date"
          size="small"
          value={filter.arrivalDateFrom ?? ''}
          onChange={(e) => updateFilter({ arrivalDateFrom: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
          data-testid="arrival-filter-date-from"
        />
        <TextField
          label={t('filter.arrivalDateTo')}
          type="date"
          size="small"
          value={filter.arrivalDateTo ?? ''}
          onChange={(e) => updateFilter({ arrivalDateTo: e.target.value || undefined })}
          slotProps={{ inputLabel: { shrink: true } }}
          data-testid="arrival-filter-date-to"
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
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 0 }} data-testid="arrival-table-container">
            <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('table.poNumber')}</TableCell>
                  <TableCell>{t('table.invoiceNumber')}</TableCell>
                  <TableCell>{t('table.supplier')}</TableCell>
                  <TableCell>{t('table.brand')}</TableCell>
                  <TableCell>{t('table.eta')}</TableCell>
                  <TableCell align="right">{t('table.orderedQty')}</TableCell>
                  <TableCell align="right">{t('table.invoiceQty')}</TableCell>
                  <TableCell align="right">{t('table.arrivalQty')}</TableCell>
                  <TableCell align="right">{t('table.stockInQty')}</TableCell>
                  <TableCell>{t('table.warehouseReportStatus')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.map((row) => (
                  <TableRow
                    key={`${row.supplierCode}-${row.poNumber}-${row.invoiceNumber}`}
                    hover
                    sx={{ cursor: 'pointer' }}
                    data-testid={`arrival-row-${row.poNumber}`}
                    onClick={() =>
                      navigate(
                        withReturnTo(
                          `/arrivals/${encodeURIComponent(row.supplierCode ?? '')}/${encodeURIComponent(row.poNumber)}/${encodeURIComponent(row.invoiceNumber)}`,
                          listPath,
                        ),
                      )
                    }
                  >
                    <TableCell>{row.poNumber}</TableCell>
                    <TableCell>{row.invoiceNumber}</TableCell>
                    <TableCell>{row.supplierName ?? row.supplierCode}</TableCell>
                    <TableCell>{row.brandName ?? row.brandCode}</TableCell>
                    <TableCell>{row.eta ?? '—'}</TableCell>
                    <TableCell align="right">{qty(row.orderedQty)}</TableCell>
                    <TableCell align="right">{qty(row.invoiceQty)}</TableCell>
                    <TableCell align="right">{qty(row.arrivalQty)}</TableCell>
                    <TableCell align="right">{qty(row.stockInQty)}</TableCell>
                    <TableCell>{row.warehouseReportStatus ?? '—'}</TableCell>
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
            data-testid="arrival-pagination"
          />
        </Box>
      )}
    </Box>
  )
}
