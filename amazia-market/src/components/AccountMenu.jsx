import { useState } from 'react';
import { Box, Button, Menu, MenuItem, Typography } from '@mui/material';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useAuth } from '../features/customer/context/useAuth';

export default function AccountMenu() {
  const { isAuthenticated, customer, logout } = useAuth();
  const navigate = useNavigate();
  const [anchorEl, setAnchorEl] = useState(null);
  const open = Boolean(anchorEl);

  const handleOpen = (e) => setAnchorEl(e.currentTarget);
  const handleClose = () => setAnchorEl(null);

  const handleLogout = async () => {
    handleClose();
    await logout();
    navigate('/');
  };

  return (
    <Box sx={{ display: 'flex', alignItems: 'center' }}>
      {isAuthenticated && (
        <Box sx={{ display: { xs: 'none', sm: 'block' }, mr: 1 }}>
          <Typography variant="caption" sx={{ color: 'header.text' }}>
            こんにちは、{customer?.email}
          </Typography>
        </Box>
      )}
      <Button
        onClick={handleOpen}
        sx={{
          color: 'header.text',
          textTransform: 'none',
          border: '1px solid transparent',
          '&:hover': { borderColor: 'header.hoverBorder' },
        }}
      >
        アカウント＆リスト
      </Button>
      <Menu anchorEl={anchorEl} open={open} onClose={handleClose}>
        {isAuthenticated
          ? [
              <MenuItem key="mypage" component={RouterLink} to="/mypage" onClick={handleClose}>
                マイページ
              </MenuItem>,
              <MenuItem key="orders" component={RouterLink} to="/orders" onClick={handleClose}>
                購入履歴
              </MenuItem>,
              <MenuItem key="logout" onClick={handleLogout} data-testid="logout-button">
                ログアウト
              </MenuItem>,
            ]
          : [
              <MenuItem key="login" component={RouterLink} to="/login" onClick={handleClose}>
                ログイン
              </MenuItem>,
              <MenuItem key="register" component={RouterLink} to="/register" onClick={handleClose}>
                会員登録
              </MenuItem>,
            ]}
      </Menu>
    </Box>
  );
}
