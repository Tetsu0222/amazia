import api from '../../auth/api/authApi.js';

/**
 * Console 入荷管理 API クライアント。
 *
 * Console (Laravel) の /api/inbounds を呼び出す。
 * Laravel 側で Core の /api/inbounds へ中継される。
 *
 * RRRR-5：warehouseId はリクエストに含めない（Console / Core 側でデフォルト倉庫を自動セット）。
 */

export function listInbounds(params = {}) {
  return api.get('/inbounds', { params });
}

export function registerInbound(payload) {
  return api.post('/inbounds', payload);
}
