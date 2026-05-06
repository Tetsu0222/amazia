import { useEffect, useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Container, Typography, Paper, Stack, Button,
  CircularProgress, Alert, Divider, Box,
  ToggleButton, ToggleButtonGroup, Chip,
} from '@mui/material';
import { getMarketProduct } from '../api/products';
import { NOIMAGE } from '../constants';
import { useAuth } from '../../customer/context/useAuth';

export default function ProductDetail() {
  const { id }     = useParams();
  const navigate   = useNavigate();
  const { isAuthenticated } = useAuth();
  const [data, setData]       = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState(null);
  const [selectedColor, setSelectedColor] = useState(null);
  const [selectedSize, setSelectedSize]   = useState(null);
  const [mainImageIdx, setMainImageIdx]   = useState(0);

  useEffect(() => {
    getMarketProduct(id)
      .then(d => {
        setData(d);
        const firstColor = d.skus[0]?.color ?? null;
        setSelectedColor(firstColor);
      })
      .catch(() => setError('商品データの取得に失敗しました'))
      .finally(() => setLoading(false));
  }, [id]);

  // 色の候補（重複なし）
  const colors = useMemo(() => {
    if (!data) return [];
    return [...new Set(data.skus.map(s => s.color))];
  }, [data]);

  // 選択中の色に対して選べるサイズ
  const sizes = useMemo(() => {
    if (!data || !selectedColor) return [];
    return data.skus
      .filter(s => s.color === selectedColor)
      .map(s => s.size);
  }, [data, selectedColor]);

  // 色が変わったらサイズをリセット
  const handleColorChange = (_, newColor) => {
    if (newColor === null) return;
    setSelectedColor(newColor);
    setSelectedSize(null);
    setMainImageIdx(0);
  };

  const handleSizeChange = (_, newSize) => {
    if (newSize === null) return;
    setSelectedSize(newSize);
    setMainImageIdx(0);
  };

  // 現在選択中のSKU
  const selectedSku = useMemo(() => {
    if (!data || !selectedColor || !selectedSize) return null;
    return data.skus.find(s => s.color === selectedColor && s.size === selectedSize) ?? null;
  }, [data, selectedColor, selectedSize]);

  const images = selectedSku?.images ?? [];

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error)   return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  const { product } = data;

  return (
    <Container sx={{ mt: 4, maxWidth: 800 }}>
      <Button onClick={() => navigate('/')} sx={{ mb: 2 }}>← 一覧へ戻る</Button>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h5" gutterBottom>{product.name}</Typography>
        <Divider sx={{ mb: 2 }} />

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={3}>
          {/* 画像エリア */}
          <Box sx={{ flexShrink: 0, width: { xs: '100%', sm: 300 } }}>
            {selectedSku ? (
              <>
                <Box
                  component="img"
                  src={images[mainImageIdx] ?? NOIMAGE}
                  alt={product.name}
                  sx={{
                    width: '100%',
                    height: 280,
                    objectFit: 'contain',
                    bgcolor: '#f5f5f5',
                    borderRadius: 1,
                  }}
                />
                {images.length > 1 && (
                  <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
                    {images.map((img, i) => (
                      <Box
                        key={i}
                        component="img"
                        src={img}
                        alt=""
                        onClick={() => setMainImageIdx(i)}
                        sx={{
                          width: 56,
                          height: 56,
                          objectFit: 'contain',
                          border: mainImageIdx === i ? '2px solid' : '1px solid #ddd',
                          borderColor: mainImageIdx === i ? 'primary.main' : '#ddd',
                          borderRadius: 1,
                          cursor: 'pointer',
                          bgcolor: '#f5f5f5',
                        }}
                      />
                    ))}
                  </Stack>
                )}
              </>
            ) : (
              <Box sx={{
                width: '100%',
                height: 280,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                bgcolor: '#f5f5f5',
                borderRadius: 1,
              }}>
                <Typography color="text.secondary" variant="body2">
                  色とサイズを選択してください
                </Typography>
              </Box>
            )}
          </Box>

          {/* 選択・情報エリア */}
          <Stack spacing={2} sx={{ flexGrow: 1 }}>
            <Typography color="text.secondary">{product.description ?? ''}</Typography>

            {/* 色選択 */}
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

            {/* サイズ選択 */}
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

            {/* 価格・在庫 */}
            {selectedSku && (
              <Stack spacing={1}>
                <Typography variant="h5" color="primary">
                  ¥{selectedSku.price != null ? selectedSku.price.toLocaleString() : '—'}
                </Typography>
                <Box>
                  {selectedSku.stock > 0 ? (
                    <Chip label={`在庫 ${selectedSku.stock} 個`} color="success" size="small" />
                  ) : (
                    <Chip label="在庫なし" color="error" size="small" />
                  )}
                </Box>
                <Button
                  variant="contained"
                  color="primary"
                  disabled={selectedSku.stock <= 0}
                  onClick={() => {
                    const target = `/checkout?product_id=${id}&sku_id=${selectedSku.skuId}&quantity=1`;
                    if (!isAuthenticated) {
                      navigate('/login', { state: { from: target } });
                    } else {
                      navigate(target);
                    }
                  }}
                >
                  購入する
                </Button>
              </Stack>
            )}
          </Stack>
        </Stack>
      </Paper>
    </Container>
  );
}
