import axios from 'axios';
import { getCsrfToken } from '../../customer/api/customer';

// /api/customer/sales-returns は MarketSession + CSRF 保護下で呼び出す。
// checkout.js と同じ作法で client を別途用意する。
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

/**
 * 返品申請 API（POST /api/customer/sales-returns）。
 *
 * 設計書 r4 / phase14 §返品申請。
 * 申請成功で 201 + { id, status: 'REQUESTED' }、ガード違反で 4xx + メッセージ。
 */
export const requestSalesReturn = (payload) =>
  client.post('/sales-returns', payload).then((r) => r.data);
