import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Box from '@mui/material/Box'

import { DashboardPage } from '../features/dashboard/DashboardPage'
import { CandidateListPage } from '../features/candidates/CandidateListPage'
import { SkuDetailPage } from '../features/skuDetail/SkuDetailPage'
import { OrderDraftPage } from '../features/drafts/OrderDraftPage'
import { PoPreviewPage } from '../features/drafts/PoPreviewPage'
import { SupplierResponsePage } from '../features/supplierResponse/SupplierResponsePage'
import { OrderHistoryListPage } from '../features/history/OrderHistoryListPage'
import { OrderHistoryDetailPage } from '../features/history/OrderHistoryDetailPage'
import { LoginPage } from '../features/auth/LoginPage'
import { useAuth } from '../features/auth/AuthContext'

/**
 * Step 5 finalizes the Prototype for the 9/17 demo: Dashboard (Action/
 * Operation Cockpit) is now the landing page, with SKU Detail, Attention
 * Acknowledge, and a persistent "デモ環境" badge added around the Core
 * Workflow completed in Step 4 - none of that Core Workflow business logic
 * changes here.
 */
export function App() {
  const { t } = useTranslation(['common', 'drafts'])
  const { user, loading, logout } = useAuth()
  const location = useLocation()

  const isHistorySection = location.pathname.startsWith('/orders/history') || /^\/orders\/\d+$/.test(location.pathname)

  return (
    <>
      <AppBar position="static" color="default" elevation={1}>
        <Toolbar variant="dense">
          <Typography variant="h6" component="div" sx={{ mr: 1 }}>
            {t('appName')}
          </Typography>
          {/* Implementation instructions Step 5 10章: a small, unobtrusive
              "デモ環境" badge on the common Header, distinct from (and less
              prominent than) the larger contextual banner below. */}
          <Chip size="small" variant="outlined" color="warning" label={t('demoEnvironmentBadge')} data-testid="demo-environment-badge" />
          <Box sx={{ flexGrow: 1 }} />
          {user && (
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
              <Button
                size="small"
                component={Link}
                to="/"
                color={location.pathname === '/' ? 'primary' : 'inherit'}
                data-testid="nav-dashboard"
              >
                {t('navDashboard')}
              </Button>
              <Button
                size="small"
                component={Link}
                to="/candidates"
                color={location.pathname === '/candidates' ? 'primary' : 'inherit'}
                data-testid="nav-candidates"
              >
                {t('navCandidates')}
              </Button>
              <Button
                size="small"
                component={Link}
                to="/orders/history"
                color={isHistorySection ? 'primary' : 'inherit'}
                data-testid="nav-history"
              >
                {t('navHistory')}
              </Button>
              <Typography variant="body2" data-testid="current-user-display">
                {user.displayName}
                {/* Phase 7-C1 19章: Role must be visible on every screen, not
                    just this Header - existing Layout is otherwise untouched. */}
                {' '}
                <Chip size="small" variant="outlined" label={t(`drafts:roleLabel.${user.role}`, { defaultValue: user.role })} data-testid="current-user-role" />
              </Typography>
              <Button size="small" onClick={() => void logout()} data-testid="nav-logout">
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
          <Route path="/" element={<DashboardPage />} />
          <Route path="/candidates" element={<CandidateListPage />} />
          <Route path="/items/:sku" element={<SkuDetailPage />} />
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
