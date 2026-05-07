import { useState } from 'react';
import {
  Box,
  TextField,
  IconButton,
  Select,
  MenuItem,
  FormControl,
  InputAdornment,
} from '@mui/material';
import SearchIcon from '@mui/icons-material/Search';
import { useNavigate, useSearchParams } from 'react-router-dom';

// 左側のカテゴリプリセレクトはダミー（送信時は q のみ反映）
const CATEGORY_PRESETS = [
  'すべて',
  'ファッション',
  '家電',
  '食品・飲料',
  '本',
  'ホビー',
  'セール',
];

export default function SearchBar() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [keyword, setKeyword] = useState(searchParams.get('q') ?? '');
  const [category, setCategory] = useState('すべて');

  const submit = (e) => {
    e.preventDefault();
    const trimmed = keyword.trim();
    if (trimmed) {
      navigate(`/?q=${encodeURIComponent(trimmed)}`);
    } else {
      navigate('/');
    }
  };

  return (
    <Box
      component="form"
      role="search"
      onSubmit={submit}
      sx={{
        display: 'flex',
        alignItems: 'stretch',
        width: '100%',
        bgcolor: 'background.paper',
        borderRadius: 1,
        overflow: 'hidden',
      }}
    >
      <FormControl size="small" sx={{ minWidth: 96, display: { xs: 'none', md: 'block' } }}>
        <Select
          value={category}
          onChange={(e) => setCategory(e.target.value)}
          inputProps={{ 'aria-label': 'カテゴリ' }}
          sx={{ height: '100%', borderRadius: 0, bgcolor: '#F3F3F3' }}
        >
          {CATEGORY_PRESETS.map((c) => (
            <MenuItem key={c} value={c}>{c}</MenuItem>
          ))}
        </Select>
      </FormControl>
      <TextField
        fullWidth
        size="small"
        placeholder="Amazia を検索"
        value={keyword}
        onChange={(e) => setKeyword(e.target.value)}
        slotProps={{
          htmlInput: { 'aria-label': '商品検索' },
          input: {
            endAdornment: (
              <InputAdornment position="end">
                <IconButton
                  type="submit"
                  aria-label="検索"
                  sx={{ bgcolor: 'accent.main', color: 'accent.contrastText', borderRadius: 0 }}
                >
                  <SearchIcon />
                </IconButton>
              </InputAdornment>
            ),
          },
        }}
        sx={{ flexGrow: 1 }}
      />
    </Box>
  );
}
