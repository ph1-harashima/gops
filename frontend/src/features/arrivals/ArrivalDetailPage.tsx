import { useNavigate, useParams, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'

import { useArrivalDetail } from './api'
import { resolveReturnTo } from '../../shared/navigation/returnTo'

function qty(value: number | null): string {
  return value == null ? '—' : String(value)
}

/**
 * Arrival Detail (Phase 8-G 6章) - PO -> Invoice -> (BL) -> Arrival ->
 * Stock-In, READ ONLY. No computed line Status/discrepancy anywhere on this
 * screen (7章's explicit prohibition) - every quantity is a plain Source
 * Fact shown side by side.
 */
export function ArrivalDetailPage() {
  const { t } = useTranslation(['arrivals', 'common'])
  const navigate = useNavigate()
  const params = useParams<{ supplierCode: string; poNumber: string; invoiceNumber: string }>()
  const [searchParams] = useSearchParams()
  const supplierCode = params.supplierCode ?? ''
  const poNumber = params.poNumber ?? ''
  const invoiceNumber = params.invoiceNumber ?? ''
  // Phase 8-M (Global Navigation Audit): returns to whichever Arrival List
  // (with its Filter/Page state) actually launched this Detail; falls back
  // to the plain List route for Direct URL Access (Principle E).
  const backTarget = resolveReturnTo(searchParams.get('returnTo'), '/arrivals')

  const { data, isLoading, isError, refetch } = useArrivalDetail(supplierCode, poNumber, invoiceNumber)

  if (isLoading) {
    return (
      <Box sx={{ p: 3 }}>
        <CircularProgress size={20} />
      </Box>
    )
  }

  if (isError || !data) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" action={<Button onClick={() => refetch()}>{t('common:retry')}</Button>}>
          {t('common:errorGeneric')}
        </Alert>
      </Box>
    )
  }

  const header = data.header

  return (
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" component="h1">
          {t('detailTitle')} - {header.poNumber} / {header.invoiceNumber}
        </Typography>
        <Box sx={{ flexGrow: 1 }} />
        <Button size="small" onClick={() => navigate(backTarget)} data-testid="back-to-arrival-list">
          {t('backToList')}
        </Button>
      </Stack>

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Stack spacing={2}>
          <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Typography variant="body2">{t('field.supplier')}: <strong>{header.supplierName ?? header.supplierCode}</strong></Typography>
            <Typography variant="body2">{t('field.brand')}: <strong>{header.brandName ?? header.brandCode ?? '—'}</strong></Typography>
            <Typography variant="body2">{t('field.blNumber')}: <strong>{header.blNumber ?? '—'}</strong></Typography>
            <Typography variant="body2">{t('field.vesselNumber')}: <strong>{header.vesselNumber ?? '—'}</strong></Typography>
          </Stack>
          <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Typography variant="body2">{t('field.etd')}: {header.etd ?? '—'}</Typography>
            <Typography variant="body2">{t('field.eta')}: {header.eta ?? '—'}</Typography>
            <Typography variant="body2">{t('field.etaWarehouse')}: {header.etaWarehouse ?? '—'}</Typography>
            <Typography variant="body2">{t('field.stockInDate')}: {header.stockInDate ?? '—'}</Typography>
          </Stack>
          <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
            <Typography variant="body2">{t('field.warehouseReportStatus')}: {header.warehouseReportStatus ?? '—'}</Typography>
            <Typography variant="body2">{t('field.warehouseReportResult')}: {header.warehouseReportResult ?? '—'}</Typography>
          </Stack>
        </Stack>
      </Paper>

      <Typography variant="h6" gutterBottom>{t('quantitySummaryTitle')}</Typography>
      <Alert severity="info" sx={{ mb: 2 }}>{t('quantitySummaryNote')}</Alert>
      <TableContainer component={Paper} variant="outlined" sx={{ mb: 3 }}>
        <Table size="small" data-testid="arrival-quantity-summary">
          <TableHead>
            <TableRow>
              <TableCell align="right">{t('table.orderedQty')}</TableCell>
              <TableCell align="right">{t('table.invoiceQty')}</TableCell>
              <TableCell align="right">{t('table.arrivalQty')}</TableCell>
              <TableCell align="right">{t('table.stockInQty')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            <TableRow>
              <TableCell align="right">{qty(header.orderedQty)}</TableCell>
              <TableCell align="right">{qty(header.invoiceQty)}</TableCell>
              <TableCell align="right">{qty(header.arrivalQty)}</TableCell>
              <TableCell align="right">{qty(header.stockInQty)}</TableCell>
            </TableRow>
          </TableBody>
        </Table>
      </TableContainer>

      <Divider sx={{ my: 3 }} />

      <Typography variant="h6" gutterBottom>{t('lineTraceabilityTitle')}</Typography>
      {data.lines.length === 0 ? (
        <Alert severity="info">{t('lineTraceabilityEmpty')}</Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small" data-testid="arrival-line-table">
            <TableHead>
              <TableRow>
                <TableCell>{t('table.sku')}</TableCell>
                <TableCell>{t('table.itemName')}</TableCell>
                <TableCell align="right">{t('table.orderedQty')}</TableCell>
                <TableCell align="right">{t('table.invoiceQty')}</TableCell>
                <TableCell align="right">{t('table.stockInQty')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.lines.map((line) => (
                <TableRow key={line.sku} data-testid={`arrival-line-${line.sku}`}>
                  <TableCell>{line.sku}</TableCell>
                  <TableCell>{line.itemName ?? line.sku}</TableCell>
                  <TableCell align="right">{qty(line.orderedQty)}</TableCell>
                  <TableCell align="right">{qty(line.invoiceQty)}</TableCell>
                  <TableCell align="right">{qty(line.stockInQty)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  )
}
