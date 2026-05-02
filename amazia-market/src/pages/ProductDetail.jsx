import { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  Container, Typography, Paper, Stack, Button,
  CircularProgress, Alert, Divider,
} from '@mui/material';
import { getProduct } from '../api/products';

export default function ProductDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    getProduct(id)
      .then(setProduct)
      .catch(() => setError('商品データの取得に失敗しました'))
      .finally(() => setLoading(false));
  }, [id]);

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error)   return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  return (
    <Container sx={{ mt: 4, maxWidth: 600 }}>
      <Button onClick={() => navigate('/')} sx={{ mb: 2 }}>← 一覧へ戻る</Button>
      <Paper sx={{ p: 3 }}>
        <Typography variant="h5" gutterBottom>{product.name}</Typography>
        <Divider sx={{ mb: 2 }} />
        <Stack spacing={1.5}>
          <Typography>説明：{product.description ?? '—'}</Typography>
          <Typography>価格：{product.price.toLocaleString()} 円</Typography>
          <Typography>在庫：{product.stock} 個</Typography>
        </Stack>
      </Paper>
    </Container>
  );
}
