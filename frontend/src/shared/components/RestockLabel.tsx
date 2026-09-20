import { useTranslation } from 'react-i18next'
import Typography from '@mui/material/Typography'
import type { RestockSource } from '../types/restockExpectation'

/**
 * Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
 * §5/§11) - the single place every screen (Candidate List/Order History
 * Detail/Stock-Sales/SKU Detail) renders a SKU's restock info from, so the
 * ja/en terminology (入荷予定 vs 再入荷予定 vs 再入荷予定：未定 - never a
 * mixed English/Japanese phrase like "Stockout Until") stays consistent
 * everywhere by construction rather than by convention.
 */
export function RestockLabel({ source, date, variant = 'body2' }: {
  source: RestockSource
  date: string | null
  variant?: 'body2' | 'caption'
}) {
  const { t } = useTranslation('restockExpectation')

  if (source === 'NONE') {
    return null
  }

  const text =
    source === 'LEGACY_EXPECTED_ARRIVAL' ? t('legacyDisplay', { date }) :
    source === 'PORTAL_MANUAL' ? t('manualDisplay', { date }) :
    t('unknownDisplay')

  return (
    <Typography variant={variant} color="text.secondary" data-testid="restock-label" data-restock-source={source}>
      {text}
    </Typography>
  )
}
