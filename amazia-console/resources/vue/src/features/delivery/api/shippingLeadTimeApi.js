import api from '../../auth/api/authApi.js';

/**
 * Console 都道府県別リードタイムマスタ API クライアント（フェーズX-5）。
 *
 * Console (Laravel) の /api/shipping-lead-times 系を呼び出す。
 * Laravel 側で Core の /api/shipping-lead-times 系へ中継される。
 */

export function listShippingLeadTimes(params = {}) {
  return api.get('/shipping-lead-times', { params });
}

export function getShippingLeadTime(id) {
  return api.get(`/shipping-lead-times/${id}`);
}

export function updateShippingLeadTime(id, payload) {
  return api.patch(`/shipping-lead-times/${id}`, payload);
}
