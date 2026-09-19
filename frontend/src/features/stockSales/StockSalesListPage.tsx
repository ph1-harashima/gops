import { useState } from 'react'
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
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Drawer from '@mui/material/Drawer'
import IconButton from '@mui/material/IconButton'
import Tooltip from '@mui/material/Tooltip'
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined'
import CloseIcon from '@mui/icons-material/Close'

import { useStockSalesDetail, useStockSalesList } from './api'
import type { StockSalesListFilter } from './api'
import { isSafeInternalPath, listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'

const FILTER_PARAMS = ['skuKeyword', 'brandCode', 'supplierCode', 'minStock', 'maxStock', 'minSales', 'maxSales'] as const
const DEFAULT_PAGE_SIZE = 20

function qty(value: number | null): string {
  return value == null ? '—' : String(value)
}

/**
 * SKU Detail Drawer (Phase 8-H 7章) - no new Route/screen. Re-fetches via
 * GET /api/stock-sales/{sku} rather than reusing the already-fetched List
 * row, same freshness principle as Phase 8-G's WarehouseStockDrawer.
 * Navigates to Warehouse Stock / SKU Detail rather than embedding either
 * (13章: "Business Joinはしない", "可能ならWarehouse Stock画面への
 * Navigationを優先").
 */
function StockSalesDrawer({ sku, onClose, listPath }: { sku: string | null; onClose: () => void; listPath: string }) {
  const { t } = useTranslation(['stockSales', 'common'])
  const navigate = useNavigate()
  const { data, isLoading, isError } = useStockSalesDetail(sku)

  return (
    <Drawer anchor="right" open={sku != null} onClose={onClose} data-testid="stock-sales-drawer">
      <Box sx={{ width: 440, p: 3 }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>{t('drawer.title')}</Typography>
          <IconButton size="small" onClick={onClose} data-testid="stock-sales-drawer-close"><CloseIcon fontSize="small" /></IconButton>
        </Stack>
        {isLoading && <CircularProgress size={20} />}
        {isError && <Alert severity="error">{t('common:errorGeneric')}</Alert>}
        {data && (
          <Stack spacing={2}>
            <Box>
              <Typography variant="subtitle1">{data.sku}</Typography>
              <Typography variant="body2" color="text.secondary">
                {data.itemName ?? '—'} / {data.brandName ?? data.brandCode ?? '—'} / {data.supplierName ?? data.supplierCode ?? '—'}
              </Typography>
            </Box>
            <Stack spacing={1}>
              <Typography variant="body2">{t('field.currentStock')}: <strong>{qty(data.currentStock)}</strong></Typography>
              <Typography variant="body2">
                {t('field.currentMonthSalesQty')}
                <Tooltip title={t('tooltip.currentMonthSalesQty')}>
                  <InfoOutlinedIcon fontSize="inherit" sx={{ ml: 0.5, verticalAlign: 'middle' }} />
                </Tooltip>
                : <strong>{qty(data.currentMonthSalesQty)}</strong>
              </Typography>
              <Typography variant="body2">{t('field.openPoQty')}: <strong>{qty(data.openPoQty)}</strong></Typography>
              <Typography variant="body2">{t('field.openArrivalQty')}: <strong>{qty(data.openArrivalQty)}</strong></Typography>
              <Typography variant="body2" color="text.secondary">{t('field.recommendedQty')}（{t('referenceValue')}）: {qty(data.recommendedQty)}</Typography>
              <Typography variant="body2" color="text.secondary">
                {t('field.updatedAt')}: {data.updatedAt ? new Date(data.updatedAt).toLocaleString('ja-JP') : '—'}
              </Typography>
            </Stack>
            <Stack direction="row" spacing={1}>
              <Button
                size="small"
                variant="outlined"
                onClick={() => navigate(withReturnTo(`/warehouse-stock?skuKeyword=${encodeURIComponent(data.sku)}`, listPath))}
                data-testid="stock-sales-drawer-warehouse-stock-link"
              >
                {t('viewWarehouseStock')}
              </Button>
              <Button
                size="small"
                variant="outlined"
                onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(data.sku)}`, listPath))}
                data-testid="stock-sales-drawer-sku-detail-link"
              >
                {t('viewSkuDetail')}
              </Button>
            </Stack>
          </Stack>
        )}
      </Box>
    </Drawer>
  )
}

/**
 * Stock/Sales List (Phase 8-H 4章) - "在庫・販売確認". Backend-paginated +
 * Backend-filtered. SOLD_QTY-derived currentMonthSalesQty is always labeled
 * with its Current-Month-Cumulative meaning (5章) - never rendered as a
 * Trend/Daily/Recent figure anywhere on this screen.
 */
export function StockSalesListPage() {
  const { t } = useTranslation(['stockSales', 'common'])
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [drawerSku, setDrawerSku] = useState<string | null>(null)

  // Phase 8-M (Global Navigation Audit): same conditional-Back-button
  // convention as ArrivalListPage/WarehouseStockListPage.
  const incomingReturnTo = searchParams.get('returnTo')
  const ownBackTarget = incomingReturnTo && isSafeInternalPath(incomingReturnTo) ? incomingReturnTo : null
  const listPath = listReturnTo('/stock-sales', searchParams)

  const filter: StockSalesListFilter = {
    skuKeyword: searchParams.get('skuKeyword') ?? undefined,
    brandCode: searchParams.get('brandCode') ?? undefined,
    supplierCode: searchParams.get('supplierCode') ?? undefined,
    minStock: searchParams.get('minStock') ? Number(searchParams.get('minStock')) : undefined,
    maxStock: searchParams.get('maxStock') ? Number(searchParams.get('maxStock')) : undefined,
    minSales: searchParams.get('minSales') ? Number(searchParams.get('minSales')) : undefined,
    maxSales: searchParams.get('maxSales') ? Number(searchParams.get('maxSales')) : undefined,
  }
  const page = Number(searchParams.get('page') ?? '0')
  const size = Number(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE))

  function updateFilter(patch: Partial<StockSalesListFilter>) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        const merged = { ...filter, ...patch }
        for (const key of FILTER_PARAMS) {
          const value = merged[key]
          if (value !== undefined && value !== '') next.set(key, String(value))
          else next.delete(key)
        }
        next.set('page', '0')
        return next
      },
      { replace: true },
    )
  }

  function changePage(newPage: number) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('page', String(newPage))
      return next
    }, { replace: true })
  }

  function changeSize(newSize: number) {
    setSearchParams((prev) => {
      const next = new URLSearchParams(prev)
      next.set('size', String(newSize))
      next.set('page', '0')
      return next
    }, { replace: true })
  }

  const { data, isLoading, isError, refetch } = useStockSalesList(filter, page, size)

  // IA Audit §11 (docs/gops-information-architecture-cross-screen-audit.md /
  // gops-master-maintenance-hub-implementation.md): "現在何を見ているのか"
  // Filter Chips, same visual pattern CandidateListPage already established -
  // Brand/Supplier/SKU Keyword are the identity-context Filters (min/max
  // stock/sales are refinements, not "what am I looking at" Context).
  const activeFilterChips = [
    filter.brandCode ? { key: 'brandCode', label: `${t('filter.brandCode')}: ${filter.brandCode}`, onDelete: () => updateFilter({ brandCode: undefined }) } : null,
    filter.supplierCode ? { key: 'supplierCode', label: `${t('filter.supplierCode')}: ${filter.supplierCode}`, onDelete: () => updateFilter({ supplierCode: undefined }) } : null,
    filter.skuKeyword ? { key: 'skuKeyword', label: `${t('filter.skuKeyword')}: ${filter.skuKeyword}`, onDelete: () => updateFilter({ skuKeyword: undefined }) } : null,
  ].filter((c): c is { key: string; label: string; onDelete: () => void } => c !== null)

  return (
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" component="h1" gutterBottom sx={{ mb: 0 }}>
          {t('listTitle')}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {ownBackTarget && (
          <Button size="small" onClick={() => navigate(ownBackTarget)} data-testid="back-to-stock-sales-origin">
            {t('back')}
          </Button>
        )}
      </Stack>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <TextField
          key={`sku-${filter.skuKeyword ?? ''}`}
          size="small"
          label={t('filter.skuKeyword')}
          sx={{ minWidth: 200 }}
          defaultValue={filter.skuKeyword ?? ''}
          onBlur={(e) => updateFilter({ skuKeyword: e.target.value || undefined })}
          data-testid="stock-sales-filter-sku"
        />
        <TextField
          key={`brand-${filter.brandCode ?? ''}`}
          size="small"
          label={t('filter.brandCode')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.brandCode ?? ''}
          onBlur={(e) => updateFilter({ brandCode: e.target.value || undefined })}
          data-testid="stock-sales-filter-brand"
        />
        <TextField
          key={`supplier-${filter.supplierCode ?? ''}`}
          size="small"
          label={t('filter.supplierCode')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.supplierCode ?? ''}
          onBlur={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
          data-testid="stock-sales-filter-supplier"
        />
        <TextField
          size="small"
          type="number"
          label={t('filter.minStock')}
          sx={{ minWidth: 120 }}
          defaultValue={filter.minStock ?? ''}
          onBlur={(e) => updateFilter({ minStock: e.target.value ? Number(e.target.value) : undefined })}
          data-testid="stock-sales-filter-min-stock"
        />
        <TextField
          size="small"
          type="number"
          label={t('filter.maxStock')}
          sx={{ minWidth: 120 }}
          defaultValue={filter.maxStock ?? ''}
          onBlur={(e) => updateFilter({ maxStock: e.target.value ? Number(e.target.value) : undefined })}
          data-testid="stock-sales-filter-max-stock"
        />
        <TextField
          size="small"
          type="number"
          label={t('filter.minSales')}
          sx={{ minWidth: 260 }}
          defaultValue={filter.minSales ?? ''}
          onBlur={(e) => updateFilter({ minSales: e.target.value ? Number(e.target.value) : undefined })}
          data-testid="stock-sales-filter-min-sales"
        />
        <TextField
          size="small"
          type="number"
          label={t('filter.maxSales')}
          sx={{ minWidth: 260 }}
          defaultValue={filter.maxSales ?? ''}
          onBlur={(e) => updateFilter({ maxSales: e.target.value ? Number(e.target.value) : undefined })}
          data-testid="stock-sales-filter-max-sales"
        />
      </Stack>

      {activeFilterChips.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1 }} data-testid="stock-sales-active-filter-chips">
          {activeFilterChips.map((chip) => (
            <Chip key={chip.key} size="small" label={chip.label} onDelete={chip.onDelete} data-testid={`stock-sales-filter-chip-${chip.key}`} />
          ))}
        </Stack>
      )}

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
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="stock-sales-table-container">
            <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('table.sku')}</TableCell>
                  <TableCell>{t('table.itemName')}</TableCell>
                  <TableCell>{t('table.brand')}</TableCell>
                  <TableCell>{t('table.supplier')}</TableCell>
                  <TableCell align="right">{t('table.currentStock')}</TableCell>
                  <TableCell align="right">
                    {t('table.currentMonthSalesQty')}
                    <Tooltip title={t('tooltip.currentMonthSalesQty')}>
                      <InfoOutlinedIcon fontSize="inherit" sx={{ ml: 0.5, verticalAlign: 'middle' }} />
                    </Tooltip>
                  </TableCell>
                  <TableCell align="right">{t('table.openPoQty')}</TableCell>
                  <TableCell align="right">{t('table.openArrivalQty')}</TableCell>
                  <TableCell>{t('table.updatedAt')}</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.map((row) => (
                  <TableRow key={row.sku} hover data-testid={`stock-sales-row-${row.sku}`}>
                    <TableCell>{row.sku}</TableCell>
                    <TableCell>{row.itemName ?? '—'}</TableCell>
                    <TableCell>{row.brandName ?? row.brandCode ?? '—'}</TableCell>
                    <TableCell>{row.supplierName ?? row.supplierCode ?? '—'}</TableCell>
                    <TableCell align="right">{qty(row.currentStock)}</TableCell>
                    <TableCell align="right">{qty(row.currentMonthSalesQty)}</TableCell>
                    <TableCell align="right">{qty(row.openPoQty)}</TableCell>
                    <TableCell align="right">{qty(row.openArrivalQty)}</TableCell>
                    <TableCell>{row.updatedAt ? new Date(row.updatedAt).toLocaleString('ja-JP') : '—'}</TableCell>
                    <TableCell>
                      <Button size="small" onClick={() => setDrawerSku(row.sku)} data-testid={`stock-sales-detail-button-${row.sku}`}>
                        {t('viewDetail')}
                      </Button>
                    </TableCell>
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
            data-testid="stock-sales-pagination"
          />
        </Box>
      )}

      <StockSalesDrawer sku={drawerSku} onClose={() => setDrawerSku(null)} listPath={listPath} />
    </Box>
  )
}
