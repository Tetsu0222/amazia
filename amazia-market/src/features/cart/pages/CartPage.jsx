import { useState } from 'react';
import {
  Container, Typography, Box, Paper, Table, TableBody, TableCell, TableHead,
  TableRow, IconButton, MenuItem, Select, Button, CircularProgress, Alert, Link,
} from '@mui/material';
import DeleteIcon from '@mui/icons-material/Delete';
import { useNavigate, Link as RouterLink } from 'react-router-dom';
import { useCart } from '../context/useCart';

export default function CartPage() {
  const navigate = useNavigate();
  const { items, totalCount, totalPrice, loading, updateQuantity, removeFromCart } = useCart();
  const [error, setError] = useState(null);

  const handleQuantityChange = async (itemId, quantity) => {
    setError(null);
    try {
      await updateQuantity(itemId, quantity);
    } catch (e) {
      const status = e?.response?.status;
      setError(status === 409 ? '在庫数を超える数量は指定できません' : '数量の更新に失敗しました');
    }
  };

  const handleRemove = async (itemId) => {
    setError(null);
    try {
      await removeFromCart(itemId);
    } catch {
      setError('削除に失敗しました');
    }
  };

  const handleProceed = () => navigate('/checkout?from=cart');

  if (loading && items.length === 0) {
    return (
      <Container maxWidth={false} sx={{ mt: 4 }}>
        <CircularProgress />
      </Container>
    );
  }

  if (items.length === 0) {
    return (
      <Container maxWidth={false} sx={{ mt: 4 }}>
        <Typography variant="h5" gutterBottom>カート</Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          カートは空です
        </Typography>
        <Link component={RouterLink} to="/">商品一覧に戻る</Link>
      </Container>
    );
  }

  return (
    <Container maxWidth={false} sx={{ mt: 4 }}>
      <Typography variant="h5" gutterBottom>カート（{totalCount}点）</Typography>
      {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
      <Box sx={{ display: 'flex', flexDirection: { xs: 'column', md: 'row' }, gap: 2 }}>
        <Paper variant="outlined" sx={{ flexGrow: 1 }}>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>商品</TableCell>
                <TableCell align="right">単価</TableCell>
                <TableCell align="center">数量</TableCell>
                <TableCell align="right">小計</TableCell>
                <TableCell align="center">削除</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((item) => (
                <TableRow key={item.itemId}>
                  <TableCell>
                    <Link
                      component={RouterLink}
                      to={`/products/${item.productId}`}
                      sx={{ color: 'link.main' }}
                    >
                      {item.productName}
                    </Link>
                    <Typography variant="caption" display="block" color="text.secondary">
                      {item.color} / {item.size}
                      {item.preorder && '（予約）'}
                    </Typography>
                  </TableCell>
                  <TableCell align="right">¥{item.unitPrice.toLocaleString()}</TableCell>
                  <TableCell align="center">
                    <Select
                      size="small"
                      value={item.quantity}
                      onChange={(e) => handleQuantityChange(item.itemId, Number(e.target.value))}
                      inputProps={{ 'aria-label': `数量（${item.productName}）` }}
                    >
                      {Array.from({ length: Math.max(item.availableStock, item.quantity) }, (_, i) => i + 1)
                        .slice(0, 10)
                        .map((n) => (
                          <MenuItem key={n} value={n}>{n}</MenuItem>
                        ))}
                    </Select>
                  </TableCell>
                  <TableCell align="right">¥{item.subtotal.toLocaleString()}</TableCell>
                  <TableCell align="center">
                    <IconButton
                      onClick={() => handleRemove(item.itemId)}
                      aria-label={`削除（${item.productName}）`}
                    >
                      <DeleteIcon />
                    </IconButton>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Paper>
        <Paper variant="outlined" sx={{ p: 2, minWidth: 240, alignSelf: 'flex-start' }}>
          <Typography variant="subtitle2" color="text.secondary">合計（{totalCount}点）</Typography>
          <Typography variant="h5" sx={{ color: 'link.main', mb: 2 }}>
            ¥{totalPrice.toLocaleString()}
          </Typography>
          <Button
            fullWidth
            onClick={handleProceed}
            sx={{
              bgcolor: 'accent.main',
              color: 'accent.contrastText',
              '&:hover': { bgcolor: 'accent.main', filter: 'brightness(0.95)' },
            }}
          >
            レジに進む（{totalCount} 点）
          </Button>
        </Paper>
      </Box>
    </Container>
  );
}
