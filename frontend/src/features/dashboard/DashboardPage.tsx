import { useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
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
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Grid from '@mui/material/Grid'
import Tooltip from '@mui/material/Tooltip'
import TextField from '@mui/material/TextField'
import FormControlLabel from '@mui/material/FormControlLabel'
import Checkbox from '@mui/material/Checkbox'

import { useDashboard } from './api'
import { useAuth } from '../auth/AuthContext'
import { ROLE_ADMIN, ROLE_OPERATOR } from '../../shared/types/auth'
import type { DashboardBrandRow } from '../../shared/types/dashboard'

const BRAND_TABLE_DEFAULT_ROWS = 10

/**
 * Action / Operation Cockpit (implementation instructions Step 5 3章).
 * Deliberately not an Analytics screen - every number here is "what needs a
 * decision right now" (a count), never a trend/margin/turnover-rate chart.
 * Clicking a KPI or a Brand row cell navigates to the corresponding
 * pre-filtered screen.
 *
 * G-OPS Operational Workflow Realignment Phase D (docs/ux-audit/
 * gops-operational-workflow-realignment-implementation.md §9/§10):
 * reorganized from a flat KPI grid into the real operational sequence
 * (発注候補 → 発注作成中 → 承認待ち → 署名待ち → メーカー送付待ち →
 * メーカー回答待ち → 要確認, with 問い合わせ中/価格変更 as a secondary
 * group) - a pure reordering/grouping of counts that already existed
 * (signaturePendingCount/readyToSendCount are Phase D's only new counts,
 * both computed live from Postgres only, never touching the Legacy Read
 * Model - see DashboardService's own Javadoc). The Brand Breakdown table
 * no longer dumps every Brand unfiltered - it defaults to hiding
 * zero-activity Brands and capping to the first 10, with a search box and
 * an explicit "すべて表示" action, since Brand *search* to place an order
 * belongs on Candidate Brand List, not here (§10).
 */
export function DashboardPage() {
  const { t } = useTranslation(['dashboard', 'common', 'status'])
  const navigate = useNavigate()
  const { user } = useAuth()
  const { data, isLoading, isError, refetch } = useDashboard()
  const [brandSearch, setBrandSearch] = useState('')
  const [showZeroActivityBrands, setShowZeroActivityBrands] = useState(false)
  const [showAllBrands, setShowAllBrands] = useState(false)

  const filteredBrands = useMemo(() => {
    if (!data) return [] as DashboardBrandRow[]
    const query = brandSearch.trim().toLowerCase()
    return data.brands.filter((b) => {
      if (!showZeroActivityBrands && isZeroActivityBrand(b)) return false
      if (!query) return true
      return b.brandCode.toLowerCase().includes(query) || b.brandName.toLowerCase().includes(query)
    })
  }, [data, brandSearch, showZeroActivityBrands])

  const visibleBrands = showAllBrands ? filteredBrands : filteredBrands.slice(0, BRAND_TABLE_DEFAULT_ROWS)

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
        <Alert severity="error" action={<Button color="inherit" size="small" onClick={() => refetch()}>{t('common:retry')}</Button>}>
          {t('common:errorGeneric')}
        </Alert>
      </Box>
    )
  }

  // Stage 5K: candidateCount/outOfStockCount/longTermOutOfStockCount now
  // come from a background Read Model Refresh (docs/real-data-audit/
  // gops-stage5k-dashboard-read-model-implementation.md) - `initialized`
  // is only ever false in the narrow window before the very first Refresh
  // has succeeded (e.g. a freshly-reset environment). Every count is 0
  // then, not a real value, so this shows an explicit "preparing" state
  // instead of a misleadingly-empty-looking Dashboard.
  if (!data.initialized) {
    return (
      <Box sx={{ p: 3 }}>
        <Typography variant="h5" component="h1" gutterBottom>
          {t('title')}
        </Typography>
        <Alert severity="info" action={<Button color="inherit" size="small" onClick={() => refetch()}>{t('common:retry')}</Button>}>
          {t('initializing')}
        </Alert>
      </Box>
    )
  }

  const isAdmin = user?.role === ROLE_ADMIN
  const isOperator = user?.role === ROLE_OPERATOR

  // Phase D §9: the workflow-sequence order, grouped into two sections -
  // "対応が必要" (needs a decision) and "その他" (secondary/no dedicated
  // Filter target yet, same precedent openFollowUpCaseCount already had).
  // Every onClick target/query-param below is unchanged from before this
  // Phase except the 2 new tiles - see DashboardKpiContractIntegrationTest
  // for the exact-match vs. superset-destination contract each one holds.
  type Kpi = { key: string; label: string; value: number; onClick: () => void; tooltip?: string; highlightForRole?: boolean }
  const actionNeeded: Kpi[] = [
    { key: 'candidateCount', label: t('kpi.candidates'), value: data.candidateCount, onClick: () => navigate('/candidates') },
    { key: 'outOfStockCount', label: t('kpi.outOfStock'), value: data.outOfStockCount, onClick: () => navigate('/candidates?outOfStockOnly=true'), tooltip: t('status:stockJudgementTooltip') },
    { key: 'longTermOutOfStockCount', label: t('kpi.longTermOutOfStock'), value: data.longTermOutOfStockCount, onClick: () => navigate('/candidates?longTermOutOfStockOnly=true'), tooltip: t('status:stockJudgementTooltip') },
    { key: 'pendingApprovalCount', label: t('kpi.pendingApproval'), value: data.pendingApprovalCount, onClick: () => navigate('/orders/history?status=PENDING_APPROVAL'), highlightForRole: isAdmin },
    // Phase D §9/§2 (Critical Design Principle): 署名待ち/メーカー送付待ち
    // have no dedicated Order List Filter yet (same "count only, superset
    // destination" precedent as openFollowUpCaseCount below) - both land on
    // the broader APPROVED list, a real superset of their own count.
    { key: 'signaturePendingCount', label: t('kpi.signaturePending'), value: data.signaturePendingCount, onClick: () => navigate('/orders/history?status=APPROVED'), highlightForRole: isAdmin },
    { key: 'readyToSendCount', label: t('kpi.readyToSend'), value: data.readyToSendCount, onClick: () => navigate('/orders/history?status=APPROVED'), highlightForRole: isOperator },
    { key: 'attentionCount', label: t('kpi.attention'), value: data.attentionCount, onClick: () => navigate('/orders/history?hasAttention=true') },
  ]
  const inProgress: Kpi[] = [
    { key: 'draftCount', label: t('kpi.draft'), value: data.draftCount, onClick: () => navigate('/orders/history?status=DRAFT') },
    { key: 'awaitingSupplierCount', label: t('kpi.awaitingSupplier'), value: data.awaitingSupplierCount, onClick: () => navigate('/orders/history?status=AWAITING_SUPPLIER'), highlightForRole: isOperator },
  ]
  const other: Kpi[] = [
    { key: 'openFollowUpCaseCount', label: t('kpi.openFollowUpCase'), value: data.openFollowUpCaseCount, onClick: () => navigate('/orders/history') },
    { key: 'priceChangeDraftCount', label: t('kpi.priceChangeDraft'), value: data.priceChangeDraftCount, onClick: () => navigate('/price-changes?status=DRAFT') },
  ]

  const renderKpiTile = (kpi: Kpi) => {
    const tile = (
      <Paper
        variant="outlined"
        onClick={kpi.onClick}
        sx={{
          p: 2, textAlign: 'center', cursor: 'pointer', '&:hover': { boxShadow: 2 },
          ...(kpi.highlightForRole ? { borderColor: 'primary.main', borderWidth: 2 } : {}),
        }}
        data-testid={`kpi-tile-${kpi.key}`}
      >
        <Typography variant="h4">{kpi.value}</Typography>
        <Typography variant="body2" color="text.secondary">{kpi.label}</Typography>
      </Paper>
    )
    return (
      <Grid key={kpi.key} size={{ xs: 6, sm: 4, md: 2 }}>
        {kpi.tooltip ? <Tooltip title={kpi.tooltip}>{tile}</Tooltip> : tile}
      </Grid>
    )
  }

  // Phase 8-J 11章/13章: plain Navigation Cards (no count) into Arrival/
  // Warehouse Stock/Stock-Sales (Phase 8-G/8-H) - Dashboard had zero entry
  // point into any of these 3 screens until this Phase. Deliberately no
  // number attached to any of them (§12 forbids inventing a meaningful
  // count here - e.g. an "Arrival count" or "Warehouse Stock count" implies
  // a threshold/anomaly judgement this Phase does not define) - §13
  // explicitly allows "a plain Navigation Card (no count)" instead.
  const navCards = [
    { key: 'arrivals', label: t('nav.arrivals'), onClick: () => navigate('/arrivals') },
    { key: 'warehouseStock', label: t('nav.warehouseStock'), onClick: () => navigate('/warehouse-stock') },
    { key: 'stockSales', label: t('nav.stockSales'), onClick: () => navigate('/stock-sales') },
  ]

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('title')}
      </Typography>
      {data.calculatedAt && (
        // Stage 5J §14 Freshness UX: a plain timestamp, never "Read Model" /
        // "Refresh Version" / "calc4" technical language shown to a general
        // Operator (Stage 5J §13 explicit instruction).
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }} data-testid="dashboard-last-updated">
          {t('lastUpdated', { timestamp: new Date(data.calculatedAt).toLocaleString('ja-JP') })}
        </Typography>
      )}

      <Typography variant="h6" gutterBottom>{t('section.actionNeeded')}</Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {actionNeeded.map(renderKpiTile)}
      </Grid>

      <Typography variant="h6" gutterBottom>{t('section.inProgress')}</Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {inProgress.map(renderKpiTile)}
      </Grid>

      <Typography variant="h6" gutterBottom>{t('section.other')}</Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {other.map(renderKpiTile)}
      </Grid>

      <Typography variant="h6" gutterBottom>{t('nav.title')}</Typography>
      <Grid container spacing={2} sx={{ mb: 3 }}>
        {navCards.map((card) => (
          <Grid key={card.key} size={{ xs: 6, sm: 4, md: 2 }}>
            <Paper
              variant="outlined"
              onClick={card.onClick}
              sx={{ p: 2, textAlign: 'center', cursor: 'pointer', '&:hover': { boxShadow: 2 } }}
              data-testid={`nav-card-${card.key}`}
            >
              <Typography variant="body2">{card.label}</Typography>
            </Paper>
          </Grid>
        ))}
      </Grid>

      <Typography variant="h6" gutterBottom>{t('brandBreakdown')}</Typography>
      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 1, alignItems: 'center' }}>
        <TextField
          size="small"
          label={t('brandSearch')}
          value={brandSearch}
          onChange={(e) => setBrandSearch(e.target.value)}
          sx={{ minWidth: 220 }}
          data-testid="dashboard-brand-search"
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={showZeroActivityBrands}
              onChange={(e) => setShowZeroActivityBrands(e.target.checked)}
              data-testid="dashboard-show-zero-activity-brands"
            />
          }
          label={t('showZeroActivityBrands')}
        />
      </Stack>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('table.brand')}</TableCell>
              <TableCell align="right">{t('table.candidates')}</TableCell>
              <TableCell align="right">{t('table.outOfStock')}</TableCell>
              <TableCell align="right">{t('table.draft')}</TableCell>
              <TableCell align="right">{t('table.awaitingSupplier')}</TableCell>
              <TableCell align="right">{t('table.attention')}</TableCell>
              <TableCell align="right">{t('table.priceChange')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {visibleBrands.map((b) => (
              <TableRow key={b.brandCode} hover>
                <TableCell>
                  <Button size="small" onClick={() => navigate(`/candidates?brandCode=${b.brandCode}`)}>
                    {b.brandName}
                  </Button>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/candidates?brandCode=${b.brandCode}&recommendedOnly=true`)}>
                    {b.candidateCount}
                  </Button>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/candidates?brandCode=${b.brandCode}&outOfStockOnly=true`)}>
                    {b.outOfStockCount}
                  </Button>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/orders/history?brandCode=${b.brandCode}&status=DRAFT`)}>
                    {b.draftCount}
                  </Button>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/orders/history?brandCode=${b.brandCode}&status=AWAITING_SUPPLIER`)}>
                    {b.awaitingSupplierCount}
                  </Button>
                </TableCell>
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/orders/history?brandCode=${b.brandCode}&hasAttention=true`)}>
                    {b.attentionCount}
                  </Button>
                </TableCell>
                {/* IA Audit §6/10 (docs/gops-master-maintenance-hub-implementation.md):
                    Brand -> 対象SKU -> Price Change - PriceChangeListPage's
                    own brandCode passthrough carries this Brand into the
                    newly-created Set's Edit screen (already Brand-filterable
                    via its existing Product Selection panel, now URL-driven). */}
                <TableCell align="right">
                  <Button size="small" onClick={() => navigate(`/price-changes?brandCode=${b.brandCode}`)} data-testid={`dashboard-brand-price-change-${b.brandCode}`}>
                    {t('priceChangeAction')}
                  </Button>
                </TableCell>
              </TableRow>
            ))}
            {visibleBrands.length === 0 && (
              <TableRow>
                <TableCell colSpan={7}>
                  <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
                    {t('noBrandsMatch')}
                  </Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>
      {!showAllBrands && filteredBrands.length > BRAND_TABLE_DEFAULT_ROWS && (
        <Button sx={{ mt: 1 }} onClick={() => setShowAllBrands(true)} data-testid="dashboard-show-all-brands">
          {t('showAllBrands', { count: filteredBrands.length })}
        </Button>
      )}
    </Box>
  )
}

/** Phase D §10: a Brand with nothing actionable anywhere in this row - the
 * same "0件のBrand" concept the End-to-End UX Audit's §2/§3 proposed,
 * applied here too since Dashboard's own Brand Breakdown table shares the
 * exact same over-long-unfiltered-list problem Candidate Brand List has. */
function isZeroActivityBrand(b: DashboardBrandRow): boolean {
  return b.candidateCount === 0 && b.outOfStockCount === 0 && b.longTermOutOfStockCount === 0
      && b.draftCount === 0 && b.awaitingSupplierCount === 0 && b.attentionCount === 0
}
