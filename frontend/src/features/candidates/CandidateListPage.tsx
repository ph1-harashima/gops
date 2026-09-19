import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'

import { useOrderCandidates } from './api'
import { useCreateDraft } from '../drafts/api'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'
import { StockJudgementChip } from '../../shared/components/StockJudgementChip'
import { Toast } from '../../shared/components/Toast'
import { computeStockJudgement } from '../../shared/domain/stockJudgement'
import { listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import type { OrderCandidateFilter } from '../../shared/types/orderCandidate'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const FILTER_PARAMS = ['brandCode', 'supplierCode', 'keyword'] as const

export function CandidateListPage() {
  const { t } = useTranslation(['candidates', 'common'])
  const navigate = useNavigate()
  // Phase 6-A (docs/production-ux-workflow-redesign.md 6.2章): the URL is the
  // single source of truth for Filter state - every change below writes
  // straight back to searchParams (replace: true, so typing/selecting
  // doesn't spam browser history), and Dashboard's ?brandCode=... deep-link
  // (Step 5 3章) is read the same way as any other in-flight change, not
  // just once at mount.
  const [searchParams, setSearchParams] = useSearchParams()
  const filter: OrderCandidateFilter = {
    brandCode: searchParams.get('brandCode') ?? undefined,
    supplierCode: searchParams.get('supplierCode') ?? undefined,
    keyword: searchParams.get('keyword') ?? undefined,
  }
  // Phase 6-D (docs/production-ux-workflow-redesign.md 3章/11章): Dashboard's
  // 発注候補 KPI counts recommendedQty > 0 over the SAME unfiltered candidate
  // set this screen already fetches (DashboardService.isCandidate() - no
  // Backend/API change). recommendedOnly is therefore a pure client-side
  // display filter, deliberately NOT part of OrderCandidateFilter/
  // useOrderCandidates - it never becomes a Backend query param. Kept in
  // the URL like every other Filter so the KPI's Deep Link, Browser Back/
  // Forward, and returnTo all keep working the same way.
  const recommendedOnly = searchParams.get('recommendedOnly') === 'true'
  // Phase 7-F Header/List UX Audit (Dashboard Drill-down整合): mirrors
  // recommendedOnly exactly - a pure client-side display filter reusing the
  // SAME provisional Predicate DashboardService already counts with
  // (isOutOfStock/isLongTermOutOfStock, currentStock==0 /
  // currentStock==0&&openPo==0). No Business Rule change: this is not a new
  // definition, only making the Candidate List capable of showing the
  // identical subset the Dashboard tile/Brand row already counted, so the
  // two numbers agree instead of one going to an always-unfiltered list.
  const outOfStockOnly = searchParams.get('outOfStockOnly') === 'true'
  const longTermOutOfStockOnly = searchParams.get('longTermOutOfStockOnly') === 'true'
  const [keywordInput, setKeywordInput] = useState(filter.keyword ?? '')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  // Keeps the Keyword text field in sync when the URL changes from outside
  // a keystroke here - browser Back/Forward, a typed/bookmarked URL, or a
  // future Dashboard deep-link.
  useEffect(() => {
    setKeywordInput(searchParams.get('keyword') ?? '')
  }, [searchParams])

  function updateFilter(patch: Partial<OrderCandidateFilter>) {
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

  const listPath = listReturnTo('/candidates', searchParams)

  const { data, isLoading, isError, refetch } = useOrderCandidates(filter)
  const createDraftMutation = useCreateDraft()

  const visibleData = useMemo(() => {
    if (!data) return data
    // Phase 7-G: filters now route through the same computeStockJudgement()
    // the new 在庫判定 column/Badge renders from - previously this predicate
    // was duplicated inline here, independently from what any Badge showed
    // (there was no Badge at all). outOfStockOnly matches judgement !==
    // NORMAL (not === OUT_OF_STOCK) because 長期欠品 is a SUBSET of 欠品
    // (see stockJudgement.ts) - this keeps the exact same filtering RESULT
    // as before, only the implementation is now shared/single-sourced.
    return data
      .filter((row) => !recommendedOnly || (row.recommendedQty ?? 0) > 0)
      .filter((row) => !outOfStockOnly || computeStockJudgement(row.currentStock, row.openPo) !== 'NORMAL')
      .filter((row) => !longTermOutOfStockOnly || computeStockJudgement(row.currentStock, row.openPo) === 'LONG_TERM_OUT_OF_STOCK')
  }, [data, recommendedOnly, outOfStockOnly, longTermOutOfStockOnly])

  function toggleRecommendedOnly(checked: boolean) {
    setBooleanParam('recommendedOnly', checked)
  }

  function setBooleanParam(key: string, value: boolean) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value) next.set(key, 'true')
        else next.delete(key)
        return next
      },
      { replace: true },
    )
  }

  const brandOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of data ?? []) {
      if (row.brandCode) map.set(row.brandCode, row.brandName ?? row.brandCode)
    }
    return Array.from(map.entries())
  }, [data])

  const supplierOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of data ?? []) {
      if (row.supplierCode) map.set(row.supplierCode, row.supplierName ?? row.supplierCode)
    }
    return Array.from(map.entries())
  }, [data])

  // Phase 7-F Header/List UX Audit (Filter Chip): every active Filter -
  // Brand/Supplier/Keyword text Filters plus the three boolean display
  // Filters - surfaced as one removable Chip each, so a Dashboard Deep Link
  // (e.g. Brand + 欠品) is legible at a glance instead of only visible by
  // re-reading the Filter controls above.
  function brandOptionsLabel(code: string): string {
    return brandOptions.find(([c]) => c === code)?.[1] ?? code
  }
  function supplierOptionsLabel(code: string): string {
    return supplierOptions.find(([c]) => c === code)?.[1] ?? code
  }
  const activeFilterChips = [
    filter.brandCode ? { key: 'brandCode', label: `${t('candidates:filter.brand')}: ${brandOptionsLabel(filter.brandCode)}`, onDelete: () => updateFilter({ brandCode: undefined }) } : null,
    filter.supplierCode ? { key: 'supplierCode', label: `${t('candidates:filter.supplier')}: ${supplierOptionsLabel(filter.supplierCode)}`, onDelete: () => updateFilter({ supplierCode: undefined }) } : null,
    filter.keyword ? { key: 'keyword', label: `${t('candidates:filter.keyword')}: ${filter.keyword}`, onDelete: () => updateFilter({ keyword: undefined }) } : null,
    recommendedOnly ? { key: 'recommendedOnly', label: t('candidates:filter.recommendedOnly'), onDelete: () => toggleRecommendedOnly(false) } : null,
    outOfStockOnly ? { key: 'outOfStockOnly', label: t('candidates:filter.outOfStockOnly'), onDelete: () => setBooleanParam('outOfStockOnly', false) } : null,
    longTermOutOfStockOnly ? { key: 'longTermOutOfStockOnly', label: t('candidates:filter.longTermOutOfStockOnly'), onDelete: () => setBooleanParam('longTermOutOfStockOnly', false) } : null,
  ].filter((c): c is { key: string; label: string; onDelete: () => void } => c !== null)

  function toggleSelect(sku: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(sku)) next.delete(sku)
      else next.add(sku)
      return next
    })
  }

  function applyKeyword() {
    updateFilter({ keyword: keywordInput || undefined })
  }

  function handleCreateDraft() {
    createDraftMutation.mutate(
      { skus: Array.from(selected) },
      {
        onSuccess: (draft) => {
          setSelected(new Set())
          navigate(withReturnTo(`/orders/drafts/${draft.id}`, listPath))
        },
      },
    )
  }

  const createDraftErrorCode =
    createDraftMutation.isError && axios.isAxiosError<ApiErrorBody>(createDraftMutation.error)
      ? createDraftMutation.error.response?.data?.errorCode
      : null

  return (
    // Phase 7-F Header/List UX Audit (Sticky Table Header): a bounded flex
    // column, not plain document flow. TableContainer's own default
    // `overflow-x: auto` (for horizontal scroll on wide tables) makes the
    // CSS engine treat IT as the nearest scrolling ancestor for any
    // `position: sticky` cell inside it (the "auto y follows non-visible x"
    // overflow quirk) - if TableContainer never has a bounded height of its
    // own, that scrolling ancestor never actually scrolls, so stickyHeader
    // visibly does nothing (confirmed via live reproduction: the header
    // scrolled away with the rest of the page). Giving TableContainer
    // `flex: 1, overflow: auto, minHeight: 0` below makes it the real,
    // intentional scroll region - MUI's own documented pattern for
    // `stickyHeader` - while everything above it (title/filters/Filter
    // Chips/alerts) stays on-screen, never scrolling out of view.
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      {/* Order Candidates Brand Entry (docs/gops-order-candidates-brand-entry-implementation.md
          §5): this screen is only ever reached via a Query Parameter now
          (CandidatesEntryPage routes the bare /candidates to the Brand List
          instead) - so a "grandparent" Back button back to the Brand List
          belongs here, distinct from the List<->Detail returnTo chain below. */}
      <Button
        size="small"
        onClick={() => navigate('/candidates')}
        sx={{ alignSelf: 'flex-start', mb: 1 }}
        data-testid="candidates-back-to-brand-list"
      >
        {t('candidates:brandList.backToBrandList')}
      </Button>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('candidates:title')}
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <TextField
          select
          size="small"
          label={t('candidates:filter.brand')}
          sx={{ minWidth: 200 }}
          value={filter.brandCode ?? ''}
          onChange={(e) => updateFilter({ brandCode: e.target.value || undefined })}
        >
          <MenuItem value="">{t('candidates:filter.all')}</MenuItem>
          {brandOptions.map(([code, name]) => (
            <MenuItem key={code} value={code}>
              {name}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          size="small"
          label={t('candidates:filter.supplier')}
          sx={{ minWidth: 220 }}
          value={filter.supplierCode ?? ''}
          onChange={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
        >
          <MenuItem value="">{t('candidates:filter.all')}</MenuItem>
          {supplierOptions.map(([code, name]) => (
            <MenuItem key={code} value={code}>
              {name}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          size="small"
          label={t('candidates:filter.keyword')}
          placeholder={t('candidates:filter.keywordPlaceholder') ?? undefined}
          sx={{ minWidth: 240 }}
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyKeyword()}
          onBlur={applyKeyword}
        />

        <FormControlLabel
          control={
            <Checkbox
              checked={recommendedOnly}
              onChange={(e) => toggleRecommendedOnly(e.target.checked)}
              data-testid="recommended-only-checkbox"
            />
          }
          label={t('candidates:filter.recommendedOnly')}
        />
        {/* Phase 7-F Header/List UX Audit (Dashboard Drill-down整合): same
            provisional Predicate as DashboardService.isOutOfStock/
            isLongTermOutOfStock (currentStock==0 / +openPo==0) - no new
            Business Rule, just making the List capable of showing the exact
            subset the Dashboard tile already counts, driven by the same
            outOfStockOnly/longTermOutOfStockOnly URL Filter the Dashboard's
            own Deep Link now sets. */}
        <FormControlLabel
          control={
            <Checkbox
              checked={outOfStockOnly}
              onChange={(e) => setBooleanParam('outOfStockOnly', e.target.checked)}
              data-testid="out-of-stock-only-checkbox"
            />
          }
          label={t('candidates:filter.outOfStockOnly')}
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={longTermOutOfStockOnly}
              onChange={(e) => setBooleanParam('longTermOutOfStockOnly', e.target.checked)}
              data-testid="long-term-out-of-stock-only-checkbox"
            />
          }
          label={t('candidates:filter.longTermOutOfStockOnly')}
        />

        <Box sx={{ flexGrow: 1 }} />

        <Button
          variant="contained"
          disabled={selected.size === 0 || createDraftMutation.isPending}
          onClick={handleCreateDraft}
          data-testid="create-draft-button"
        >
          {createDraftMutation.isPending ? (
            <CircularProgress size={20} />
          ) : (
            t('candidates:createDraft', { count: selected.size })
          )}
        </Button>
      </Stack>

      {activeFilterChips.length > 0 && (
        <Stack direction="row" spacing={1} sx={{ mb: 2, flexWrap: 'wrap', rowGap: 1 }} data-testid="active-filter-chips">
          {activeFilterChips.map((chip) => (
            <Chip key={chip.key} size="small" label={chip.label} onDelete={chip.onDelete} data-testid={`filter-chip-${chip.key}`} />
          ))}
        </Stack>
      )}

      {/* Phase 7-I (Layout Shift audit): mutation-result error, previously
          an inline <Alert> mounted directly above the Table (would push
          selection Checkboxes down mid-selection) - moved to shared Toast. */}
      <Toast
        open={createDraftErrorCode === 'MIXED_SUPPLIER_NOT_ALLOWED'}
        severity="error"
        message={t('candidates:createDraftMixedSupplier')}
        onClose={() => createDraftMutation.reset()}
      />
      <Toast
        open={Boolean(createDraftErrorCode) && createDraftErrorCode !== 'MIXED_SUPPLIER_NOT_ALLOWED'}
        severity="error"
        message={t('candidates:createDraftFailed', { code: createDraftErrorCode })}
        onClose={() => createDraftMutation.reset()}
      />

      {isLoading && (
        <Stack direction="row" spacing={1} sx={{ my: 4, alignItems: 'center' }}>
          <CircularProgress size={20} />
          <Typography>{t('common:loading')}</Typography>
        </Stack>
      )}

      {isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => refetch()}>
              {t('common:retry')}
            </Button>
          }
          sx={{ my: 2 }}
        >
          {t('common:errorGeneric')}
        </Alert>
      )}

      {!isLoading && !isError && visibleData && visibleData.length === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>
          {t('candidates:empty')}
        </Alert>
      )}

      {!isLoading && !isError && visibleData && visibleData.length > 0 && (
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('candidates:resultCount', { count: visibleData.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="candidate-list-table-container">
            {/* Phase 7-F Header/List UX Audit: MUI's default stickyHeader
                background was found transparent in this theme via live
                reproduction (underlying row text visibly showed through the
                sticky header) - forced opaque explicitly rather than relying
                on the default. */}
            <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox">{t('candidates:table.select')}</TableCell>
                  <TableCell>{t('candidates:table.sku')}</TableCell>
                  <TableCell>{t('candidates:table.itemName')}</TableCell>
                  <TableCell>{t('candidates:table.brand')}</TableCell>
                  <TableCell>{t('candidates:table.supplier')}</TableCell>
                  <TableCell align="right">{t('candidates:table.currentStock')}</TableCell>
                  <TableCell align="right">{t('candidates:table.safetyStock')}</TableCell>
                  <TableCell align="right">{t('candidates:table.openPo')}</TableCell>
                  <TableCell align="right">{t('candidates:table.monthlySales')}</TableCell>
                  <TableCell align="right">{t('candidates:table.leadTime')}</TableCell>
                  <TableCell align="right">{t('candidates:table.recommendedQty')}</TableCell>
                  <TableCell>{t('candidates:table.itemStatus')}</TableCell>
                  <TableCell>{t('candidates:table.stockJudgement')}</TableCell>
                  <TableCell align="right">{t('candidates:table.unitPrice')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleData.map((row) => (
                  <TableRow key={row.sku} hover selected={selected.has(row.sku)} data-testid={`candidate-row-${row.sku}`}>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={selected.has(row.sku)}
                        onChange={() => toggleSelect(row.sku)}
                        slotProps={{ input: { 'aria-label': row.sku } as never }}
                        data-testid={`candidate-checkbox-${row.sku}`}
                      />
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(row.sku)}`, listPath))}
                      >
                        {row.sku}
                      </Button>
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <span>{row.itemName ?? t('candidates:notAvailable')}</span>
                        <DataSourceBadge dataSource={row.dataSource} />
                      </Stack>
                    </TableCell>
                    <TableCell>{row.brandName ?? row.brandCode}</TableCell>
                    <TableCell>{row.supplierName ?? row.supplierCode ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.currentStock ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.safetyStock ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.openPo ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.monthlySales ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.leadTime ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">
                      {row.recommendedQty == null && row.regionClassification === 'DOMESTIC' ? (
                        <Typography component="span" variant="caption" color="text.secondary" data-testid={`recommended-qty-domestic-pending-${row.sku}`}>
                          {t('candidates:recommendedQtyDomesticPending')}
                        </Typography>
                      ) : (
                        <Typography component="span" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
                          {row.recommendedQty ?? t('candidates:notAvailable')}
                        </Typography>
                      )}
                    </TableCell>
                    <TableCell>
                      <ItemStatusChip status={row.itemStatus} />
                    </TableCell>
                    <TableCell>
                      <StockJudgementChip judgement={computeStockJudgement(row.currentStock, row.openPo)} />
                    </TableCell>
                    <TableCell align="right">
                      {row.unitPrice != null ? `¥${row.unitPrice.toLocaleString()}` : t('candidates:notAvailable')}
                    </TableCell>
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
