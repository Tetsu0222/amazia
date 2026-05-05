import api from '../../auth/api/authApi.js';

export const getProductSkus     = (productId) => api.get(`/products/${productId}/skus`).then(r => r.data);
export const createProductSku   = (productId, data) => api.post(`/products/${productId}/skus`, data).then(r => r.data);

export const getSkuPrices       = (skuId) => api.get(`/skus/${skuId}/prices`).then(r => r.data);
export const createSkuPrice     = (skuId, data) => api.post(`/skus/${skuId}/prices`, data).then(r => r.data);

export const getSkuStock        = (skuId) => api.get(`/skus/${skuId}/stocks`).then(r => r.data);
export const receiveSkuStock    = (skuId, data) => api.post(`/skus/${skuId}/stocks/receive`, data).then(r => r.data);
export const getSkuStockHistory = (skuId) => api.get(`/skus/${skuId}/stocks/history`).then(r => r.data);

export const getSkuImages       = (skuId) => api.get(`/skus/${skuId}/images`).then(r => r.data);
export const uploadSkuImage     = (skuId, file) => {
  const form = new FormData();
  form.append('image', file);
  return api.post(`/skus/${skuId}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data);
};
