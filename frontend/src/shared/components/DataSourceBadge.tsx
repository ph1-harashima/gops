import Chip from '@mui/material/Chip'
import { useTranslation } from 'react-i18next'

/**
 * Makes it visible at a glance that data on screen comes from the disposable
 * Legacy Demo Instance, never confused with real Legacy G-SYS data
 * (implementation instructions 0章/7章 data source identification requirement).
 */
export function DataSourceBadge({ dataSource }: { dataSource: string }) {
  const { t } = useTranslation('status')
  return (
    <Chip
      size="small"
      variant="outlined"
      color="info"
      label={t(`dataSource.${dataSource}`, { defaultValue: dataSource })}
    />
  )
}
