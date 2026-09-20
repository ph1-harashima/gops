import Dialog from '@mui/material/Dialog'
import DialogTitle from '@mui/material/DialogTitle'
import DialogContent from '@mui/material/DialogContent'
import DialogActions from '@mui/material/DialogActions'
import Button from '@mui/material/Button'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Stack from '@mui/material/Stack'
import Card from '@mui/material/Card'
import CardContent from '@mui/material/CardContent'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import CircularProgress from '@mui/material/CircularProgress'
import useMediaQuery from '@mui/material/useMediaQuery'
import { useTheme } from '@mui/material/styles'
import { useTranslation } from 'react-i18next'
import { useRestockExpectationHistory } from '../../features/skuDetail/api'
import { StockoutStatusChip } from './StockoutStatusChip'

/**
 * Post-Freeze Business Refinement 2 (requirements doc §11/§21) - the
 * Business-facing Manufacturer Stockout timeline, oldest first. Mobile
 * renders the same Card-per-entry idiom used throughout this app instead
 * of a dense Table (no horizontal overflow at 375-430px).
 */
export function ManufacturerStockoutHistoryDialog({ sku, open, onClose }: { sku: string; open: boolean; onClose: () => void }) {
  const { t } = useTranslation('restockExpectation')
  const theme = useTheme()
  const isCardLayout = useMediaQuery(theme.breakpoints.down('sm'))
  const { data, isLoading, isError } = useRestockExpectationHistory(sku, open)

  return (
    <Dialog open={open} onClose={onClose} maxWidth="md" fullWidth fullScreen={isCardLayout} data-testid="stockout-history-dialog">
      <DialogTitle>{t('historyTitle')}</DialogTitle>
      <DialogContent>
        {isLoading && <CircularProgress size={20} />}
        {isError && <Alert severity="error">{t('errorGeneric')}</Alert>}
        {data && data.length === 0 && <Alert severity="info">{t('historyEmpty')}</Alert>}
        {data && data.length > 0 && (
          isCardLayout ? (
            <Stack spacing={1.5} data-testid="stockout-history-cards">
              {data.map((entry, i) => (
                <Card key={i} variant="outlined" data-testid={`stockout-history-row-${i}`}>
                  <CardContent sx={{ '&:last-child': { pb: 2 } }}>
                    <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                      <Typography variant="body2" color="text.secondary">
                        {new Date(entry.recordedAt).toLocaleString('ja-JP')}
                      </Typography>
                      <StockoutStatusChip status={entry.stockoutStatus} />
                    </Stack>
                    <Stack spacing={0.5}>
                      <Typography variant="body2">
                        {t('historyColumn.restock')}: {entry.unknown ? t('unknownDisplay') : (entry.expectedRestockDate ?? t('noneDisplay'))}
                      </Typography>
                      <Typography variant="body2">{t('historyColumn.shortageQty')}: {entry.shortageQty ?? t('noneDisplay')}</Typography>
                      <Typography variant="body2">
                        {t('historyColumn.informationReceivedDate')}: {entry.informationReceivedDate ?? t('noneDisplay')}
                        {entry.contactMethod ? `（${t(`contactMethod.${entry.contactMethod}`)}）` : ''}
                      </Typography>
                      {entry.memo && <Typography variant="body2" color="text.secondary">{entry.memo}</Typography>}
                      <Typography variant="caption" color="text.secondary">{t('historyColumn.recordedBy')}: {entry.recordedBy}</Typography>
                    </Stack>
                  </CardContent>
                </Card>
              ))}
            </Stack>
          ) : (
            <TableContainer data-testid="stockout-history-table-container">
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t('historyColumn.recordedAt')}</TableCell>
                    <TableCell>{t('historyColumn.status')}</TableCell>
                    <TableCell>{t('historyColumn.restock')}</TableCell>
                    <TableCell align="right">{t('historyColumn.shortageQty')}</TableCell>
                    <TableCell>{t('historyColumn.informationReceivedDate')}</TableCell>
                    <TableCell>{t('historyColumn.contactMethod')}</TableCell>
                    <TableCell>{t('historyColumn.memo')}</TableCell>
                    <TableCell>{t('historyColumn.recordedBy')}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {data.map((entry, i) => (
                    <TableRow key={i} hover data-testid={`stockout-history-row-${i}`}>
                      <TableCell>{new Date(entry.recordedAt).toLocaleString('ja-JP')}</TableCell>
                      <TableCell><StockoutStatusChip status={entry.stockoutStatus} /></TableCell>
                      <TableCell>{entry.unknown ? t('unknownDisplay') : (entry.expectedRestockDate ?? t('noneDisplay'))}</TableCell>
                      <TableCell align="right">{entry.shortageQty ?? t('noneDisplay')}</TableCell>
                      <TableCell>{entry.informationReceivedDate ?? t('noneDisplay')}</TableCell>
                      <TableCell>{entry.contactMethod ? t(`contactMethod.${entry.contactMethod}`) : t('noneDisplay')}</TableCell>
                      <TableCell>{entry.memo ?? t('noneDisplay')}</TableCell>
                      <TableCell>{entry.recordedBy}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          )
        )}
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} data-testid="stockout-history-close-button">{t('historyCloseButton')}</Button>
      </DialogActions>
    </Dialog>
  )
}
