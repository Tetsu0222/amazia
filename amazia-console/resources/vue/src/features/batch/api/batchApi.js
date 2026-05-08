import api from '../../auth/api/authApi.js';

/**
 * Console バッチ管理 API クライアント（フェーズ17 Step 6）。
 * Laravel 側で Core /api/console/batch/* へ中継される。
 */

function clean(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
  );
}

export function listBatchExecutions(params = {}) {
  return api.get('/console/batch/executions', { params: clean(params) });
}

export function getBatchExecution(id) {
  return api.get(`/console/batch/executions/${id}`);
}

export function listBatchNotifications(params = {}) {
  return api.get('/console/batch/notifications', { params: clean(params) });
}

export function markBatchNotificationRead(id) {
  return api.put(`/console/batch/notifications/${id}/read`, {});
}

export function triggerBatchManual(jobName) {
  return api.post(`/console/batch/${jobName}/run`, {});
}
