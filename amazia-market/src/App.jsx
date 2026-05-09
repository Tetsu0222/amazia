import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom';
import { CssBaseline } from '@mui/material';
import { ThemeProvider } from '@mui/material/styles';
import theme from './theme';
import ProductList from './features/products/pages/ProductList';
import ProductDetail from './features/products/pages/ProductDetail';
import Login from './features/customer/pages/Login';
import Register from './features/customer/pages/Register';
import PasswordResetRequest from './features/customer/pages/PasswordResetRequest';
import PasswordResetConfirm from './features/customer/pages/PasswordResetConfirm';
import MyPage from './features/customer/pages/MyPage';
import { AuthProvider } from './features/customer/context/AuthContext';
import { CartProvider } from './features/cart/context/CartContext';
import ProtectedRoute from './features/customer/components/ProtectedRoute';
import AppHeader from './components/AppHeader';
import Checkout from './features/checkout/pages/Checkout';
import CheckoutComplete from './features/checkout/pages/CheckoutComplete';
import PurchaseHistory from './features/orders/pages/PurchaseHistory';
import CartPage from './features/cart/pages/CartPage';
import MyPageInquiryList from './features/inquiry/pages/MyPageInquiryList';
import MyPageInquiryDetail from './features/inquiry/pages/MyPageInquiryDetail';
import MyPageInquiryNew from './features/inquiry/pages/MyPageInquiryNew';

function Layout() {
  return (
    <>
      <AppHeader />
      <Outlet />
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <AuthProvider>
          <CartProvider>
          <Routes>
            <Route element={<Layout />}>
              <Route path="/" element={<ProductList />} />
              <Route path="/products/:id" element={<ProductDetail />} />
              <Route path="/login" element={<Login />} />
              <Route path="/register" element={<Register />} />
              <Route path="/password/reset" element={<PasswordResetRequest />} />
              <Route path="/password/reset/confirm" element={<PasswordResetConfirm />} />
              <Route
                path="/mypage"
                element={
                  <ProtectedRoute>
                    <MyPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/checkout"
                element={
                  <ProtectedRoute>
                    <Checkout />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/checkout/complete"
                element={
                  <ProtectedRoute>
                    <CheckoutComplete />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/orders"
                element={
                  <ProtectedRoute>
                    <PurchaseHistory />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/cart"
                element={
                  <ProtectedRoute>
                    <CartPage />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mypage/inquiries"
                element={
                  <ProtectedRoute>
                    <MyPageInquiryList />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mypage/inquiries/new"
                element={
                  <ProtectedRoute>
                    <MyPageInquiryNew />
                  </ProtectedRoute>
                }
              />
              <Route
                path="/mypage/inquiries/:id"
                element={
                  <ProtectedRoute>
                    <MyPageInquiryDetail />
                  </ProtectedRoute>
                }
              />
            </Route>
          </Routes>
          </CartProvider>
        </AuthProvider>
      </ThemeProvider>
    </BrowserRouter>
  );
}
