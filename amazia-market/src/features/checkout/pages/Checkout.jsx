import { useEffect, useMemo, useState } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import {
  Container, Paper, Stack, Typography, Button, Alert,
  CircularProgress, Divider, Box, MenuItem, TextField,
} from '@mui/material';
import { useAuth } from '../../customer/context/useAuth';
import { getMarketProduct } from '../../products/api/products';
import { confirmOrder } from '../api/checkout';

// 設計書 r4 / 命名規約 docs/ai_context/operation_logs_naming.md
//   screen_name: market.checkout.confirm
//   api_name   : POST /api/customer/orders/confirm
//
// 配送先は market_customers の現住所をサーバ側で自動スナップショットするため、
// この画面では会員住所を読み取り表示するのみ。

const PAYMENT_METHODS = [
  { id: 1, label: 'クレジットカード' },
  { id: 2, label: 'd払い' },
  { id: 3, label: '代引き' },
];

const SHIPPING_METHODS = [
  { id: 1, label: '宅配' },
  { id: 2, label: 'コンビニ受取' },
  { id: 3, label: '置き配' },
];

export default function Checkout() {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { customer } = useAuth();

  const productIdParam = searchParams.get('product_id');
  const skuIdParam = searchParams.get('sku_id');
  const quantityParam = searchParams.get('quantity');
  const productId = productIdParam ? Number(productIdParam) : null;
  const skuId = skuIdParam ? Number(skuIdParam) : null;
  const initialQuantity = quantityParam ? Math.max(1, Number(quantityParam)) : 1;

  const [productData, setProductData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [quantity, setQuantity] = useState(initialQuantity);
  const [paymentMethodId, setPaymentMethodId] = useState(1);
  const [shippingMethodId, setShippingMethodId] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // product_id + sku_id をクエリで受け取り、product 詳細 API を 1 回だけ呼んで対象 SKU を抽出する。
  // 旧実装は SKU から逆引きするため全商品取得していたが、Summary に skus が含まれず破綻していた（036 関連）。

  useEffect(() => {
    let canceled = false;
    if (productId == null || skuId == null) {
      setError('購入対象の商品または SKU が指定されていません。');
      setLoading(false);
      return;
    }
    (async () => {
      try {
        const detail = await getMarketProduct(productId);
        if (!canceled) {
          setProductData(detail);
          setLoading(false);
        }
      } catch (e) {
        if (!canceled) {
          setError('商品情報の取得に失敗しました。');
          setLoading(false);
        }
      }
    })();
    return () => { canceled = true; };
  }, [productId, skuId]);

  const selectedSku = useMemo(() => {
    if (!productData) return null;
    return productData.skus.find(s => s.skuId === skuId) ?? null;
  }, [productData, skuId]);

  const totalAmount = useMemo(() => {
    if (!selectedSku || selectedSku.price == null) return 0;
    return selectedSku.price * quantity;
  }, [selectedSku, quantity]);

  const handleSubmit = async () => {
    setSubmitError(null);
    setSubmitting(true);
    try {
      const result = await confirmOrder({
        skuId: selectedSku.skuId,
        quantity,
        paymentMethodId,
        shippingMethodId,
        preorder: false,
      });
      navigate('/checkout/complete', {
        state: {
          salesId: result.salesId,
          paymentId: result.paymentId,
          amount: result.amount,
          quantity: result.quantity,
        },
      });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 401) {
        navigate('/login', { state: { from: window.location.pathname + window.location.search } });
      } else if (status === 409) {
        setSubmitError('在庫が不足しているか、決済 ID が競合しました。時間をおいて再度お試しください。');
      } else if (status === 400) {
        setSubmitError('入力内容に誤りがあります。');
      } else {
        setSubmitError('注文確定に失敗しました。');
      }
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error) return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;
  if (!selectedSku) return <Alert severity="error" sx={{ m: 4 }}>SKU が見つかりません。</Alert>;

  const stockShortage = selectedSku.stock != null && quantity > selectedSku.stock;

  return (
    <Container sx={{ mt: 4 }} maxWidth="sm">
      <Typography variant="h5" gutterBottom>ご注文内容の確認</Typography>
      <Paper sx={{ p: 3 }}>
        <Stack spacing={2}>
          <Box>
            <Typography variant="subtitle2" color="text.secondary">商品</Typography>
            <Typography>{productData.product.name}（{selectedSku.color} / {selectedSku.size}）</Typography>
          </Box>

          <Divider />

          <TextField
            label="数量"
            type="number"
            value={quantity}
            onChange={(e) => setQuantity(Math.max(1, Number(e.target.value) || 1))}
            slotProps={{ htmlInput: { min: 1, max: selectedSku.stock ?? 99 } }}
          />
          {stockShortage && (
            <Alert severity="warning">在庫数（{selectedSku.stock}）を超えています。</Alert>
          )}

          <TextField
            select
            label="決済方法"
            value={paymentMethodId}
            onChange={(e) => setPaymentMethodId(Number(e.target.value))}
          >
            {PAYMENT_METHODS.map(m => (
              <MenuItem key={m.id} value={m.id}>{m.label}</MenuItem>
            ))}
          </TextField>

          <TextField
            select
            label="配送方法"
            value={shippingMethodId}
            onChange={(e) => setShippingMethodId(Number(e.target.value))}
          >
            {SHIPPING_METHODS.map(m => (
              <MenuItem key={m.id} value={m.id}>{m.label}</MenuItem>
            ))}
          </TextField>

          <Divider />

          <Box>
            <Typography variant="subtitle2" color="text.secondary">お届け先</Typography>
            {customer ? (
              <Typography variant="body2">
                〒{customer.postalCode}<br />
                {customer.address}<br />
                {customer.nameLast} {customer.nameFirst} 様
              </Typography>
            ) : (
              <Alert severity="error">会員情報が取得できません。再ログインしてください。</Alert>
            )}
          </Box>

          <Divider />

          <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'baseline' }}>
            <Typography variant="subtitle1">合計</Typography>
            <Typography variant="h5" color="primary">¥{totalAmount.toLocaleString()}</Typography>
          </Stack>

          {submitError && <Alert severity="error">{submitError}</Alert>}

          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button onClick={() => navigate(`/products/${productId}`)} disabled={submitting}>
              戻る
            </Button>
            <Button
              variant="contained"
              onClick={handleSubmit}
              disabled={submitting || stockShortage || !customer}
            >
              {submitting ? '送信中…' : '注文を確定する'}
            </Button>
          </Stack>
        </Stack>
      </Paper>
    </Container>
  );
}
