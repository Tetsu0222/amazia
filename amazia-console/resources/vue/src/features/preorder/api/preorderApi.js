import api from '../../auth/api/authApi.js';

/**
 * Console 予約管理 API クライアント。
 *
 * Console (Laravel) の GET /api/preorders を呼び出す。
 * Laravel 側で Core の GET /api/products/preorders へ中継される。
 */
export function listPreorders() {
  return api.get('/preorders');
}
