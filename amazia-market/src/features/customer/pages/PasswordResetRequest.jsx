import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import {
  Container, Paper, Stack, TextField, Button, Typography,
  Alert, Link,
} from '@mui/material';
import { requestPasswordReset } from '../api/customer';

export default function PasswordResetRequest() {
  const [email, setEmail] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [done, setDone] = useState(false);
  const [error, setError] = useState(null);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await requestPasswordReset(email);
      // メール存在の有無を漏らさないため常に同じ応答
      setDone(true);
    } catch {
      setError('メール送信に失敗しました。時間をおいて再度お試しください。');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 6 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>パスワード再発行</Typography>
        {done ? (
          <Stack spacing={2}>
            <Alert severity="success">
              再設定用のリンクを送信しました。メールをご確認ください（30 分以内に有効）。
            </Alert>
            <Link component={RouterLink} to="/login">ログイン画面へ戻る</Link>
          </Stack>
        ) : (
          <form onSubmit={handleSubmit} noValidate>
            <Stack spacing={2}>
              {error && <Alert severity="error">{error}</Alert>}
              <Typography variant="body2" color="text.secondary">
                ご登録のメールアドレス宛に再設定用のリンクを送信します。
              </Typography>
              <TextField
                label="メールアドレス" type="email" required
                value={email} onChange={(e) => setEmail(e.target.value)}
              />
              <Button type="submit" variant="contained" disabled={submitting || !email}>
                {submitting ? '送信中…' : '再発行メールを送信'}
              </Button>
              <Link component={RouterLink} to="/login" variant="body2">
                ログイン画面へ戻る
              </Link>
            </Stack>
          </form>
        )}
      </Paper>
    </Container>
  );
}
