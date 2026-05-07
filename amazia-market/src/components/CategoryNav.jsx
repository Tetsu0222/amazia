// フェーズ16.5 時点ではダミー。categories テーブル追加時に実機能化。
import { Box, Stack, Link } from '@mui/material';

const CATEGORIES = [
  'すべて',
  'ファッション',
  '家電',
  '食品・飲料',
  '本',
  'ホビー',
  'セール',
];

export default function CategoryNav() {
  const handleClick = (e) => {
    e.preventDefault();
  };

  return (
    <Box
      sx={{
        bgcolor: 'header.sub',
        color: 'header.text',
        px: 2,
        height: 36,
        display: 'flex',
        alignItems: 'center',
        overflowX: 'auto',
      }}
    >
      <Stack
        direction="row"
        spacing={2}
        component="nav"
        aria-label="カテゴリナビゲーション"
        sx={{ alignItems: 'center', height: '100%' }}
      >
        {CATEGORIES.map((label) => (
          <Link
            key={label}
            href="#"
            onClick={handleClick}
            underline="none"
            sx={{
              color: 'header.text',
              fontSize: 14,
              px: 1,
              py: 0.5,
              border: '1px solid transparent',
              whiteSpace: 'nowrap',
              '&:hover': { borderColor: 'header.hoverBorder' },
            }}
          >
            {label}
          </Link>
        ))}
      </Stack>
    </Box>
  );
}
