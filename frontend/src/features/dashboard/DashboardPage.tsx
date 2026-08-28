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

  const kpis = [
    { key: 'candidateCount', label: t('kpi.candidates'), value: data.candidateCount, onClick: () => navigate('/candidates') },
    { key: 'outOfStockCount', label: t('kpi.outOfStock'), value: data.outOfStockCount, onClick: () => navigate('/candidates') },
    { key: 'longTermOutOfStockCount', label: t('kpi.longTermOutOfStock'), value: data.longTermOutOfStockCount, onClick: () => navigate('/candidates') },
    { key: 'draftCount', label: t('kpi.draft'), value: data.draftCount, onClick: () => navigate('/orders/history?status=DRAFT') },
    { key: 'awaitingSupplierCount', label: t('kpi.awaitingSupplier'), value: data.awaitingSupplierCount, onClick: () => navigate('/orders/history?status=AWAITING_SUPPLIER') },
    { key: 'attentionCount', label: t('kpi.attention'), value: data.attentionCount, onClick: () => navigate('/orders/history') },
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
                  <Button size="small" onClick={() => navigate(`/candidates?brandCode=${b.brandCode}`)}>
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
                  <Button size="small" onClick={() => navigate(`/orders/history?brandCode=${b.brandCode}`)}>
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
