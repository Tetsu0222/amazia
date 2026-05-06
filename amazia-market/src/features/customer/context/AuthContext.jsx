import { useEffect, useState, useCallback } from 'react';
import {
  loginCustomer as apiLogin,
  logoutCustomer as apiLogout,
  getMyPage,
  fetchCsrfToken,
  setCsrfToken,
} from '../api/customer';
import { AuthContext } from './AuthContextValue';

export function AuthProvider({ children }) {
  const [customer, setCustomer] = useState(null);
  const [initializing, setInitializing] = useState(true);

  useEffect(() => {
    let canceled = false;
    (async () => {
      try {
        const me = await getMyPage();
        if (canceled) return;
        setCustomer(me);
        try {
          const { csrfToken } = await fetchCsrfToken();
          if (!canceled) setCsrfToken(csrfToken);
        } catch {
          // CSRF トークン取得失敗時は次回ログインで取り直す
        }
      } catch {
        // 401 なら未ログイン状態
      } finally {
        if (!canceled) setInitializing(false);
      }
    })();
    return () => {
      canceled = true;
    };
  }, []);

  const login = useCallback(async (email, password) => {
    const res = await apiLogin({ email, password });
    setCsrfToken(res.csrfToken);
    const me = await getMyPage();
    setCustomer(me);
    return me;
  }, []);

  const logout = useCallback(async () => {
    try {
      await apiLogout();
    } finally {
      setCsrfToken(null);
      setCustomer(null);
    }
  }, []);

  const value = { customer, initializing, login, logout, isAuthenticated: customer != null };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
