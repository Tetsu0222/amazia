import api from '../../auth/api/authApi.js';

/**
 * Console 配送管理 API クライアント。
 *
 * Console (Laravel) の /api/deliveries 系を呼び出す。
 * Laravel 側で Core の /api/deliveries 系へ中継される。
 */

export function listDeliveries(params = {}) {
  return api.get('/deliveries', { params });
}

export function getDelivery(id) {
  return api.get(`/deliveries/${id}`);
}

export function updateShippingStatus(id, payload) {
  return api.patch(`/deliveries/${id}/status`, payload);
}

export function updateShippingAddress(id, payload) {
  return api.patch(`/deliveries/${id}/address`, payload);
}

export function updateScheduledDate(id, payload) {
  return api.patch(`/deliveries/${id}/scheduled-date`, payload);
}

export function registerTrackingCode(id, payload) {
  return api.patch(`/deliveries/${id}/tracking-code`, payload);
}

export function listShippingMethods() {
  return api.get('/shipping-methods');
}
