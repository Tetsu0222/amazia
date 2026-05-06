import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container, Typography, Grid, Card, CardActionArea,
  CardMedia, CardContent, CircularProgress, Alert, Box,
} from '@mui/material';
import { getMarketProducts } from '../api/products';
import { NOIMAGE } from '../constants';

export default function ProductList() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    getMarketProducts()
      .then(setProducts)
      .catch(() => setError('商品データの取得に失敗しました'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error)   return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  return (
    <Container sx={{ mt: 4 }}>
      <Typography variant="h5" gutterBottom>商品一覧</Typography>
      {products.length === 0 && (
        <Typography color="text.secondary">現在表示できる商品がありません。</Typography>
      )}
      <Grid container spacing={3}>
        {products.map(p => (
          <Grid size={{ xs: 12, sm: 6, md: 4 }} key={p.productId}>
            <Card sx={{ height: '100%' }}>
              <CardActionArea
                sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}
                onClick={() => navigate(`/products/${p.productId}`)}
              >
                <CardMedia
                  component="img"
                  height="200"
                  image={p.mainImage ?? NOIMAGE}
                  alt={p.productName}
                  sx={{ objectFit: 'contain', bgcolor: '#f5f5f5' }}
                  loading="lazy"
                />
                <CardContent sx={{ flexGrow: 1 }}>
                  <Typography variant="subtitle1" fontWeight="bold" noWrap>
                    {p.productName}
                  </Typography>
                  <Box sx={{ mt: 1 }}>
                    <Typography variant="body2" color="text.secondary">
                      {p.description ?? ''}
                    </Typography>
                    <Typography variant="h6" color="primary" sx={{ mt: 1 }}>
                      ¥{p.minPrice != null ? p.minPrice.toLocaleString() : '—'} 〜
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                      在庫：{p.totalStock} 個
                    </Typography>
                  </Box>
                </CardContent>
              </CardActionArea>
            </Card>
          </Grid>
        ))}
      </Grid>
    </Container>
  );
}
