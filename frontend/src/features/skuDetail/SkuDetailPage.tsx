import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Stack from '@mui/material/Stack'
import Button from '@mui/material/Button'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'

import { useSkuDetail } from './api'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'

/**
 * SKU Detail (implementation instructions Step 5 4章). READ ONLY reference
 * screen for order judgement - deliberately no Sales Trend chart, no
 * 直近30/60/90日 or 前月/前々月 breakdown anywhere on this page (Legacy only
 * exposes one current-month figure - Phase 0.5 audit finding).
 */
export function SkuDetailPage() {
  const { t } = useTranslation(['skuDetail', 'common', 'status'])
  const { sku } = useParams<{ sku: string }>()
  const navigate = useNavigate()
  const { data, isLoading, isError } = useSkuDetail(sku ?? '')

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
        <Alert severity="error">{t('notFound')}</Alert>
      </Box>
    )
  }

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={() => navigate('/candidates')}>{t('back')}</Button>
        <Typography variant="h5" component="h1">{data.sku} - {data.itemName}</Typography>
        <ItemStatusChip status={data.itemStatus} />
        <DataSourceBadge dataSource={data.dataSource} />
      </Stack>

      <Stack direction="row" spacing={2} sx={{ flexWrap: 'wrap' }}>
        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
          <Typography variant="subtitle1" gutterBottom>{t('section.product')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">{t('field.brand')}: {data.brandName ?? data.brandCode ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.supplier')}: {data.supplierName ?? data.supplierCode ?? t('notAvailable')}</Typography>
          </Stack>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
          <Typography variant="subtitle1" gutterBottom>{t('section.inventory')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">{t('field.currentStock')}: {data.currentStock ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.safetyStock')}: {data.safetyStock ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.openPo')}: {data.openPo ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.openArrival')}: {data.openArrival ?? t('notAvailable')}</Typography>
          </Stack>
        </Paper>

        <Paper variant="outlined" sx={{ p: 2, flex: 1, minWidth: 260 }}>
          <Typography variant="subtitle1" gutterBottom>{t('section.ordering')}</Typography>
          <Stack spacing={0.5}>
            <Typography variant="body2">{t('field.monthlySales')}: {data.monthlySales ?? t('notAvailable')}</Typography>
            <Typography variant="body2">{t('field.leadTime')}: {data.leadTime ?? t('notAvailable')}</Typography>
            <Typography variant="body2">
              {t('field.recommendedQty')}: <strong>{data.recommendedQty ?? t('notAvailable')}</strong>
            </Typography>
            <Typography variant="body2">
              {t('field.unitPrice')}: {data.unitPrice != null ? `¥${data.unitPrice.toLocaleString()}` : t('notAvailable')}
            </Typography>
          </Stack>
        </Paper>
      </Stack>

      <Typography variant="h6" sx={{ mt: 3 }} gutterBottom>{t('section.history')}</Typography>
      {data.poHistory.length === 0 ? (
        <Alert severity="info">{t('historyEmpty')}</Alert>
      ) : (
        <TableContainer component={Paper} variant="outlined">
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t('historyTable.poNo')}</TableCell>
                <TableCell>{t('historyTable.orderDate')}</TableCell>
                <TableCell>{t('historyTable.supplier')}</TableCell>
                <TableCell align="right">{t('historyTable.qty')}</TableCell>
                <TableCell align="right">{t('historyTable.unitPrice')}</TableCell>
                <TableCell>{t('historyTable.status')}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {data.poHistory.map((line, i) => (
                <TableRow key={`${line.poNo}-${i}`} hover>
                  <TableCell>{line.poNo}</TableCell>
                  <TableCell>{line.orderDate ?? t('notAvailable')}</TableCell>
                  <TableCell>{line.supplierName ?? line.supplierCode ?? t('notAvailable')}</TableCell>
                  <TableCell align="right">{line.qty ?? t('notAvailable')}</TableCell>
                  <TableCell align="right">{line.unitPrice != null ? `¥${line.unitPrice.toLocaleString()}` : t('notAvailable')}</TableCell>
                  <TableCell>{line.status ?? t('notAvailable')}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
      )}
    </Box>
  )
}
