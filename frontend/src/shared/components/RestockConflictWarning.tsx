import Tooltip from '@mui/material/Tooltip'
import WarningAmberIcon from '@mui/icons-material/WarningAmber'
import { useTranslation } from 'react-i18next'

/**
 * Post-Freeze Business Refinement 2 (requirements doc §23) - Legacy
 * Expected Arrival and Manufacturer Stockout Information must never fully
 * hide one another when they actually disagree (Legacy shows an incoming
 * Arrival while the Manufacturer is still telling us it's short). This is
 * the shared "review both values" marker every screen renders next to the
 * conflicting SKU, rather than resolving the disagreement silently.
 */
export function RestockConflictWarning() {
  const { t } = useTranslation('restockExpectation')
  return (
    <Tooltip title={t('conflictWarning')}>
      {/* MUI's Tooltip forwards its `title` onto the child as `aria-label`
          when the child has none of its own - the full conflictWarning
          sentence contains "メーカー" as a substring, which made this icon
          ambiguously match any `getByLabel('メーカー')` query elsewhere on
          the same page (e.g. the Candidate List's メーカー filter Select).
          An explicit short aria-label overrides that forwarding. */}
      <WarningAmberIcon fontSize="small" color="warning" aria-label={t('conflictWarningIconLabel')} data-testid="restock-conflict-warning" />
    </Tooltip>
  )
}
