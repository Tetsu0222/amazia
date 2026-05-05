import { reactive } from 'vue';
import axios from 'axios';

const APPROVER_ROLES = ['supervisor', 'admin', 'senior_admin', 'eternal_advisor'];
const ADMIN_ROLES    = ['admin', 'senior_admin', 'eternal_advisor'];

const state = reactive({
  accessToken: localStorage.getItem('accessToken') ?? null,
  role: localStorage.getItem('role') ?? null,
  userId: localStorage.getItem('userId') ?? null,
});

if (state.accessToken) {
  axios.defaults.headers.common['Authorization'] = `Bearer ${state.accessToken}`;
}

export const authStore = {
  get accessToken() { return state.accessToken; },
  get role()        { return state.role; },
  get userId()      { return state.userId; },
  get isLoggedIn()  { return !!state.accessToken; },

  get isApprover()  { return APPROVER_ROLES.includes(state.role); },
  get isAdmin()     { return ADMIN_ROLES.includes(state.role); },
  get isUser()      { return state.role === 'user'; },

  hasRole(roles) {
    if (!state.role) return false;
    return Array.isArray(roles) ? roles.includes(state.role) : roles === state.role;
  },

  setAuth(accessToken, role, userId = null) {
    state.accessToken = accessToken;
    state.role        = role;
    state.userId      = userId;
    localStorage.setItem('accessToken', accessToken);
    localStorage.setItem('role', role);
    if (userId !== null) localStorage.setItem('userId', userId);
    axios.defaults.headers.common['Authorization'] = `Bearer ${accessToken}`;
  },

  clear() {
    state.accessToken = null;
    state.role        = null;
    state.userId      = null;
    localStorage.removeItem('accessToken');
    localStorage.removeItem('role');
    localStorage.removeItem('userId');
    delete axios.defaults.headers.common['Authorization'];
  },
};
