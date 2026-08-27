import Chip from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'

const COLOR_BY_STATUS: Record<string, 'default' | 'success' | 'warning'> = {
  DRAFT: 'default',
  READY_TO_ORDER: 'success',
}

export function OrderStatusChip({ status }: { status: string }) {
  const { t } = useTranslation('status')
  const label = t(`orderStatus.${status}`, { defaultValue: status })
  const color = COLOR_BY_STATUS[status] ?? 'default'
  return <Chip size="small" label={label} color={color} variant={color === 'default' ? 'outlined' : 'filled'} />
}
