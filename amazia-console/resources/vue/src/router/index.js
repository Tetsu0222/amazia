import { createRouter, createWebHistory } from 'vue-router';
import { authStore } from '../stores/authStore.js';

import LoginPage                  from '../features/auth/pages/LoginPage.vue';
import PasswordResetRequestPage   from '../features/auth/pages/PasswordResetRequestPage.vue';
import PasswordResetConfirmPage   from '../features/auth/pages/PasswordResetConfirmPage.vue';
import ProductList                from '../features/products/pages/ProductList.vue';
import ProductForm                from '../features/products/pages/ProductForm.vue';
import ProductImport              from '../features/products/pages/ProductImport.vue';
import ProductMarketList          from '../features/products/pages/ProductMarketList.vue';
import SkuList                    from '../features/skus/pages/SkuList.vue';
import SkuStockList               from '../features/skus/pages/SkuStockList.vue';
import SkuStockImport             from '../features/skus/pages/SkuStockImport.vue';
import ListUserPage               from '../features/users/pages/ListUserPage.vue';
import CreateUserPage             from '../features/users/pages/CreateUserPage.vue';
import EditUserPage               from '../features/users/pages/EditUserPage.vue';
import WorkflowList               from '../features/workflows/pages/WorkflowList.vue';
import WorkflowDetail             from '../features/workflows/pages/WorkflowDetail.vue';
import WorkflowRequestForm        from '../features/workflows/pages/WorkflowRequestForm.vue';
import SalesList                  from '../features/sales/pages/SalesList.vue';
import SalesReturnList            from '../features/salesReturn/pages/SalesReturnList.vue';
import OperationLogList           from '../features/operationLog/pages/OperationLogList.vue';
import DeliveryList               from '../features/delivery/pages/DeliveryList.vue';
import DeliveryDetail             from '../features/delivery/pages/DeliveryDetail.vue';
import InboundList                from '../features/inbound/pages/InboundList.vue';
import InboundCreate              from '../features/inbound/pages/InboundCreate.vue';

const routes = [
  { path: '/login',                    component: LoginPage,                meta: { public: true } },
  { path: '/password/reset',           component: PasswordResetRequestPage, meta: { public: true } },
  { path: '/password/reset/confirm',   component: PasswordResetConfirmPage, meta: { public: true } },

  { path: '/',                         component: ProductList,   meta: { requiresAuth: true } },
  { path: '/products/new',             component: ProductForm,   meta: { requiresAuth: true } },
  { path: '/products/:id/edit',        component: ProductForm,   meta: { requiresAuth: true } },
  { path: '/products/import',          component: ProductImport, meta: { requiresAuth: true } },
  { path: '/products/market-view',     component: ProductMarketList, meta: { requiresAuth: true } },
  { path: '/skus',                     component: SkuList,       meta: { requiresAuth: true } },
  { path: '/skus/stocks',              component: SkuStockList,  meta: { requiresAuth: true } },
  { path: '/skus/stocks/import',       component: SkuStockImport, meta: { requiresAuth: true } },

  { path: '/users',                    component: ListUserPage,  meta: { requiresAuth: true, roles: ['admin', 'senior_admin', 'eternal_advisor'] } },
  { path: '/users/new',                component: CreateUserPage, meta: { requiresAuth: true, roles: ['admin', 'senior_admin', 'eternal_advisor'] } },
  { path: '/users/:id/edit',           component: EditUserPage,  meta: { requiresAuth: true, roles: ['admin', 'senior_admin', 'eternal_advisor'] } },

  { path: '/workflows',                component: WorkflowList,        meta: { requiresAuth: true } },
  { path: '/workflows/new',            component: WorkflowRequestForm, meta: { requiresAuth: true } },
  { path: '/workflows/:id',            component: WorkflowDetail,      meta: { requiresAuth: true } },

  { path: '/sales',                    component: SalesList,           meta: { requiresAuth: true } },
  { path: '/sales-returns',            component: SalesReturnList,     meta: { requiresAuth: true } },
  { path: '/operation-logs',           component: OperationLogList,    meta: { requiresAuth: true } },

  // フェーズ15 配送管理 / 入荷管理
  // 静的ルート（/inbound/create）を動的ルート（/delivery/:id）より先に並べる（test_insights カテゴリ2）
  { path: '/inbound',                  component: InboundList,         meta: { requiresAuth: true } },
  { path: '/inbound/create',           component: InboundCreate,       meta: { requiresAuth: true } },
  { path: '/delivery',                 component: DeliveryList,        meta: { requiresAuth: true } },
  { path: '/delivery/:id',             component: DeliveryDetail,      meta: { requiresAuth: true } },
];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
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

  if (to.meta.roles && !to.meta.roles.includes(authStore.role)) {
    return false;
  }

  return true;
});

export default router;
