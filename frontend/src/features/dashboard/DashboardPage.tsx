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

import { useDashboard } from './api'

/**
 * Action / Operation Cockpit (implementation instructions Step 5 3章).
 * Deliberately not an Analytics screen - every number here is "what needs a
 * decision right now" (a count), never a trend/margin/turnover-rate chart.
 * Clicking a KPI or a Brand row cell navigates to the corresponding
 * pre-filtered screen.
 */
export function DashboardPage() {
  const { t } = useTranslation(['dashboard', 'common', 'status'])
  const navigate = useNavigate()
  const { data, isLoading, isError, refetch } = useDashboard()

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

  // Phase 6-D Deep Link audit (docs/production-ux-workflow-redesign.md 10章;
  // full per-KPI writeup in the Phase 6-D completion report). Every target
  // filter reuses Phase 6-A's URL Query Parameter mechanism as-is - no new
  // state management, no Backend/API change.
  //
  // - 発注候補: DashboardService.isCandidate() = recommendedQty > 0, over the
  //   SAME unfiltered Candidate set /candidates itself fetches. ?recommendedOnly=true
  //   applies that identical condition client-side on the List (6-D 3章).
  // - 欠品/長期欠品: DashboardService's own comment marks their definition
  //   [TBD - CUSTOMER REVIEW] (currentStock==0 / +openPo==0, a provisional
  //   Proxy). Phase 7-F Header/List UX Audit: Candidate List now has a
  //   matching client-side Filter (outOfStockOnly/longTermOutOfStockOnly)
  //   reusing this EXACT SAME provisional Predicate - not a new Business
  //   Rule, just closing the Dashboard-count-vs-List-count gap the 6-D
  //   comment above previously reported (not hidden) as a known mismatch.
  //   The provisional definition itself remains unchanged and still
  //   unconfirmed (customer-review-decision-package.md D-5).
  // - 発注作成中: DashboardService.draftCount is STATUS_DRAFT only -
  //   READY_TO_ORDER is excluded, matching Label ("作成中" reads as "still
  //   editable", not "confirmed but unsent") - no change needed (6-D 4章).
  // - メーカー回答待ち: already correct (status=AWAITING_SUPPLIER).
  // - 要確認: DashboardService.attentionCount counts any order (any Status)
  //   with an active Attention. Order List already returns activeAttentionTypes
  //   per row, so ?hasAttention=true is a client-side Filter over data the
  //   existing API already sends - no new Attention Filter API (6-D 6章).
  const kpis = [
    { key: 'candidateCount', label: t('kpi.candidates'), value: data.candidateCount, onClick: () => navigate('/candidates?recommendedOnly=true'), tooltip: undefined as string | undefined },
    // Phase 7-G: same provisional Predicate/Tooltip wording as the new
    // 在庫判定 Chip (status:stockJudgementTooltip) so a user who has already
    // seen the Chip's Tooltip on the List/Detail recognizes this KPI refers
    // to the identical concept - not a new caveat, just making the existing
    // "件数だけでは分からない" gap this KPI already had (undocumented until
    // now) explicit here too.
    { key: 'outOfStockCount', label: t('kpi.outOfStock'), value: data.outOfStockCount, onClick: () => navigate('/candidates?outOfStockOnly=true'), tooltip: t('status:stockJudgementTooltip') },
    { key: 'longTermOutOfStockCount', label: t('kpi.longTermOutOfStock'), value: data.longTermOutOfStockCount, onClick: () => navigate('/candidates?longTermOutOfStockOnly=true'), tooltip: t('status:stockJudgementTooltip') },
    { key: 'draftCount', label: t('kpi.draft'), value: data.draftCount, onClick: () => navigate('/orders/history?status=DRAFT'), tooltip: undefined as string | undefined },
    // Phase 7-C1 14章: ADMIN's approval queue entry point - minimal design,
    // reusing the same Dashboard KPI -> pre-filtered Order List pattern as
    // every other tile here (no new screen, no new API).
    { key: 'pendingApprovalCount', label: t('kpi.pendingApproval'), value: data.pendingApprovalCount, onClick: () => navigate('/orders/history?status=PENDING_APPROVAL'), tooltip: undefined as string | undefined },
    { key: 'awaitingSupplierCount', label: t('kpi.awaitingSupplier'), value: data.awaitingSupplierCount, onClick: () => navigate('/orders/history?status=AWAITING_SUPPLIER'), tooltip: undefined as string | undefined },
    { key: 'attentionCount', label: t('kpi.attention'), value: data.attentionCount, onClick: () => navigate('/orders/history?hasAttention=true'), tooltip: undefined as string | undefined },
    // Phase 7-C7A 19章: no dedicated Order List Filter exists for "has an
    // Open Follow-up Case" this Phase (same "count only, unfiltered target"
    // precedent as 欠品/長期欠品 above) - the count itself is exact.
    { key: 'openFollowUpCaseCount', label: t('kpi.openFollowUpCase'), value: data.openFollowUpCaseCount, onClick: () => navigate('/orders/history'), tooltip: undefined as string | undefined },
  ]

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('title')}
      </Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {kpis.map((kpi) => {
          const tile = (
            <Paper
              variant="outlined"
              onClick={kpi.onClick}
              sx={{ p: 2, textAlign: 'center', cursor: 'pointer', '&:hover': { boxShadow: 2 } }}
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
        })}
      </Grid>

      <Typography variant="h6" gutterBottom>{t('brandBreakdown')}</Typography>
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
            </TableRow>
          </TableHead>
          <TableBody>
            {data.brands.map((b) => (
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
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
