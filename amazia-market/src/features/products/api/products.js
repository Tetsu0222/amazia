import axios from 'axios';

const client = axios.create({
  baseURL: '/api',
});

export const getProducts = () => client.get('/products').then(r => r.data);
export const getProduct = (id) => client.get(`/products/${id}`).then(r => r.data);

// SKU集約API（Market向け）
export const getMarketProducts = () => client.get('/products/market').then(r => r.data);
export const getMarketProduct = (id) => client.get(`/products/${id}/market`).then(r => r.data);
