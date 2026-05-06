import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container, Paper, Stack, Typography, Button, Alert,
  CircularProgress, Divider, Box, Chip, Table, TableBody,
  TableCell, TableHead, TableRow,
} from '@mui/material';
import { getMyPurchaseHistory } from '../../checkout/api/checkout';

// 設計書 r4 / Amazia Market §購入履歴
//   表示: 購入日時 / 商品名+色+サイズ / 数量 / 金額 / 配送予定日 / 配送ステータス / 配送方法 / 予約 or 通常購入区分
//   命名規約: docs/ai_context/operation_logs_naming.md
//     screen_name: market.purchase_history.list
//     api_name   : GET /api/customer/orders

const SHIPPING_STATUS_LABEL = {
  PENDING:          { text: '配送準備中', color: 'default' },
  SHIPPED:          { text: '発送済',     color: 'info' },
  DELIVERED:        { text: '配送完了',   color: 'success' },
  RETURN_REQUESTED: { text: '返品申請中', color: 'warning' },
  RETURNED:         { text: '返品完了',   color: 'default' },
};

const SHIPPING_METHOD_LABEL = {
  1: '宅配',
  2: 'コンビニ受取',
  3: '置き配',
};

const PAYMENT_METHOD_LABEL = {
  1: 'クレジットカード',
  2: 'd払い',
  3: '代引き',
};

export default function PurchaseHistory() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    let canceled = false;
    (async () => {
      try {
        const data = await getMyPurchaseHistory();
        if (!canceled) setItems(data ?? []);
      } catch (e) {
        if (!canceled) setError('購入履歴の取得に失敗しました。');
      } finally {
        if (!canceled) setLoading(false);
      }
    })();
    return () => { canceled = true; };
  }, []);

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error) return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;

  return (
    <Container sx={{ mt: 4 }} maxWidth="md">
      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h5">購入履歴</Typography>
        <Button onClick={() => navigate('/mypage')}>マイページへ</Button>
      </Stack>

      {items.length === 0 ? (
        <Paper sx={{ p: 3 }}>
          <Typography color="text.secondary">購入履歴はまだありません。</Typography>
        </Paper>
      ) : (
        <Paper>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>購入日</TableCell>
                <TableCell>商品</TableCell>
                <TableCell align="right">数量</TableCell>
                <TableCell align="right">金額</TableCell>
                <TableCell>配送方法</TableCell>
                <TableCell>支払方法</TableCell>
                <TableCell>区分</TableCell>
                <TableCell>配送状況</TableCell>
                <TableCell>配送日</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((it) => {
                const statusInfo = SHIPPING_STATUS_LABEL[it.shippingStatusCode] ?? { text: it.shippingStatusCode, color: 'default' };
                return (
                  <TableRow key={it.salesId}>
                    <TableCell>{it.salesDate}</TableCell>
                    <TableCell>
                      <Box>
                        <Typography variant="body2">{it.productName ?? '—'}</Typography>
                        <Typography variant="caption" color="text.secondary">
                          {it.color}{it.size ? ` / ${it.size}` : ''}
                        </Typography>
                      </Box>
                    </TableCell>
                    <TableCell align="right">{it.quantity}</TableCell>
                    <TableCell align="right">¥{it.amount?.toLocaleString()}</TableCell>
                    <TableCell>{SHIPPING_METHOD_LABEL[it.shippingMethodId] ?? '—'}</TableCell>
                    <TableCell>{PAYMENT_METHOD_LABEL[it.paymentMethodId] ?? '—'}</TableCell>
                    <TableCell>
                      <Chip
                        size="small"
                        label={it.preorder ? '予約' : '通常'}
                        color={it.preorder ? 'secondary' : 'default'}
                      />
                    </TableCell>
                    <TableCell>
                      <Chip size="small" label={statusInfo.text} color={statusInfo.color} />
                    </TableCell>
                    <TableCell>{it.shippingDate ?? '—'}</TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Paper>
      )}
    </Container>
  );
}
