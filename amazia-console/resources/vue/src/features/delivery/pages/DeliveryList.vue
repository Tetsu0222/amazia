<template>
  <div style="padding: 24px; max-width: 1400px">
    <a-page-header title="配送管理" sub-title="Amazia Console" />

    <a-form layout="inline" :model="filter" style="margin-bottom: 16px">
      <a-form-item label="配送ステータス">
        <a-select
          v-model:value="filter.shippingStatusId"
          allow-clear
          placeholder="すべて"
          style="width: 200px"
        >
          <a-select-option
            v-for="opt in statusOptions"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="reload">検索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="reset">クリア</a-button>
      </a-form-item>
    </a-form>

    <a-table
      :dataSource="deliveries"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="{ pageSize: 50 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'actions'">
          <a-button type="link" size="small" @click="goDetail(record.id)">詳細</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listDeliveries } from '../api/deliveryApi.js';

const SHIPPING_STATUS_LABEL = {
  1: '配送準備中（PENDING）',
  2: '配送済（SHIPPED）',
  3: '配送完了（DELIVERED）',
  4: '返品申請中（RETURN_REQUESTED）',
  5: '返品完了（RETURNED）',
};

const SHIPPING_METHOD_LABEL = {
  1: '宅配',
  2: 'コンビニ受取',
  3: '置き配',
};

const router = useRouter();
const deliveries = ref([]);
const loading = ref(false);
const filter = reactive({
  shippingStatusId: undefined,
});

const statusOptions = [
  { value: 1, label: '配送準備中' },
  { value: 2, label: '配送済' },
  { value: 3, label: '配送完了' },
  { value: 4, label: '返品申請中' },
  { value: 5, label: '返品完了' },
];

const columns = [
  { title: 'ID',          dataIndex: 'id',                key: 'id',         width: 80 },
  { title: '売上ID',      dataIndex: 'salesId',           key: 'salesId',    width: 100 },
  { title: '配送方法',    dataIndex: 'shippingMethodId',  key: 'shippingMethodId',
    customRender: ({ text }) => SHIPPING_METHOD_LABEL[text] ?? `#${text}` },
  { title: '配送ステータス', dataIndex: 'shippingStatusId', key: 'shippingStatusId',
    customRender: ({ text }) => SHIPPING_STATUS_LABEL[text] ?? `#${text}` },
  { title: '配送予定日',  dataIndex: 'scheduledDate',     key: 'scheduledDate',
    customRender: ({ text }) => text ?? '入荷待ち' },
  { title: '発送日',      dataIndex: 'shippedDate',       key: 'shippedDate',
    customRender: ({ text }) => text ?? '—' },
  { title: '配達完了日',  dataIndex: 'deliveredDate',     key: 'deliveredDate',
    customRender: ({ text }) => text ?? '—' },
  { title: '追跡番号',    dataIndex: 'trackingCode',      key: 'trackingCode',
    customRender: ({ text }) => text ?? '—' },
  { title: '操作',        key: 'actions',                 width: 100 },
];

async function reload() {
  loading.value = true;
  try {
    const params = {};
    if (filter.shippingStatusId !== undefined && filter.shippingStatusId !== null) {
      params.shippingStatusId = filter.shippingStatusId;
    }
    const res = await listDeliveries(params);
    deliveries.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('配送一覧の取得に失敗しました');
    deliveries.value = [];
  } finally {
    loading.value = false;
  }
}

function reset() {
  filter.shippingStatusId = undefined;
  reload();
}

function goDetail(id) {
  router.push(`/delivery/${id}`);
}

onMounted(reload);
</script>
