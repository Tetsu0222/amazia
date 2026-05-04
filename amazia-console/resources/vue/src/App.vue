<template>
  <a-config-provider :theme="{ token: { colorPrimary: '#1677ff' } }">
    <a-layout style="min-height: 100vh">
      <a-layout-sider width="200" theme="light" style="border-right: 1px solid #f0f0f0">
        <div style="padding: 16px 20px; font-weight: bold; font-size: 15px; border-bottom: 1px solid #f0f0f0">
          Amazia Console
        </div>
        <a-menu
          mode="inline"
          :selected-keys="[currentPath]"
          style="border-right: none"
          @click="({ key }) => $router.push(key)"
        >
          <a-menu-item key="/">商品マスタ</a-menu-item>
          <a-menu-item key="/skus">SKU管理</a-menu-item>
          <a-menu-item key="/sku-prices">SKU価格管理</a-menu-item>
          <a-menu-item key="/sku-stocks">SKU在庫管理</a-menu-item>
          <a-menu-item key="/sku-images">SKU画像管理</a-menu-item>
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
import { useRoute } from 'vue-router';

const route = useRoute();
const currentPath = computed(() => route.path);
</script>
