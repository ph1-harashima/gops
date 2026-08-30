import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import Table from '@mui/material/Table'
import TableBody from '@mui/material/TableBody'
import TableCell from '@mui/material/TableCell'
import TableContainer from '@mui/material/TableContainer'
import TableHead from '@mui/material/TableHead'
import TableRow from '@mui/material/TableRow'
import Paper from '@mui/material/Paper'
import TextField from '@mui/material/TextField'
import IconButton from '@mui/material/IconButton'
import Chip from '@mui/material/Chip'
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined'

import type { PriceChangeSetLine } from '../../shared/types/priceChange'
import { CONCURRENCY_CHANGED, CONCURRENCY_NOT_AVAILABLE, CONCURRENCY_UNCHANGED } from '../../shared/types/priceChange'

function yen(value: number | null, na: string): string {
  if (value == null) return na
  // Negative values read more naturally as "-¥103" than "¥-103"
  // (Number.toLocaleString() puts the sign before the digits, not before a
  // manually-prepended currency symbol).
  return value < 0 ? `-¥${Math.abs(value).toLocaleString()}` : `¥${value.toLocaleString()}`
}

function percent(value: number | null, na: string): string {
  return value == null ? na : `${(value * 100).toFixed(2)}%`
}

function concurrencyColor(status: string): 'default' | 'success' | 'warning' | 'error' {
  switch (status) {
    case CONCURRENCY_UNCHANGED:
      return 'success'
    case CONCURRENCY_CHANGED:
      return 'warning'
    case CONCURRENCY_NOT_AVAILABLE:
      return 'error'
    default:
      return 'default'
  }
}

/**
 * Shared line table for Price Change Create/Edit AND Detail
 * (target-price-change-workflow.md 10章's display columns: 旧価格|新価格|
 * 差額|変更率|原価|利益|利益率, plus Concurrency Status per line, 13章).
 * `editable` toggles the Proposed Price input and the Remove button; the
 * calculation columns themselves are always the same read-only data either
 * way - there is no separate "warning" styling anywhere here (Phase 8-B
 * forbids Threshold/Warning Rules, Section 5).
 */
export function PriceChangeLineTable({
  lines,
  editable,
  onProposedPriceChange,
  onRemove,
}: {
  lines: PriceChangeSetLine[]
  editable: boolean
  onProposedPriceChange?: (detailId: number, value: number | null) => void
  onRemove?: (detailId: number) => void
}) {
  const { t } = useTranslation(['priceChanges'])
  const na = t('priceChanges:notAvailable')
  const [drafts, setDrafts] = useState<Record<number, string>>({})

  function commit(detailId: number, raw: string) {
    const trimmed = raw.trim()
    if (trimmed === '') {
      onProposedPriceChange?.(detailId, null)
      return
    }
    const parsed = Number(trimmed)
    if (Number.isFinite(parsed)) {
      onProposedPriceChange?.(detailId, parsed)
    }
  }

  if (lines.length === 0) {
    return null
  }

  return (
    <TableContainer component={Paper} variant="outlined" data-testid="price-change-line-table-container">
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>{t('priceChanges:lineTable.sku')}</TableCell>
            <TableCell>{t('priceChanges:lineTable.itemName')}</TableCell>
            <TableCell>{t('priceChanges:lineTable.brand')}</TableCell>
            <TableCell>{t('priceChanges:lineTable.itemGroup')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.currentPrice')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.cost')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.margin')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.marginRate')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.proposedPrice')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.proposedMargin')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.proposedMarginRate')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.difference')}</TableCell>
            <TableCell align="right">{t('priceChanges:lineTable.percentageChange')}</TableCell>
            <TableCell>{t('priceChanges:lineTable.concurrency')}</TableCell>
            {editable && <TableCell align="center">{t('priceChanges:lineTable.remove')}</TableCell>}
          </TableRow>
        </TableHead>
        <TableBody>
          {lines.map((line) => (
            <TableRow key={line.detailId} data-testid={`price-change-line-${line.itemCd}`}>
              <TableCell>{line.itemCd}</TableCell>
              <TableCell>{line.itemName ?? na}</TableCell>
              <TableCell>{line.brandCode ?? na}</TableCell>
              <TableCell>{line.itemGrpCd ?? na}</TableCell>
              <TableCell align="right">{yen(line.currentPrcSellWTax, na)}</TableCell>
              <TableCell align="right">{yen(line.costWTax, na)}</TableCell>
              <TableCell align="right">{yen(line.marginAmount, na)}</TableCell>
              <TableCell align="right">{percent(line.marginRate, na)}</TableCell>
              <TableCell align="right">
                {editable ? (
                  <TextField
                    size="small"
                    type="number"
                    sx={{ width: 110 }}
                    value={drafts[line.detailId] ?? (line.proposedPrcSellWTax != null ? String(line.proposedPrcSellWTax) : '')}
                    onChange={(e) => setDrafts((prev) => ({ ...prev, [line.detailId]: e.target.value }))}
                    onBlur={(e) => commit(line.detailId, e.target.value)}
                    slotProps={{ htmlInput: { 'data-testid': `proposed-price-input-${line.itemCd}` } }}
                  />
                ) : (
                  yen(line.proposedPrcSellWTax, na)
                )}
              </TableCell>
              <TableCell align="right">{yen(line.proposedMarginAmount, na)}</TableCell>
              <TableCell align="right">{percent(line.proposedMarginRate, na)}</TableCell>
              <TableCell align="right">{yen(line.priceDifference, na)}</TableCell>
              <TableCell align="right">{line.percentageChange == null ? na : `${line.percentageChange}%`}</TableCell>
              <TableCell>
                <Chip
                  size="small"
                  color={concurrencyColor(line.concurrencyStatus)}
                  label={t(`priceChanges:concurrencyStatus.${line.concurrencyStatus}`)}
                  data-testid={`concurrency-chip-${line.itemCd}`}
                />
              </TableCell>
              {editable && (
                <TableCell align="center">
                  <IconButton size="small" onClick={() => onRemove?.(line.detailId)} data-testid={`remove-detail-${line.itemCd}`}>
                    <DeleteOutlineIcon fontSize="small" />
                  </IconButton>
                </TableCell>
              )}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </TableContainer>
  )
}
