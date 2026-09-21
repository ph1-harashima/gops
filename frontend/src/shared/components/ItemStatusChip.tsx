import Chip from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'

const COLOR_BY_STATUS: Record<string, 'default' | 'warning' | 'error'> = {
  NEW: 'default',
  DISCON: 'error',
  DISCON_STK: 'error',
  ON_HOLD: 'warning',
}

/**
 * Stage 4 Targeted Real-Data Remediation (docs/real-data-audit/
 * gops-stage3-real-data-compatibility-review.md §5/§C6): real Production
 * data shows genuinely-discontinued items (MS_ITEM.DISCON=1) rarely carry
 * a recognizable itemStatus string (mostly NULL/blank/even "NEW") - the
 * COLOR_BY_STATUS DISCON/DISCON_STK entries below almost never actually
 * fire against real data. `discon` (MS_ITEM's own boolean, independent of
 * the itemStatus string) is checked FIRST and wins whenever true, so a
 * genuinely discontinued item is reliably flagged regardless of what its
 * itemStatus text happens to say. Unknown itemStatus values still render
 * as-is via `defaultValue` - that robustness is unchanged.
 */
export function ItemStatusChip({ status, discon }: { status: string | null; discon?: boolean | null }) {
  const { t } = useTranslation('status')
  if (discon) {
    return <Chip size="small" label={t('itemStatus.DISCON')} color="error" variant="filled" data-testid="item-status-chip-discon" />
  }
  if (!status) return null
  const label = t(`itemStatus.${status}`, { defaultValue: status })
  const color = COLOR_BY_STATUS[status] ?? 'default'
  return <Chip size="small" label={label} color={color} variant={color === 'default' ? 'outlined' : 'filled'} />
}
