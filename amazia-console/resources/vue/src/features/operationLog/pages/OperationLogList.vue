<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="操作履歴" sub-title="Amazia Console" />

    <a-form layout="inline" style="margin-bottom: 16px" @submit.prevent="fetchList">
      <a-form-item label="画面名">
        <a-select
          v-model:value="screenNameInput"
          placeholder="画面名を選択"
          style="width: 240px"
          allow-clear
          show-search
          option-filter-prop="label"
          :options="screenNameOptions"
        />
      </a-form-item>
      <a-form-item label="API名">
        <a-select
          v-model:value="apiNameInput"
          placeholder="API名を選択"
          style="width: 240px"
          allow-clear
          show-search
          option-filter-prop="label"
          :options="apiNameOptions"
        />
      </a-form-item>
      <a-form-item label="操作">
        <a-select
          v-model:value="actionInput"
          placeholder="操作を選択"
          style="width: 240px"
          allow-clear
          show-search
          option-filter-prop="label"
          :options="actionOptions"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit" :loading="loading">検索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="resetFilters" :disabled="loading">クリア</a-button>
      </a-form-item>
    </a-form>

    <a-table
      :dataSource="logs"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="{ pageSize: 50 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createdAt'">
          {{ formatDateTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'comment'">
          <span :title="record.comment">{{ record.comment || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'action'">
          <span :title="record.action">{{ labelOr(ACTION_LABELS, record.action) || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'target'">
          <span v-if="record.targetType" :title="`${record.targetType}${record.targetId ? ':' + record.targetId : ''}`">
            {{ labelOr(TARGET_TYPE_LABELS, record.targetType) }}<span v-if="record.targetId">:{{ record.targetId }}</span>
          </span>
          <span v-else>—</span>
        </template>
        <template v-else-if="column.key === 'screenName'">
          <span :title="record.screenName">{{ labelOr(SCREEN_NAME_LABELS, record.screenName) || '—' }}</span>
        </template>
        <template v-else-if="column.key === 'apiName'">
          <span :title="record.apiName">{{ labelOr(API_NAME_LABELS, record.apiName) || '—' }}</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue';
import { message } from 'ant-design-vue';
import { listOperationLogs } from '../api/operationLogApi.js';
import {
  ACTION_LABELS,
  TARGET_TYPE_LABELS,
  SCREEN_NAME_LABELS,
  API_NAME_LABELS,
  labelOr,
} from '../config/labels.js';

const logs = ref([]);
const loading = ref(false);

const screenNameInput = ref(undefined);
const apiNameInput = ref(undefined);
const actionInput = ref(undefined);

const toOptions = (map) =>
  Object.entries(map).map(([value, label]) => ({ value, label }));

const screenNameOptions = computed(() => toOptions(SCREEN_NAME_LABELS));
const apiNameOptions = computed(() => toOptions(API_NAME_LABELS));
const actionOptions = computed(() => toOptions(ACTION_LABELS));

const columns = [
  { title: '操作日時', key: 'createdAt',  width: 160 },
  { title: '操作者',   dataIndex: 'userName',   key: 'userName' },
  { title: '操作',     dataIndex: 'action',     key: 'action' },
  { title: '対象',     key: 'target', width: 160 },
  { title: '画面名',   dataIndex: 'screenName', key: 'screenName' },
  { title: 'API名',    dataIndex: 'apiName',    key: 'apiName' },
  { title: 'コメント', key: 'comment', ellipsis: true, width: 200 },
];

function formatDateTime(value) {
  if (!value) return '—';
  return value.replace('T', ' ').slice(0, 19);
}

async function fetchList() {
  loading.value = true;
  try {
    const res = await listOperationLogs({
      screenName: screenNameInput.value,
      apiName: apiNameInput.value,
      action: actionInput.value,
    });
    logs.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('操作履歴の取得に失敗しました');
    logs.value = [];
  } finally {
    loading.value = false;
  }
}

function resetFilters() {
  screenNameInput.value = undefined;
  apiNameInput.value = undefined;
  actionInput.value = undefined;
  fetchList();
}

onMounted(fetchList);
</script>
