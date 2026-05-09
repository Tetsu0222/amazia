import api from '../../auth/api/authApi.js';

/**
 * Console 問い合わせ API クライアント（フェーズ18）。
 * Laravel 側で Core /api/console/inquiries/* へ中継される。
 */

function clean(params) {
  return Object.fromEntries(
    Object.entries(params || {}).filter(([, v]) => v !== null && v !== undefined && v !== '')
  );
}

export function getUnreadInquiryCount() {
  return api.get('/console/inquiries/unread-count');
}

export function listInquiries(params = {}) {
  return api.get('/console/inquiries', { params: clean(params) });
}

export function getInquiry(id) {
  return api.get(`/console/inquiries/${id}`);
}

export function replyInquiry(id, payload) {
  return api.post(`/console/inquiries/${id}/messages`, payload);
}

export function updateInquiryStatus(id, payload) {
  return api.patch(`/console/inquiries/${id}/status`, payload);
}
