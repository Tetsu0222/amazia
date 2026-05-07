import axios from 'axios';
import { getCsrfToken } from '../../customer/api/customer';

// /api/customer/carts/* は MarketSession + CSRF 保護下で呼び出す。
// CSRF トークンは customer.js が保持しているため再利用する。
const client = axios.create({
  baseURL: '/api/customer/carts',
  withCredentials: true,
});

client.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toUpperCase();
  if (method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    const token = getCsrfToken();
    if (token) {
      config.headers = config.headers ?? {};
      config.headers['X-CSRF-Token'] = token;
    }
  }
  return config;
});

export const getMyCart = () =>
  client.get('/me').then((r) => r.data);

export const addToCart = (skuId, quantity, preorder = false) =>
  client.post('/me/items', { skuId, quantity, preorder }).then((r) => r.data);

export const updateQuantity = (itemId, quantity) =>
  client.put(`/me/items/${itemId}`, { quantity }).then((r) => r.data);

export const removeFromCart = (itemId) =>
  client.delete(`/me/items/${itemId}`).then((r) => r.data);

export const clearCart = () =>
  client.delete('/me').then((r) => r.data);
