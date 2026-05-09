import {
  Box,
  Collapse,
  IconButton,
  Typography,
  Chip,
} from '@mui/material';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import ExpandLessIcon from '@mui/icons-material/ExpandLess';
import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useHeaderNotices } from '../hooks/useHeaderNotices';

/**
 * フェーズ19: ヘッダー直下のお知らせ自動切替アコーディオン。
 *
 * - 未読 0 件のときは非表示（早期 return）。
 * - 5 秒ごとに `currentList[activeIndex]` を切り替えて 1 件ずつ表示。
 * - クリックで /notices へ遷移。
 * - aria-live="polite" でアクセシビリティ確保。
 */
export default function HeaderNotice() {
  const { currentList, activeIndex, authError } = useHeaderNotices();
  const [expanded, setExpanded] = useState(false);

  if (authError) return null;
  if (!currentList || currentList.length === 0) return null;

  const active = currentList[activeIndex] ?? currentList[0];

  return (
    <Box
      sx={{
        bgcolor: 'background.paper',
        borderBottom: '1px solid',
        borderColor: 'divider',
        px: 2,
      }}
      aria-live="polite"
    >
      <Box
        sx={{
          display: 'flex',
          alignItems: 'center',
          gap: 1,
          py: 1,
          minHeight: 40,
        }}
      >
        <Chip
          label={active?.category?.label ?? active?.category?.code ?? ''}
          size="small"
          color={active?.category?.code === 'important' ? 'error' : 'primary'}
        />
        <Typography
          component={RouterLink}
          to="/notices"
          variant="body2"
          sx={{
            flexGrow: 1,
            color: 'text.primary',
            textDecoration: 'none',
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            whiteSpace: 'nowrap',
            '&:hover': { textDecoration: 'underline' },
          }}
        >
          {active?.subject}
        </Typography>
        {currentList.length > 1 && (
          <Typography variant="caption" color="text.secondary" sx={{ minWidth: 40, textAlign: 'right' }}>
            {activeIndex + 1} / {currentList.length}
          </Typography>
        )}
        {currentList.length > 1 && (
          <IconButton
            size="small"
            onClick={() => setExpanded((v) => !v)}
            aria-label={expanded ? 'お知らせを閉じる' : 'お知らせをすべて表示'}
          >
            {expanded ? <ExpandLessIcon /> : <ExpandMoreIcon />}
          </IconButton>
        )}
      </Box>

      <Collapse in={expanded && currentList.length > 1}>
        <Box sx={{ pb: 1 }}>
          {currentList.map((n) => (
            <Box
              key={n.id}
              sx={{
                display: 'flex',
                alignItems: 'center',
                gap: 1,
                py: 0.5,
                pl: 1,
              }}
            >
              <Chip
                label={n.category?.label ?? n.category?.code ?? ''}
                size="small"
                color={n.category?.code === 'important' ? 'error' : 'primary'}
              />
              <Typography
                component={RouterLink}
                to="/notices"
                variant="body2"
                sx={{
                  color: 'text.primary',
                  textDecoration: 'none',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                  '&:hover': { textDecoration: 'underline' },
                }}
              >
                {n.subject}
              </Typography>
            </Box>
          ))}
        </Box>
      </Collapse>
    </Box>
  );
}
