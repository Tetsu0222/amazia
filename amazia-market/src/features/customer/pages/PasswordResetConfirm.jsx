import { useState, useMemo } from 'react';
import { useSearchParams, useNavigate, Link as RouterLink } from 'react-router-dom';
import {
  Container, Paper, Stack, TextField, Button, Typography,
  Alert, Link,
} from '@mui/material';
import { confirmPasswordReset } from '../api/customer';

const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d).{8,}$/;

export default function PasswordResetConfirm() {
  const [params] = useSearchParams();
  const token = params.get('token') ?? '';
  const navigate = useNavigate();

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);

  const passwordError = useMemo(() => {
    if (!password) return null;
    if (!PASSWORD_REGEX.test(password)) return '8文字以上で英大文字・英小文字・数字を含めてください';
    return null;
  }, [password]);
  const mismatch = confirm && password !== confirm;

  if (!token) {
    return (
      <Container maxWidth="sm" sx={{ mt: 6 }}>
        <Paper sx={{ p: 4 }}>
          <Alert severity="error">トークンが指定されていません。メール内のリンクからアクセスしてください。</Alert>
        </Paper>
      </Container>
    );
  }

  const handleSubmit = async (e) => {
    e.preventDefault();
    if (passwordError || mismatch || !password) return;
    setSubmitting(true);
    setError(null);
    try {
      await confirmPasswordReset(token, password);
      navigate('/login', { state: { passwordReset: true } });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 410 || status === 404) {
        setError('リンクの有効期限が切れています。再度パスワード再発行を申請してください。');
      } else if (status === 400) {
        setError('パスワードが要件を満たしていません。');
      } else {
        setError('パスワード再設定に失敗しました。時間をおいて再度お試しください。');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 6 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>パスワード再設定</Typography>
        <form onSubmit={handleSubmit} noValidate>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="新しいパスワード" type="password" required
              value={password} onChange={(e) => setPassword(e.target.value)}
              error={!!passwordError}
              helperText={passwordError ?? '8文字以上・英大小文字・数字を含む'}
            />
            <TextField
              label="新しいパスワード（確認）" type="password" required
              value={confirm} onChange={(e) => setConfirm(e.target.value)}
              error={!!mismatch}
              helperText={mismatch ? 'パスワードが一致しません' : ' '}
            />
            <Button
              type="submit" variant="contained"
              disabled={submitting || !password || !!passwordError || !!mismatch}
            >
              {submitting ? '送信中…' : 'パスワードを再設定'}
            </Button>
            <Link component={RouterLink} to="/login" variant="body2">
              ログイン画面へ戻る
            </Link>
          </Stack>
        </form>
      </Paper>
    </Container>
  );
}
