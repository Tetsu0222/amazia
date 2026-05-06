import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container, Paper, Stack, Typography, Button, Alert,
  CircularProgress, Box, Chip, Table, TableBody,
  TableCell, TableHead, TableRow,
  Dialog, DialogTitle, DialogContent, DialogActions,
  TextField, Snackbar,
} from '@mui/material';
import { getMyPurchaseHistory } from '../../checkout/api/checkout';
import { requestSalesReturn } from '../api/salesReturn';

// 設計書 r4 / Amazia Market §購入履歴
//   表示: 購入日時 / 商品名+色+サイズ / 数量 / 金額 / 配送予定日 / 配送ステータス / 配送方法 / 予約 or 通常購入区分
//   命名規約: docs/ai_context/operation_logs_naming.md
//     screen_name: market.purchase_history.list
//     api_name   : GET /api/customer/orders
// B-5-7: DELIVERED の sales に対して「返品申請」ボタンを表示。
//   申請後は配送ステータスが RETURN_REQUESTED に変わるため、ボタンは自動的に消える。

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

  // 返品申請モーダル
  const [returnTarget, setReturnTarget] = useState(null);
  const [returnQuantity, setReturnQuantity] = useState(1);
  const [returnReason, setReturnReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [snackbar, setSnackbar] = useState(null);

  const fetchHistory = async () => {
    try {
      const data = await getMyPurchaseHistory();
      setItems(data ?? []);
    } catch (e) {
      setError('購入履歴の取得に失敗しました。');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHistory();
  }, []);

  const openReturnDialog = (item) => {
    setReturnTarget(item);
    setReturnQuantity(1);
    setReturnReason('');
    setSubmitError(null);
  };

  const closeReturnDialog = () => {
    if (submitting) return;
    setReturnTarget(null);
  };

  const submitReturn = async () => {
    if (!returnTarget) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      await requestSalesReturn({
        salesId: returnTarget.salesId,
        quantity: returnQuantity,
        reason: returnReason || null,
      });
      setSnackbar('返品申請を受け付けました。承認をお待ちください。');
      setReturnTarget(null);
      await fetchHistory();
    } catch (e) {
      const status = e?.response?.status;
      if (status === 409) {
        setSubmitError('既に返品申請が進行中です。');
      } else if (status === 400) {
        setSubmitError('返品数量が不正です（購入数量を超えています）。');
      } else if (status === 401) {
        setSubmitError('セッションが切れました。再度ログインしてください。');
      } else {
        setSubmitError('返品申請に失敗しました。時間を置いて再度お試しください。');
      }
    } finally {
      setSubmitting(false);
    }
  };

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
                <TableCell>返品</TableCell>
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
                    <TableCell>
                      {it.shippingStatusCode === 'DELIVERED' ? (
                        <Button size="small" variant="outlined" onClick={() => openReturnDialog(it)}>
                          返品申請
                        </Button>
                      ) : (
                        <Typography variant="caption" color="text.secondary">—</Typography>
                      )}
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        </Paper>
      )}

      <Dialog open={returnTarget !== null} onClose={closeReturnDialog} fullWidth maxWidth="sm">
        <DialogTitle>返品申請</DialogTitle>
        <DialogContent>
          {returnTarget && (
            <Stack spacing={2} sx={{ mt: 1 }}>
              <Box>
                <Typography variant="body2" color="text.secondary">対象</Typography>
                <Typography>
                  {returnTarget.productName}（{returnTarget.color} / {returnTarget.size}）
                </Typography>
                <Typography variant="caption" color="text.secondary">
                  購入数量：{returnTarget.quantity}
                </Typography>
              </Box>
              <TextField
                label="返品数量"
                type="number"
                value={returnQuantity}
                onChange={(e) => setReturnQuantity(Number(e.target.value) || 1)}
                slotProps={{ htmlInput: { min: 1, max: returnTarget.quantity } }}
              />
              <TextField
                label="返品理由（任意）"
                multiline
                rows={3}
                value={returnReason}
                onChange={(e) => setReturnReason(e.target.value)}
                slotProps={{ htmlInput: { maxLength: 1000 } }}
              />
              {submitError && <Alert severity="error">{submitError}</Alert>}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={closeReturnDialog} disabled={submitting}>キャンセル</Button>
          <Button onClick={submitReturn} variant="contained" disabled={submitting}>
            {submitting ? '送信中…' : '申請する'}
          </Button>
        </DialogActions>
      </Dialog>

      <Snackbar
        open={snackbar !== null}
        autoHideDuration={4000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
      />
    </Container>
  );
}
