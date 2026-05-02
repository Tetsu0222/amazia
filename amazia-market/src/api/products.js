import axios from 'axios';

const client = axios.create({
  baseURL: 'http://localhost:8080/api',
});

export const getProducts = () => client.get('/products').then(r => r.data);
export const getProduct = (id) => client.get(`/products/${id}`).then(r => r.data);
