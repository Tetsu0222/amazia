<template>
  <a-config-provider :theme="{ token: { colorPrimary: '#1677ff' } }">
    <template v-if="isPublicRoute">
      <router-view />
    </template>
    <a-layout v-else style="min-height: 100vh">
      <a-layout-sider width="200" theme="light" style="border-right: 1px solid #f0f0f0">
        <div style="padding: 16px 20px; font-weight: bold; font-size: 15px; border-bottom: 1px solid #f0f0f0">
          Amazia Console
        </div>
        <a-menu
          mode="inline"
          :selected-keys="[currentPath]"
          style="border-right: none"
          @click="onMenuClick"
        >
          <a-menu-item key="/">商品マスタ</a-menu-item>
          <a-menu-item key="/skus">SKU管理</a-menu-item>
          <a-menu-item key="/products/market-view">商品一覧（SKU集約版）</a-menu-item>
          <a-menu-item key="/sales">売上管理</a-menu-item>
          <a-menu-item key="/sales-returns">返品管理</a-menu-item>
          <a-menu-item key="/operation-logs">操作履歴</a-menu-item>
          <a-menu-item key="/workflows">ワークフロー</a-menu-item>
          <a-menu-item v-if="isAdmin" key="/users">社員管理</a-menu-item>
          <a-menu-divider />
          <a-menu-item key="__logout">ログアウト</a-menu-item>
        </a-menu>
      </a-layout-sider>
      <a-layout>
        <a-layout-content style="padding: 0">
          <router-view />
        </a-layout-content>
      </a-layout>
    </a-layout>
  </a-config-provider>
</template>

<script setup>
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { authStore } from './stores/authStore.js';

const route  = useRoute();
const router = useRouter();

const PUBLIC_PATHS = ['/login', '/password/reset', '/password/reset/confirm'];
const isPublicRoute = computed(() => PUBLIC_PATHS.some(p => route.path.startsWith(p)));
const currentPath   = computed(() => route.path);
const isAdmin       = computed(() => authStore.isAdmin);

function onMenuClick({ key }) {
  if (key === '__logout') {
    logout();
    return;
  }
  router.push(key);
}

function logout() {
  authStore.clear();
  router.push('/login');
}
</script>
