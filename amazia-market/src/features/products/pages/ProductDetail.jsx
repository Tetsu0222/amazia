import { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Container, Typography, Paper, Stack, Button,
  CircularProgress, Alert, Divider, Box, Chip, Snackbar,
  ToggleButton, ToggleButtonGroup,
} from '@mui/material';
import { getMarketProduct } from '../api/products';
import { PREORDER_STATUS, getPreorderStatusMeta } from '../preorderStatus';
import ImageGallery from '../components/ImageGallery';
import PurchaseBox from '../components/PurchaseBox';
import { getEstimatedDeliveryDate } from '../deliveryEstimate';

export default function ProductDetail() {
  const { id }     = useParams();
  const navigate   = useNavigate();
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);
  const [selectedColor, setSelectedColor] = useState(null);
  const [selectedSize, setSelectedSize]   = useState(null);
  const [snack, setSnack]                 = useState(null);

  useEffect(() => {
    getMarketProduct(id)
      .then(d => {
        setData(d);
        const firstSku = d.skus[0];
        setSelectedColor(firstSku?.color ?? null);
        setSelectedSize(firstSku?.size ?? null);
      })
      .catch(() => setError('商品データの取得に失敗しました'))
      .finally(() => setLoading(false));
  }, [id]);

  const colors = useMemo(() => {
    if (!data) return [];
    return [...new Set(data.skus.map(s => s.color))];
  }, [data]);

  const sizes = useMemo(() => {
    if (!data || !selectedColor) return [];
    return data.skus.filter(s => s.color === selectedColor).map(s => s.size);
  }, [data, selectedColor]);

  const handleColorChange = (_, newColor) => {
    if (newColor === null) return;
    setSelectedColor(newColor);
    const firstSizeOfColor = data?.skus.find(s => s.color === newColor)?.size ?? null;
    setSelectedSize(firstSizeOfColor);
  };

  const handleSizeChange = (_, newSize) => {
    if (newSize === null) return;
    setSelectedSize(newSize);
  };

  const selectedSku = useMemo(() => {
    if (!data || !selectedColor || !selectedSize) return null;
    return data.skus.find(s => s.color === selectedColor && s.size === selectedSize) ?? null;
  }, [data, selectedColor, selectedSize]);

  const images = selectedSku?.images ?? [];

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error)   return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  const { product } = data;
  const preorderStatus = data.preorderStatus;
  const statusMeta = getPreorderStatusMeta(preorderStatus);
  const estimatedDelivery = getEstimatedDeliveryDate(data);

  return (
    <Container sx={{ mt: 4 }} maxWidth={false}>
      <Button onClick={() => navigate('/')} sx={{ mb: 2 }}>← 一覧へ戻る</Button>
      <Stack
        direction={{ xs: 'column', md: 'row' }}
        spacing={3}
        sx={{ alignItems: 'flex-start' }}
      >
        <Box sx={{ width: { xs: '100%', md: 360 }, flexShrink: 0 }}>
          <ImageGallery
            images={images}
            alt={product.name}
            placeholder={selectedSku ? null : '色とサイズを選択してください'}
          />
        </Box>

        <Paper sx={{ p: 3, flexGrow: 1, minWidth: 0 }}>
          <Typography variant="h5" gutterBottom>{product.name}</Typography>
          {statusMeta && (
            <Chip
              label={statusMeta.label}
              color={statusMeta.chipColor}
              size="small"
              sx={{ mb: 1 }}
            />
          )}
          {preorderStatus === PREORDER_STATUS.PRE_ORDER && product.releaseDate && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              発売日：{product.releaseDate}
            </Typography>
          )}
          {preorderStatus === PREORDER_STATUS.PRE_ORDER_NOT_STARTED && product.preorderStartDate && (
            <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
              予約開始：{product.preorderStartDate}
            </Typography>
          )}
          <Divider sx={{ mb: 2 }} />

          <Typography color="text.secondary" sx={{ mb: 2 }}>
            {product.description ?? ''}
          </Typography>

          <Stack spacing={2}>
            <Box>
              <Typography variant="body2" fontWeight="bold" sx={{ mb: 0.5 }}>色</Typography>
              <ToggleButtonGroup
                value={selectedColor}
                exclusive
                onChange={handleColorChange}
                size="small"
              >
                {colors.map(c => (
                  <ToggleButton key={c} value={c}>{c}</ToggleButton>
                ))}
              </ToggleButtonGroup>
            </Box>

            {selectedColor && (
              <Box>
                <Typography variant="body2" fontWeight="bold" sx={{ mb: 0.5 }}>サイズ</Typography>
                <ToggleButtonGroup
                  value={selectedSize}
                  exclusive
                  onChange={handleSizeChange}
                  size="small"
                >
                  {sizes.map(s => (
                    <ToggleButton key={s} value={s}>{s}</ToggleButton>
                  ))}
                </ToggleButtonGroup>
              </Box>
            )}

            {/* 価格・在庫ステータスは右カラムの PurchaseBox に集約。
                xs では Stack のレイアウトで下に縦積みされるため重複表示はしない。 */}
          </Stack>
        </Paper>

        <Box sx={{ width: { xs: '100%', md: 280 }, flexShrink: 0 }}>
          <PurchaseBox
            productId={id}
            selectedSku={selectedSku}
            preorderStatus={preorderStatus}
            statusMeta={statusMeta}
            estimatedDelivery={estimatedDelivery}
            onSnack={setSnack}
          />
        </Box>
      </Stack>
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
    </Container>
  );
}
