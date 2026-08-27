import Chip from '@mui/material/Chip'
import Stack from '@mui/material/Stack'
import { useTranslation } from 'react-i18next'

/** Requirements MD 31.2 / implementation instructions 28章: Attention badges
 * used on History and Supplier Response screens. */
export function AttentionChips({ types }: { types: string[] }) {
  const { t } = useTranslation('status')
  if (types.length === 0) return null
  return (
    <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', rowGap: 0.5 }}>
      {types.map((type) => (
        <Chip
          key={type}
          size="small"
          color={type === 'PARTIAL_CONFIRMATION' ? 'info' : 'warning'}
          label={t(`attentionType.${type}`, { defaultValue: type })}
        />
      ))}
    </Stack>
  )
}
