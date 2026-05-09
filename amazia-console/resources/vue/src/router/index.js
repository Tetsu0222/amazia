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
import ListUserPage               from '../features/users/pages/ListUserPage.vue';
import CreateUserPage             from '../features/users/pages/CreateUserPage.vue';
import EditUserPage               from '../features/users/pages/EditUserPage.vue';
import WorkflowList               from '../features/workflows/pages/WorkflowList.vue';
import WorkflowDetail             from '../features/workflows/pages/WorkflowDetail.vue';
import WorkflowRequestForm        from '../features/workflows/pages/WorkflowRequestForm.vue';
import SalesList                  from '../features/sales/pages/SalesList.vue';
import PreorderList               from '../features/preorder/pages/PreorderList.vue';
import SalesReturnList            from '../features/salesReturn/pages/SalesReturnList.vue';
import OperationLogList           from '../features/operationLog/pages/OperationLogList.vue';
import DeliveryList               from '../features/delivery/pages/DeliveryList.vue';
import DeliveryDetail             from '../features/delivery/pages/DeliveryDetail.vue';
import InboundList                from '../features/inbound/pages/InboundList.vue';
import InboundCreate              from '../features/inbound/pages/InboundCreate.vue';
import InboundStockImport         from '../features/inbound/pages/InboundStockImport.vue';
import BatchManagementPage        from '../features/batch/pages/BatchManagementPage.vue';
import BatchNotificationList      from '../features/batch/pages/BatchNotificationList.vue';
import InquiryList                from '../features/inquiry/pages/InquiryList.vue';
import InquiryDetail              from '../features/inquiry/pages/InquiryDetail.vue';
import NoticeList                 from '../features/notice/pages/NoticeList.vue';
import NoticeForm                 from '../features/notice/pages/NoticeForm.vue';

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

  { path: '/users',                    component: ListUserPage,  meta: { requiresAuth: true, roles: ['admin', 'senior_admin', 'eternal_advisor'] } },
  { path: '/users/new',                component: CreateUserPage, meta: { requiresAuth: true, roles: ['admin', 'senior_admin', 'eternal_advisor'] } },
  { path: '/users/:id/edit',           component: EditUserPage,  meta: { requiresAuth: true, roles: ['admin', 'senior_admin', 'eternal_advisor'] } },

  { path: '/workflows',                component: WorkflowList,        meta: { requiresAuth: true } },
  { path: '/workflows/new',            component: WorkflowRequestForm, meta: { requiresAuth: true } },
  { path: '/workflows/:id',            component: WorkflowDetail,      meta: { requiresAuth: true } },

  { path: '/sales',                    component: SalesList,           meta: { requiresAuth: true } },
  { path: '/preorders',                component: PreorderList,        meta: { requiresAuth: true } },
  { path: '/sales-returns',            component: SalesReturnList,     meta: { requiresAuth: true } },
  { path: '/operation-logs',           component: OperationLogList,    meta: { requiresAuth: true } },

  // フェーズ15 配送管理 / 入荷管理
  // 静的ルート（/inbound/create）を動的ルート（/delivery/:id）より先に並べる（test_insights カテゴリ2）
  { path: '/inbound',                  component: InboundList,         meta: { requiresAuth: true } },
  { path: '/inbound/create',           component: InboundCreate,       meta: { requiresAuth: true } },
  { path: '/inbound/import',           component: InboundStockImport,  meta: { requiresAuth: true } },
  { path: '/delivery',                 component: DeliveryList,        meta: { requiresAuth: true } },
  { path: '/delivery/:id',             component: DeliveryDetail,      meta: { requiresAuth: true } },

  // フェーズ17 バッチ管理（実行履歴・手動起動はタブ切り替え。手動タブは admin 相当のみ表示）
  { path: '/batch',                    component: BatchManagementPage,   meta: { requiresAuth: true } },
  { path: '/batch/executions',         redirect: { path: '/batch', query: { tab: 'executions' } } },
  { path: '/batch/manual',             redirect: { path: '/batch', query: { tab: 'manual' } } },
  { path: '/batch/notifications',      component: BatchNotificationList, meta: { requiresAuth: true } },

  // フェーズ18 問い合わせ管理
  { path: '/inquiries',                component: InquiryList,           meta: { requiresAuth: true } },
  { path: '/inquiries/:id',            component: InquiryDetail,         meta: { requiresAuth: true } },

  // フェーズ19 お知らせ管理（静的ルートを動的ルートより先に並べる / test_insights カテゴリ2）
  { path: '/notices',                  component: NoticeList,            meta: { requiresAuth: true } },
  { path: '/notices/create',           component: NoticeForm,            meta: { requiresAuth: true } },
  { path: '/notices/:id/edit',         component: NoticeForm,            meta: { requiresAuth: true } },
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
