import api from '../../auth/api/authApi.js';

/**
 * Console お知らせ API クライアント（フェーズ19）。
 * Laravel 側で Core /api/notices / /api/notice-categories へ中継される。
 */

function clean(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
  );
}

export function listNotices(params = {}) {
  return api.get('/admin/notices', { params: clean(params) });
}

export function getNotice(id, params = {}) {
  return api.get(`/admin/notices/${id}`, { params: clean(params) });
}

export function createNotice(payload) {
  return api.post('/admin/notices', payload);
}

export function updateNotice(id, payload) {
  return api.put(`/admin/notices/${id}`, payload);
}

export function deleteNotice(id) {
  return api.delete(`/admin/notices/${id}`);
}

export function listNoticeCategories() {
  return api.get('/notice-categories');
}
