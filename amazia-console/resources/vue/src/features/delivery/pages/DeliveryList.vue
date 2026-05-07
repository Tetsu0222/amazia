<template>
  <div style="padding: 24px; max-width: 1400px">
    <a-page-header title="配送管理" sub-title="Amazia Console" />

    <a-card size="small" style="margin-bottom: 16px" :body-style="{ padding: '12px 16px' }">
      <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 8px">
        <span style="font-weight: 500; min-width: 80px">追跡番号</span>
        <a-input
          v-model:value="searchForm.trackingCode"
          placeholder="部分一致で検索"
          allow-clear
          style="flex: 1; max-width: 480px"
        />
      </div>
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="売上ID">
          <a-input-number
            v-model:value="searchForm.salesId"
            placeholder="完全一致"
            :min="1"
            style="width: 110px"
          />
        </a-form-item>
        <a-form-item label="配送方法">
          <a-select
            v-model:value="searchForm.shippingMethodId"
            allow-clear
            placeholder="すべて"
            style="width: 140px"
          >
            <a-select-option v-for="opt in methodOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="配送ステータス">
          <a-select
            v-model:value="searchForm.shippingStatusId"
            allow-clear
            placeholder="すべて"
            style="width: 180px"
          >
            <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="配送予定日">
          <a-date-picker
            v-model:value="searchForm.scheduledDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 140px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-date-picker
            v-model:value="searchForm.scheduledDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item label="発送日">
          <a-date-picker
            v-model:value="searchForm.shippedDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 140px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-date-picker
            v-model:value="searchForm.shippedDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item label="配達完了日">
          <a-date-picker
            v-model:value="searchForm.deliveredDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 140px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-date-picker
            v-model:value="searchForm.deliveredDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item>
          <a-button @click="resetSearch">クリア</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-table
      :dataSource="filteredDeliveries"
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
import { ref, computed, onMounted } from 'vue';
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

const statusOptions = [
  { value: 1, label: '配送準備中' },
  { value: 2, label: '配送済' },
  { value: 3, label: '配送完了' },
  { value: 4, label: '返品申請中' },
  { value: 5, label: '返品完了' },
];

const methodOptions = [
  { value: 1, label: '宅配' },
  { value: 2, label: 'コンビニ受取' },
  { value: 3, label: '置き配' },
];

const searchForm = ref({
  trackingCode: '',
  salesId: null,
  shippingMethodId: undefined,
  shippingStatusId: undefined,
  scheduledDateFrom: null,
  scheduledDateTo: null,
  shippedDateFrom: null,
  shippedDateTo: null,
  deliveredDateFrom: null,
  deliveredDateTo: null,
});

const resetSearch = () => {
  searchForm.value = {
    trackingCode: '',
    salesId: null,
    shippingMethodId: undefined,
    shippingStatusId: undefined,
    scheduledDateFrom: null,
    scheduledDateTo: null,
    shippedDateFrom: null,
    shippedDateTo: null,
    deliveredDateFrom: null,
    deliveredDateTo: null,
  };
};

const filteredDeliveries = computed(() => {
  const f = searchForm.value;
  const tracking = (f.trackingCode || '').trim().toLowerCase();

  return deliveries.value.filter(d => {
    if (tracking && !(d.trackingCode || '').toLowerCase().includes(tracking)) return false;
    if (f.salesId != null && d.salesId !== f.salesId) return false;
    if (f.shippingMethodId != null && d.shippingMethodId !== f.shippingMethodId) return false;
    if (f.shippingStatusId != null && d.shippingStatusId !== f.shippingStatusId) return false;

    if (f.scheduledDateFrom) {
      if (!d.scheduledDate || d.scheduledDate < f.scheduledDateFrom) return false;
    }
    if (f.scheduledDateTo) {
      if (!d.scheduledDate || d.scheduledDate > f.scheduledDateTo) return false;
    }
    if (f.shippedDateFrom) {
      if (!d.shippedDate || d.shippedDate < f.shippedDateFrom) return false;
    }
    if (f.shippedDateTo) {
      if (!d.shippedDate || d.shippedDate > f.shippedDateTo) return false;
    }
    if (f.deliveredDateFrom) {
      if (!d.deliveredDate || d.deliveredDate < f.deliveredDateFrom) return false;
    }
    if (f.deliveredDateTo) {
      if (!d.deliveredDate || d.deliveredDate > f.deliveredDateTo) return false;
    }

    return true;
  });
});

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
    const res = await listDeliveries();
    deliveries.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('配送一覧の取得に失敗しました');
    deliveries.value = [];
  } finally {
    loading.value = false;
  }
}

function goDetail(id) {
  router.push(`/delivery/${id}`);
}

onMounted(reload);
</script>
