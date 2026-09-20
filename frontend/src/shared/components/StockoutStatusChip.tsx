import Chip from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'
import type { StockoutStatus } from '../types/restockExpectation'

const COLOR_BY_STATUS: Record<StockoutStatus, 'warning' | 'error' | 'success'> = {
  STOCKOUT: 'warning',
  LONG_TERM_STOCKOUT: 'error',
  RESOLVED: 'success',
}

/**
 * Post-Freeze Business Refinement 2
 * (docs/gops-manufacturer-stockout-information-management.md §4) -
 * Manufacturer Stockout Status only (STOCKOUT/LONG_TERM_STOCKOUT/RESOLVED),
 * deliberately separate from ItemStatusChip (Legacy's own
 * NEW/DISCON/DISCON_STK/ON_HOLD catalog status) and from
 * StockJudgementChip (Legacy stock-quantity-derived judgement) - three
 * distinct concepts that must never be visually conflated into one Chip
 * (requirements doc §3/§4).
 */
export function StockoutStatusChip({ status }: { status: StockoutStatus | null }) {
  const { t } = useTranslation('restockExpectation')
  if (!status) return null
  return (
    <Chip
      size="small"
      label={t(`stockoutStatus.${status}`)}
      color={COLOR_BY_STATUS[status]}
      variant={status === 'RESOLVED' ? 'outlined' : 'filled'}
      data-testid="stockout-status-chip"
      data-stockout-status={status}
    />
  )
}
