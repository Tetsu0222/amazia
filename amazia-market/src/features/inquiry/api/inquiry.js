import axios from 'axios';
import { getCsrfToken } from '../../customer/api/customer';

/**
 * フェーズ18: Market 顧客向け問い合わせ API クライアント。
 * /api/customer/inquiries 系を MarketSession + CSRF 保護下で呼び出す。
 */
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

export const listMyInquiries = (params = {}) =>
  client.get('/inquiries', { params }).then((r) => r.data);

export const getMyInquiry = (id) =>
  client.get(`/inquiries/${id}`).then((r) => r.data);

export const createInquiry = (payload) =>
  client.post('/inquiries', payload).then((r) => r.data);

export const replyMyInquiry = (id, message) =>
  client.post(`/inquiries/${id}/messages`, { message }).then((r) => r.data);
