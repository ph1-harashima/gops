import { useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Chip from '@mui/material/Chip'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Divider from '@mui/material/Divider'

import { usePriceChangeDetail } from './api'
import { PriceChangeLineTable } from './PriceChangeLineTable'
import { PRICE_CHANGE_STATUS_DRAFT } from '../../shared/types/priceChange'

/**
 * Price Change Detail (target-price-change-workflow.md 15章) - read-only
 * header/line/Audit Trail view. Folds Approval Detail into this same screen
 * (per that Document's consolidation recommendation) - a no-op section for
 * Phase 8-B since Approval itself is not implemented (CUSTOMER REVIEW-gated).
 */
export function PriceChangeDetailPage() {
  const { t } = useTranslation(['priceChanges', 'common', 'status'])
  const navigate = useNavigate()
  const params = useParams<{ id: string }>()
  const id = Number(params.id)

  const { data, isLoading, isError, refetch } = usePriceChangeDetail(id)

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

  return (
    <Box sx={{ p: 3, height: '100%', overflow: 'auto' }}>
      <Stack direction="row" spacing={2} sx={{ alignItems: 'center', mb: 2 }}>
        <Typography variant="h5" component="h1">
          {t('priceChanges:detailTitle')} #{data.id}
        </Typography>
        <Chip size="small" label={t(`priceChanges:status.${data.status}`)} />
        <Box sx={{ flexGrow: 1 }} />
        {data.status === PRICE_CHANGE_STATUS_DRAFT && (
          <Button variant="contained" size="small" onClick={() => navigate(`/price-changes/${id}/edit`)} data-testid="edit-price-change-button">
            {t('priceChanges:editButton')}
          </Button>
        )}
        <Button size="small" onClick={() => navigate('/price-changes')} data-testid="back-to-price-change-list">
          {t('priceChanges:backToList')}
        </Button>
      </Stack>

      {data.status !== PRICE_CHANGE_STATUS_DRAFT && (
        <Alert severity="info" sx={{ mb: 2 }}>
          {t('priceChanges:notEditableNotice')}
        </Alert>
      )}

      <Paper variant="outlined" sx={{ p: 2, mb: 3 }}>
        <Stack spacing={0.5}>
          <Typography variant="body2">
            {t('priceChanges:table.note')}: {data.note ?? t('priceChanges:notAvailable')}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('priceChanges:table.createdBy')}: {data.createdByDisplayName ?? t('priceChanges:notAvailable')} ({new Date(data.createdAt).toLocaleString()})
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {t('priceChanges:table.updatedAt')}: {new Date(data.updatedAt).toLocaleString()}
          </Typography>
        </Stack>
      </Paper>

      <Typography variant="h6" gutterBottom>
        {t('priceChanges:detailsCount', { count: data.details.length })}
      </Typography>
      {data.details.length === 0 ? (
        <Alert severity="info">{t('priceChanges:detailsEmpty')}</Alert>
      ) : (
        <PriceChangeLineTable lines={data.details} editable={false} />
      )}

      <Divider sx={{ my: 3 }} />

      <Typography variant="h6" gutterBottom>
        {t('priceChanges:auditTrailTitle')}
      </Typography>
      {data.auditTrail.length === 0 ? (
        <Alert severity="info">{t('priceChanges:auditTrailEmpty')}</Alert>
      ) : (
        <Stack spacing={1}>
          {data.auditTrail.map((e, i) => (
            <Paper key={i} variant="outlined" sx={{ p: 1.5 }} data-testid={`price-change-audit-event-${i}`}>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'baseline', flexWrap: 'wrap' }}>
                <Typography variant="body2" sx={{ fontWeight: 'bold', minWidth: 200 }}>
                  {t(`status:eventType.${e.eventType}`, { defaultValue: e.eventType })}
                </Typography>
                {e.fieldName && (
                  <Typography variant="body2" color="text.secondary">
                    {e.fieldName}
                  </Typography>
                )}
                {(e.oldValue != null || e.newValue != null) && (
                  <Typography variant="body2" color="text.secondary">
                    {e.oldValue ?? '—'} → {e.newValue ?? '—'}
                  </Typography>
                )}
                <Box sx={{ flexGrow: 1 }} />
                <Typography variant="caption" color="text.secondary">
                  {e.performedByDisplayName ?? t('priceChanges:notAvailable')} · {new Date(e.performedAt).toLocaleString()}
                </Typography>
              </Stack>
            </Paper>
          ))}
        </Stack>
      )}
    </Box>
  )
}
