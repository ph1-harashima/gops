import { useState } from 'react'
import { Link, Navigate, Route, Routes, useLocation } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import AppBar from '@mui/material/AppBar'
import Toolbar from '@mui/material/Toolbar'
import Typography from '@mui/material/Typography'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Divider from '@mui/material/Divider'
import CircularProgress from '@mui/material/CircularProgress'
import Box from '@mui/material/Box'
import Menu from '@mui/material/Menu'
import MenuItem from '@mui/material/MenuItem'
import PersonIcon from '@mui/icons-material/Person'
import LogoutIcon from '@mui/icons-material/Logout'

import { LanguageSwitcher } from '../shared/components/LanguageSwitcher'

import { DashboardPage } from '../features/dashboard/DashboardPage'
import { CandidateListPage } from '../features/candidates/CandidateListPage'
import { SkuDetailPage } from '../features/skuDetail/SkuDetailPage'
import { OrderDraftPage } from '../features/drafts/OrderDraftPage'
import { PoPreviewPage } from '../features/drafts/PoPreviewPage'
import { SupplierResponsePage } from '../features/supplierResponse/SupplierResponsePage'
import { OrderHistoryListPage } from '../features/history/OrderHistoryListPage'
import { OrderHistoryDetailPage } from '../features/history/OrderHistoryDetailPage'
import { SupplierContactPage } from '../features/admin/SupplierContactPage'
import { ManufacturerChannelPage } from '../features/admin/ManufacturerChannelPage'
import { MailTemplatePage } from '../features/admin/MailTemplatePage'
import { PortalMailSettingsPage } from '../features/admin/PortalMailSettingsPage'
import { SupplierRegionClassificationPage } from '../features/admin/SupplierRegionClassificationPage'
import { PriceChangeListPage } from '../features/priceChanges/PriceChangeListPage'
import { PriceChangeEditPage } from '../features/priceChanges/PriceChangeEditPage'
import { PriceChangeDetailPage } from '../features/priceChanges/PriceChangeDetailPage'
import { ArrivalListPage } from '../features/arrivals/ArrivalListPage'
import { ArrivalDetailPage } from '../features/arrivals/ArrivalDetailPage'
import { WarehouseStockListPage } from '../features/warehouseStock/WarehouseStockListPage'
import { StockSalesListPage } from '../features/stockSales/StockSalesListPage'
import { LoginPage } from '../features/auth/LoginPage'
import { useAuth } from '../features/auth/AuthContext'
import { ROLE_ADMIN } from '../shared/types/auth'

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
  const isAdmin = user?.role === ROLE_ADMIN

  const isHistorySection = location.pathname.startsWith('/orders/history') || /^\/orders\/\d+$/.test(location.pathname)
  // Phase 7-E Section 6: Information-Architecture-only reorganization - the
  // two ADMIN Master screens (Supplier Contact / Mail Template) move from
  // separate top-level Nav buttons into one "マスタメンテナンス" submenu, so
  // the Top Nav stops mixing per-record Portal Master upkeep with the
  // business menus (Dashboard/発注候補/発注一覧). No Route, no functional
  // change - both pages, their data-testids, and the Backend's own
  // @PreAuthorize gate are all untouched.
  const isMasterMaintenanceSection =
    location.pathname === '/admin/supplier-contacts' || location.pathname === '/admin/mail-templates'
    || location.pathname === '/admin/manufacturer-channels' || location.pathname === '/admin/mail-settings'
    || location.pathname === '/admin/supplier-region-classifications'
  const [masterMenuAnchor, setMasterMenuAnchor] = useState<HTMLElement | null>(null)

  const roleLabel = user ? t(`drafts:roleLabel.${user.role}`, { defaultValue: user.role }) : ''

  return (
    // Phase 7-F Header/List UX Audit: the whole App shell is a fixed-height
    // flex column now (AppBar/banner as non-scrolling flex items, routed
    // content as the single flex:1/overflow:auto region below them) so that
    // every list screen's MUI `stickyHeader` Table sticks correctly against
    // ITS nearest scrolling ancestor (this Box) without ever needing to
    // coordinate pixel offsets against the Header's own height - the Header
    // is simply never inside the scrolling region, so it can't be scrolled
    // under or overlapped by a sticky Table header. Previously this Box
    // didn't exist and `<AppBar position="static">` scrolled away with the
    // rest of the page like any other content.
    <Box sx={{ height: '100vh', display: 'flex', flexDirection: 'column' }}>
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
            <>
              {/* Phase 7-F Header UX Audit: Navigation Area - business/admin
                  menu links only. Deliberately separated (Divider below)
                  from the User/Session Area so the two groups are never
                  visually confused with each other (found during manual
                  review: all 7 items - 4 Nav links, user name, Role, Logout
                  - previously rendered in one undifferentiated row). */}
              {/* Acceptance Fix item 9: English labels (e.g. "ORDER
                  CANDIDATES", "MASTER MAINTENANCE") run noticeably longer
                  than their Japanese equivalents and, without an explicit
                  nowrap, would wrap onto a second line inside their own
                  Button and grow the whole Header's height. `whiteSpace:
                  'nowrap'` keeps every label on one line; `overflowX: 'auto'`
                  is the fallback for a viewport too narrow to fit them all
                  (a horizontal scroll within this one Nav strip, never a
                  page-wide scrollbar) - both are no-ops for the shorter
                  Japanese labels, so ja layout is unchanged. */}
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center', flexWrap: 'wrap', rowGap: 0.5, '& .MuiButton-root': { whiteSpace: 'nowrap' } }} data-testid="header-nav-area">
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
                {/* Phase 8-B: Price Change Foundation - independent of the
                    Ordering nav group above (no Business/Technical
                    Dependency on Ordering, customer-review-decision-package.md
                    16.2章), so it gets its own top-level Nav entry rather
                    than nesting under an Ordering-related menu. */}
                <Button
                  size="small"
                  component={Link}
                  to="/price-changes"
                  color={location.pathname.startsWith('/price-changes') ? 'primary' : 'inherit'}
                  data-testid="nav-price-changes"
                >
                  {t('navPriceChanges')}
                </Button>
                {/* Phase 8-G: Arrival / Warehouse Stock Visibility Foundation
                    - independent of Ordering (no Business/Technical
                    Dependency, customer-review-decision-package.md 16.2章
                    #19), so both get their own top-level Nav entries, same
                    reasoning as Price Change above. */}
                <Button
                  size="small"
                  component={Link}
                  to="/arrivals"
                  color={location.pathname.startsWith('/arrivals') ? 'primary' : 'inherit'}
                  data-testid="nav-arrivals"
                >
                  {t('navArrivals')}
                </Button>
                <Button
                  size="small"
                  component={Link}
                  to="/warehouse-stock"
                  color={location.pathname.startsWith('/warehouse-stock') ? 'primary' : 'inherit'}
                  data-testid="nav-warehouse-stock"
                >
                  {t('navWarehouseStock')}
                </Button>
                {/* Phase 8-H: Stock/Sales Visibility Foundation - same
                    independent-of-Ordering reasoning as Arrival/Warehouse
                    Stock above. */}
                <Button
                  size="small"
                  component={Link}
                  to="/stock-sales"
                  color={location.pathname.startsWith('/stock-sales') ? 'primary' : 'inherit'}
                  data-testid="nav-stock-sales"
                >
                  {t('navStockSales')}
                </Button>
                {/* Phase 7-C3 12章 / Phase 7-E Section 6: ADMIN-only Master
                    management, grouped under one submenu. Backend also
                    enforces this (403 for OPERATOR on every underlying API
                    call) - hiding the Nav entry is UX convenience, not the
                    access control. */}
                {isAdmin && (
                  <>
                    <Button
                      size="small"
                      color={isMasterMaintenanceSection ? 'primary' : 'inherit'}
                      onClick={(e) => setMasterMenuAnchor(e.currentTarget)}
                      data-testid="nav-master-maintenance"
                    >
                      {t('navMasterMaintenance')}
                    </Button>
                    <Menu anchorEl={masterMenuAnchor} open={Boolean(masterMenuAnchor)} onClose={() => setMasterMenuAnchor(null)}>
                      <MenuItem
                        component={Link}
                        to="/admin/supplier-contacts"
                        selected={location.pathname === '/admin/supplier-contacts'}
                        onClick={() => setMasterMenuAnchor(null)}
                        data-testid="nav-admin-supplier-contacts"
                      >
                        {t('navAdminSupplierContacts')}
                      </MenuItem>
                      <MenuItem
                        component={Link}
                        to="/admin/mail-templates"
                        selected={location.pathname === '/admin/mail-templates'}
                        onClick={() => setMasterMenuAnchor(null)}
                        data-testid="nav-admin-mail-templates"
                      >
                        {t('navAdminMailTemplates')}
                      </MenuItem>
                      <MenuItem
                        component={Link}
                        to="/admin/manufacturer-channels"
                        selected={location.pathname === '/admin/manufacturer-channels'}
                        onClick={() => setMasterMenuAnchor(null)}
                        data-testid="nav-admin-manufacturer-channels"
                      >
                        {t('navAdminManufacturerChannels')}
                      </MenuItem>
                      <MenuItem
                        component={Link}
                        to="/admin/mail-settings"
                        selected={location.pathname === '/admin/mail-settings'}
                        onClick={() => setMasterMenuAnchor(null)}
                        data-testid="nav-admin-mail-settings"
                      >
                        {t('navAdminMailSettings')}
                      </MenuItem>
                      <MenuItem
                        component={Link}
                        to="/admin/supplier-region-classifications"
                        selected={location.pathname === '/admin/supplier-region-classifications'}
                        onClick={() => setMasterMenuAnchor(null)}
                        data-testid="nav-admin-supplier-region-classifications"
                      >
                        {t('navAdminSupplierRegionClassifications')}
                      </MenuItem>
                    </Menu>
                  </>
                )}
              </Stack>

              <Divider orientation="vertical" flexItem sx={{ mx: 2, my: 1 }} data-testid="header-area-divider" />

              {/* Phase 7-F Header UX Audit: User/Session Area - a single
                  visual group (Icon + Name + Role Badge + Logout), separated
                  from Navigation by the Divider above. The Role is a
                  non-interactive filled Chip (no onClick, no href, no Link
                  styling) so it reads as a status badge, never as another
                  clickable Menu Item next to it - the exact confusion this
                  audit's manual review flagged ("購買担当（デモ） 購買担当"
                  previously rendered as two adjacent, same-looking text
                  runs). User name itself is plain Typography (not a Link/
                  Button) since it has no click behavior this Phase - per
                  the audit's own instruction, a non-interactive label must
                  never be styled to look clickable. */}
              <Stack direction="row" spacing={1} sx={{ alignItems: 'center' }} data-testid="header-user-area">
                <PersonIcon fontSize="small" color="action" />
                <Stack spacing={0.25} sx={{ lineHeight: 1.1 }}>
                  <Typography variant="body2" sx={{ fontWeight: 500, lineHeight: 1.2 }} data-testid="current-user-display">
                    {user.displayName}
                  </Typography>
                  <Chip
                    size="small"
                    variant="filled"
                    color={isAdmin ? 'secondary' : 'default'}
                    label={roleLabel}
                    data-testid="current-user-role"
                    sx={{ height: 18, fontSize: '0.7rem', '& .MuiChip-label': { px: 0.75 } }}
                  />
                </Stack>
                <Button
                  size="small"
                  onClick={() => void logout()}
                  startIcon={<LogoutIcon fontSize="small" />}
                  data-testid="nav-logout"
                  sx={{ ml: 1 }}
                >
                  {t('logout')}
                </Button>
              </Stack>
            </>
          )}
          {/* G-OPS i18n完成 #3: 右上、常時表示（未ログイン時のLoginページでも
              言語を選べるよう{user && ...}の外に置く）。Mail Templateの
              language(DB値、メーカー/Supplierごとの送信言語)とは無関係 -
              これはUI表示言語のみを切り替える(§7)。 */}
          <Box sx={{ ml: 2 }}>
            <LanguageSwitcher />
          </Box>
        </Toolbar>
      </AppBar>
      <Alert severity="warning" square sx={{ borderRadius: 0 }}>
        {t('demoEnvironmentBanner')}
      </Alert>

      <Box sx={{ flex: 1, overflow: 'auto' }}>
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
            <Route path="/price-changes" element={<PriceChangeListPage />} />
            <Route path="/price-changes/:id/edit" element={<PriceChangeEditPage />} />
            <Route path="/price-changes/:id" element={<PriceChangeDetailPage />} />
            <Route path="/arrivals" element={<ArrivalListPage />} />
            <Route path="/arrivals/:supplierCode/:poNumber/:invoiceNumber" element={<ArrivalDetailPage />} />
            <Route path="/warehouse-stock" element={<WarehouseStockListPage />} />
            <Route path="/stock-sales" element={<StockSalesListPage />} />
            {/* Phase 7-C3 12章: Backend enforces ADMIN-only on every
                underlying API (403 for OPERATOR) regardless of this Route
                being reachable - no client-side route guard is the sole
                control here, matching 13章/17章's convention throughout this
                engagement. */}
            <Route path="/admin/supplier-contacts" element={<SupplierContactPage />} />
            <Route path="/admin/manufacturer-channels" element={<ManufacturerChannelPage />} />
            <Route path="/admin/mail-templates" element={<MailTemplatePage />} />
            <Route path="/admin/mail-settings" element={<PortalMailSettingsPage />} />
            <Route path="/admin/supplier-region-classifications" element={<SupplierRegionClassificationPage />} />
            <Route path="*" element={<Navigate to="/" replace />} />
          </Routes>
        )}
      </Box>
    </Box>
  )
}
