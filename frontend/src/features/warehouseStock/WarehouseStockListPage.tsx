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
import CloseIcon from '@mui/icons-material/Close'

import { useWarehouseStockDetail, useWarehouseStockList } from './api'
import type { WarehouseStockListFilter } from './api'
import { isSafeInternalPath, listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'

const FILTER_PARAMS = ['skuKeyword', 'brandCode', 'warehouseCode', 'minQty', 'maxQty'] as const
const DEFAULT_PAGE_SIZE = 20

function qty(value: number | null): string {
  return value == null ? '—' : String(value)
}

/**
 * Warehouse Stock Detail Drawer (Phase 8-G 10章) - "全WarehouseのQty" for
 * one SKU, rendered as a Drawer rather than a new top-level Route/Screen
 * (10章's explicit "画面を不必要に増やさない" instruction).
 */
function WarehouseStockDrawer({ sku, onClose, listPath }: { sku: string | null; onClose: () => void; listPath: string }) {
  const { t } = useTranslation(['warehouseStock', 'common'])
  const navigate = useNavigate()
  const { data, isLoading, isError } = useWarehouseStockDetail(sku)

  return (
    <Drawer anchor="right" open={sku != null} onClose={onClose} data-testid="warehouse-stock-drawer">
      <Box sx={{ width: 420, p: 3 }}>
        <Stack direction="row" sx={{ alignItems: 'center', mb: 2 }}>
          <Typography variant="h6" sx={{ flexGrow: 1 }}>{t('drawer.title')}</Typography>
          <IconButton size="small" onClick={onClose} data-testid="warehouse-stock-drawer-close"><CloseIcon fontSize="small" /></IconButton>
        </Stack>
        {isLoading && <CircularProgress size={20} />}
        {isError && <Alert severity="error">{t('common:errorGeneric')}</Alert>}
        {data && (
          <>
            <Typography variant="subtitle1">{data.sku}</Typography>
            <Typography variant="body2" color="text.secondary" gutterBottom>
              {data.itemName ?? '—'} / {data.brandName ?? data.brandCode ?? '—'}
            </Typography>
            {/* Phase 8-J 8章: SKU used only as a search condition / direct
                identifier lookup on these 2 other screens - never a
                Warehouse Stock <-> Arrival link (that stays forbidden, no
                Source-confirmed Key joins them - see
                docs/legacy-warehouse-logistics-logizero-reverse-engineering.md). */}
            <Stack direction="row" spacing={1} sx={{ mb: 1 }}>
              <Button
                size="small"
                onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(data.sku)}`, listPath))}
                data-testid="warehouse-stock-drawer-sku-detail-link"
              >
                {t('drawer.viewSkuDetail')}
              </Button>
              <Button
                size="small"
                onClick={() => navigate(withReturnTo(`/stock-sales?skuKeyword=${encodeURIComponent(data.sku)}`, listPath))}
                data-testid="warehouse-stock-drawer-stock-sales-link"
              >
                {t('drawer.viewStockSales')}
              </Button>
            </Stack>
            <TableContainer sx={{ mt: 2 }}>
              <Table size="small" data-testid="warehouse-stock-drawer-table">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('table.warehouseCode')}</TableCell>
                    <TableCell align="right">{t('table.stockQty')}</TableCell>
                    <TableCell>{t('table.updatedAt')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.warehouses.map((w) => (
                    <TableRow key={w.warehouseCode} data-testid={`warehouse-stock-drawer-row-${w.warehouseCode}`}>
                      <TableCell><Chip size="small" label={w.warehouseCode} /></TableCell>
                      <TableCell align="right">{qty(w.stockQty)}</TableCell>
                      <TableCell>{w.updatedAt ? new Date(w.updatedAt).toLocaleString('ja-JP') : '—'}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </>
        )}
      </Box>
    </Drawer>
  )
}

/**
 * Warehouse Stock List (Phase 8-G 8章/9章) - "倉庫在庫". Backend-paginated +
 * Backend-filtered, same principle as ArrivalListPage. warehouseCode is
 * rendered as a bare Chip everywhere on this screen - no Japanese warehouse
 * name is ever invented for it (Phase 8-G 8章/17章).
 */
export function WarehouseStockListPage() {
  const { t } = useTranslation(['warehouseStock', 'common'])
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const [drawerSku, setDrawerSku] = useState<string | null>(null)

  // Phase 8-M (Global Navigation Audit): same conditional-Back-button
  // convention as ArrivalListPage - shown only when reached via an incoming
  // returnTo (e.g. from Stock/Sales' Drawer); otherwise this stays a plain
  // top-level Nav destination with no Back button (Principle E).
  const incomingReturnTo = searchParams.get('returnTo')
  const ownBackTarget = incomingReturnTo && isSafeInternalPath(incomingReturnTo) ? incomingReturnTo : null
  const listPath = listReturnTo('/warehouse-stock', searchParams)

  const filter: WarehouseStockListFilter = {
    skuKeyword: searchParams.get('skuKeyword') ?? undefined,
    brandCode: searchParams.get('brandCode') ?? undefined,
    warehouseCode: searchParams.get('warehouseCode') ?? undefined,
    minQty: searchParams.get('minQty') ? Number(searchParams.get('minQty')) : undefined,
    maxQty: searchParams.get('maxQty') ? Number(searchParams.get('maxQty')) : undefined,
  }
  const page = Number(searchParams.get('page') ?? '0')
  const size = Number(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE))

  function updateFilter(patch: Partial<WarehouseStockListFilter>) {
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

  const { data, isLoading, isError, refetch } = useWarehouseStockList(filter, page, size)

  // IA Audit §12 (docs/gops-information-architecture-cross-screen-audit.md /
  // gops-master-maintenance-hub-implementation.md): "現在何を見ているのか"
  // Filter Chips - same pattern as CandidateListPage/StockSalesListPage.
  const activeFilterChips = [
    filter.brandCode ? { key: 'brandCode', label: `${t('filter.brandCode')}: ${filter.brandCode}`, onDelete: () => updateFilter({ brandCode: undefined }) } : null,
    filter.warehouseCode ? { key: 'warehouseCode', label: `${t('filter.warehouseCode')}: ${filter.warehouseCode}`, onDelete: () => updateFilter({ warehouseCode: undefined }) } : null,
    filter.skuKeyword ? { key: 'skuKeyword', label: `${t('filter.skuKeyword')}: ${filter.skuKeyword}`, onDelete: () => updateFilter({ skuKeyword: undefined }) } : null,
  ].filter((c): c is { key: string; label: string; onDelete: () => void } => c !== null)

  return (
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" component="h1" gutterBottom sx={{ mb: 0 }}>
          {t('listTitle')}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {ownBackTarget && (
          <Button size="small" onClick={() => navigate(ownBackTarget)} data-testid="back-to-warehouse-stock-origin">
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
          data-testid="warehouse-stock-filter-sku"
        />
        <TextField
          key={`brand-${filter.brandCode ?? ''}`}
          size="small"
          label={t('filter.brandCode')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.brandCode ?? ''}
          onBlur={(e) => updateFilter({ brandCode: e.target.value || undefined })}
          data-testid="warehouse-stock-filter-brand"
        />
        <TextField
          key={`warehouse-${filter.warehouseCode ?? ''}`}
          size="small"
          label={t('filter.warehouseCode')}
          sx={{ minWidth: 160 }}
          defaultValue={filter.warehouseCode ?? ''}
          onBlur={(e) => updateFilter({ warehouseCode: e.target.value || undefined })}
          data-testid="warehouse-stock-filter-wh-code"
        />
        <TextField
          size="small"
          type="number"
          label={t('filter.minQty')}
          sx={{ minWidth: 120 }}
          defaultValue={filter.minQty ?? ''}
          onBlur={(e) => updateFilter({ minQty: e.target.value ? Number(e.target.value) : undefined })}
          data-testid="warehouse-stock-filter-min-qty"
        />
        <TextField
          size="small"
          type="number"
          label={t('filter.maxQty')}
          sx={{ minWidth: 120 }}
          defaultValue={filter.maxQty ?? ''}
          onBlur={(e) => updateFilter({ maxQty: e.target.value ? Number(e.target.value) : undefined })}
          data-testid="warehouse-stock-filter-max-qty"
        />
      </Stack>

      {activeFilterChips.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1 }} data-testid="warehouse-stock-active-filter-chips">
          {activeFilterChips.map((chip) => (
            <Chip key={chip.key} size="small" label={chip.label} onDelete={chip.onDelete} data-testid={`warehouse-stock-filter-chip-${chip.key}`} />
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
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="warehouse-stock-table-container">
            <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('table.sku')}</TableCell>
                  <TableCell>{t('table.itemName')}</TableCell>
                  <TableCell>{t('table.brand')}</TableCell>
                  <TableCell>{t('table.warehouseCode')}</TableCell>
                  <TableCell align="right">{t('table.stockQty')}</TableCell>
                  <TableCell>{t('table.updatedAt')}</TableCell>
                  <TableCell />
                </TableRow>
              </TableHead>
              <TableBody>
                {data.content.map((row) => (
                  <TableRow key={`${row.warehouseCode}-${row.sku}`} hover data-testid={`warehouse-stock-row-${row.warehouseCode}-${row.sku}`}>
                    <TableCell>{row.sku}</TableCell>
                    <TableCell>{row.itemName ?? '—'}</TableCell>
                    <TableCell>{row.brandName ?? row.brandCode ?? '—'}</TableCell>
                    <TableCell><Chip size="small" label={row.warehouseCode} /></TableCell>
                    <TableCell align="right">{qty(row.stockQty)}</TableCell>
                    <TableCell>{row.updatedAt ? new Date(row.updatedAt).toLocaleString('ja-JP') : '—'}</TableCell>
                    <TableCell>
                      <Button size="small" onClick={() => setDrawerSku(row.sku)} data-testid={`warehouse-stock-detail-button-${row.sku}`}>
                        {t('viewAllWarehouses')}
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
            data-testid="warehouse-stock-pagination"
          />
        </Box>
      )}

      <WarehouseStockDrawer sku={drawerSku} onClose={() => setDrawerSku(null)} listPath={listPath} />
    </Box>
  )
}
