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

import { useDashboard } from '../dashboard/api'

/**
 * Order Candidates Brand Entry (docs/gops-order-candidates-brand-entry-implementation.md):
 * "発注候補 -> Brand一覧 -> Brand選択 -> そのBrandの発注候補SKU一覧 -> SKU選択 -> Draft",
 * the same "select the business-level parent Context before operating on its
 * children" principle Master Maintenance Hub already established
 * (docs/gops-master-maintenance-hub-implementation.md).
 *
 * Reuses Dashboard's own `useDashboard()` query as-is (same Brand breakdown
 * data Dashboard's own table already renders) - no second Dashboard-like
 * endpoint, no new Candidate API. CandidateListPage itself (the actual SKU
 * Flat List) is completely unchanged; this screen is purely a new landing
 * page in front of it.
 */
export function OrderCandidateBrandListPage() {
  const { t } = useTranslation(['candidates', 'common'])
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

  return (
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1 }}>
        <Typography variant="h5" component="h1" gutterBottom sx={{ mb: 0 }}>
          {t('candidates:brandList.title')}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        {/* Section 3: the explicit, single all-Brand entry point - the
            EXISTING /candidates?recommendedOnly=true Deep Link, unchanged. */}
        <Button
          variant="outlined"
          size="small"
          onClick={() => navigate('/candidates?recommendedOnly=true')}
          data-testid="candidates-view-all-button"
        >
          {t('candidates:brandList.viewAllButton')}
        </Button>
      </Stack>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('candidates:brandList.subtitle')}
      </Typography>

      <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="order-candidate-brand-list-table-container">
        <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
          <TableHead>
            <TableRow>
              <TableCell>{t('candidates:brandList.table.brand')}</TableCell>
              <TableCell align="right">{t('candidates:brandList.table.candidates')}</TableCell>
              <TableCell align="right">{t('candidates:brandList.table.outOfStock')}</TableCell>
              <TableCell align="right">{t('candidates:brandList.table.longTermOutOfStock')}</TableCell>
              <TableCell align="right">{t('candidates:brandList.table.draft')}</TableCell>
              <TableCell align="right">{t('candidates:brandList.table.awaitingSupplier')}</TableCell>
              <TableCell align="right">{t('candidates:brandList.table.attention')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {data.brands.map((b) => (
              <TableRow key={b.brandCode} hover data-testid={`order-candidate-brand-row-${b.brandCode}`}>
                <TableCell>
                  {/* Section 2: Brand name -> Brand-only Candidate List, no
                      additional Filter riding along - mirrors Dashboard's
                      own Brand Name cell exactly. */}
                  <Button
                    size="small"
                    onClick={() => navigate(`/candidates?brandCode=${b.brandCode}`)}
                    data-testid={`order-candidate-brand-link-${b.brandCode}`}
                  >
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
                  <Button size="small" onClick={() => navigate(`/candidates?brandCode=${b.brandCode}&longTermOutOfStockOnly=true`)}>
                    {b.longTermOutOfStockCount}
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
