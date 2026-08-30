import Snackbar from '@mui/material/Snackbar'
import Alert from '@mui/material/Alert'
import type { AlertColor } from '@mui/material/Alert'
import type { ReactNode } from 'react'

/**
 * Phase 7-I (Layout Shift audit): shared primitive for every TRANSIENT
 * notification (Save succeeded, a mutation failed, "you have unsaved
 * changes") across the app - MUI Snackbar renders via a Portal at a fixed
 * screen position, so it can never push the page's own document flow up or
 * down the way an inline <Alert> conditionally mounted above other content
 * does (the confirmed Root Cause of the reported bug: Order Draft's qty
 * Number Input moving out from under the mouse mid-click-sequence, because
 * "保存されていない変更があります" mounted an Alert directly above it).
 *
 * Deliberately NOT for standing/persistent Business Messages that must stay
 * visible for as long as a real business state holds (e.g. "G-SYS正式PO未連携
 * です", Fulfillment/Concurrency status panels, empty-list states, role-only
 * indicators) - those remain inline <Alert> in document flow, unchanged.
 * The dividing line is "does this represent a one-off event or a live,
 * bounded-duration edit-session state" (Save success, a failed mutation,
 * "you have unsaved changes right now") vs "a fact about the Order/data that
 * stays true until something else changes it" - not severity or wording.
 *
 * `autoHideDuration`: pass a number (ms) for one-shot events that should
 * self-dismiss (Save succeeded, a mutation failed) - default 4000. Pass
 * `null` for a state that should stay visible for exactly as long as its
 * own `open` condition holds true (e.g. isDirty) rather than timing out
 * while the condition is still true.
 */
export interface ToastProps {
  open: boolean
  message: ReactNode
  severity: AlertColor
  onClose?: () => void
  autoHideDuration?: number | null
  anchorOrigin?: { vertical: 'top' | 'bottom'; horizontal: 'left' | 'center' | 'right' }
  testId?: string
}

export function Toast({
  open,
  message,
  severity,
  onClose,
  autoHideDuration = 4000,
  anchorOrigin = { vertical: 'bottom', horizontal: 'center' },
  testId,
}: ToastProps) {
  return (
    <Snackbar
      open={open}
      autoHideDuration={autoHideDuration ?? undefined}
      onClose={(_event, reason) => {
        // Phase 7-E precedent (Confirm Dialog stability): clicking elsewhere
        // on the page must never silently dismiss a message the user hasn't
        // actually acknowledged - only an explicit timeout or the Alert's
        // own close (X) button does.
        if (reason === 'clickaway') return
        onClose?.()
      }}
      anchorOrigin={anchorOrigin}
    >
      <Alert severity={severity} onClose={onClose} variant="filled" sx={{ width: '100%' }} data-testid={testId}>
        {message}
      </Alert>
    </Snackbar>
  )
}
