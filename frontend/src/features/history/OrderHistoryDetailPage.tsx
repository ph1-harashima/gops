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
import Divider from '@mui/material/Divider'

import { useOrderHistoryDetail, useOrderEvents } from './api'
import { OrderStatusChip } from '../../shared/components/OrderStatusChip'
import { AttentionChips } from '../../shared/components/AttentionChips'

/** READ ONLY (Requirements MD 31.2) - no Save/Edit control anywhere on this
 * screen. Edits go through the Draft / Supplier Response screens only,
 * reached via the Link buttons below. */
export function OrderHistoryDetailPage() {
  const { t } = useTranslation(['history', 'common', 'status'])
  const { id } = useParams<{ id: string }>()
  const orderId = Number(id)
  const navigate = useNavigate()

  const { data: detail, isLoading, isError } = useOrderHistoryDetail(orderId)
  const { data: events, isLoading: eventsLoading } = useOrderEvents(orderId)

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError || !detail) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error">{t('notFound')}</Alert>
      </Box>
    )
  }

  const canGoToPreview = ['DRAFT', 'READY_TO_ORDER'].includes(detail.status)
  const canGoToSupplierResponse = ['AWAITING_SUPPLIER', 'SUPPLIER_CONFIRMED'].includes(detail.status)

  return (
    <Box sx={{ p: 3 }}>
      <Stack direction="row" spacing={2} sx={{ mb: 2, alignItems: 'center' }}>
        <Button onClick={() => navigate('/orders/history')}>{t('backToList')}</Button>
        <Typography variant="h5" component="h1">
          {t('detailTitle')} - {detail.prototypePoNo ?? detail.draftNo}
        </Typography>
        <OrderStatusChip status={detail.status} />
        <AttentionChips types={detail.orderAttentionTypes} />
      </Stack>

      <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
        <Stack direction="row" spacing={4} sx={{ flexWrap: 'wrap', rowGap: 1 }}>
          <Typography variant="body2">{t('detail.supplier')}: <strong>{detail.supplierName ?? detail.supplierCode}</strong></Typography>
          <Typography variant="body2">{t('detail.brand')}: <strong>{detail.brandName ?? detail.brandCode}</strong></Typography>
          <Typography variant="body2">{t('detail.orderDate')}: <strong>{detail.orderDate ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('detail.requestedDelivery')}: <strong>{detail.requestedDelivery ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('detail.currency')}: <strong>{detail.currency ?? '—'}</strong></Typography>
          <Typography variant="body2">{t('detail.totalQty')}: <strong>{detail.totalQty}</strong></Typography>
          <Typography variant="body2">{t('detail.totalAmount')}: <strong>¥{detail.totalAmount.toLocaleString()}</strong></Typography>
        </Stack>
        {detail.remark && <Typography variant="body2" sx={{ mt: 1 }}>{t('detail.remark')}: {detail.remark}</Typography>}
      </Paper>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>{t('detailTable.sku')}</TableCell>
              <TableCell>{t('detailTable.itemName')}</TableCell>
              <TableCell align="right">{t('detailTable.recommendedQty')}</TableCell>
              <TableCell align="right">{t('detailTable.orderedQty')}</TableCell>
              <TableCell align="right">{t('detailTable.confirmedQty')}</TableCell>
              <TableCell>{t('detailTable.requestedDelivery')}</TableCell>
              <TableCell>{t('detailTable.confirmedDelivery')}</TableCell>
              <TableCell>{t('detailTable.attention')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {detail.details.map((line) => (
              <TableRow key={line.sku} hover>
                <TableCell>{line.sku}</TableCell>
                <TableCell>{line.itemName}</TableCell>
                <TableCell align="right">{line.recommendedQty}</TableCell>
                <TableCell align="right">{line.orderedQty}</TableCell>
                <TableCell align="right">{line.confirmedQty ?? t('notAvailable')}</TableCell>
                <TableCell>{line.requestedDelivery ?? t('notAvailable')}</TableCell>
                <TableCell>{line.confirmedDelivery ?? t('notAvailable')}</TableCell>
                <TableCell><AttentionChips types={line.attentionTypes} /></TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <Stack direction="row" spacing={2} sx={{ mt: 2 }}>
        {canGoToPreview && (
          <Button variant="outlined" onClick={() => navigate(`/orders/drafts/${detail.id}/preview`)}>
            {t('goToPreview')}
          </Button>
        )}
        {canGoToSupplierResponse && (
          <Button variant="outlined" onClick={() => navigate(`/orders/${detail.id}/supplier-response`)}>
            {t('goToSupplierResponse')}
          </Button>
        )}
      </Stack>

      <Divider sx={{ my: 3 }} />

      <Typography variant="h6" gutterBottom>{t('timelineTitle')}</Typography>
      {eventsLoading && <CircularProgress size={20} />}
      {!eventsLoading && (!events || events.length === 0) && (
        <Alert severity="info">{t('timelineEmpty')}</Alert>
      )}
      {!eventsLoading && events && events.length > 0 && (
        <Stack spacing={1}>
          {events.map((e, i) => (
            <Paper key={i} variant="outlined" sx={{ p: 1.5 }}>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ fontWeight: 'bold', minWidth: 200 }}>
                  {t(`status:eventType.${e.eventType}`, { defaultValue: e.eventType })}
                </Typography>
                {(e.oldValue !== null || e.newValue !== null) && (
                  <Typography variant="body2" color="text.secondary">
                    {t('timelineFieldOldNew', { old: e.oldValue ?? t('notAvailable'), new: e.newValue ?? t('notAvailable') })}
                  </Typography>
                )}
                <Typography variant="caption" color="text.secondary" sx={{ ml: 'auto' }}>
                  {e.performedBy} - {new Date(e.performedAt).toLocaleString('ja-JP')}
                </Typography>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  )
}
