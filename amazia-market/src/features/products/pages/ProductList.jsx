import { useEffect, useMemo, useState } from 'react';
import { useSearchParams, Link as RouterLink } from 'react-router-dom';
import {
  Container, Typography, Grid, CircularProgress, Alert, Box, Link,
} from '@mui/material';
import { getMarketProducts } from '../api/products';
import ProductCard from '../components/ProductCard';
import SortSelect, { sortProducts } from '../components/SortSelect';
import { searchProducts } from '../searchUtils';

export default function ProductList() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading]   = useState(true);
  const [error, setError]       = useState(null);
  const [sortKey, setSortKey]   = useState('recommended');
  const [searchParams] = useSearchParams();
  const keyword = searchParams.get('q') ?? '';

  useEffect(() => {
    getMarketProducts()
      .then(setProducts)
      .catch(() => setError('商品データの取得に失敗しました'))
      .finally(() => setLoading(false));
  }, []);

  const visibleProducts = useMemo(() => {
    const filtered = searchProducts(products, keyword);
    return sortProducts(filtered, sortKey);
  }, [products, keyword, sortKey]);

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error)   return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  return (
    <Container maxWidth={false} sx={{ mt: 4 }}>
      {keyword && (
        <Typography variant="subtitle1" sx={{ mb: 1 }}>
          「{keyword}」の検索結果（{visibleProducts.length}件）
        </Typography>
      )}
      <Box sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', mb: 2 }}>
        <Typography variant="h5">商品一覧</Typography>
        {products.length > 0 && (
          <SortSelect value={sortKey} onChange={setSortKey} />
        )}
      </Box>
      {products.length === 0 && (
        <Typography color="text.secondary">現在表示できる商品がありません。</Typography>
      )}
      {products.length > 0 && visibleProducts.length === 0 && (
        <Box sx={{ py: 4 }}>
          <Typography color="text.secondary" sx={{ mb: 1 }}>
            該当する商品がありません
          </Typography>
          <Link component={RouterLink} to="/">すべての商品を見る</Link>
        </Box>
      )}
      <Grid container spacing={2}>
        {visibleProducts.map((p) => (
          <Grid size={{ xs: 6, sm: 4, md: 3, lg: 2.4 }} key={p.productId}>
            <ProductCard product={p} />
          </Grid>
        ))}
      </Grid>
    </Container>
  );
}
