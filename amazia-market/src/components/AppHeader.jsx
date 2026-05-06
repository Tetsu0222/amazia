import { AppBar, Toolbar, Typography, Button, Stack, Box } from '@mui/material';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/customer/context/useAuth';

export default function AppHeader() {
  const { isAuthenticated, customer, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = async () => {
    await logout();
    navigate('/');
  };

  return (
    <AppBar position="static" color="default" elevation={1}>
      <Toolbar>
        <Typography
          variant="h6"
          component={RouterLink}
          to="/"
          sx={{ textDecoration: 'none', color: 'inherit', flexGrow: 1 }}
        >
          Amazia Market
        </Typography>
        <Stack direction="row" spacing={1} alignItems="center">
          {isAuthenticated ? (
            <>
              <Box sx={{ display: { xs: 'none', sm: 'block' } }}>
                <Typography variant="body2" color="text.secondary">
                  {customer?.email}
                </Typography>
              </Box>
              <Button component={RouterLink} to="/mypage" color="inherit">
                マイページ
              </Button>
              <Button onClick={handleLogout} color="inherit" data-testid="logout-button">
                ログアウト
              </Button>
            </>
          ) : (
            <>
              <Button component={RouterLink} to="/login" color="inherit">
                ログイン
              </Button>
              <Button
                component={RouterLink}
                to="/register"
                variant="contained"
                color="primary"
                size="small"
              >
                会員登録
              </Button>
            </>
          )}
        </Stack>
      </Toolbar>
    </AppBar>
  );
}
