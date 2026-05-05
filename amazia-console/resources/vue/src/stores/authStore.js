import { reactive } from 'vue';
import axios from 'axios';

const state = reactive({
  accessToken: localStorage.getItem('accessToken') ?? null,
  role: localStorage.getItem('role') ?? null,
});

if (state.accessToken) {
  axios.defaults.headers.common['Authorization'] = `Bearer ${state.accessToken}`;
}

export const authStore = {
  get accessToken() { return state.accessToken; },
  get role() { return state.role; },
  get isLoggedIn() { return !!state.accessToken; },

  setAuth(accessToken, role) {
    state.accessToken = accessToken;
    state.role = role;
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('role', role);
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  },

  clear() {
    state.accessToken = null;
    state.role = null;
    localStorage.removeItem('accessToken');
    localStorage.removeItem('role');
    delete axios.defaults.headers.common['Authorization'];
  },
};
