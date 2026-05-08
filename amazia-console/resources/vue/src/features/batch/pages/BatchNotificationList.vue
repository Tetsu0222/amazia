<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="通知センター" sub-title="Amazia Console" />

    <a-form layout="inline" style="margin-bottom: 16px" @submit.prevent="reload(0)">
      <a-form-item label="レベル">
        <a-select
          v-model:value="levelInput"
          placeholder="すべて"
          style="width: 140px"
          allow-clear
          :options="LEVEL_OPTIONS"
        />
      </a-form-item>
      <a-form-item label="購読タグ">
        <a-select
          v-model:value="tagInput"
          placeholder="すべて"
          style="width: 200px"
          allow-clear
          :options="TAG_OPTIONS"
        />
      </a-form-item>
      <a-form-item>
        <a-checkbox v-model:checked="includeRead">既読も含める</a-checkbox>
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
        <template v-if="column.key === 'level'">
          <a-tag :color="LEVEL_COLOR[record.level] || 'default'">{{ record.level }}</a-tag>
        </template>
        <template v-else-if="column.key === 'createdAt'">
          {{ formatDateTime(record.createdAt) }}
        </template>
        <template v-else-if="column.key === 'readAt'">
          {{ formatDateTime(record.readAt) }}
        </template>
        <template v-else-if="column.key === 'actions'">
          <a-button
            v-if="!record.readByUserId"
            type="link"
            size="small"
            :loading="readingId === record.id"
            @click="markRead(record.id)"
          >
            既読にする
          </a-button>
          <span v-else style="color: #aaa">既読</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listBatchNotifications, markBatchNotificationRead } from '../api/batchApi.js';

const LEVEL_OPTIONS = [
  { value: 'INFO',  label: 'INFO' },
  { value: 'WARN',  label: 'WARN' },
  { value: 'ERROR', label: 'ERROR' },
];

const LEVEL_COLOR = {
  INFO:  'blue',
  WARN:  'orange',
  ERROR: 'red',
};

const TAG_OPTIONS = [
  { value: 'inventory_alerts', label: 'inventory_alerts（在庫不整合）' },
  { value: 'sales_alerts',     label: 'sales_alerts（売上不整合）' },
  { value: 'delivery_alerts',  label: 'delivery_alerts（配送遅延）' },
  { value: 'postal_alerts',    label: 'postal_alerts（郵便整合性）' },
  { value: 'batch_failure',    label: 'batch_failure（バッチ失敗汎用）' },
];

const items = ref([]);
const total = ref(0);
const loading = ref(false);
const readingId = ref(null);

const levelInput   = ref(undefined);
const tagInput     = ref(undefined);
const includeRead  = ref(false);

const pageState = reactive({ current: 1, pageSize: 50 });

const columns = [
  { title: 'ID',          dataIndex: 'id',                    key: 'id',          width: 80,  align: 'right' },
  { title: 'レベル',      key: 'level',                                       width: 80 },
  { title: 'タグ',        dataIndex: 'targetSubscriptionTag', key: 'targetSubscriptionTag', width: 160 },
  { title: 'タイトル',    dataIndex: 'title',                 key: 'title' },
  { title: '本文',        dataIndex: 'body',                  key: 'body', ellipsis: true },
  { title: '発火ジョブ',  dataIndex: 'sourceJob',             key: 'sourceJob',   width: 200 },
  { title: '発生時刻',    key: 'createdAt',                                  width: 160 },
  { title: '既読時刻',    key: 'readAt',                                     width: 160 },
  { title: '操作',        key: 'actions',                                    width: 110 },
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
    const res = await listBatchNotifications({
      level: levelInput.value || undefined,
      tag:   tagInput.value || undefined,
      include_read: includeRead.value ? true : undefined,
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
    message.warning('通知の取得に失敗しました');
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
  levelInput.value = undefined;
  tagInput.value = undefined;
  includeRead.value = false;
  reload(0);
}

async function markRead(id) {
  readingId.value = id;
  try {
    await markBatchNotificationRead(id);
    message.success('既読にしました');
    reload(pageState.current - 1);
  } catch (e) {
    if (e.response?.status === 403) {
      message.error('この通知を既読にする権限がありません');
    } else {
      message.warning('既読化に失敗しました');
    }
  } finally {
    readingId.value = null;
  }
}

onMounted(() => reload(0));
</script>
