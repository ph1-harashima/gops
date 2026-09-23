import { useState } from 'react'
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
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Divider from '@mui/material/Divider'
import TextField from '@mui/material/TextField'
import FormControlLabel from '@mui/material/FormControlLabel'
import Checkbox from '@mui/material/Checkbox'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'

import { useOrderCandidateBrands } from './api'

/**
 * Order Candidates Brand Entry (docs/gops-order-candidates-brand-entry-implementation.md):
 * "発注候補 -> Brand一覧 -> Brand選択 -> そのBrandの発注候補SKU一覧 -> SKU選択 -> Draft",
 * the same "select the business-level parent Context before operating on its
 * children" principle Master Maintenance Hub already established
 * (docs/gops-master-maintenance-hub-implementation.md).
 *
 * CandidateListPage itself (the actual SKU Flat List) is completely
 * unchanged; this screen is purely a landing page in front of it.
 *
 * G-OPS Operational Workflow Realignment Phase E §11 / Candidate Brand
 * List UX improvement: search (Brand Code/Name) + a "候補0件のブランドも
 * 表示" toggle defaulting to hidden. Both now run server-side via
 * `useOrderCandidateBrands` (`GET /api/order-candidates/brands`,
 * `DashboardService.findBrandRowsForCandidateEntry`) - a dedicated,
 * separate endpoint reusing the exact same Read Model `GET /api/dashboard`
 * already reads, filtered before it ever reaches the Frontend (Production
 * Snapshot scale: 571 Brand rows). Originally (Phase E) this screen called
 * `useDashboard()` directly and filtered the full Brand list client-side -
 * changed because that fetched every Brand row (and every unrelated
 * Dashboard KPI) just to power this screen's own search, the same
 * fetch-all-then-filter-in-Frontend pattern Stage 5E RC-B already rejected
 * for this screen's Brand Filter Chip (see `api.ts`'s own `useBrandNames`
 * Javadoc).
 *
 * Search commits on blur/Enter (not per keystroke) - same idiom as
 * SupplierMasterListPage/OrderHistoryListPage's own keyword filters, to
 * avoid firing a request per character. Deliberately LOCAL React state,
 * not URL Query Parameters: {@link CandidatesEntryPage} renders this
 * component only when the URL has ZERO Query Parameters at all - adding
 * one for this screen's own search/toggle would flip routing to the flat
 * CandidateListPage instead. This is a real, discovered limitation (not
 * an oversight) - browser Back to a bare `/candidates` remounts this page
 * fresh, so this search/toggle does not survive that round trip the way a
 * URL-backed filter would. Still not fixed (out of this change's scope).
 */
export function OrderCandidateBrandListPage() {
  const { t } = useTranslation(['candidates', 'common'])
  const navigate = useNavigate()
  const theme = useTheme()
  // Mobile Responsive Audit UX-01/UX-03: the Desktop 7-column KPI Table is
  // too dense to read at a glance below `sm` (600px) - a Brand Card (name +
  // labeled, stacked KPI rows) replaces it there. Same data, same Buttons,
  // same testids/URLs as the Desktop Table below - purely a presentation
  // swap, no new API/Business Logic.
  const isCardLayout = useMediaQuery(theme.breakpoints.down('sm'))
  // Same "local input state, commit on blur/Enter" idiom as
  // SupplierMasterListPage/OrderHistoryListPage's own keyword filters -
  // typing must not fire a fresh Backend request on every keystroke.
  const [brandSearchInput, setBrandSearchInput] = useState('')
  const [brandSearch, setBrandSearch] = useState('')
  function applyBrandSearch() {
    setBrandSearch(brandSearchInput)
  }
  const [showZeroCandidateBrands, setShowZeroCandidateBrands] = useState(false)
  const { data, isLoading, isError, refetch } = useOrderCandidateBrands(brandSearch, showZeroCandidateBrands)
  const filteredBrands = data ?? []

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
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 1, flexWrap: 'wrap', rowGap: 1 }}>
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

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 1, alignItems: 'center' }}>
        <TextField
          size="small"
          label={t('candidates:brandList.search')}
          value={brandSearchInput}
          onChange={(e) => setBrandSearchInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyBrandSearch()}
          onBlur={applyBrandSearch}
          sx={{ minWidth: 220 }}
          slotProps={{ htmlInput: { 'data-testid': 'candidate-brand-list-search' } }}
        />
        <FormControlLabel
          control={
            <Checkbox
              checked={showZeroCandidateBrands}
              onChange={(e) => setShowZeroCandidateBrands(e.target.checked)}
              data-testid="candidate-brand-list-show-zero"
            />
          }
          label={t('candidates:brandList.showZeroCandidateBrands')}
        />
      </Stack>

      {filteredBrands.length === 0 && (
        <Alert severity="info" sx={{ mb: 2 }}>{t('candidates:brandList.noBrandsMatch')}</Alert>
      )}

      {isCardLayout ? (
        <Box sx={{ flex: 1, overflow: 'auto' }} data-testid="order-candidate-brand-list-table-container">
          <Stack spacing={1.5}>
            {filteredBrands.map((b) => (
              <Card key={b.brandCode} variant="outlined" data-testid={`order-candidate-brand-row-${b.brandCode}`}>
                <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                  <Button
                    size="small"
                    onClick={() => navigate(`/candidates?brandCode=${b.brandCode}`)}
                    data-testid={`order-candidate-brand-link-${b.brandCode}`}
                    sx={{ fontWeight: 600, fontSize: '1rem', px: 0, mb: 1 }}
                  >
                    {b.brandName}
                  </Button>
                  <Divider sx={{ mb: 1 }} />
                  <Stack spacing={0.75}>
                    {[
                      { label: t('candidates:brandList.table.candidates'), value: b.candidateCount, to: `/candidates?brandCode=${b.brandCode}&recommendedOnly=true` },
                      { label: t('candidates:brandList.table.outOfStock'), value: b.outOfStockCount, to: `/candidates?brandCode=${b.brandCode}&outOfStockOnly=true` },
                      { label: t('candidates:brandList.table.longTermOutOfStock'), value: b.longTermOutOfStockCount, to: `/candidates?brandCode=${b.brandCode}&longTermOutOfStockOnly=true` },
                      { label: t('candidates:brandList.table.draft'), value: b.draftCount, to: `/orders/history?brandCode=${b.brandCode}&status=DRAFT` },
                      { label: t('candidates:brandList.table.awaitingSupplier'), value: b.awaitingSupplierCount, to: `/orders/history?brandCode=${b.brandCode}&status=AWAITING_SUPPLIER` },
                      { label: t('candidates:brandList.table.attention'), value: b.attentionCount, to: `/orders/history?brandCode=${b.brandCode}&hasAttention=true` },
                    ].map((row) => (
                      <Stack key={row.label} direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center' }}>
                        <Typography variant="body2" color="text.secondary">{row.label}</Typography>
                        <Button size="small" onClick={() => navigate(row.to)} sx={{ minWidth: 48 }}>
                          {row.value}
                        </Button>
                      </Stack>
                    ))}
                  </Stack>
                </CardContent>
              </Card>
            ))}
          </Stack>
        </Box>
      ) : (
      <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="order-candidate-brand-list-table-container">
        <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
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
            {filteredBrands.map((b) => (
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
      )}
    </Box>
  )
}
