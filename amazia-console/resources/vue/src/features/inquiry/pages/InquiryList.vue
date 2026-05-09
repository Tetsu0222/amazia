<template>
  <div style="padding: 24px">
    <a-page-header title="問い合わせ管理">
      <template #description>
        <span style="color: rgba(0, 0, 0, 0.65); font-size: 14px">{{ countLabel }}</span>
      </template>
    </a-page-header>

    <a-card style="margin-bottom: 16px">
      <a-form layout="inline" @finish="onSearch">
        <a-form-item label="ステータス">
          <a-select
            v-model:value="searchForm.status"
            allow-clear
            placeholder="すべて"
            style="width: 160px"
          >
            <a-select-option value="NEW">未対応</a-select-option>
            <a-select-option value="IN_PROGRESS">対応中</a-select-option>
            <a-select-option value="DONE">完了</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="対象種別">
          <a-select
            v-model:value="searchForm.targetType"
            allow-clear
            placeholder="すべて"
            style="width: 160px"
          >
            <a-select-option value="delivery">配送</a-select-option>
            <a-select-option value="product">商品</a-select-option>
            <a-select-option value="sales">注文</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="顧客名">
          <a-input v-model:value="searchForm.userName" placeholder="部分一致" style="width: 200px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit">検索</a-button>
          <a-button style="margin-left: 8px" @click="resetSearch">クリア</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-table
      :columns="columns"
      :data-source="rows"
      :pagination="pagination"
      :loading="loading"
      row-key="id"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'subject'">
          <router-link :to="`/inquiries/${record.id}`">{{ record.subject }}</router-link>
        </template>
        <template v-else-if="column.dataIndex === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'updatedAt'">
          {{ formatDate(record.updatedAt) }}
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import { listInquiries } from '../api/inquiryApi.js';

const loading = ref(false);
const rows = ref([]);
const total = ref(0);

const searchForm = reactive({
  status: undefined,
  targetType: undefined,
  userName: '',
});

const pagination = reactive({
  current: 1,
  pageSize: 50,
  total: 0,
  showSizeChanger: false,
});

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '件名', dataIndex: 'subject' },
  { title: 'ステータス', dataIndex: 'status', width: 110 },
  { title: '顧客名', dataIndex: 'userName', width: 160 },
  { title: '対象', dataIndex: 'targetLabel', width: 200 },
  { title: '最終更新', dataIndex: 'updatedAt', width: 180 },
];

const countLabel = computed(() => `全 ${total.value} 件`);

function statusColor(status) {
  return { NEW: 'red', IN_PROGRESS: 'blue', DONE: 'default' }[status] || 'default';
}

function statusLabel(status) {
  return { NEW: '未対応', IN_PROGRESS: '対応中', DONE: '完了' }[status] || status;
}

function formatDate(s) {
  if (!s) return '';
  return new Date(s).toLocaleString('ja-JP');
}

async function fetchData() {
  loading.value = true;
  try {
    const res = await listInquiries({
      status: searchForm.status,
      targetType: searchForm.targetType,
      userName: searchForm.userName,
      page: pagination.current - 1,
      size: pagination.pageSize,
    });
    rows.value = res.data.content || [];
    total.value = res.data.totalElements || 0;
    pagination.total = total.value;
  } finally {
    loading.value = false;
  }
}

function onSearch() {
  pagination.current = 1;
  fetchData();
}

function resetSearch() {
  searchForm.status = undefined;
  searchForm.targetType = undefined;
  searchForm.userName = '';
  pagination.current = 1;
  fetchData();
}

function onTableChange(p) {
  pagination.current = p.current;
  fetchData();
}

onMounted(fetchData);
</script>
