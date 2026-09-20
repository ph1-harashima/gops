import Typography from '@mui/material/Typography'
import { useTranslation } from 'react-i18next'
import type { ContactMethod } from '../types/restockExpectation'

/**
 * Post-Freeze Business Refinement 2 (requirements doc §9/§15) - "when and
 * how the Manufacturer told us", distinct from `updatedAt` (when G-OPS
 * itself was edited). Renders nothing when no Information Received Date
 * was ever recorded.
 */
export function ManufacturerConfirmationCaption({
  informationReceivedDate,
  contactMethod,
}: {
  informationReceivedDate: string | null
  contactMethod: ContactMethod | null
}) {
  const { t } = useTranslation('restockExpectation')
  if (!informationReceivedDate) return null
  const method = contactMethod ? t(`contactMethod.${contactMethod}`) : t('contactMethodNoneOption')
  return (
    <Typography variant="caption" color="text.secondary" data-testid="manufacturer-confirmed-caption">
      {t('manufacturerConfirmedCaption', { date: informationReceivedDate, method })}
    </Typography>
  )
}
