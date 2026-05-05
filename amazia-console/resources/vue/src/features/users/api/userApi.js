import api from '../../auth/api/authApi.js';

export function listUsers() {
  return api.get('/users');
}

export function createUser(data) {
  return api.post('/users', data);
}

export function updateUser(id, data) {
  return api.put(`/users/${id}`, data);
}
