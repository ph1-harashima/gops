import { useNavigate, useSearchParams } from 'react-router-dom'
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
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'

import { usePriceChangeList, useCreatePriceChangeSet } from './api'
import { Toast } from '../../shared/components/Toast'
import {
  PRICE_CHANGE_STATUS_APPLIED,
  PRICE_CHANGE_STATUS_CANCELLED,
  PRICE_CHANGE_STATUS_DRAFT,
  PRICE_CHANGE_STATUS_FAILED,
  PRICE_CHANGE_STATUS_SUBMITTED,
} from '../../shared/types/priceChange'

const STATUS_OPTIONS = [
  PRICE_CHANGE_STATUS_DRAFT,
  PRICE_CHANGE_STATUS_SUBMITTED,
  PRICE_CHANGE_STATUS_APPLIED,
  PRICE_CHANGE_STATUS_FAILED,
  PRICE_CHANGE_STATUS_CANCELLED,
]

function statusChipColor(status: string): 'default' | 'primary' | 'success' | 'error' | 'warning' {
  switch (status) {
    case PRICE_CHANGE_STATUS_DRAFT:
      return 'default'
    case PRICE_CHANGE_STATUS_SUBMITTED:
      return 'primary'
    case PRICE_CHANGE_STATUS_APPLIED:
      return 'success'
    case PRICE_CHANGE_STATUS_FAILED:
      return 'error'
    case PRICE_CHANGE_STATUS_CANCELLED:
      return 'warning'
    default:
      return 'default'
  }
}

/**
 * Price Change List (target-price-change-workflow.md 15章) - folds Scheduled
 * Changes / Price History into this same screen via the Status Filter
 * (unreachable States for now, Phase 8-B Foundation scope) rather than
 * separate screens, per that Document's UI consolidation recommendation.
 */
export function PriceChangeListPage() {
  const { t } = useTranslation(['priceChanges', 'common', 'status'])
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const status = searchParams.get('status') ?? undefined

  const { data, isLoading, isError, refetch } = usePriceChangeList(status)
  const createMutation = useCreatePriceChangeSet()

  function updateStatus(value: string) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (value) next.set('status', value)
        else next.delete('status')
        return next
      },
      { replace: true },
    )
  }

  function handleCreate() {
    createMutation.mutate(null, {
      onSuccess: (set) => navigate(`/price-changes/${set.id}/edit`),
    })
  }

  return (
    <Box sx={{ p: 3, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('priceChanges:listTitle')}
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2, alignItems: 'center' }}>
        <TextField
          select
          size="small"
          label={t('priceChanges:statusFilter.label')}
          sx={{ minWidth: 200 }}
          value={status ?? ''}
          onChange={(e) => updateStatus(e.target.value)}
          data-testid="price-change-status-filter"
        >
          <MenuItem value="">{t('priceChanges:statusFilter.all')}</MenuItem>
          {STATUS_OPTIONS.map((s) => (
            <MenuItem key={s} value={s}>
              {t(`priceChanges:status.${s}`)}
            </MenuItem>
          ))}
        </TextField>

        <Box sx={{ flexGrow: 1 }} />

        <Button
          variant="contained"
          onClick={handleCreate}
          disabled={createMutation.isPending}
          data-testid="create-price-change-button"
        >
          {createMutation.isPending ? <CircularProgress size={20} /> : t('priceChanges:createButton')}
        </Button>
      </Stack>

      <Toast
        open={createMutation.isError}
        severity="error"
        message={t('priceChanges:saveFailed')}
        onClose={() => createMutation.reset()}
      />

      {isLoading && (
        <Stack direction="row" spacing={1} sx={{ my: 4, alignItems: 'center' }}>
          <CircularProgress size={20} />
          <Typography>{t('common:loading')}</Typography>
        </Stack>
      )}

      {isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => refetch()}>
              {t('common:retry')}
            </Button>
          }
          sx={{ my: 2 }}
        >
          {t('common:errorGeneric')}
        </Alert>
      )}

      {!isLoading && !isError && data && data.length === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>
          {t('priceChanges:empty')}
        </Alert>
      )}

      {!isLoading && !isError && data && data.length > 0 && (
        <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
          <TableContainer component={Paper} variant="outlined" sx={{ flex: 1, overflow: 'auto', minHeight: 0 }} data-testid="price-change-list-table-container">
            <Table size="small" stickyHeader sx={{ '& .MuiTableCell-stickyHeader': { backgroundColor: 'background.paper' } }}>
              <TableHead>
                <TableRow>
                  <TableCell>{t('priceChanges:table.id')}</TableCell>
                  <TableCell>{t('priceChanges:table.status')}</TableCell>
                  <TableCell>{t('priceChanges:table.note')}</TableCell>
                  <TableCell>{t('priceChanges:table.createdBy')}</TableCell>
                  <TableCell align="right">{t('priceChanges:table.detailCount')}</TableCell>
                  <TableCell>{t('priceChanges:table.updatedAt')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {data.map((row) => (
                  <TableRow
                    key={row.id}
                    hover
                    onClick={() => navigate(row.status === PRICE_CHANGE_STATUS_DRAFT ? `/price-changes/${row.id}/edit` : `/price-changes/${row.id}`)}
                    sx={{ cursor: 'pointer' }}
                    data-testid={`price-change-row-${row.id}`}
                  >
                    <TableCell>{row.id}</TableCell>
                    <TableCell>
                      <Chip size="small" color={statusChipColor(row.status)} label={t(`priceChanges:status.${row.status}`)} />
                    </TableCell>
                    <TableCell>{row.note ?? t('priceChanges:notAvailable')}</TableCell>
                    <TableCell>{row.createdByDisplayName ?? t('priceChanges:notAvailable')}</TableCell>
                    <TableCell align="right">{row.detailCount}</TableCell>
                    <TableCell>{new Date(row.updatedAt).toLocaleString()}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </Box>
      )}
    </Box>
  )
}
