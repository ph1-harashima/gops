import { useTranslation } from 'react-i18next'
import Typography from '@mui/material/Typography'
import Tooltip from '@mui/material/Tooltip'
import Chip from '@mui/material/Chip'
import type { RestockSource } from '../types/restockExpectation'

/**
 * Post-Freeze Business Refinement (docs/gops-20260917-business-requirements-re-audit.md
 * §5/§11) - the single place every screen (Candidate List/Order History
 * Detail/Stock-Sales/SKU Detail) renders a SKU's restock info from, so the
 * ja/en terminology (入荷予定 vs 再入荷予定 vs 再入荷予定：未定 - never a
 * mixed English/Japanese phrase like "Stockout Until") stays consistent
 * everywhere by construction rather than by convention.
 *
 * G-OPS Operational Workflow Realignment Phase E §13 (Arrival Display):
 * source/data audit confirmed Legacy CAN reliably determine "already
 * physically arrived" - ArrivalExpectedBySkuQuery.sql already filters
 * `stk_in_date IS NULL`, so a resolved Arrival is never even returned as
 * an "expected" date in the first place (already correct, no change
 * needed there). What Legacy data CANNOT reliably determine is whether a
 * LEGACY_EXPECTED_ARRIVAL date that has passed while still un-resolved is
 * "genuinely delayed but still coming" or "forgotten/abandoned" - that
 * distinction is explicitly HELD this Phase (GULLIVER CONFIRMATION
 * REQUIRED, per docs/ux-audit/gops-end-to-end-operational-ux-workflow-audit.md
 * §6) rather than guessed at via an invented auto-hide/fallback rule. The
 * one safe, render-time-only, non-guessing thing this Phase DOES add is a
 * plain "already past" visual marker - it changes no data, invents no
 * Business Rule, and never hides or replaces the existing date text
 * (the E2E-asserted `restock-label`/`data-restock-source`/date text are
 * all left completely unchanged - this is a purely additive Chip next to
 * them).
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

  const isOverdue = source === 'LEGACY_EXPECTED_ARRIVAL' && isPastDate(date)

  return (
    <Typography variant={variant} color="text.secondary" data-testid="restock-label" data-restock-source={source}>
      {text}
      {isOverdue && (
        <Tooltip title={t('overdueTooltip')}>
          <Chip
            component="span"
            size="small"
            color="warning"
            variant="outlined"
            label={t('overdueLabel')}
            data-testid="restock-label-overdue"
            sx={{ ml: 0.5, height: 18, fontSize: '0.7rem', verticalAlign: 'middle' }}
          />
        </Tooltip>
      )}
    </Typography>
  )
}

function isPastDate(date: string | null): boolean {
  if (!date) return false
  const parsed = new Date(date)
  if (Number.isNaN(parsed.getTime())) return false
  const today = new Date()
  today.setHours(0, 0, 0, 0)
  return parsed.getTime() < today.getTime()
}
