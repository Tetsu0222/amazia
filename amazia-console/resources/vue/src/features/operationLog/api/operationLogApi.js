import api from '../../auth/api/authApi.js';

/**
 * Console 操作履歴 API クライアント。
 *
 * Console (Laravel) の GET /api/operation-logs を呼び出す。
 * Laravel 側で Core の GET /api/operation-logs へ中継される。
 *
 * @param {{screenName?: string, apiName?: string, action?: string}} params
 */
export function listOperationLogs(params = {}) {
  const cleaned = Object.fromEntries(
    Object.entries(params).filter(([, v]) => v !== null && v !== undefined && v !== '')
  );
  return api.get('/operation-logs', { params: cleaned });
}
