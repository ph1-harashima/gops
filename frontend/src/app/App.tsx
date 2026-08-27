import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
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
import { PoPreviewPage } from '../features/drafts/PoPreviewPage'
import { SupplierResponsePage } from '../features/supplierResponse/SupplierResponsePage'
import { OrderHistoryListPage } from '../features/history/OrderHistoryListPage'
import { OrderHistoryDetailPage } from '../features/history/OrderHistoryDetailPage'
import { LoginPage } from '../features/auth/LoginPage'
import { useAuth } from '../features/auth/AuthContext'

/**
 * Step 4 completes the Core Workflow: Candidate -> Draft -> Preview ->
 * Confirm -> Demo Send -> Supplier Response -> History. Dashboard / SKU
 * Detail remain out of scope.
 */
export function App() {
  const { t } = useTranslation('common')
  const { user, loading, logout } = useAuth()
  const location = useLocation()

  return (
    <>
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar variant="dense">
          <Typography variant="h6" component="div" sx={{ flexGrow: 1 }}>
            {t('appName')}
          </Typography>
          {user && (
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <Button
                size="small"
                component={Link}
                to="/"
                color={location.pathname === '/' ? 'primary' : 'inherit'}
              >
                {t('navCandidates')}
              </Button>
              <Button
                size="small"
                component={Link}
                to="/orders/history"
                color={location.pathname.startsWith('/orders/history') || /^\/orders\/\d+$/.test(location.pathname) ? 'primary' : 'inherit'}
              >
                {t('navHistory')}
              </Button>
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
          <Route path="/orders/drafts/:id/preview" element={<PoPreviewPage />} />
          <Route path="/orders/:id/supplier-response" element={<SupplierResponsePage />} />
          <Route path="/orders/history" element={<OrderHistoryListPage />} />
          <Route path="/orders/:id" element={<OrderHistoryDetailPage />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      )}
    </>
  )
}
