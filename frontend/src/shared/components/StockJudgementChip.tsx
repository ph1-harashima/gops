import Chip from '@mui/material/Chip'
import Tooltip from '@mui/material/Tooltip'
import { useTranslation } from 'react-i18next'
import type { StockJudgement } from '../domain/stockJudgement'

const COLOR_BY_JUDGEMENT: Record<StockJudgement, 'default' | 'warning' | 'error'> = {
  NORMAL: 'default',
  OUT_OF_STOCK: 'warning',
  LONG_TERM_OUT_OF_STOCK: 'error',
}

/** Phase 7-G: renders the Dashboard's 欠品/長期欠品 Predicate result as a
 * Chip distinct in both label and Tooltip wording from ItemStatusChip
 * (商品状態) - the two are independent axes on the same SKU (see
 * shared/domain/stockJudgement.ts). Always wrapped in a Tooltip stating
 * this is a provisional Prototype judgement, not the confirmed Gulliver
 * Business Rule (customer-review-decision-package.md D-5) - kept on every
 * instance (not just the column header) so the caveat travels with the
 * Badge wherever it's shown (List row, Detail page). */
export function StockJudgementChip({ judgement, size = 'small' }: { judgement: StockJudgement; size?: 'small' | 'medium' }) {
  const { t } = useTranslation('status')
  const label = t(`stockJudgement.${judgement}`)
  const color = COLOR_BY_JUDGEMENT[judgement]
  return (
    <Tooltip title={t('stockJudgementTooltip')}>
      <Chip
        size={size}
        label={label}
        color={color}
        variant={color === 'default' ? 'outlined' : 'filled'}
        data-testid="stock-judgement-chip"
      />
    </Tooltip>
  )
}
