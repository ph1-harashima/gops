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
import Chip from '@mui/material/Chip'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'

import { useSupplierMasterList } from './supplierMasterApi'
import type { SupplierMasterSummary } from '../../shared/types/supplierMaster'

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * "Master Maintenance -> Supplier一覧 -> Supplier選択 -> Supplier Settings",
 * the Cross-Screen IA Audit's 4章 recommendation (Commit c5f005c). Supplier
 * Code/Name here is Legacy `ms_comm` READ ONLY (the exact same Source of
 * Truth every Master Maintenance Create already validates a Supplier Code
 * against) - never a new Portal Supplier Master, never guessed.
 */
export function SupplierMasterListPage() {
  const { t } = useTranslation(['supplierMaster', 'manufacturerChannel', 'supplierRegionClassification', 'common'])
  const navigate = useNavigate()
  const { data, isLoading, isError, refetch } = useSupplierMasterList()

  function regionLabel(value: SupplierMasterSummary['regionClassification']): string {
    if (value === 'MIXED') return t('supplierMaster:mixed')
    if (value == null) return t('supplierMaster:missing')
    return t(`supplierRegionClassification:regionClassification.${value}`)
  }

  function channelLabel(value: SupplierMasterSummary['channel']): string {
    if (value === 'MIXED') return t('supplierMaster:mixed')
    if (value == null) return t('supplierMaster:missing')
    return t(`manufacturerChannel:channel.${value}`)
  }

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError) {
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
      <Typography variant="h5" component="h1" gutterBottom>
        {t('supplierMaster:listTitle')}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t('supplierMaster:listSubtitle')}
      </Typography>

      <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 220 }} data-testid="supplier-master-list-table-container">
        <Table size="small" stickyHeader sx={{ minWidth: 650, '& .MuiTableCell-root': { whiteSpace: 'nowrap' }, '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
          <TableHead>
            <TableRow>
              <TableCell>{t('supplierMaster:table.supplierCode')}</TableCell>
              <TableCell>{t('supplierMaster:table.supplierName')}</TableCell>
              <TableCell>{t('supplierMaster:table.region')}</TableCell>
              <TableCell align="right">{t('supplierMaster:table.brands')}</TableCell>
              <TableCell>{t('supplierMaster:table.contact')}</TableCell>
              <TableCell>{t('supplierMaster:table.channel')}</TableCell>
              <TableCell>{t('supplierMaster:table.poCode')}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {(data ?? []).map((s) => (
              <TableRow
                key={s.supplierCode}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => navigate(`/master/suppliers/${encodeURIComponent(s.supplierCode)}/overview`)}
                data-testid={`supplier-master-row-${s.supplierCode}`}
              >
                <TableCell>
                  <Button size="small" data-testid={`supplier-master-link-${s.supplierCode}`}>
                    {s.supplierCode}
                  </Button>
                </TableCell>
                <TableCell>{s.supplierName}</TableCell>
                <TableCell>{regionLabel(s.regionClassification)}</TableCell>
                <TableCell align="right">{s.brandCount}</TableCell>
                <TableCell>
                  <Chip
                    size="small"
                    color={s.contactConfigured ? 'success' : 'default'}
                    label={s.contactConfigured ? t('supplierMaster:configured') : t('supplierMaster:missing')}
                  />
                </TableCell>
                <TableCell>{channelLabel(s.channel)}</TableCell>
                <TableCell>{s.officialPoShortCode ?? t('supplierMaster:missing')}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
    </Box>
  )
}
