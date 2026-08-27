import Chip from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'

const COLOR_BY_STATUS: Record<string, 'default' | 'warning' | 'error'> = {
  NEW: 'default',
  DISCON: 'error',
  DISCON_STK: 'error',
  ON_HOLD: 'warning',
}

export function ItemStatusChip({ status }: { status: string | null }) {
  const { t } = useTranslation('status')
  if (!status) return null
  const label = t(`itemStatus.${status}`, { defaultValue: status })
  const color = COLOR_BY_STATUS[status] ?? 'default'
  return <Chip size="small" label={label} color={color} variant={color === 'default' ? 'outlined' : 'filled'} />
}
