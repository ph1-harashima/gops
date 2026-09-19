import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Stack from '@mui/material/Stack'
import Chip from '@mui/material/Chip'
import Grid from '@mui/material/Grid'
import Button from '@mui/material/Button'

import type { SupplierMasterDetail } from '../../shared/types/supplierMaster'

interface Props {
  supplier: SupplierMasterDetail
  onNavigateTab: (tab: 'contacts' | 'communication' | 'region' | 'po-code') => void
}

/**
 * Master Maintenance Hub: "Overview" tab. Brands come from the same Order
 * Candidate/Order-derived association Dashboard's own Brand breakdown
 * already uses (Backend SupplierMasterService) - no new Brand Master.
 */
export function SupplierOverviewTab({ supplier, onNavigateTab }: Props) {
  const { t } = useTranslation(['supplierMaster', 'manufacturerChannel', 'supplierRegionClassification'])

  function regionLabel(): string {
    if (supplier.regionClassification === 'MIXED') return t('supplierMaster:mixed')
    if (supplier.regionClassification == null) return t('supplierMaster:missing')
    return t(`supplierRegionClassification:regionClassification.${supplier.regionClassification}`)
  }

  function channelLabel(): string {
    if (supplier.channel === 'MIXED') return t('supplierMaster:mixed')
    if (supplier.channel == null) return t('supplierMaster:missing')
    return t(`manufacturerChannel:channel.${supplier.channel}`)
  }

  return (
    <Box data-testid="supplier-overview-tab">
      <Typography variant="h6" gutterBottom>{t('supplierMaster:overview.brandsTitle')}</Typography>
      {supplier.brands.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 3 }}>
          {t('supplierMaster:overview.brandsEmpty')}
        </Typography>
      ) : (
        <Stack direction="row" spacing={1} sx={{ mb: 3, flexWrap: 'wrap', rowGap: 1 }}>
          {supplier.brands.map((b) => (
            <Chip key={b.brandCode} label={b.brandName} data-testid={`supplier-overview-brand-${b.brandCode}`} />
          ))}
        </Stack>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" gutterBottom>{t('supplierMaster:overview.contactTitle')}</Typography>
            <Chip
              size="small"
              color={supplier.contactConfigured ? 'success' : 'default'}
              label={supplier.contactConfigured ? t('supplierMaster:configured') : t('supplierMaster:missing')}
              sx={{ mb: 1 }}
            />
            {supplier.contactConfigured && (
              <Typography variant="body2" color="text.secondary">
                {t('supplierMaster:overview.activeContactCount', { count: supplier.activeContactCount })}
              </Typography>
            )}
            <Button size="small" onClick={() => onNavigateTab('contacts')} data-testid="supplier-overview-goto-contacts">
              {t('supplierMaster:tabs.contacts')}
            </Button>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" gutterBottom>{t('supplierMaster:overview.channelTitle')}</Typography>
            <Typography variant="body1" sx={{ mb: 1 }}>{channelLabel()}</Typography>
            <Button size="small" onClick={() => onNavigateTab('communication')} data-testid="supplier-overview-goto-communication">
              {t('supplierMaster:tabs.communication')}
            </Button>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" gutterBottom>{t('supplierMaster:overview.regionTitle')}</Typography>
            <Typography variant="body1" sx={{ mb: 1 }}>{regionLabel()}</Typography>
            <Button size="small" onClick={() => onNavigateTab('region')} data-testid="supplier-overview-goto-region">
              {t('supplierMaster:tabs.region')}
            </Button>
          </Paper>
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <Paper variant="outlined" sx={{ p: 2 }}>
            <Typography variant="subtitle2" gutterBottom>{t('supplierMaster:overview.poCodeTitle')}</Typography>
            <Typography variant="body1" sx={{ mb: 1 }}>{supplier.officialPoShortCode ?? t('supplierMaster:missing')}</Typography>
            <Button size="small" onClick={() => onNavigateTab('po-code')} data-testid="supplier-overview-goto-po-code">
              {t('supplierMaster:tabs.poCode')}
            </Button>
          </Paper>
        </Grid>
      </Grid>
    </Box>
  )
}
