import { useTranslation } from 'react-i18next'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'

import { CandidateListPage } from '../features/candidates/CandidateListPage'

/**
 * Step 0/1 vertical slice: Order Candidate List only.
 * Dashboard / SKU Detail / Draft / PO Preview / Supplier Response / History /
 * routing between them are explicitly out of scope for this Step (implementation
 * instructions 11章) and are intentionally not wired up here yet.
 */
export function App() {
  const { t } = useTranslation('common')

  return (
    <>
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar variant="dense">
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            {t('appName')}
          </Typography>
        </Toolbar>
      </AppBar>
      <Alert severity="warning" square sx={{ borderRadius: 0 }}>
        {t('demoEnvironmentBanner')}
      </Alert>
      <CandidateListPage />
    </>
  )
}
