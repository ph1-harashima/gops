import { useEffect, useMemo, useRef, useState } from 'react'
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
import TablePagination from '@mui/material/TablePagination'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Divider from '@mui/material/Divider'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'

import { useOrderCandidates, useBrandNames } from './api'
import { useCreateDraft } from '../drafts/api'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'
import { StockJudgementChip } from '../../shared/components/StockJudgementChip'
import { RestockLabel } from '../../shared/components/RestockLabel'
import { StockoutStatusChip } from '../../shared/components/StockoutStatusChip'
import { ManufacturerConfirmationCaption } from '../../shared/components/ManufacturerConfirmationCaption'
import { RestockConflictWarning } from '../../shared/components/RestockConflictWarning'
import { Toast } from '../../shared/components/Toast'
import { computeStockJudgement } from '../../shared/domain/stockJudgement'
import { listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import type { OrderCandidate, OrderCandidateFilter } from '../../shared/types/orderCandidate'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const FILTER_PARAMS = ['brandCode', 'supplierCode', 'keyword'] as const
// Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
// gops-stage4-targeted-real-data-remediation.md): matches
// OrderCandidateService's own MAX/DEFAULT_PAGE_SIZE (Backend clamps
// regardless - this is only the initial/default value on first load).
const DEFAULT_PAGE_SIZE = 20

export function CandidateListPage() {
  const { t } = useTranslation(['candidates', 'common'])
  const navigate = useNavigate()
  // Post-Freeze Visual Walkthrough Findings Fix (Finding #6,
  // docs/gops-visual-walkthrough-findings-fix.md): matches Order History
  // List's own mobile-card breakpoint ('sm', 600px) - this screen is the
  // same "list of rows, drill in for detail" shape and should switch to
  // cards at the same width, for consistency. Previously this list had NO
  // mobile layout at all - it stayed the Desktop-width Table (12+ columns)
  // and required horizontal scroll on a phone, including for 在庫判定/
  // メーカー欠品情報 (the exact columns Scenario 2's own Business Flow
  // depends on) - Desktop's own Table is completely unchanged below.
  const theme = useTheme()
  const isCardLayout = useMediaQuery(theme.breakpoints.down('sm'))
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
  // G-OPS Operational Workflow Realignment Phase E §12: 商品状態（通常/廃番）
  // is a separate axis from 在庫状態（通常/欠品/長期欠品）above - reusing the
  // existing `row.discon`/ItemStatusChip judgement as-is (no new Business
  // Rule, no change to what "discontinued" means). Default hides 廃番 items
  // from the actionable Candidate List (ordering a discontinued item is
  // rarely the intent); checkbox reveals them - same URL-persisted Boolean
  // idiom as outOfStockOnly/longTermOutOfStockOnly above, defaulted to the
  // OPPOSITE sense (absence of the param = hide, not show).
  const showDiscontinued = searchParams.get('showDiscontinued') === 'true'
  const page = Number(searchParams.get('page') ?? '0')
  const size = Number(searchParams.get('size') ?? String(DEFAULT_PAGE_SIZE))
  const [keywordInput, setKeywordInput] = useState(filter.keyword ?? '')
  // G-OPS Operational Workflow Realignment Phase E §15: selection must
  // survive a round trip to SKU Detail and back (both Browser Back and the
  // in-app "戻る" button use the same URL, so persisting to the URL - the
  // same "single source of truth" idiom every other Filter on this page
  // already uses - covers both at once). Read once on mount from
  // `?selected=SKU1,SKU2,...`; a `useEffect` below mirrors state changes
  // back to the URL. Capped defensively (MAX_PERSISTED_SELECTION) so an
  // unusually large selection can never produce an unwieldy URL - the
  // cap only affects what is PERSISTED across a navigation, never the
  // current session's own Set (Create Draft always uses the full `selected`
  // below, uncapped).
  const [selected, setSelected] = useState<Set<string>>(() => {
    const raw = searchParams.get('selected')
    return raw ? new Set(raw.split(',').filter(Boolean)) : new Set()
  })
  // Candidate Selection Supplier UX (docs/real-data-audit/
  // gops-stage4-targeted-real-data-remediation.md §6): real data shows
  // 27.1% of Brands span multiple Suppliers (Stage 2 §5) - once the first
  // SKU is selected, its Supplier is locked in and other-Supplier rows
  // become unselectable, rather than only discovering
  // MIXED_SUPPLIER_NOT_ALLOWED after Create Draft. Tracked separately from
  // `selected` (a plain Set<string> of SKUs) because selection can span
  // multiple pages - the owning Supplier of an already-selected SKU from a
  // page no longer in view would otherwise be unrecoverable. Persisted to
  // the URL alongside `selected` for the same round-trip reason.
  const [selectedSupplierCode, setSelectedSupplierCode] = useState<string | null>(
    () => searchParams.get('selectedSupplier'),
  )

  const MAX_PERSISTED_SELECTION = 200
  // Deliberately NOT a reactive useEffect keyed on [selected,
  // selectedSupplierCode]: Create Draft's onSuccess clears both AND
  // navigates away in the same event handler - an effect would still fire
  // once more on that clearing update, racing setSearchParams(replace)
  // against navigate()'s own history push and intermittently corrupting
  // the just-navigated-to Draft page's URL (confirmed live via a failing
  // E2E run before this fix). Called explicitly, only from toggleSelect
  // below (the one place selection changes while staying on this page) -
  // Create Draft's own reset never calls this, since the page is leaving
  // anyway and there is nothing on the new URL to clean up.
  function syncSelectionToUrl(nextSelected: Set<string>, nextSelectedSupplierCode: string | null) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (nextSelected.size > 0) {
          next.set('selected', Array.from(nextSelected).slice(0, MAX_PERSISTED_SELECTION).join(','))
        } else {
          next.delete('selected')
        }
        if (nextSelectedSupplierCode) next.set('selectedSupplier', nextSelectedSupplierCode)
        else next.delete('selectedSupplier')
        return next
      },
      { replace: true },
    )
  }

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
        // Stage 4: a changed Filter can invalidate the current page (fewer
        // total results) - reset to page 0, same convention
        // useStockSalesList's own updateFilter already established.
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

  const listPath = listReturnTo('/candidates', searchParams)

  const { data, isLoading, isError, refetch } = useOrderCandidates(filter, page, size)

  // §15 (best-effort): scroll position within the Desktop Table, restored
  // after returning from SKU Detail. Keyed by the list's own returnTo path
  // (already incorporates filters/page/selection) via sessionStorage - a
  // per-tab, per-viewer convenience only, never read by anyone else, so
  // this is an acceptable use of browser storage per this codebase's own
  // "per-viewer convenience, not shared state" convention. Desktop layout
  // only (data-testid="candidate-list-table-container") - the Mobile Card
  // layout uses the page's own scroll (App shell), out of scope here.
  const scrollContainerRef = useRef<HTMLDivElement>(null)
  const scrollStorageKey = `candidateListScroll:${listPath}`
  useEffect(() => {
    if (isLoading || !data) return
    const saved = sessionStorage.getItem(scrollStorageKey)
    if (saved && scrollContainerRef.current) {
      try {
        scrollContainerRef.current.scrollTop = Number(saved)
      } catch {
        // sessionStorage/scroll restoration is a pure convenience - never
        // let a read/parse failure affect the List itself.
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isLoading, data])

  function saveScrollPosition() {
    if (!scrollContainerRef.current) return
    try {
      sessionStorage.setItem(scrollStorageKey, String(scrollContainerRef.current.scrollTop))
    } catch {
      // Same convenience-only rationale as above.
    }
  }

  const createDraftMutation = useCreateDraft()

  const visibleData = useMemo(() => {
    if (!data) return undefined
    // Phase 7-G: filters now route through the same computeStockJudgement()
    // the new 在庫判定 column/Badge renders from - previously this predicate
    // was duplicated inline here, independently from what any Badge showed
    // (there was no Badge at all). outOfStockOnly matches judgement !==
    // NORMAL (not === OUT_OF_STOCK) because 長期欠品 is a SUBSET of 欠品
    // (see stockJudgement.ts) - this keeps the exact same filtering RESULT
    // as before, only the implementation is now shared/single-sourced.
    //
    // Stage 4: these 3 remain a CLIENT-SIDE display filter over the
    // CURRENT PAGE only (unchanged design - they exist to match Dashboard
    // KPI predicates, never became a Backend query param) - now that the
    // List is paginated, a page can legitimately show fewer than `size`
    // rows (or none) while more matching rows exist on other pages. This
    // is an accepted, pre-existing trade-off carried forward, not
    // redesigned this Stage.
    return data.content
      .filter((row) => !recommendedOnly || (row.recommendedQty ?? 0) > 0)
      .filter((row) => !outOfStockOnly || computeStockJudgement(row.currentStock, row.openPo) !== 'NORMAL')
      .filter((row) => !longTermOutOfStockOnly || computeStockJudgement(row.currentStock, row.openPo) === 'LONG_TERM_OUT_OF_STOCK')
      .filter((row) => showDiscontinued || !row.discon)
  }, [data, recommendedOnly, outOfStockOnly, longTermOutOfStockOnly, showDiscontinued])

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

  // Stage 4 Targeted Real-Data Remediation: Brand *filter input* options
  // used to be derived from the (previously unpaginated) full result set -
  // with Backend Pagination, `data` only ever holds one page, so that no
  // longer reflects every real Brand/Supplier (matches useStockSalesList's
  // own already-paginated convention: typed code, not a dropdown of every
  // known value, rather than showing an incomplete/misleading one).
  //
  // The Filter Chip's own Brand *label* is a separate concern - it already
  // showed the resolved name (not the bare code) before Pagination, and
  // that display behavior is preserved unchanged here. Stage 5E Targeted
  // Remediation (RC-B, docs/real-data-audit/gops-stage5e-targeted-remediation.md):
  // resolves it via the lightweight useBrandNames() lookup instead of
  // useDashboard() - Stage 5D confirmed the latter pulled Dashboard's
  // entire candidate-count computation into the background on every
  // Candidate List visit (this page never used any other field from it),
  // eventually freezing the tab even though the visible paginated table
  // itself was always fast and correct.
  const { data: brandSummaries } = useBrandNames()
  function brandLabel(code: string): string {
    return brandSummaries?.find((b) => b.brandCode === code)?.brandName ?? code
  }

  // Phase 7-F Header/List UX Audit (Filter Chip): every active Filter -
  // Brand/Supplier/Keyword text Filters plus the three boolean display
  // Filters - surfaced as one removable Chip each, so a Dashboard Deep Link
  // (e.g. Brand + 欠品) is legible at a glance instead of only visible by
  // re-reading the Filter controls above.
  const activeFilterChips = [
    filter.brandCode ? { key: 'brandCode', label: `${t('candidates:filter.brand')}: ${brandLabel(filter.brandCode)}`, onDelete: () => updateFilter({ brandCode: undefined }) } : null,
    filter.supplierCode ? { key: 'supplierCode', label: `${t('candidates:filter.supplier')}: ${filter.supplierCode}`, onDelete: () => updateFilter({ supplierCode: undefined }) } : null,
    filter.keyword ? { key: 'keyword', label: `${t('candidates:filter.keyword')}: ${filter.keyword}`, onDelete: () => updateFilter({ keyword: undefined }) } : null,
    recommendedOnly ? { key: 'recommendedOnly', label: t('candidates:filter.recommendedOnly'), onDelete: () => toggleRecommendedOnly(false) } : null,
    outOfStockOnly ? { key: 'outOfStockOnly', label: t('candidates:filter.outOfStockOnly'), onDelete: () => setBooleanParam('outOfStockOnly', false) } : null,
    longTermOutOfStockOnly ? { key: 'longTermOutOfStockOnly', label: t('candidates:filter.longTermOutOfStockOnly'), onDelete: () => setBooleanParam('longTermOutOfStockOnly', false) } : null,
  ].filter((c): c is { key: string; label: string; onDelete: () => void } => c !== null)

  // Candidate Selection Supplier UX (§6): blocks selecting a row whose
  // Supplier differs from the already-locked-in one - the checkbox is also
  // rendered `disabled` for such rows (see isSupplierLocked below), so this
  // defensive check should never actually trigger through normal UI use;
  // it exists so toggleSelect itself can never silently mix Suppliers even
  // if called some other way. Backend's own MIXED_SUPPLIER_NOT_ALLOWED
  // (OrderDraftService) is unchanged and remains the authoritative guard.
  function toggleSelect(row: OrderCandidate) {
    if (selected.has(row.sku)) {
      const next = new Set(selected)
      next.delete(row.sku)
      const nextSupplierCode = next.size === 0 ? null : selectedSupplierCode
      setSelected(next)
      setSelectedSupplierCode(nextSupplierCode)
      syncSelectionToUrl(next, nextSupplierCode)
      return
    }
    if (selectedSupplierCode && row.supplierCode && row.supplierCode !== selectedSupplierCode) {
      return
    }
    const next = new Set(selected)
    next.add(row.sku)
    const nextSupplierCode = next.size === 1 ? (row.supplierCode ?? null) : selectedSupplierCode
    setSelected(next)
    setSelectedSupplierCode(nextSupplierCode)
    syncSelectionToUrl(next, nextSupplierCode)
  }

  function isSupplierLocked(row: OrderCandidate): boolean {
    return Boolean(selectedSupplierCode && row.supplierCode && row.supplierCode !== selectedSupplierCode && !selected.has(row.sku))
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
          setSelectedSupplierCode(null)
          // §15: the just-drafted SKUs are no longer an "in-progress
          // selection" once committed to a Draft - the returnTo target for
          // THIS navigation must not carry `selected`/`selectedSupplier`
          // forward (unlike the general `listPath` used for SKU Detail
          // navigation, where preserving an in-progress selection across
          // the round trip is the whole point of this Phase's change).
          // Without this, returning via "戻る" would re-show the
          // already-drafted SKU's checkbox as still checked.
          const cleanParams = new URLSearchParams(searchParams)
          cleanParams.delete('selected')
          cleanParams.delete('selectedSupplier')
          const cleanListPath = listReturnTo('/candidates', cleanParams)
          navigate(withReturnTo(`/orders/drafts/${draft.id}`, cleanListPath))
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
        {/* Stage 4: typed code, not a dropdown - see the comment above
            activeFilterChips for why (Backend Pagination means `data` is
            never the full Brand/Supplier universe anymore). `key` forces
            React to remount (re-read defaultValue) when the URL Filter
            changes from outside this field, same uncontrolled-field
            convention useStockSalesList's own Brand/Supplier inputs use. */}
        <TextField
          key={`brand-${filter.brandCode ?? ''}`}
          size="small"
          label={t('candidates:filter.brand')}
          sx={{ minWidth: 200 }}
          defaultValue={filter.brandCode ?? ''}
          onBlur={(e) => updateFilter({ brandCode: e.target.value || undefined })}
          data-testid="candidate-filter-brand"
        />

        <TextField
          key={`supplier-${filter.supplierCode ?? ''}`}
          size="small"
          label={t('candidates:filter.supplier')}
          sx={{ minWidth: 220 }}
          defaultValue={filter.supplierCode ?? ''}
          onBlur={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
          data-testid="candidate-filter-supplier"
        />

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
        <FormControlLabel
          control={
            <Checkbox
              checked={showDiscontinued}
              onChange={(e) => setBooleanParam('showDiscontinued', e.target.checked)}
              data-testid="show-discontinued-checkbox"
            />
          }
          label={t('candidates:filter.showDiscontinued')}
        />

        <Box sx={{ flexGrow: 1 }} />

        {/* Candidate Selection Supplier UX (§6): visible reason the other
            rows' checkboxes are disabled once a Supplier is locked in -
            real data shows 27.1% of Brands span multiple Suppliers (Stage
            2 §5), so this is a real, not hypothetical, everyday state. */}
        {selectedSupplierCode && (
          <Chip
            size="small"
            color="primary"
            variant="outlined"
            label={t('candidates:selectionLockedSupplier', { supplier: selectedSupplierCode })}
            data-testid="selection-locked-supplier-chip"
          />
        )}

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

      {/* Stage 4: emptiness is now judged from the Backend's own total
          (data.totalElements), not visibleData.length - the latter can be
          0 on a page whose rows were all removed by the client-side
          recommendedOnly/outOfStockOnly/longTermOutOfStockOnly Filters
          while other pages still have real results (see the comment above
          visibleData's own useMemo). */}
      {!isLoading && !isError && data && data.totalElements === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>
          {t('candidates:empty')}
        </Alert>
      )}

      {!isLoading && !isError && data && data.totalElements > 0 && visibleData && (
        <Box sx={isCardLayout ? {} : { flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('candidates:resultCount', { count: visibleData.length })}
          </Typography>
          {visibleData.length === 0 && (
            <Alert severity="info" sx={{ mb: 2 }} data-testid="candidate-page-filtered-empty">
              {t('candidates:pageFilteredEmpty')}
            </Alert>
          )}
          {visibleData.length > 0 && (isCardLayout ? (
            // Post-Freeze Visual Walkthrough Findings Fix (Finding #6): a
            // Card per SKU, mirroring Order History List's own mobile Card
            // pattern - minimum fields per the fix task's own §8.1: SKU/
            // 商品名/現在庫/当月販売数/推奨発注数/在庫判定(欠品Status)/入荷予定, plus
            // the selection Checkbox (bulk "選択したN件でドラフト作成" is
            // unchanged and must keep working identically from Mobile).
            // Secondary fields (安全在庫/発注残/リードタイム/単価/商品状態) are kept
            // but visually de-emphasized (caption-sized, secondary color)
            // rather than dropped, per "情報過多にならないよう、Secondary情報は
            // 視覚的に弱める" - nothing the Desktop Table shows is hidden here.
            <Stack spacing={1.5} data-testid="candidate-list-cards">
              {visibleData.map((row) => (
                <Card key={row.sku} variant="outlined" data-testid={`candidate-card-${row.sku}`}>
                  <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                    <Stack direction="row" spacing={1} sx={{ alignItems: 'flex-start' }}>
                      <Checkbox
                        checked={selected.has(row.sku)}
                        onChange={() => toggleSelect(row)}
                        disabled={isSupplierLocked(row)}
                        slotProps={{ input: { 'aria-label': row.sku } as never }}
                        data-testid={`candidate-checkbox-${row.sku}`}
                        sx={{ mt: -1, ml: -1.5 }}
                      />
                      <Box sx={{ flex: 1, minWidth: 0 }}>
                        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', rowGap: 0.5 }}>
                          <Button
                            size="small"
                            sx={{ p: 0, minWidth: 0 }}
                            onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(row.sku)}`, listPath))}
                            data-testid={`candidate-card-sku-link-${row.sku}`}
                          >
                            {row.sku}
                          </Button>
                          <ItemStatusChip status={row.itemStatus} discon={row.discon} />
                        </Stack>
                        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap', mt: 0.25 }}>
                          <Typography variant="body2" sx={{ fontWeight: 600, wordBreak: 'break-word' }}>
                            {row.itemName ?? t('candidates:notAvailable')}
                          </Typography>
                          <DataSourceBadge dataSource={row.dataSource} />
                        </Stack>
                        <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
                          {row.brandName ?? row.brandCode} / {row.supplierName ?? row.supplierCode ?? t('candidates:notAvailable')}
                        </Typography>

                        <Divider sx={{ my: 1 }} />

                        <Stack spacing={0.75}>
                          <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                            <Typography variant="body2" color="text.secondary">{t('candidates:table.currentStock')}</Typography>
                            <Typography variant="body2" sx={{ fontWeight: 500 }}>{row.currentStock ?? t('candidates:notAvailable')}</Typography>
                          </Stack>
                          <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                            <Typography variant="body2" color="text.secondary">{t('candidates:table.monthlySales')}</Typography>
                            <Typography variant="body2" sx={{ fontWeight: 500 }}>{row.monthlySales ?? t('candidates:notAvailable')}</Typography>
                          </Stack>
                          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Typography variant="body2" color="text.secondary">{t('candidates:table.recommendedQty')}</Typography>
                            {row.recommendedQty == null && row.regionClassification === 'DOMESTIC' ? (
                              <Typography component="span" variant="caption" color="text.secondary" data-testid={`recommended-qty-domestic-pending-${row.sku}`}>
                                {t('candidates:recommendedQtyDomesticPending')}
                              </Typography>
                            ) : (
                              <Typography component="span" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
                                {row.recommendedQty ?? t('candidates:notAvailable')}
                              </Typography>
                            )}
                          </Stack>
                          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                            <Typography variant="body2" color="text.secondary">{t('candidates:table.stockJudgement')}</Typography>
                            <StockJudgementChip judgement={computeStockJudgement(row.currentStock, row.openPo)} />
                          </Stack>
                          <Stack sx={{ justifyContent: 'space-between' }}>
                            <Typography variant="body2" color="text.secondary">{t('candidates:table.restock')}</Typography>
                            <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                              <StockoutStatusChip status={row.stockoutStatus} />
                              <RestockLabel source={row.restockSource} date={row.restockDate} variant="caption" />
                              {row.restockHasConflict && <RestockConflictWarning />}
                            </Stack>
                            <ManufacturerConfirmationCaption informationReceivedDate={row.informationReceivedDate} contactMethod={row.contactMethod} />
                          </Stack>
                        </Stack>

                        <Divider sx={{ my: 1 }} />

                        <Stack direction="row" spacing={1.5} sx={{ flexWrap: 'wrap', rowGap: 0.25 }}>
                          <Typography variant="caption" color="text.secondary">{t('candidates:table.safetyStock')}: {row.safetyStock ?? t('candidates:notAvailable')}</Typography>
                          <Typography variant="caption" color="text.secondary">{t('candidates:table.openPo')}: {row.openPo ?? t('candidates:notAvailable')}</Typography>
                          <Typography variant="caption" color="text.secondary">{t('candidates:table.leadTime')}: {row.leadTime ?? t('candidates:notAvailable')}</Typography>
                          <Typography variant="caption" color="text.secondary">
                            {t('candidates:table.unitPrice')}: {row.unitPrice != null ? `¥${row.unitPrice.toLocaleString()}` : t('candidates:notAvailable')}
                          </Typography>
                        </Stack>
                      </Box>
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          ) : (
          <TableContainer ref={scrollContainerRef} onScroll={saveScrollPosition} component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="candidate-list-table-container">
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
                  <TableCell>{t('candidates:table.restock')}</TableCell>
                  <TableCell align="right">{t('candidates:table.unitPrice')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleData.map((row) => (
                  <TableRow key={row.sku} hover selected={selected.has(row.sku)} data-testid={`candidate-row-${row.sku}`}>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={selected.has(row.sku)}
                        onChange={() => toggleSelect(row)}
                        disabled={isSupplierLocked(row)}
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
                      <ItemStatusChip status={row.itemStatus} discon={row.discon} />
                    </TableCell>
                    <TableCell>
                      <StockJudgementChip judgement={computeStockJudgement(row.currentStock, row.openPo)} />
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.25}>
                        <Stack direction="row" spacing={0.5} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                          <StockoutStatusChip status={row.stockoutStatus} />
                          <RestockLabel source={row.restockSource} date={row.restockDate} variant="caption" />
                          {row.restockHasConflict && <RestockConflictWarning />}
                        </Stack>
                        <ManufacturerConfirmationCaption informationReceivedDate={row.informationReceivedDate} contactMethod={row.contactMethod} />
                      </Stack>
                    </TableCell>
                    <TableCell align="right">
                      {row.unitPrice != null ? `¥${row.unitPrice.toLocaleString()}` : t('candidates:notAvailable')}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
          ))}
          <TablePagination
            component="div"
            count={data.totalElements}
            page={data.page}
            rowsPerPage={data.size}
            rowsPerPageOptions={[10, 20, 50, 100]}
            onPageChange={(_e, newPage) => changePage(newPage)}
            onRowsPerPageChange={(e) => changeSize(Number(e.target.value))}
            data-testid="candidate-list-pagination"
          />
        </Box>
      )}
    </Box>
  )
}
