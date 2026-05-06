import axios from 'axios';

// MARKET_SESSION_ID Cookie をやり取りするため withCredentials: true 必須。
// CSRF トークンはログインレスポンスから取得し、setCsrfToken() で注入する。
const client = axios.create({
  baseURL: '/api/customer',
  withCredentials: true,
});

let csrfToken = null;

export function setCsrfToken(token) {
  csrfToken = token ?? null;
}

export function getCsrfToken() {
  return csrfToken;
}

client.interceptors.request.use((config) => {
  const method = (config.method ?? 'get').toUpperCase();
  if (csrfToken && method !== 'GET' && method !== 'HEAD' && method !== 'OPTIONS') {
    config.headers = config.headers ?? {};
    config.headers['X-CSRF-Token'] = csrfToken;
  }
  return config;
});

export const registerCustomer = (payload) =>
  client.post('/register', payload).then((r) => r.data);

export const loginCustomer = (payload) =>
  client.post('/login', payload).then((r) => r.data);

export const logoutCustomer = () =>
  client.post('/logout').then((r) => r.data);

export const getMyPage = () => client.get('/me').then((r) => r.data);

export const checkEmailAvailability = (email) =>
  client.get('/email-availability', { params: { email } }).then((r) => r.data);

export const searchPostalAddresses = (postalCode) =>
  client.get('/postal-addresses', { params: { postal_code: postalCode } }).then((r) => r.data);

export const requestPasswordReset = (email) =>
  client.post('/password/reset', { email }).then((r) => r.data);

export const confirmPasswordReset = (token, newPassword) =>
  client.post('/password/reset/confirm', { token, newPassword }).then((r) => r.data);

export const fetchCsrfToken = () =>
  client.get('/csrf-token').then((r) => r.data);
