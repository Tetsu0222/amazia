import { createRouter, createWebHistory } from 'vue-router';
import { authStore } from '../stores/authStore.js';

import LoginPage                  from '../features/auth/pages/LoginPage.vue';
import PasswordResetRequestPage   from '../features/auth/pages/PasswordResetRequestPage.vue';
import PasswordResetConfirmPage   from '../features/auth/pages/PasswordResetConfirmPage.vue';
import ProductList                from '../features/products/pages/ProductList.vue';
import ProductForm                from '../features/products/pages/ProductForm.vue';
import ProductImport              from '../features/products/pages/ProductImport.vue';
import SkuList                    from '../features/skus/pages/SkuList.vue';
import ListUserPage               from '../features/users/pages/ListUserPage.vue';
import CreateUserPage             from '../features/users/pages/CreateUserPage.vue';
import EditUserPage               from '../features/users/pages/EditUserPage.vue';

const routes = [
  { path: '/login',                    component: LoginPage,                meta: { public: true } },
  { path: '/password/reset',           component: PasswordResetRequestPage, meta: { public: true } },
  { path: '/password/reset/confirm',   component: PasswordResetConfirmPage, meta: { public: true } },

  { path: '/',                         component: ProductList,   meta: { requiresAuth: true } },
  { path: '/products/new',             component: ProductForm,   meta: { requiresAuth: true } },
  { path: '/products/:id/edit',        component: ProductForm,   meta: { requiresAuth: true } },
  { path: '/products/import',          component: ProductImport, meta: { requiresAuth: true } },
  { path: '/skus',                     component: SkuList,       meta: { requiresAuth: true } },

  { path: '/users',                    component: ListUserPage,  meta: { requiresAuth: true, role: 'admin' } },
  { path: '/users/new',                component: CreateUserPage, meta: { requiresAuth: true, role: 'admin' } },
  { path: '/users/:id/edit',           component: EditUserPage,  meta: { requiresAuth: true, role: 'admin' } },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach((to) => {
  if (to.meta.public) return true;

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return '/login';
  }

  if (to.meta.role && authStore.role !== to.meta.role) {
    return false;
  }

  return true;
});

export default router;
