<template>
  <div style="padding: 24px">
    <a-page-header title="配送管理">
      <template #description>
        <a-space :size="8">
          <span style="color: rgba(0, 0, 0, 0.65); font-size: 14px">{{ countLabel }}</span>
          <a-tag v-if="isFilterApplied" color="blue">フィルタ適用中</a-tag>
        </a-space>
      </template>
    </a-page-header>

    <SearchCard
      wide-field-label="追跡番号"
      :wide-field-value="searchForm.trackingCode"
      @update:wide-field-value="searchForm.trackingCode = $event"
      @clear="resetSearch"
    >
      <a-form-item label="売上ID">
        <a-input-number
          v-model:value="searchForm.salesId"
          placeholder="完全一致"
          :min="1"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="配送方法">
        <a-select
          v-model:value="searchForm.shippingMethodId"
          allow-clear
          placeholder="すべて"
          style="width: 100%"
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
          style="width: 100%"
        >
          <a-select-option v-for="opt in statusOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="配送予定日" class="span-2">
        <a-input-group compact class="range-group">
          <a-date-picker
            v-model:value="searchForm.scheduledDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="flex: 1; min-width: 0"
          />
          <span class="range-sep">〜</span>
          <a-date-picker
            v-model:value="searchForm.scheduledDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="flex: 1; min-width: 0"
          />
        </a-input-group>
      </a-form-item>
      <a-form-item label="発送日" class="span-2">
        <a-input-group compact class="range-group">
          <a-date-picker
            v-model:value="searchForm.shippedDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="flex: 1; min-width: 0"
          />
          <span class="range-sep">〜</span>
          <a-date-picker
            v-model:value="searchForm.shippedDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="flex: 1; min-width: 0"
          />
        </a-input-group>
      </a-form-item>
      <a-form-item label="配達完了日" class="span-2">
        <a-input-group compact class="range-group">
          <a-date-picker
            v-model:value="searchForm.deliveredDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="flex: 1; min-width: 0"
          />
          <span class="range-sep">〜</span>
          <a-date-picker
            v-model:value="searchForm.deliveredDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="flex: 1; min-width: 0"
          />
        </a-input-group>
      </a-form-item>
    </SearchCard>

    <a-table
      :dataSource="filteredDeliveries"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="paginationConfig"
      :locale="{ emptyText: '該当データがありません' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'actions'">
          <a-button type="default" size="small" @click="goDetail(record.id)">詳細</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, h } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listDeliveries } from '../api/deliveryApi.js';
import SearchCard from '../../../components/SearchCard.vue';

const SHIPPING_STATUS_LABEL = {
  1: '配送準備中',
  2: '配送済',
  3: '配送完了',
  4: '返品申請中',
  5: '返品完了',
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

const initialSearchForm = () => ({
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

const searchForm = ref(initialSearchForm());

const resetSearch = () => {
  searchForm.value = initialSearchForm();
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

const isFilterApplied = computed(() => {
  const f = searchForm.value;
  return !!(
    (f.trackingCode || '').trim() ||
    f.salesId != null ||
    f.shippingMethodId != null ||
    f.shippingStatusId != null ||
    f.scheduledDateFrom ||
    f.scheduledDateTo ||
    f.shippedDateFrom ||
    f.shippedDateTo ||
    f.deliveredDateFrom ||
    f.deliveredDateTo
  );
});

const countLabel = computed(() => {
  const total = deliveries.value.length;
  const shown = filteredDeliveries.value.length;
  return `全 ${total} 件中 ${shown} 件を表示`;
});

const headerLeft = (label) => () =>
  h('span', { style: 'display: block; text-align: left' }, label);

const dimmedDash = (text) =>
  text == null
    ? h('span', { style: 'color: #ccc' }, '—')
    : text;

const columns = [
  { title: headerLeft('ID'),       dataIndex: 'id',                key: 'id',         width: 80, align: 'right' },
  { title: headerLeft('売上ID'),   dataIndex: 'salesId',           key: 'salesId',    width: 100, align: 'right' },
  { title: '配送方法',    dataIndex: 'shippingMethodId',  key: 'shippingMethodId',
    customRender: ({ text }) => SHIPPING_METHOD_LABEL[text] ?? `#${text}` },
  { title: '配送ステータス', dataIndex: 'shippingStatusId', key: 'shippingStatusId',
    customRender: ({ text }) => SHIPPING_STATUS_LABEL[text] ?? `#${text}` },
  { title: '配送予定日',  dataIndex: 'scheduledDate',     key: 'scheduledDate',
    customRender: ({ text }) => text ?? '入荷待ち' },
  { title: '発送日',      dataIndex: 'shippedDate',       key: 'shippedDate',
    customRender: ({ text }) => dimmedDash(text) },
  { title: '配達完了日',  dataIndex: 'deliveredDate',     key: 'deliveredDate',
    customRender: ({ text }) => dimmedDash(text) },
  { title: '追跡番号',    dataIndex: 'trackingCode',      key: 'trackingCode', width: 140,
    customRender: ({ text }) => dimmedDash(text) },
  { title: '操作',        key: 'actions',                 width: 80 },
];

const paginationConfig = {
  defaultPageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
};

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

<style scoped>
:deep(.range-group) {
  display: flex;
  align-items: center;
  width: 100%;
}
</style>
