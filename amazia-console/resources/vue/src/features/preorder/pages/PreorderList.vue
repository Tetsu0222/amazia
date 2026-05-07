<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="予約管理" sub-title="Amazia Console" />

    <a-table
      :dataSource="preorders"
      :columns="columns"
      :loading="loading"
      rowKey="productId"
      size="small"
      :pagination="{ pageSize: 50 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'preorderStartDate'">
          {{ record.preorderStartDate ?? '公開と同時' }}
        </template>
        <template v-else-if="column.key === 'daysUntilRelease'">
          {{ formatDaysUntilRelease(record.daysUntilRelease) }}
        </template>
        <template v-else-if="column.key === 'acceptPreorder'">
          <a-tag :color="record.acceptPreorder ? 'green' : 'default'">
            {{ record.acceptPreorder ? '受付中' : '停止中' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'preorderAmount'">
          {{ record.preorderAmount.toLocaleString() }}
        </template>
        <template v-else-if="column.key === 'isActive'">
          <a-tag :color="record.isActive ? 'blue' : 'red'">
            {{ record.isActive ? '公開中' : '非公開' }}
          </a-tag>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listPreorders } from '../api/preorderApi.js';

const preorders = ref([]);
const loading = ref(false);

const columns = [
  { title: '商品ID',       dataIndex: 'productId',         key: 'productId' },
  { title: '商品名',       dataIndex: 'productName',       key: 'productName' },
  { title: '予約開始日',   dataIndex: 'preorderStartDate', key: 'preorderStartDate' },
  { title: '発売日',       dataIndex: 'releaseDate',       key: 'releaseDate' },
  { title: '発売まで',     dataIndex: 'daysUntilRelease',  key: 'daysUntilRelease' },
  { title: '予約受付',     dataIndex: 'acceptPreorder',    key: 'acceptPreorder' },
  { title: '予約数',       dataIndex: 'preorderQuantity',  key: 'preorderQuantity' },
  { title: '予約金額（円）', dataIndex: 'preorderAmount',  key: 'preorderAmount' },
  { title: 'Market 公開',  dataIndex: 'isActive',          key: 'isActive' },
];

function formatDaysUntilRelease(days) {
  if (days == null) return '—';
  if (days === 0) return '本日発売';
  if (days < 0) return `${-days}日経過`;
  return `${days}日`;
}

onMounted(async () => {
  loading.value = true;
  try {
    const res = await listPreorders();
    preorders.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('予約商品一覧の取得に失敗しました');
    preorders.value = [];
  } finally {
    loading.value = false;
  }
});
</script>
