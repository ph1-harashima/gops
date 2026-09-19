import { Navigate, Route, Routes, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Stack from '@mui/material/Stack'
import Tabs from '@mui/material/Tabs'
import Tab from '@mui/material/Tab'
import Button from '@mui/material/Button'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import ArrowBackIcon from '@mui/icons-material/ArrowBack'

import { useSupplierMasterDetail } from './supplierMasterApi'
import { SupplierOverviewTab } from './SupplierOverviewTab'
import { SupplierContactPage } from './SupplierContactPage'
import { ManufacturerChannelPage } from './ManufacturerChannelPage'
import { SupplierRegionClassificationPage } from './SupplierRegionClassificationPage'
import { OfficialPoShortCodePage } from './OfficialPoShortCodePage'

const TABS = ['overview', 'contacts', 'communication', 'region', 'po-code'] as const

/**
 * Master Maintenance Hub (docs/gops-master-maintenance-hub-implementation.md):
 * "Supplier Settings" - the Context (Supplier Code, in the URL per Navigation
 * 原則) never changes while moving between Overview/Contacts/Communication/
 * Region/PO Code, and Browser Back/Forward keeps working because this is
 * still an ordinary nested Route, not any new Navigation Framework.
 *
 * Reuses the 4 existing Master screens AS-IS via their own optional
 * `supplierCodeFilter` prop (their Backend Service/API/Validation is
 * completely untouched) - this Layout only owns the Context header + Tab
 * chrome around them.
 */
export function SupplierSettingsLayout() {
  const { t } = useTranslation(['supplierMaster', 'common'])
  const { supplierCode } = useParams<{ supplierCode: string }>()
  const navigate = useNavigate()
  const location = useLocation()
  const { data, isLoading, isError } = useSupplierMasterDetail(supplierCode ?? '')

  const activeTab = TABS.find((tab) => location.pathname.endsWith(`/${tab}`)) ?? 'overview'

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  if (isError || !data) {
    return (
      <Box sx={{ p: 3 }}>
        <Alert severity="error" sx={{ mb: 2 }}>{t('supplierMaster:notFound')}</Alert>
        <Button startIcon={<ArrowBackIcon />} onClick={() => navigate('/master/suppliers')} data-testid="supplier-settings-back-to-list">
          {t('supplierMaster:backToList')}
        </Button>
      </Box>
    )
  }

  return (
    <Box sx={{ p: { xs: 1.5, sm: 3 }, height: '100%', display: 'flex', flexDirection: 'column' }}>
      <Button
        startIcon={<ArrowBackIcon />}
        size="small"
        onClick={() => navigate('/master/suppliers')}
        sx={{ alignSelf: 'flex-start', mb: 1 }}
        data-testid="supplier-settings-back-to-list"
      >
        {t('supplierMaster:backToList')}
      </Button>

      {/* Navigation原則: Supplier Context is always visible, on every Tab. */}
      <Typography variant="h5" component="h1" data-testid="supplier-settings-context-name">
        {t('supplierMaster:contextHeader', { name: data.supplierName })}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }} data-testid="supplier-settings-context-code">
        {t('supplierMaster:contextSupplierCode', { code: data.supplierCode })}
      </Typography>

      {/* Mobile Responsive Audit §26 (priority): the 5 Tabs would otherwise
          wrap/overflow at 375-430px - `variant="scrollable"` is MUI's own
          built-in Mobile Tab treatment (swipeable + scroll arrows), no
          custom Select/Menu swap needed, and it's a no-op at Desktop widths
          where all 5 Tabs already fit. */}
      <Tabs
        value={activeTab}
        variant="scrollable"
        scrollButtons="auto"
        allowScrollButtonsMobile
        sx={{ mb: 2, borderBottom: 1, borderColor: 'divider' }}
      >
        {TABS.map((tab) => (
          <Tab
            key={tab}
            value={tab}
            label={t(`supplierMaster:tabs.${tab === 'po-code' ? 'poCode' : tab}`)}
            onClick={() => navigate(`/master/suppliers/${encodeURIComponent(supplierCode ?? '')}/${tab}`)}
            data-testid={`supplier-settings-tab-${tab}`}
          />
        ))}
      </Tabs>

      <Box sx={{ flex: 1, minHeight: 0, display: 'flex', flexDirection: 'column' }}>
        <Routes>
          <Route index element={<Navigate to="overview" replace />} />
          <Route path="overview" element={<SupplierOverviewTab supplier={data} onNavigateTab={(tab) => navigate(`/master/suppliers/${encodeURIComponent(supplierCode ?? '')}/${tab}`)} />} />
          <Route path="contacts" element={<SupplierContactPage supplierCodeFilter={supplierCode} />} />
          <Route path="communication" element={<ManufacturerChannelPage supplierCodeFilter={supplierCode} />} />
          <Route path="region" element={<SupplierRegionClassificationPage supplierCodeFilter={supplierCode} />} />
          <Route path="po-code" element={<OfficialPoShortCodePage supplierCodeFilter={supplierCode} />} />
        </Routes>
      </Box>
    </Box>
  )
}
