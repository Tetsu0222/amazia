import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container, Typography, Table, TableHead, TableRow,
  TableCell, TableBody, Paper, CircularProgress, Alert, Chip,
} from '@mui/material';
import { getProducts } from '../api/products';

const STATUS_MAP = {
  WAITING:     { label: '入荷待',    color: 'default' },
  RESERVATION: { label: '予約受付中', color: 'primary' },
  ON_SALE:     { label: '販売中',    color: 'success' },
};

export default function ProductList() {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    getProducts()
      .then(setProducts)
      .catch(() => setError('商品データの取得に失敗しました'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error)   return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  return (
    <Container sx={{ mt: 4 }}>
      <Typography variant="h5" gutterBottom>商品一覧</Typography>
      <Paper>
        <Table>
          <TableHead>
            <TableRow>
              <TableCell>ID</TableCell>
              <TableCell>商品名</TableCell>
              <TableCell align="right">価格（円）</TableCell>
              <TableCell align="right">在庫数</TableCell>
              <TableCell>ステータス</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {products.map(p => {
              const status = STATUS_MAP[p.statusCode];
              return (
                <TableRow
                  key={p.id}
                  hover
                  sx={{ cursor: 'pointer' }}
                  onClick={() => navigate(`/products/${p.id}`)}
                >
                  <TableCell>{p.id}</TableCell>
                  <TableCell>{p.name}</TableCell>
                  <TableCell align="right">{p.price.toLocaleString()}</TableCell>
                  <TableCell align="right">{p.stock}</TableCell>
                  <TableCell>
                    {status && (
                      <Chip
                        label={status.label}
                        color={status.color}
                        size="small"
                      />
                    )}
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      </Paper>
    </Container>
  );
}
