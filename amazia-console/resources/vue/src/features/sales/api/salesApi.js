import api from '../../auth/api/authApi.js';

/**
 * Console 売上管理 API クライアント。
 *
 * Console (Laravel) の GET /api/sales を呼び出す。
 * Laravel 側で Core の GET /api/sales へ中継される。
 */
export function listSales() {
  return api.get('/sales');
}
