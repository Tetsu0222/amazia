import api from '../../auth/api/authApi.js';

export const getProducts        = () => api.get('/products').then(r => r.data);
export const getAdminProducts   = () => api.get('/admin/products').then(r => r.data);
export const getMarketProducts  = () => api.get('/products/market').then(r => r.data);
export const getProduct         = (id) => api.get(`/products/${id}`).then(r => r.data);
export const createProduct      = (data) => api.post('/products', data).then(r => r.data);
export const updateProduct      = (id, data) => api.put(`/products/${id}`, data).then(r => r.data);
export const deleteProduct      = (id) => api.delete(`/products/${id}`);
export const bulkDeleteProducts = (ids) => api.delete('/products', { data: { ids: ids.join(',') } });
export const bulkUpdateStock    = (updates) => api.patch('/products/bulk-stock', updates).then(r => r.data);
export const getProductStatuses = () => api.get('/product-statuses').then(r => r.data);

export const getProductImages   = (id) => api.get(`/products/${id}/images`).then(r => r.data);
export const uploadProductImage = (id, file) => {
  const form = new FormData();
  form.append('image', file);
  return api.post(`/products/${id}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const deleteProductImage = (imageId) => api.delete(`/product-images/${imageId}`);
