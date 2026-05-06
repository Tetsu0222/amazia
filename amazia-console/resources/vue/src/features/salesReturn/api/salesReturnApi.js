import api from '../../auth/api/authApi.js';

/**
 * Console 返品管理 API クライアント。
 *
 * Console (Laravel) の /api/sales-returns 各エンドポイントを呼び出す。
 * Laravel 側で Core /api/sales-returns へ中継される。
 */

export function listSalesReturns() {
  return api.get('/sales-returns');
}

export function approveSalesReturn(id) {
  return api.post(`/sales-returns/${id}/approve`);
}

export function rejectSalesReturn(id) {
  return api.post(`/sales-returns/${id}/reject`);
}

export function refundSalesReturn(id) {
  return api.post(`/sales-returns/${id}/refund`);
}
