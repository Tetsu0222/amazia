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

  const skuIdParam = searchParams.get('sku_id');
  const quantityParam = searchParams.get('quantity');
  const skuId = skuIdParam ? Number(skuIdParam) : null;
  const initialQuantity = quantityParam ? Math.max(1, Number(quantityParam)) : 1;

  const [productId, setProductId] = useState(null);
  const [productData, setProductData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const [quantity, setQuantity] = useState(initialQuantity);
  const [paymentMethodId, setPaymentMethodId] = useState(1);
  const [shippingMethodId, setShippingMethodId] = useState(1);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);

  // SKU から商品を引くため、URL に sku_id だけだと商品情報が取れない。
  // ProductDetail から遷移してきた場合は sku_id しか持っていないため、
  // 全商品 SKU から探す代わりに URL に product_id を持たせる方式が望ましいが、
  // 本フェーズでは ProductList などからも遷移可能とするため、
  // sku_id から逆引きする最小ロジックを Core 側 API で追加することを将来課題とし、
  // 当面は ProductDetail 経由のみを対象とし state 経由 or リダイレクトで補完する。
  // ここでは sku_id から /api/products を全件取得して該当 SKU を含む product を探す簡便実装。

  useEffect(() => {
    let canceled = false;
    if (skuId == null) {
      setError('購入対象の SKU が指定されていません。');
      setLoading(false);
      return;
    }
    (async () => {
      try {
        // 商品 ID は location.state ではなく ProductDetail からの sku_id クエリのみで来る場合に備え、
        // 全件取得してからローカルで特定する。商品数が大きくなったら Core 側に sku_id 起点 API を追加する。
        const list = await import('../../products/api/products').then(m => m.getMarketProducts());
        const target = list.find(p => p.skus?.some(s => s.id === skuId));
        if (!target) {
          if (!canceled) {
            setError('対象の商品が見つかりません。');
            setLoading(false);
          }
          return;
        }
        const detail = await getMarketProduct(target.product.id);
        if (!canceled) {
          setProductId(target.product.id);
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
  }, [skuId]);

  const selectedSku = useMemo(() => {
    if (!productData) return null;
    return productData.skus.find(s => s.id === skuId) ?? null;
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
        skuId: selectedSku.id,
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
            inputProps={{ min: 1, max: selectedSku.stock ?? 99 }}
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

          <Stack direction="row" justifyContent="space-between" alignItems="baseline">
            <Typography variant="subtitle1">合計</Typography>
            <Typography variant="h5" color="primary">¥{totalAmount.toLocaleString()}</Typography>
          </Stack>

          {submitError && <Alert severity="error">{submitError}</Alert>}

          <Stack direction="row" spacing={1} justifyContent="flex-end">
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
