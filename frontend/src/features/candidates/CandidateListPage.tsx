import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import axios from 'axios'
import Box from '@mui/material/Box'
import Typography from '@mui/material/Typography'
import Paper from '@mui/material/Paper'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Checkbox from '@mui/material/Checkbox'
import FormControlLabel from '@mui/material/FormControlLabel'
import TextField from '@mui/material/TextField'
import MenuItem from '@mui/material/MenuItem'
import Stack from '@mui/material/Stack'
import CircularProgress from '@mui/material/CircularProgress'
import Alert from '@mui/material/Alert'
import Button from '@mui/material/Button'

import { useOrderCandidates } from './api'
import { useCreateDraft } from '../drafts/api'
import { ItemStatusChip } from '../../shared/components/ItemStatusChip'
import { DataSourceBadge } from '../../shared/components/DataSourceBadge'
import { listReturnTo, withReturnTo } from '../../shared/navigation/returnTo'
import type { OrderCandidateFilter } from '../../shared/types/orderCandidate'
import type { ApiErrorBody } from '../../shared/types/orderDraft'

const FILTER_PARAMS = ['brandCode', 'supplierCode', 'keyword'] as const

export function CandidateListPage() {
  const { t } = useTranslation(['candidates', 'common'])
  const navigate = useNavigate()
  // Phase 6-A (docs/production-ux-workflow-redesign.md 6.2章): the URL is the
  // single source of truth for Filter state - every change below writes
  // straight back to searchParams (replace: true, so typing/selecting
  // doesn't spam browser history), and Dashboard's ?brandCode=... deep-link
  // (Step 5 3章) is read the same way as any other in-flight change, not
  // just once at mount.
  const [searchParams, setSearchParams] = useSearchParams()
  const filter: OrderCandidateFilter = {
    brandCode: searchParams.get('brandCode') ?? undefined,
    supplierCode: searchParams.get('supplierCode') ?? undefined,
    keyword: searchParams.get('keyword') ?? undefined,
  }
  // Phase 6-D (docs/production-ux-workflow-redesign.md 3章/11章): Dashboard's
  // 発注候補 KPI counts recommendedQty > 0 over the SAME unfiltered candidate
  // set this screen already fetches (DashboardService.isCandidate() - no
  // Backend/API change). recommendedOnly is therefore a pure client-side
  // display filter, deliberately NOT part of OrderCandidateFilter/
  // useOrderCandidates - it never becomes a Backend query param. Kept in
  // the URL like every other Filter so the KPI's Deep Link, Browser Back/
  // Forward, and returnTo all keep working the same way.
  const recommendedOnly = searchParams.get('recommendedOnly') === 'true'
  const [keywordInput, setKeywordInput] = useState(filter.keyword ?? '')
  const [selected, setSelected] = useState<Set<string>>(new Set())

  // Keeps the Keyword text field in sync when the URL changes from outside
  // a keystroke here - browser Back/Forward, a typed/bookmarked URL, or a
  // future Dashboard deep-link.
  useEffect(() => {
    setKeywordInput(searchParams.get('keyword') ?? '')
  }, [searchParams])

  function updateFilter(patch: Partial<OrderCandidateFilter>) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        const merged = { ...filter, ...patch }
        for (const key of FILTER_PARAMS) {
          const value = merged[key]
          if (value) next.set(key, value)
          else next.delete(key)
        }
        return next
      },
      { replace: true },
    )
  }

  const listPath = listReturnTo('/candidates', searchParams)

  const { data, isLoading, isError, refetch } = useOrderCandidates(filter)
  const createDraftMutation = useCreateDraft()

  const visibleData = useMemo(() => {
    if (!data) return data
    return recommendedOnly ? data.filter((row) => (row.recommendedQty ?? 0) > 0) : data
  }, [data, recommendedOnly])

  function toggleRecommendedOnly(checked: boolean) {
    setSearchParams(
      (prev) => {
        const next = new URLSearchParams(prev)
        if (checked) next.set('recommendedOnly', 'true')
        else next.delete('recommendedOnly')
        return next
      },
      { replace: true },
    )
  }

  const brandOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of data ?? []) {
      if (row.brandCode) map.set(row.brandCode, row.brandName ?? row.brandCode)
    }
    return Array.from(map.entries())
  }, [data])

  const supplierOptions = useMemo(() => {
    const map = new Map<string, string>()
    for (const row of data ?? []) {
      if (row.supplierCode) map.set(row.supplierCode, row.supplierName ?? row.supplierCode)
    }
    return Array.from(map.entries())
  }, [data])

  function toggleSelect(sku: string) {
    setSelected((prev) => {
      const next = new Set(prev)
      if (next.has(sku)) next.delete(sku)
      else next.add(sku)
      return next
    })
  }

  function applyKeyword() {
    updateFilter({ keyword: keywordInput || undefined })
  }

  function handleCreateDraft() {
    createDraftMutation.mutate(
      { skus: Array.from(selected) },
      {
        onSuccess: (draft) => {
          setSelected(new Set())
          navigate(withReturnTo(`/orders/drafts/${draft.id}`, listPath))
        },
      },
    )
  }

  const createDraftErrorCode =
    createDraftMutation.isError && axios.isAxiosError<ApiErrorBody>(createDraftMutation.error)
      ? createDraftMutation.error.response?.data?.errorCode
      : null

  return (
    <Box sx={{ p: 3 }}>
      <Typography variant="h5" component="h1" gutterBottom>
        {t('candidates:title')}
      </Typography>

      <Stack direction="row" spacing={2} sx={{ mb: 2, flexWrap: 'wrap', gap: 2 }}>
        <TextField
          select
          size="small"
          label={t('candidates:filter.brand')}
          sx={{ minWidth: 200 }}
          value={filter.brandCode ?? ''}
          onChange={(e) => updateFilter({ brandCode: e.target.value || undefined })}
        >
          <MenuItem value="">{t('candidates:filter.all')}</MenuItem>
          {brandOptions.map(([code, name]) => (
            <MenuItem key={code} value={code}>
              {name}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          size="small"
          label={t('candidates:filter.supplier')}
          sx={{ minWidth: 220 }}
          value={filter.supplierCode ?? ''}
          onChange={(e) => updateFilter({ supplierCode: e.target.value || undefined })}
        >
          <MenuItem value="">{t('candidates:filter.all')}</MenuItem>
          {supplierOptions.map(([code, name]) => (
            <MenuItem key={code} value={code}>
              {name}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          size="small"
          label={t('candidates:filter.keyword')}
          placeholder={t('candidates:filter.keywordPlaceholder') ?? undefined}
          sx={{ minWidth: 240 }}
          value={keywordInput}
          onChange={(e) => setKeywordInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && applyKeyword()}
          onBlur={applyKeyword}
        />

        <FormControlLabel
          control={
            <Checkbox
              checked={recommendedOnly}
              onChange={(e) => toggleRecommendedOnly(e.target.checked)}
              data-testid="recommended-only-checkbox"
            />
          }
          label={t('candidates:filter.recommendedOnly')}
        />

        <Box sx={{ flexGrow: 1 }} />

        <Button
          variant="contained"
          disabled={selected.size === 0 || createDraftMutation.isPending}
          onClick={handleCreateDraft}
          data-testid="create-draft-button"
        >
          {createDraftMutation.isPending ? (
            <CircularProgress size={20} />
          ) : (
            t('candidates:createDraft', { count: selected.size })
          )}
        </Button>
      </Stack>

      {createDraftErrorCode === 'MIXED_SUPPLIER_NOT_ALLOWED' && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('candidates:createDraftMixedSupplier')}
        </Alert>
      )}
      {createDraftErrorCode && createDraftErrorCode !== 'MIXED_SUPPLIER_NOT_ALLOWED' && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {t('candidates:createDraftFailed', { code: createDraftErrorCode })}
        </Alert>
      )}

      {isLoading && (
        <Stack direction="row" spacing={1} sx={{ my: 4, alignItems: 'center' }}>
          <CircularProgress size={20} />
          <Typography>{t('common:loading')}</Typography>
        </Stack>
      )}

      {isError && (
        <Alert
          severity="error"
          action={
            <Button color="inherit" size="small" onClick={() => refetch()}>
              {t('common:retry')}
            </Button>
          }
          sx={{ my: 2 }}
        >
          {t('common:errorGeneric')}
        </Alert>
      )}

      {!isLoading && !isError && visibleData && visibleData.length === 0 && (
        <Alert severity="info" sx={{ my: 2 }}>
          {t('candidates:empty')}
        </Alert>
      )}

      {!isLoading && !isError && visibleData && visibleData.length > 0 && (
        <>
          <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
            {t('candidates:resultCount', { count: visibleData.length })}
          </Typography>
          <TableContainer component={Paper} variant="outlined">
            <Table size="small" stickyHeader>
              <TableHead>
                <TableRow>
                  <TableCell padding="checkbox">{t('candidates:table.select')}</TableCell>
                  <TableCell>{t('candidates:table.sku')}</TableCell>
                  <TableCell>{t('candidates:table.itemName')}</TableCell>
                  <TableCell>{t('candidates:table.brand')}</TableCell>
                  <TableCell>{t('candidates:table.supplier')}</TableCell>
                  <TableCell align="right">{t('candidates:table.currentStock')}</TableCell>
                  <TableCell align="right">{t('candidates:table.safetyStock')}</TableCell>
                  <TableCell align="right">{t('candidates:table.openPo')}</TableCell>
                  <TableCell align="right">{t('candidates:table.monthlySales')}</TableCell>
                  <TableCell align="right">{t('candidates:table.leadTime')}</TableCell>
                  <TableCell align="right">{t('candidates:table.recommendedQty')}</TableCell>
                  <TableCell>{t('candidates:table.itemStatus')}</TableCell>
                  <TableCell align="right">{t('candidates:table.unitPrice')}</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {visibleData.map((row) => (
                  <TableRow key={row.sku} hover selected={selected.has(row.sku)} data-testid={`candidate-row-${row.sku}`}>
                    <TableCell padding="checkbox">
                      <Checkbox
                        checked={selected.has(row.sku)}
                        onChange={() => toggleSelect(row.sku)}
                        slotProps={{ input: { 'aria-label': row.sku } as never }}
                        data-testid={`candidate-checkbox-${row.sku}`}
                      />
                    </TableCell>
                    <TableCell>
                      <Button
                        size="small"
                        onClick={() => navigate(withReturnTo(`/items/${encodeURIComponent(row.sku)}`, listPath))}
                      >
                        {row.sku}
                      </Button>
                    </TableCell>
                    <TableCell>
                      <Stack spacing={0.5}>
                        <span>{row.itemName ?? t('candidates:notAvailable')}</span>
                        <DataSourceBadge dataSource={row.dataSource} />
                      </Stack>
                    </TableCell>
                    <TableCell>{row.brandName ?? row.brandCode}</TableCell>
                    <TableCell>{row.supplierName ?? row.supplierCode ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.currentStock ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.safetyStock ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.openPo ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.monthlySales ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">{row.leadTime ?? t('candidates:notAvailable')}</TableCell>
                    <TableCell align="right">
                      <Typography component="span" sx={{ fontWeight: 'bold', color: 'primary.main' }}>
                        {row.recommendedQty ?? t('candidates:notAvailable')}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <ItemStatusChip status={row.itemStatus} />
                    </TableCell>
                    <TableCell align="right">
                      {row.unitPrice != null ? `¥${row.unitPrice.toLocaleString()}` : t('candidates:notAvailable')}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </TableContainer>
        </>
      )}
    </Box>
  )
}
