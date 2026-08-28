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

import { useDashboard } from './api'

/**
 * Action / Operation Cockpit (implementation instructions Step 5 3章).
 * Deliberately not an Analytics screen - every number here is "what needs a
 * decision right now" (a count), never a trend/margin/turnover-rate chart.
 * Clicking a KPI or a Brand row cell navigates to the corresponding
 * pre-filtered screen.
 */
export function DashboardPage() {
  const { t } = useTranslation(['dashboard', 'common'])
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
  //   Proxy). Candidate List has no matching Filter, and this Phase
  //   deliberately does not add one - baking a contested definition into a
  //   permanent Filter would overstep "現状では件数表示のみ" (6-D 7章). These
  //   two keep navigating to the unfiltered List; count will not match List
  //   count until that definition is finalized (reported, not hidden).
  // - 発注作成中: DashboardService.draftCount is STATUS_DRAFT only -
  //   READY_TO_ORDER is excluded, matching Label ("作成中" reads as "still
  //   editable", not "confirmed but unsent") - no change needed (6-D 4章).
  // - メーカー回答待ち: already correct (status=AWAITING_SUPPLIER).
  // - 要確認: DashboardService.attentionCount counts any order (any Status)
  //   with an active Attention. Order List already returns activeAttentionTypes
  //   per row, so ?hasAttention=true is a client-side Filter over data the
  //   existing API already sends - no new Attention Filter API (6-D 6章).
  const kpis = [
    { key: 'candidateCount', label: t('kpi.candidates'), value: data.candidateCount, onClick: () => navigate('/candidates?recommendedOnly=true') },
    { key: 'outOfStockCount', label: t('kpi.outOfStock'), value: data.outOfStockCount, onClick: () => navigate('/candidates') },
    { key: 'longTermOutOfStockCount', label: t('kpi.longTermOutOfStock'), value: data.longTermOutOfStockCount, onClick: () => navigate('/candidates') },
    { key: 'draftCount', label: t('kpi.draft'), value: data.draftCount, onClick: () => navigate('/orders/history?status=DRAFT') },
    { key: 'awaitingSupplierCount', label: t('kpi.awaitingSupplier'), value: data.awaitingSupplierCount, onClick: () => navigate('/orders/history?status=AWAITING_SUPPLIER') },
    { key: 'attentionCount', label: t('kpi.attention'), value: data.attentionCount, onClick: () => navigate('/orders/history?hasAttention=true') },
  ]

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('title')}
      </Typography>

      <Grid container spacing={2} sx={{ mb: 3 }}>
        {kpis.map((kpi) => (
          <Grid key={kpi.key} size={{ xs: 6, sm: 4, md: 2 }}>
            <Paper
              variant="outlined"
              onClick={kpi.onClick}
              sx={{ p: 2, textAlign: 'center', cursor: 'pointer', '&:hover': { boxShadow: 2 } }}
            >
              <Typography variant="h4">{kpi.value}</Typography>
              <Typography variant="body2" color="text.secondary">{kpi.label}</Typography>
            </Paper>
          </Grid>
        ))}
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
                  <Button size="small" onClick={() => navigate(`/candidates?brandCode=${b.brandCode}`)}>
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
