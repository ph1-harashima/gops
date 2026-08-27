import { Navigate, Route, Routes } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Box from '@mui/material/Box'

import { CandidateListPage } from '../features/candidates/CandidateListPage'
import { OrderDraftPage } from '../features/drafts/OrderDraftPage'
import { LoginPage } from '../features/auth/LoginPage'
import { useAuth } from '../features/auth/AuthContext'

/**
 * Step 2: adds Create Draft -> Order Draft (Save) on top of the Step 0/1
 * Order Candidate List. Dashboard / SKU Detail / PO Preview / Supplier
 * Response / History are still out of scope (implementation instructions
 * 14章 Preview Button is a disabled placeholder only, wired inside
 * OrderDraftPage).
 */
export function App() {
  const { t } = useTranslation('common')
  const { user, loading, logout } = useAuth()

  return (
    <>
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar variant="dense">
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            {t('appName')}
          </Typography>
          {user && (
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <Typography variant="body2">{user.displayName}</Typography>
              <Button size="small" onClick={() => void logout()}>
                {t('logout')}
              </Button>
            </Stack>
          )}
        </Toolbar>
      </AppBar>
      <Alert severity="warning" square sx={{ borderRadius: 0 }}>
        {t('demoEnvironmentBanner')}
      </Alert>

      {loading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', mt: 10 }}>
          <CircularProgress />
        </Box>
      ) : !user ? (
        <LoginPage />
      ) : (
        <Routes>
          <Route path="/" element={<CandidateListPage />} />
          <Route path="/orders/drafts/:id" element={<OrderDraftPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      )}
    </>
  )
}
