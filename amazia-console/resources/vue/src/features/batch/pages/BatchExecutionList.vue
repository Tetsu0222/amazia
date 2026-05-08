<template>
  <div style="padding: 24px; max-width: 1400px">
    <a-page-header title="バッチ実行履歴" sub-title="Amazia Console" />

    <a-form layout="inline" style="margin-bottom: 16px" @submit.prevent="reload(0)">
      <a-form-item label="ジョブ名">
        <a-input
          v-model:value="jobNameInput"
          placeholder="例: InventoryConsistencyCheckJob"
          style="width: 280px"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="ステータス">
        <a-select
          v-model:value="statusInput"
          placeholder="すべて"
          style="width: 160px"
          allow-clear
          :options="STATUS_OPTIONS"
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
      :dataSource="items"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="paginationConfig"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="STATUS_COLOR[record.status] || 'default'">{{ record.status }}</a-tag>
        </template>
        <template v-else-if="column.key === 'startedAt'">
          {{ formatDateTime(record.startedAt) }}
        </template>
        <template v-else-if="column.key === 'finishedAt'">
          {{ formatDateTime(record.finishedAt) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="openDetail(record.id)">詳細</a-button>
        </template>
      </template>
    </a-table>

    <a-modal
      :open="detailOpen"
      title="バッチ実行詳細"
      width="720px"
      :footer="null"
      @cancel="detailOpen = false"
    >
      <a-spin :spinning="detailLoading">
        <a-descriptions v-if="detail" bordered size="small" :column="1">
          <a-descriptions-item label="ID">{{ detail.id }}</a-descriptions-item>
          <a-descriptions-item label="ジョブ名">{{ detail.jobName }}</a-descriptions-item>
          <a-descriptions-item label="ステータス">
            <a-tag :color="STATUS_COLOR[detail.status] || 'default'">{{ detail.status }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="開始時刻">{{ formatDateTime(detail.startedAt) }}</a-descriptions-item>
          <a-descriptions-item label="終了時刻">{{ formatDateTime(detail.finishedAt) }}</a-descriptions-item>
          <a-descriptions-item label="対象 / 成功 / 失敗">
            {{ detail.targetCount ?? '—' }} / {{ detail.successCount ?? '—' }} / {{ detail.failureCount ?? '—' }}
          </a-descriptions-item>
          <a-descriptions-item label="起動元">{{ detail.triggeredBy }}</a-descriptions-item>
          <a-descriptions-item label="エラー要約">
            <pre v-if="detail.errorSummary" class="error-summary">{{ detail.errorSummary }}</pre>
            <span v-else>—</span>
          </a-descriptions-item>
        </a-descriptions>
      </a-spin>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listBatchExecutions, getBatchExecution } from '../api/batchApi.js';

const STATUS_OPTIONS = [
  { value: 'RUNNING', label: 'RUNNING' },
  { value: 'SUCCESS', label: 'SUCCESS' },
  { value: 'FAILED',  label: 'FAILED' },
  { value: 'PARTIAL', label: 'PARTIAL' },
];

const STATUS_COLOR = {
  RUNNING: 'blue',
  SUCCESS: 'green',
  FAILED:  'red',
  PARTIAL: 'orange',
};

const items = ref([]);
const total = ref(0);
const loading = ref(false);

const jobNameInput = ref('');
const statusInput  = ref(undefined);

const pageState = reactive({ current: 1, pageSize: 50 });

const columns = [
  { title: 'ID',         dataIndex: 'id',         key: 'id',          width: 80,  align: 'right' },
  { title: 'ジョブ名',   dataIndex: 'jobName',    key: 'jobName' },
  { title: 'ステータス', key: 'status',                            width: 100 },
  { title: '開始時刻',   key: 'startedAt',                         width: 160 },
  { title: '終了時刻',   key: 'finishedAt',                        width: 160 },
  { title: '対象',       dataIndex: 'targetCount',  key: 'targetCount',  width: 80,  align: 'right' },
  { title: '成功',       dataIndex: 'successCount', key: 'successCount', width: 80,  align: 'right' },
  { title: '失敗',       dataIndex: 'failureCount', key: 'failureCount', width: 80,  align: 'right' },
  { title: '起動元',     dataIndex: 'triggeredBy',  key: 'triggeredBy',  width: 160 },
  { title: '操作',       key: 'actions',                           width: 80 },
];

const paginationConfig = ref({
  current: 1,
  pageSize: 50,
  total: 0,
  showTotal: (t, range) => `${range[0]}-${range[1]} / 全 ${t} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
});

function formatDateTime(value) {
  if (!value) return '—';
  return String(value).replace('T', ' ').slice(0, 19);
}

async function reload(page = pageState.current - 1) {
  loading.value = true;
  try {
    const offset = page * pageState.pageSize;
    const res = await listBatchExecutions({
      jobName: jobNameInput.value || undefined,
      status:  statusInput.value || undefined,
      offset,
      size: pageState.pageSize,
    });
    items.value = Array.isArray(res.data?.items) ? res.data.items : [];
    total.value = Number(res.data?.total ?? 0);
    paginationConfig.value = {
      ...paginationConfig.value,
      current: page + 1,
      pageSize: pageState.pageSize,
      total: total.value,
    };
    pageState.current = page + 1;
  } catch (e) {
    message.warning('バッチ実行履歴の取得に失敗しました');
    items.value = [];
    total.value = 0;
  } finally {
    loading.value = false;
  }
}

function onTableChange(pagination) {
  pageState.pageSize = pagination.pageSize;
  reload(pagination.current - 1);
}

function resetFilters() {
  jobNameInput.value = '';
  statusInput.value = undefined;
  reload(0);
}

// ---- 詳細モーダル ----
const detailOpen = ref(false);
const detailLoading = ref(false);
const detail = ref(null);

async function openDetail(id) {
  detailOpen.value = true;
  detailLoading.value = true;
  detail.value = null;
  try {
    const res = await getBatchExecution(id);
    detail.value = res.data;
  } catch (e) {
    message.warning('詳細の取得に失敗しました');
    detailOpen.value = false;
  } finally {
    detailLoading.value = false;
  }
}

onMounted(() => reload(0));
</script>

<style scoped>
.error-summary {
  white-space: pre-wrap;
  background: #fff5f5;
  padding: 8px;
  border-radius: 4px;
  max-height: 280px;
  overflow: auto;
  font-size: 12px;
  margin: 0;
}
</style>
