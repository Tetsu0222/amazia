import { useLocation, useNavigate, Navigate } from 'react-router-dom';
import { Container, Paper, Stack, Typography, Button, Alert, Box } from '@mui/material';

export default function CheckoutComplete() {
  const location = useLocation();
  const navigate = useNavigate();
  const result = location.state;

  // 直接アクセスされた場合はトップへリダイレクト
  // カートモードは salesIds（配列）、単品モードは salesId（単数）で判別
  const hasSingleResult = result?.salesId != null;
  const hasCartResult = Array.isArray(result?.salesIds) && result.salesIds.length > 0;
  if (!result || (!hasSingleResult && !hasCartResult)) {
    return <Navigate to="/" replace />;
  }

  return (
    <Container sx={{ mt: 4 }} maxWidth="sm">
      <Paper sx={{ p: 4 }}>
        <Stack spacing={2}>
          <Alert severity="success">ご注文ありがとうございました。</Alert>
          {hasCartResult ? (
            <>
              <Typography variant="h6">
                {result.salesIds.length} 件の注文を承りました
              </Typography>
              <Box>
                <Typography variant="body2" color="text.secondary">注文番号</Typography>
                <Typography variant="body2">
                  {result.salesIds.map((id) => `#${id}`).join(' / ')}
                </Typography>
              </Box>
            </>
          ) : (
            <>
              <Typography variant="h6">注文番号: #{result.salesId}</Typography>
              <Box>
                <Typography variant="body2" color="text.secondary">決済ID</Typography>
                <Typography variant="body2" sx={{ wordBreak: 'break-all' }}>{result.paymentId}</Typography>
              </Box>
            </>
          )}
          <Box>
            <Typography variant="body2" color="text.secondary">数量</Typography>
            <Typography>{result.quantity} 点</Typography>
          </Box>
          <Box>
            <Typography variant="body2" color="text.secondary">お支払い金額</Typography>
            <Typography variant="h5" color="primary">¥{result.amount?.toLocaleString()}</Typography>
          </Box>
          <Stack direction="row" spacing={1} sx={{ justifyContent: 'flex-end' }}>
            <Button onClick={() => navigate('/')}>商品一覧へ</Button>
            <Button variant="contained" onClick={() => navigate('/mypage')}>マイページへ</Button>
          </Stack>
        </Stack>
      </Paper>
    </Container>
  );
}
