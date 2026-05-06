import { useState } from 'react';
import { Link as RouterLink, useNavigate, useLocation } from 'react-router-dom';
import {
  Container, Paper, Stack, TextField, Button, Typography,
  Alert, Link,
} from '@mui/material';
import { useAuth } from '../context/useAuth';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const redirectTo = location.state?.from ?? '/mypage';

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState(null);
  const [submitting, setSubmitting] = useState(false);

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      const status = err?.response?.status;
      if (status === 423) {
        setError('アカウントがロックされています。しばらく経ってから再度お試しください。');
      } else if (status === 401) {
        setError('メールアドレスまたはパスワードが正しくありません。');
      } else {
        setError('ログインに失敗しました。時間をおいて再度お試しください。');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Container maxWidth="sm" sx={{ mt: 6 }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h5" gutterBottom>ログイン</Typography>
        <form onSubmit={handleSubmit} noValidate>
          <Stack spacing={2}>
            {error && <Alert severity="error">{error}</Alert>}
            <TextField
              label="メールアドレス"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              autoComplete="email"
            />
            <TextField
              label="パスワード"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              autoComplete="current-password"
            />
            <Button
              type="submit"
              variant="contained"
              disabled={submitting}
            >
              {submitting ? 'ログイン中…' : 'ログイン'}
            </Button>
            <Stack direction="row" justifyContent="space-between">
              <Link component={RouterLink} to="/register" variant="body2">
                会員登録はこちら
              </Link>
              <Link component={RouterLink} to="/password/reset" variant="body2">
                パスワードをお忘れの方
              </Link>
            </Stack>
          </Stack>
        </form>
      </Paper>
    </Container>
  );
}
