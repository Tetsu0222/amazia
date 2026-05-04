import axios from 'axios';

const client = axios.create({
  baseURL: '/api',
});

export const getProductSkus   = (productId) => client.get(`/products/${productId}/skus`).then(r => r.data);
export const getSkuPrices     = (skuId) => client.get(`/skus/${skuId}/prices`).then(r => r.data);
export const createSkuPrice   = (skuId, data) => client.post(`/skus/${skuId}/prices`, data).then(r => r.data);
