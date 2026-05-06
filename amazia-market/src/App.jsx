import { BrowserRouter, Routes, Route, Outlet } from 'react-router-dom';
import { CssBaseline } from '@mui/material';
import ProductList from './features/products/pages/ProductList';
import ProductDetail from './features/products/pages/ProductDetail';
import Login from './features/customer/pages/Login';
import Register from './features/customer/pages/Register';
import PasswordResetRequest from './features/customer/pages/PasswordResetRequest';
import PasswordResetConfirm from './features/customer/pages/PasswordResetConfirm';
import MyPage from './features/customer/pages/MyPage';
import { AuthProvider } from './features/customer/context/AuthContext';
import ProtectedRoute from './features/customer/components/ProtectedRoute';
import AppHeader from './components/AppHeader';

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
      <CssBaseline />
      <AuthProvider>
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
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
