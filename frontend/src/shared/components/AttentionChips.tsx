import { useState } from 'react'
import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import Tooltip from '@mui/material/Tooltip'
import { useTranslation } from 'react-i18next'

import { useAcknowledgeAttention } from '../../features/attention/api'
import type { AttentionSummary } from '../types/attention'

/**
 * Requirements MD 31.2 / implementation instructions Step 4 28章 / Step 5
 * 2章: Attention badges used on History and Supplier Response screens.
 * Each ACTIVE Attention is clickable to Acknowledge it (deleteIcon = a
 * checkmark) when {@code acknowledgeable} is true - Order History Detail
 * and Supplier Response are the two screens that pass this
 * (implementation instructions Step 5 2章: "Order History Detailまたは
 * Supplier Response画面から「確認済みにする」操作を提供する").
 */
export function AttentionChips({ attentions, acknowledgeable = false }: { attentions: AttentionSummary[]; acknowledgeable?: boolean }) {
  const { t } = useTranslation('status')
  const acknowledgeMutation = useAcknowledgeAttention()
  const [acknowledgingId, setAcknowledgingId] = useState<number | null>(null)

  if (attentions.length === 0) return null

  function handleAcknowledge(id: number) {
    setAcknowledgingId(id)
    acknowledgeMutation.mutate(id, { onSettled: () => setAcknowledgingId(null) })
  }

  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
      {attentions.map((a) => {
        const label = t(`attentionType.${a.attentionType}`, { defaultValue: a.attentionType })
        const color = a.attentionType === 'PARTIAL_CONFIRMATION' ? 'info' : 'warning'
        if (!acknowledgeable) {
          return <Chip key={a.id} size="small" color={color} label={label} />
        }
        return (
          <Tooltip key={a.id} title={t('acknowledgeAttention', { defaultValue: '確認済みにする' })}>
            <Chip
              size="small"
              color={color}
              label={label}
              onDelete={() => handleAcknowledge(a.id)}
              disabled={acknowledgingId === a.id}
              deleteIcon={<span aria-hidden style={{ fontSize: '0.9em', paddingRight: 2 }}>✓</span>}
              data-testid={`attention-chip-${a.id}`}
            />
          </Tooltip>
        )
      })}
    </Stack>
  )
}
