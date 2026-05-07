import { useState } from 'react';
import {
  Paper, Stack, Typography, Button, Chip, Box, Select, MenuItem, FormControl, InputLabel,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { PREORDER_STATUS } from '../preorderStatus';
import { useCart } from '../../cart/context/useCart';
import { useAuth } from '../../customer/context/useAuth';

const QUANTITY_MAX = 10;

export default function PurchaseBox({
  productId,
  selectedSku,
  preorderStatus,
  statusMeta,
  estimatedDelivery,
  onSnack,
}) {
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const { isAuthenticated } = useAuth();
  const [quantity, setQuantity] = useState(1);

  if (!selectedSku || !statusMeta) return null;

  const isPreorderFlow =
    preorderStatus === PREORDER_STATUS.PRE_ORDER ||
    preorderStatus === PREORDER_STATUS.BACK_ORDER ||
    preorderStatus === PREORDER_STATUS.PRE_ORDER_NOT_STARTED;
  const onSaleNoStock =
    preorderStatus === PREORDER_STATUS.ON_SALE && (selectedSku.stock ?? 0) <= 0;
  const buyDisabled = statusMeta.buttonAction === 'disabled' || onSaleNoStock;
  const showBuy = statusMeta.buttonAction !== 'hidden';
  const showCart =
    preorderStatus === PREORDER_STATUS.ON_SALE && (selectedSku.stock ?? 0) > 0;

  const maxQuantity =
    preorderStatus === PREORDER_STATUS.ON_SALE
      ? Math.min(QUANTITY_MAX, selectedSku.stock ?? QUANTITY_MAX)
      : QUANTITY_MAX;
  const quantityOptions = Array.from({ length: Math.max(1, maxQuantity) }, (_, i) => i + 1);

  const buyLabel = isPreorderFlow ? '今すぐ予約' : '今すぐ買う';

  const handleBuy = () => {
    const target =
      `/checkout?product_id=${productId}&sku_id=${selectedSku.skuId}&quantity=${quantity}` +
      (isPreorderFlow ? '&preorder=1' : '');
    if (!isAuthenticated) {
      navigate('/login', { state: { from: target } });
    } else {
      navigate(target);
    }
  };

  const handleAddToCart = async () => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: `/products/${productId}` } });
      return;
    }
    try {
      await addToCart(selectedSku.skuId, quantity, false);
      onSnack?.({ severity: 'success', message: 'カートに追加しました' });
    } catch (err) {
      const status = err?.response?.status;
      onSnack?.({
        severity: 'error',
        message: status === 409 ? '在庫が不足しています' : 'カート追加に失敗しました',
      });
    }
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        p: 2,
        position: { md: 'sticky' },
        top: { md: 16 },
        minWidth: 240,
      }}
    >
      <Stack spacing={1.5}>
        {selectedSku.price != null && (
          <Typography variant="h5" sx={{ color: 'link.main' }}>
            ¥{selectedSku.price.toLocaleString()}
          </Typography>
        )}
        {preorderStatus === PREORDER_STATUS.ON_SALE && (
          selectedSku.stock > 0 ? (
            <Chip label={`在庫あり（残り${selectedSku.stock}点）`} color="success" size="small" />
          ) : (
            <Chip label="在庫切れ" color="error" size="small" />
          )
        )}
        {preorderStatus === PREORDER_STATUS.BACK_ORDER && (
          <Chip label="在庫切れ（再入荷予約受付中）" color="warning" size="small" />
        )}
        {estimatedDelivery && (
          <Typography variant="body2" color="text.secondary">
            お届け予定：{estimatedDelivery}
          </Typography>
        )}

        {showBuy && !buyDisabled && (
          <FormControl size="small">
            <InputLabel id="purchase-quantity-label">数量</InputLabel>
            <Select
              labelId="purchase-quantity-label"
              label="数量"
              value={quantity}
              onChange={(e) => setQuantity(Number(e.target.value))}
            >
              {quantityOptions.map((n) => (
                <MenuItem key={n} value={n}>{n}</MenuItem>
              ))}
            </Select>
          </FormControl>
        )}

        <Stack spacing={1}>
          {showCart && (
            <Button
              onClick={handleAddToCart}
              sx={{
                bgcolor: 'accent.main',
                color: 'accent.contrastText',
                '&:hover': { bgcolor: 'accent.main', filter: 'brightness(0.95)' },
              }}
            >
              カートに入れる
            </Button>
          )}
          {showBuy && (
            <Button
              variant="contained"
              color="primary"
              onClick={handleBuy}
              disabled={buyDisabled}
            >
              {buyLabel}
            </Button>
          )}
        </Stack>
      </Stack>
    </Paper>
  );
}
