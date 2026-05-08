<template>
  <div style="padding: 24px 24px 0 24px">
    <a-page-header title="バッチ管理" sub-title="Amazia Console" style="padding-bottom: 0" />
  </div>

  <a-tabs
    v-model:active-key="activeTab"
    type="card"
    style="padding: 0 24px"
    @change="onTabChange"
  >
    <a-tab-pane key="executions" tab="実行履歴">
      <BatchExecutionList :embedded="true" />
    </a-tab-pane>
    <a-tab-pane v-if="isAdmin" key="manual" tab="手動起動">
      <BatchManualPage :embedded="true" />
    </a-tab-pane>
  </a-tabs>
</template>

<script setup>
import { ref, computed, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { authStore } from '../../../stores/authStore.js';
import BatchExecutionList from './BatchExecutionList.vue';
import BatchManualPage from './BatchManualPage.vue';

const route = useRoute();
const router = useRouter();

const isAdmin = computed(() => authStore.isAdmin);

const VALID_TABS = ['executions', 'manual'];

function resolveInitialTab() {
  const q = route.query.tab;
  if (typeof q === 'string' && VALID_TABS.includes(q)) {
    if (q === 'manual' && !isAdmin.value) return 'executions';
    return q;
  }
  return 'executions';
}

const activeTab = ref(resolveInitialTab());

function onTabChange(key) {
  router.replace({ path: route.path, query: { ...route.query, tab: key } });
}

// URL クエリを直接書き換えた時の同期
watch(
  () => route.query.tab,
  (q) => {
    if (typeof q === 'string' && VALID_TABS.includes(q) && q !== activeTab.value) {
      if (q === 'manual' && !isAdmin.value) return;
      activeTab.value = q;
    }
  }
);
</script>
