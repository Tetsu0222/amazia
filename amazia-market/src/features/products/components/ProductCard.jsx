import { useState } from 'react';
import {
  Paper,
  Box,
  Typography,
  Chip,
  Button,
  CardActionArea,
  CardMedia,
  Snackbar,
  Alert,
} from '@mui/material';
import { useNavigate } from 'react-router-dom';
import { NOIMAGE } from '../constants';
import { PREORDER_STATUS, getPreorderStatusMeta } from '../preorderStatus';
import { useCart } from '../../cart/context/useCart';
import { useAuth } from '../../customer/context/useAuth';

export default function ProductCard({ product }) {
  const navigate = useNavigate();
  const { addToCart } = useCart();
  const { isAuthenticated } = useAuth();
  const [snack, setSnack] = useState(null);
  const meta = getPreorderStatusMeta(product.preorderStatus);
  const skuCount = product.skuCount ?? 1;
  const cartActionable =
    product.preorderStatus === PREORDER_STATUS.ON_SALE && skuCount === 1 && product.totalStock > 0;

  const handleAddToCart = async (e) => {
    e.stopPropagation();
    if (!isAuthenticated) {
      navigate('/login');
      return;
    }
    if (!product.defaultSkuId) {
      navigate(`/products/${product.productId}`);
      return;
    }
    try {
      await addToCart(product.defaultSkuId, 1, false);
      setSnack({ severity: 'success', message: 'カートに追加しました' });
    } catch (err) {
      const status = err?.response?.status;
      setSnack({
        severity: 'error',
        message: status === 409 ? '在庫が不足しています' : 'カート追加に失敗しました',
      });
    }
  };

  const handleSelectAndBuy = (e) => {
    e.stopPropagation();
    navigate(`/products/${product.productId}`);
  };

  return (
    <Paper
      variant="outlined"
      sx={{
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        borderColor: 'border.card',
        transition: 'box-shadow 0.15s ease',
        '&:hover': { boxShadow: 2 },
      }}
    >
      <CardActionArea
        onClick={() => navigate(`/products/${product.productId}`)}
        sx={{ display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}
      >
        <CardMedia
          component="img"
          height="180"
          image={product.mainImage ?? NOIMAGE}
          alt={product.productName}
          sx={{ objectFit: 'contain', bgcolor: 'background.paper' }}
          loading="lazy"
        />
        <Box sx={{ p: 1.5, flexGrow: 1 }}>
          <Typography
            variant="subtitle1"
            fontWeight="bold"
            sx={{
              display: '-webkit-box',
              WebkitLineClamp: 2,
              WebkitBoxOrient: 'vertical',
              overflow: 'hidden',
              minHeight: '2.8em',
            }}
          >
            {product.productName}
          </Typography>
          {meta && (
            <Chip label={meta.label} color={meta.chipColor} size="small" sx={{ mt: 0.5 }} />
          )}
          {product.minPrice != null && (
            <Typography variant="h6" sx={{ mt: 1, color: 'link.main' }}>
              ¥{product.minPrice.toLocaleString()}
              {product.preorderStatus === PREORDER_STATUS.PRE_ORDER && ' 〜'}
            </Typography>
          )}
          {product.preorderStatus === PREORDER_STATUS.ON_SALE && (
            <Typography variant="body2" color="text.secondary">
              残り{product.totalStock}点
            </Typography>
          )}
          {product.preorderStatus === PREORDER_STATUS.PRE_ORDER && product.releaseDate && (
            <Typography variant="body2" color="text.secondary">
              発売日：{product.releaseDate}
            </Typography>
          )}
          {product.preorderStatus === PREORDER_STATUS.PRE_ORDER_NOT_STARTED &&
            product.preorderStartDate && (
              <Typography variant="body2" color="text.secondary">
                予約開始：{product.preorderStartDate}
              </Typography>
            )}
          {product.preorderStatus === PREORDER_STATUS.BACK_ORDER && (
            <Typography variant="body2" color="text.secondary">
              在庫切れ（再入荷予約受付中）
            </Typography>
          )}
        </Box>
      </CardActionArea>
      <Box sx={{ p: 1.5, pt: 0 }}>
        {cartActionable ? (
          <Button
            fullWidth
            size="small"
            onClick={handleAddToCart}
            sx={{
              bgcolor: 'accent.main',
              color: 'accent.contrastText',
              '&:hover': { bgcolor: 'accent.main', filter: 'brightness(0.95)' },
            }}
          >
            カートに入れる
          </Button>
        ) : skuCount > 1 ? (
          <Button fullWidth size="small" variant="outlined" onClick={handleSelectAndBuy}>
            選択して購入
          </Button>
        ) : (
          <Button fullWidth size="small" variant="outlined" disabled>
            カートに入れる
          </Button>
        )}
      </Box>
      <Snackbar
        open={snack != null}
        autoHideDuration={3000}
        onClose={() => setSnack(null)}
        anchorOrigin={{ vertical: 'top', horizontal: 'right' }}
      >
        {snack && (
          <Alert severity={snack.severity} onClose={() => setSnack(null)}>
            {snack.message}
          </Alert>
        )}
      </Snackbar>
    </Paper>
  );
}
