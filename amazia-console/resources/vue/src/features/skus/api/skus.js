import axios from 'axios';

const client = axios.create({
  baseURL: '/api',
});

export const getProductSkus   = (productId) => client.get(`/products/${productId}/skus`).then(r => r.data);
export const createProductSku = (productId, data) => client.post(`/products/${productId}/skus`, data).then(r => r.data);

export const getSkuPrices     = (skuId) => client.get(`/skus/${skuId}/prices`).then(r => r.data);
export const createSkuPrice   = (skuId, data) => client.post(`/skus/${skuId}/prices`, data).then(r => r.data);

export const getSkuStock      = (skuId) => client.get(`/skus/${skuId}/stocks`).then(r => r.data);
export const receiveSkuStock  = (skuId, data) => client.post(`/skus/${skuId}/stocks/receive`, data).then(r => r.data);
export const getSkuStockHistory = (skuId) => client.get(`/skus/${skuId}/stocks/history`).then(r => r.data);

export const getSkuImages     = (skuId) => client.get(`/skus/${skuId}/images`).then(r => r.data);
export const uploadSkuImage   = (skuId, file) => {
  const form = new FormData();
  form.append('image', file);
  return client.post(`/skus/${skuId}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  }).then(r => r.data);
};
