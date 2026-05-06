import axios from 'axios';
import { getCsrfToken } from '../../customer/api/customer';

// /api/customer/orders/* は MarketSession + CSRF 保護下で呼び出す。
// CSRF トークンは customer.js が保持しているため再利用する。
const client = axios.create({
  baseURL: '/api/customer',
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

export const confirmOrder = (payload) =>
  client.post('/orders/confirm', payload).then((r) => r.data);
