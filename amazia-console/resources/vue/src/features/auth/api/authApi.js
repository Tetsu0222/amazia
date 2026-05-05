import axios from 'axios';
import { authStore } from '../../../stores/authStore.js';

// axios.create() を使わず axios 本体を使うことで defaults.headers.common が確実に反映される
// baseURL はリクエスト時に手動で付加する

// Vite の base（'/' or '/console/'）に追従。BASE_URL は末尾 '/' で来るので
// 'api' を連結すると '/api' or '/console/api' になる。
const BASE = `${import.meta.env.BASE_URL}api`;

function req(method, path, data, config) {
  const token = localStorage.getItem('accessToken');
  const headers = {
    ...(config?.headers || {}),
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
  };
  return axios({ method, url: BASE + path, data, ...config, headers })
    .catch(async (err) => {
      if (err.response?.status === 401 && !err.config._retry) {
        err.config._retry = true;
        try {
          const res = await axios.post(BASE + '/auth/refresh', {}, { withCredentials: true });
          const newToken = res.data.accessToken;
          authStore.setAuth(newToken, authStore.role);
          return axios({ ...err.config, headers: { ...err.config.headers, Authorization: `Bearer ${newToken}` } });
        } catch {
          authStore.clear();
          window.location.href = `${import.meta.env.BASE_URL}login`;
        }
      }
      return Promise.reject(err);
    });
}

const api = {
  get: (path, config) => req('get', path, undefined, config),
  post: (path, data, config) => req('post', path, data, config),
  put: (path, data, config) => req('put', path, data, config),
  patch: (path, data, config) => req('patch', path, data, config),
  delete: (path, config) => req('delete', path, undefined, config),
};

export function login(email, password) {
  return axios.post(BASE + '/auth/login', { email, password });
}

export function requestPasswordReset(email) {
  return axios.post(BASE + '/auth/password/reset/request', { email });
}

export function confirmPasswordReset(token, newPassword) {
  return axios.post(BASE + '/auth/password/reset/confirm', { token, newPassword });
}

export default api;
