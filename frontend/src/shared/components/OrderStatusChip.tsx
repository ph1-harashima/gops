import Chip from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'

const COLOR_BY_STATUS: Record<string, 'default' | 'success' | 'warning' | 'info'> = {
  DRAFT: 'default',
  // Phase 7-C1: PENDING_APPROVAL/APPROVED are the new live statuses.
  // READY_TO_ORDER is kept only so historical pre-migration Audit Timeline
  // entries (never rewritten - see V8 migration comment) still render with
  // a color; no current Order can hold this status anymore.
  PENDING_APPROVAL: 'warning',
  APPROVED: 'success',
  READY_TO_ORDER: 'success',
  SENT: 'info',
  AWAITING_SUPPLIER: 'warning',
  SUPPLIER_CONFIRMED: 'success',
  // Phase 7-C5: explicit ADMIN Business Action, distinct from SUPPLIER_CONFIRMED.
  AGREED: 'success',
}

/**
 * Post-Freeze Visual Walkthrough Findings Fix (Finding #4,
 * docs/gops-visual-walkthrough-findings-fix.md): `officialPoLifecycleStatus`
 * is the CURRENT (latest-revision) Official PO Integration Request's own
 * lifecycle axis (ACTIVE/SUPERSEDED/CANCEL_REQUESTED/CANCELLED - entirely
 * separate from `status`, which is the Order's own Business Workflow Status
 * and is NEVER changed by a PO cancellation - see PortalOrder/
 * OfficialPoIntegrationRequest's own Javadoc). Before this fix, every caller
 * of this Chip only ever passed `status`, so an Order whose Official PO had
 * been cancelled kept showing "承認済み" as its Primary Status everywhere
 * (Order Detail top, Order History list) even though the PO itself, and the
 * Revision History table on the same Order Detail page, already correctly
 * showed "キャンセル済み" - a real cross-screen status inconsistency
 * (Visual Walkthrough finding, not a business-logic bug: PortalOrder.status
 * intentionally stays APPROVED to preserve Reissue eligibility/history -
 * only the DISPLAYED Primary Status is corrected here).
 *
 * When the caller has this value and it is CANCELLED, this Chip overrides
 * its own label/color to reflect that as the Primary Status, without
 * altering the underlying `status` value anywhere else (Audit Trail, API,
 * Reissue eligibility, Dashboard KPI counts, etc. are all unaffected).
 */
export function OrderStatusChip({
  status,
  officialPoLifecycleStatus,
}: {
  status: string
  officialPoLifecycleStatus?: string | null
}) {
  const { t } = useTranslation('status')
  const isCancelled = officialPoLifecycleStatus === 'CANCELLED'
  const label = isCancelled ? t('cancelledOrderLabel') : t(`orderStatus.${status}`, { defaultValue: status })
  const color = isCancelled ? 'error' : (COLOR_BY_STATUS[status] ?? 'default')
  return (
    <Chip
      size="small"
      label={label}
      color={color}
      variant={color === 'default' ? 'outlined' : 'filled'}
      data-testid={isCancelled ? 'order-status-chip-cancelled' : undefined}
    />
  )
}
