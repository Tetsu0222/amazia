import { FormControl, InputLabel, Select, MenuItem } from '@mui/material';

export const SORT_OPTIONS = [
  { value: 'recommended', label: 'おすすめ順' },
  { value: 'priceAsc', label: '価格の安い順' },
  { value: 'priceDesc', label: '価格の高い順' },
  { value: 'newest', label: '新着順' },
];

export default function SortSelect({ value, onChange }) {
  return (
    <FormControl size="small" sx={{ minWidth: 160 }}>
      <InputLabel id="sort-select-label">並び替え</InputLabel>
      <Select
        labelId="sort-select-label"
        label="並び替え"
        value={value}
        onChange={(e) => onChange(e.target.value)}
      >
        {SORT_OPTIONS.map((opt) => (
          <MenuItem key={opt.value} value={opt.value}>
            {opt.label}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  );
}

export function sortProducts(products, sortKey) {
  const arr = [...products];
  switch (sortKey) {
    case 'priceAsc':
      return arr.sort((a, b) => (a.minPrice ?? Infinity) - (b.minPrice ?? Infinity));
    case 'priceDesc':
      return arr.sort((a, b) => (b.minPrice ?? -Infinity) - (a.minPrice ?? -Infinity));
    case 'newest':
      return arr.sort((a, b) => {
        const ad = a.createdAt ? new Date(a.createdAt).getTime() : 0;
        const bd = b.createdAt ? new Date(b.createdAt).getTime() : 0;
        return bd - ad;
      });
    case 'recommended':
    default:
      return arr;
  }
}
