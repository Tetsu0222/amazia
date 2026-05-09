import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Container, Paper, Stack, Typography, Divider,
  CircularProgress, Alert, Box, Button,
} from '@mui/material';
import { getMyPage } from '../api/customer';

const PAYMENT_LABEL = {
  credit_card: 'クレジットカード',
  bank_transfer: '銀行振込',
  other: 'その他',
};

function Row({ label, children }) {
  return (
    <Box sx={{ display: 'flex', gap: 2 }}>
      <Typography sx={{ width: 120, color: 'text.secondary' }}>{label}</Typography>
      <Typography>{children}</Typography>
    </Box>
  );
}

export default function MyPage() {
  const navigate = useNavigate();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    getMyPage()
      .then(setData)
      .catch(() => setError('情報の取得に失敗しました'))
      .finally(() => setLoading(false));
  }, []);

  if (loading) return <CircularProgress sx={{ m: 4 }} />;
  if (error) return <Alert severity="error" sx={{ m: 4 }}>{error}</Alert>;
  if (!data) return null;

  return (
    <Container maxWidth="md" sx={{ mt: 4 }}>
      <Paper sx={{ p: 4 }}>
        <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
          <Typography variant="h5">マイページ</Typography>
          <Stack direction="row" spacing={1}>
            <Button variant="outlined" onClick={() => navigate('/orders')}>
              購入履歴を見る
            </Button>
            <Button variant="outlined" onClick={() => navigate('/mypage/inquiries')}>
              問い合わせ
            </Button>
          </Stack>
        </Stack>
        <Divider sx={{ mb: 2 }} />
        <Stack spacing={1.5}>
          <Row label="お名前">{data.nameLast} {data.nameFirst}</Row>
          <Row label="メールアドレス">{data.email}</Row>
          <Row label="郵便番号">{data.postalCode}</Row>
          <Row label="住所">{data.address}</Row>
          <Row label="生年月日">{data.birthday}</Row>
          <Row label="決済方法">{PAYMENT_LABEL[data.paymentMethod] ?? data.paymentMethod}</Row>
        </Stack>
      </Paper>
    </Container>
  );
}
