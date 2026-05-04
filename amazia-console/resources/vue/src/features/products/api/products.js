import axios from 'axios';

const client = axios.create({
  baseURL: '/api',
});

export const getProducts      = () => client.get('/products').then(r => r.data);
export const getAdminProducts = () => client.get('/admin/products').then(r => r.data);
export const getProduct       = (id) => client.get(`/products/${id}`).then(r => r.data);
export const createProduct    = (data) => client.post('/products', data).then(r => r.data);
export const updateProduct    = (id, data) => client.put(`/products/${id}`, data).then(r => r.data);
export const deleteProduct    = (id) => client.delete(`/products/${id}`);
export const bulkDeleteProducts = (ids) => client.delete('/products', { data: { ids: ids.join(',') } });
export const bulkUpdateStock    = (updates) => client.patch('/products/bulk-stock', updates).then(r => r.data);
export const getProductStatuses = () => client.get('/product-statuses').then(r => r.data);

export const getProductImages    = (id) => client.get(`/products/${id}/images`).then(r => r.data);
export const uploadProductImage  = (id, file) => {
  const form = new FormData();
  form.append('image', file);
  return client.post(`/products/${id}/images`, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};
export const deleteProductImage  = (imageId) => client.delete(`/product-images/${imageId}`);
