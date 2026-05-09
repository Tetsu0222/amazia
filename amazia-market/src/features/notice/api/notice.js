import axios from 'axios';
import { getCsrfToken } from '../../customer/api/customer';

/**
 * フェーズ19: Market 顧客向けお知らせ API クライアント。
 *
 * - 公開系（一覧 / 詳細 / 分類マスタ）：認証不要だが、ログイン中は Cookie が送信され
 *   Market 認証視点（is_read 含む）になる（設計書 §4 / §5 / R19-9）。
 * - 既読登録 / 未読取得（/api/customer/notices/*）：MarketSession + CSRF 必須。
 *
 * baseURL は付けず、Vite の dev proxy 設定（/api → Console 8000、/api/customer → Core 8080）
 * に従って絶対パスで指定する。
 */
const client = axios.create({
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

function clean(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
  );
}

export const noticeApi = {
  fetchUnreadCount: () =>
    client.get('/api/customer/notices/unread-count').then((r) => r.data),

  fetchUnreadHeader: () =>
    client.get('/api/customer/notices/unread').then((r) => r.data),

  fetchList: (params = {}) =>
    client.get('/api/notices', { params: clean(params) }).then((r) => r.data),

  fetchDetail: (id) =>
    client.get(`/api/notices/${id}`).then((r) => r.data),

  markAsRead: (id) =>
    client.post(`/api/customer/notices/${id}/read`).then((r) => r.data),

  fetchCategories: () =>
    client.get('/api/notice-categories').then((r) => r.data),
};
