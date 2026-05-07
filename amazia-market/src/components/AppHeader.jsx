import {
  AppBar,
  Toolbar,
  Typography,
  Box,
  IconButton,
  Badge,
} from '@mui/material';
import ShoppingCartIcon from '@mui/icons-material/ShoppingCart';
import { Link as RouterLink } from 'react-router-dom';
import AccountMenu from './AccountMenu';
import CategoryNav from './CategoryNav';
import SearchBar from './SearchBar';
import { useCart } from '../features/cart/context/useCart';

export default function AppHeader() {
  const { totalCount } = useCart();
  const cartCount = totalCount ?? 0;

  return (
    <>
      <AppBar position="static" sx={{ bgcolor: 'header.main' }} elevation={0}>
        <Toolbar sx={{ minHeight: 60, gap: 2 }}>
          <Typography
            variant="h6"
            component={RouterLink}
            to="/"
            sx={{
              textDecoration: 'none',
              color: 'header.text',
              border: '1px solid transparent',
              px: 1,
              py: 0.5,
              whiteSpace: 'nowrap',
              '&:hover': { borderColor: 'header.hoverBorder' },
            }}
          >
            Amazia Market
          </Typography>
          <Box
            sx={{
              flexGrow: 1,
              display: { xs: 'none', sm: 'flex' },
              alignItems: 'center',
            }}
          >
            <SearchBar />
          </Box>
          <Box sx={{ flexGrow: { xs: 1, sm: 0 } }} />
          <AccountMenu />
          <IconButton
            component={RouterLink}
            to="/cart"
            aria-label="カート"
            sx={{
              color: 'header.text',
              border: '1px solid transparent',
              '&:hover': { borderColor: 'header.hoverBorder' },
            }}
          >
            <Badge
              badgeContent={cartCount}
              overlap="circular"
              sx={{
                '& .MuiBadge-badge': {
                  bgcolor: 'accent.main',
                  color: 'accent.contrastText',
                },
              }}
            >
              <ShoppingCartIcon />
            </Badge>
            <Typography
              variant="caption"
              sx={{ ml: 0.5, display: { xs: 'none', sm: 'inline' } }}
            >
              カート
            </Typography>
          </IconButton>
        </Toolbar>
        <Box
          sx={{
            display: { xs: 'flex', sm: 'none' },
            px: 2,
            pb: 1,
          }}
        >
          <SearchBar />
        </Box>
      </AppBar>
      <CategoryNav />
    </>
  );
}
