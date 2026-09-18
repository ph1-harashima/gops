import { useEffect, useState } from 'react'
import { useTranslation } from 'react-i18next'
import Box from '@mui/material/Box'
import Paper from '@mui/material/Paper'
import Typography from '@mui/material/Typography'
import TextField from '@mui/material/TextField'
import Button from '@mui/material/Button'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'

import { usePortalMailSettings, useUpdatePortalMailSettings } from './portalMailSettingsApi'
import { Toast } from '../../shared/components/Toast'

/**
 * Gap Analysis §11 (docs/gulliver-20260917-phase1-gap-analysis.md 11章):
 * Default CC Foundation admin screen. ADMIN only (Backend-enforced).
 *
 * IMPORTANT (explicit customer instruction, kept visible on-screen): this
 * value only PREFILLS the send-time CC Override field on Order Detail's
 * Mail Preview/Send Section - it is never automatically appended to a sent
 * Email, and there is no "always CC this address" Business Rule anywhere
 * in this system.
 */
export function PortalMailSettingsPage() {
  const { t } = useTranslation(['mailSettings', 'common'])
  const { data, isLoading } = usePortalMailSettings()
  const updateMutation = useUpdatePortalMailSettings()
  const [defaultCcInput, setDefaultCcInput] = useState('')
  const [seeded, setSeeded] = useState(false)

  useEffect(() => {
    if (data && !seeded) {
      setDefaultCcInput(data.defaultCc.join(', '))
      setSeeded(true)
    }
  }, [data, seeded])

  function handleSave() {
    const parsed = defaultCcInput.split(',').map((s) => s.trim()).filter((s) => s.length > 0)
    updateMutation.mutate(parsed)
  }

  if (isLoading) {
    return (
      <Stack direction="row" spacing={1} sx={{ m: 4, alignItems: 'center' }}>
        <CircularProgress size={20} />
        <Typography>{t('common:loading')}</Typography>
      </Stack>
    )
  }

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>{t('title')}</Typography>
      <Paper variant="outlined" sx={{ p: 2, maxWidth: 600 }}>
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t('description')}
        </Typography>

        <Toast open={updateMutation.isSuccess} severity="success" message={t('saveSuccess')} onClose={() => updateMutation.reset()} />
        <Toast open={updateMutation.isError} severity="error" message={t('common:errorGeneric')} onClose={() => updateMutation.reset()} />

        <TextField
          label={t('defaultCcLabel')}
          value={defaultCcInput}
          onChange={(e) => setDefaultCcInput(e.target.value)}
          fullWidth
          multiline
          minRows={2}
          helperText={t('defaultCcHelperText')}
          data-testid="mail-settings-default-cc-input"
          sx={{ mb: 2 }}
        />

        {data?.updatedBy && (
          <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mb: 2 }}>
            {t('lastUpdatedLabel')}: {data.updatedBy} - {data.updatedAt ? new Date(data.updatedAt).toLocaleString('ja-JP') : ''}
          </Typography>
        )}

        <Button
          variant="contained"
          onClick={handleSave}
          disabled={updateMutation.isPending}
          data-testid="mail-settings-save-button"
        >
          {updateMutation.isPending ? <CircularProgress size={20} /> : t('saveButton')}
        </Button>
      </Paper>
    </Box>
  )
}
