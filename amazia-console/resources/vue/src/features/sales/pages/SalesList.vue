<template>
  <div style="padding: 24px">
    <a-page-header title="売上管理">
      <template v-if="activeTab === 'list'" #description>
        全 {{ sales.length }} 件中 {{ filteredSalesForList.length }} 件を表示
      </template>
    </a-page-header>

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="list" tab="一覧">
        <a-card size="small" style="margin-bottom: 16px" :body-style="{ padding: '12px 16px' }">
          <a-form layout="vertical" :model="searchForm">
            <div class="search-row search-row--basic">
              <a-form-item label="商品名">
                <a-input
                  v-model:value="searchForm.productName"
                  placeholder="部分一致で検索"
                  allow-clear
                />
              </a-form-item>
              <a-form-item label="ユーザ名">
                <a-input
                  v-model:value="searchForm.customerName"
                  placeholder="部分一致で検索"
                  allow-clear
                />
              </a-form-item>
            </div>
            <div class="search-row search-row--status">
              <a-form-item label="配送ステータス">
                <a-select
                  v-model:value="searchForm.shippingStatus"
                  style="width: 160px"
                  :options="shippingStatusOptions"
                />
              </a-form-item>
              <a-form-item label="決済方法">
                <a-select
                  v-model:value="searchForm.paymentMethod"
                  style="width: 160px"
                  :options="paymentMethodOptions"
                />
              </a-form-item>
              <a-form-item label="区分">
                <a-radio-group v-model:value="searchForm.preorderFilter" button-style="solid">
                  <a-radio-button value="all">すべて</a-radio-button>
                  <a-radio-button value="normal">通常のみ</a-radio-button>
                  <a-radio-button value="preorder">予約のみ</a-radio-button>
                </a-radio-group>
              </a-form-item>
            </div>
            <div class="search-row search-row--date">
              <a-form-item label=" " class="search-checkbox">
                <a-checkbox v-model:checked="searchForm.excludePreorder">予約を除外</a-checkbox>
              </a-form-item>
              <a-form-item label="売上日">
                <a-input-group compact class="range-group">
                  <a-date-picker
                    v-model:value="searchForm.salesDateFrom"
                    value-format="YYYY-MM-DD"
                    placeholder="最早"
                    style="width: 140px"
                  />
                  <span class="range-sep">〜</span>
                  <a-date-picker
                    v-model:value="searchForm.salesDateTo"
                    value-format="YYYY-MM-DD"
                    placeholder="最遅"
                    style="width: 140px"
                  />
                </a-input-group>
              </a-form-item>
              <a-form-item label=" " class="search-clear">
                <a-button @click="resetSearch">クリア</a-button>
              </a-form-item>
            </div>
          </a-form>
        </a-card>
        <a-table
          :dataSource="filteredSalesForList"
          :columns="listColumns"
          :loading="loading"
          rowKey="salesId"
          size="small"
          :pagination="listPagination"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'preorder'">
              <a-tag v-if="record.preorder" color="orange">予約</a-tag>
              <a-tag v-else>通常</a-tag>
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="summary" tab="集計">
        <div style="margin-bottom: 12px">
          <a-space align="center">
            <a-switch v-model:checked="includePreorderInSummary" />
            <span>予約購入を含む見込み値で表示</span>
            <a-tooltip title="ON にすると予約購入分を集計に含めます。発売前商品の見込み売上を確認したいときに使用します。">
              <InfoCircleOutlined style="color: #999" />
            </a-tooltip>
          </a-space>
        </div>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-card :title="granularityCardTitle" size="small">
              <template #extra>
                <a-radio-group v-model:value="summaryGranularity" size="small" button-style="solid">
                  <a-radio-button value="day">日</a-radio-button>
                  <a-radio-button value="month">月</a-radio-button>
                  <a-radio-button value="year">年</a-radio-button>
                </a-radio-group>
              </template>
              <a-table
                :dataSource="summaryByGranularity"
                :columns="summaryGranularityColumns"
                rowKey="bucket"
                size="small"
                :pagination="false"
              />
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card title="SKU別売上" size="small">
              <a-table
                :dataSource="summaryBySku"
                :columns="summarySkuColumns"
                rowKey="key"
                size="small"
                :pagination="{ pageSize: 20 }"
              />
            </a-card>
          </a-col>
        </a-row>
        <a-row :gutter="16" style="margin-top: 16px">
          <a-col :span="12">
            <a-card title="決済方法別売上" size="small">
              <a-table
                :dataSource="summaryByPayment"
                :columns="summaryPaymentColumns"
                rowKey="paymentMethodName"
                size="small"
                :pagination="false"
              />
            </a-card>
          </a-col>
          <a-col :span="12">
            <a-card title="購入区分別売上" size="small">
              <a-table
                :dataSource="summaryByPreorder"
                :columns="summaryPreorderColumns"
                rowKey="label"
                size="small"
                :pagination="false"
              />
            </a-card>
          </a-col>
        </a-row>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { InfoCircleOutlined } from '@ant-design/icons-vue';
import { listSales } from '../api/salesApi.js';
import { normalizeName } from '../../../utils/normalizeName.js';

const SHIPPING_METHOD_LABELS = {
  1: '宅配便',
  2: 'コンビニ受取',
  3: '直接受取',
};
const SHIPPING_STATUS_LABELS = {
  PENDING: '出荷待ち',
  SHIPPED: '出荷済み',
  DELIVERED: '配達完了',
  RETURN_REQUESTED: '返品申請中',
  RETURNED: '返品完了',
  CANCELED: 'キャンセル',
  DELIVERY_FAILED: '配達失敗',
  RESCHEDULED: '再配達調整中',
};
const PAYMENT_METHOD_LABELS = {
  credit_card: 'クレジットカード',
  convenience: 'コンビニ決済',
  bank_transfer: '銀行振込',
};

const sales = ref([]);
const loading = ref(false);
const activeTab = ref('list');

const includePreorderInSummary = ref(false);

const summaryGranularity = ref('month');

const defaultSearchForm = () => ({
  productName: '',
  customerName: '',
  shippingStatus: 'all',
  paymentMethod: 'all',
  preorderFilter: 'all',
  excludePreorder: false,
  salesDateFrom: null,
  salesDateTo: null,
});
const searchForm = ref(defaultSearchForm());
const resetSearch = () => {
  searchForm.value = defaultSearchForm();
};

const shippingStatusOptions = [
  { value: 'all', label: 'すべて' },
  ...Object.entries(SHIPPING_STATUS_LABELS).map(([value, label]) => ({ value, label })),
];
const paymentMethodOptions = computed(() => {
  const fromData = new Set();
  for (const s of sales.value) {
    if (s.paymentMethodName) fromData.add(s.paymentMethodName);
  }
  for (const code of Object.keys(PAYMENT_METHOD_LABELS)) fromData.add(code);
  return [
    { value: 'all', label: 'すべて' },
    ...Array.from(fromData).map(code => ({
      value: code,
      label: PAYMENT_METHOD_LABELS[code] ?? code,
    })),
  ];
});

const filteredSalesForList = computed(() => {
  const f = searchForm.value;
  return sales.value.filter(s => {
    if (f.excludePreorder && s.preorder) return false;
    if (f.preorderFilter === 'normal' && s.preorder) return false;
    if (f.preorderFilter === 'preorder' && !s.preorder) return false;
    if (f.salesDateFrom && (!s.salesDate || s.salesDate < f.salesDateFrom)) return false;
    if (f.salesDateTo && (!s.salesDate || s.salesDate > f.salesDateTo)) return false;
    if (f.productName && !(s.productName ?? '').includes(f.productName)) return false;
    if (f.customerName) {
      const target = normalizeName(s.customerName) ?? '';
      if (!target.includes(f.customerName)) return false;
    }
    if (f.shippingStatus !== 'all' && s.shippingStatusCode !== f.shippingStatus) return false;
    if (f.paymentMethod !== 'all' && s.paymentMethodName !== f.paymentMethod) return false;
    return true;
  });
});

const listPagination = {
  pageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
};

// 集計タブの元データ：見込み表示中なら全件、通常時は予約除外
const salesForSummary = computed(() =>
  includePreorderInSummary.value ? sales.value : sales.value.filter(s => !s.preorder)
);

const listColumns = [
  { title: '売上日',     dataIndex: 'salesDate',    key: 'salesDate' },
  { title: '配送日',     dataIndex: 'shippingDate', key: 'shippingDate', width: 80,
    customRender: ({ text }) => text ?? '—' },
  { title: 'ユーザ名',   dataIndex: 'customerName', key: 'customerName',
    customRender: ({ text }) => normalizeName(text) ?? '' },
  { title: '商品名',     dataIndex: 'productName',  key: 'productName' },
  { title: '色',         dataIndex: 'color',        key: 'color' },
  { title: 'サイズ',     dataIndex: 'size',         key: 'size' },
  { title: '数量',       dataIndex: 'quantity',     key: 'quantity', align: 'right' },
  { title: '金額（円）', dataIndex: 'amount',       key: 'amount', align: 'right',
    customRender: ({ text }) => text != null ? text.toLocaleString() : '—' },
  { title: '配送方法',   dataIndex: 'shippingMethodId', key: 'shippingMethodId',
    customRender: ({ text }) => SHIPPING_METHOD_LABELS[text] ?? `#${text}` },
  { title: '決済方法',   dataIndex: 'paymentMethodName', key: 'paymentMethodName',
    customRender: ({ text }) => PAYMENT_METHOD_LABELS[text] ?? text },
  { title: '配送ステータス', dataIndex: 'shippingStatusCode', key: 'shippingStatusCode',
    customRender: ({ text }) => SHIPPING_STATUS_LABELS[text] ?? text },
  { title: '区分',       dataIndex: 'preorder',     key: 'preorder' },
];

const GRANULARITY_LABELS = {
  day:   { card: '日別売上', column: '日付' },
  month: { card: '月別売上', column: '年月' },
  year:  { card: '年別売上', column: '年' },
};
const granularityCardTitle = computed(() => GRANULARITY_LABELS[summaryGranularity.value].card);
const summaryGranularityColumns = computed(() => [
  { title: GRANULARITY_LABELS[summaryGranularity.value].column, dataIndex: 'bucket', key: 'bucket' },
  { title: '件数',     dataIndex: 'count',    key: 'count', align: 'right' },
  { title: '数量',     dataIndex: 'quantity', key: 'quantity', align: 'right' },
  { title: '売上（円）', dataIndex: 'amount',  key: 'amount', align: 'right',
    customRender: ({ text }) => text.toLocaleString() },
]);
const summarySkuColumns = [
  { title: '商品名',   dataIndex: 'productName', key: 'productName' },
  { title: '色',       dataIndex: 'color',       key: 'color' },
  { title: 'サイズ',   dataIndex: 'size',        key: 'size' },
  { title: '件数',     dataIndex: 'count',       key: 'count', align: 'right' },
  { title: '数量',     dataIndex: 'quantity',    key: 'quantity', align: 'right' },
  { title: '売上（円）', dataIndex: 'amount',    key: 'amount', align: 'right',
    customRender: ({ text }) => text.toLocaleString() },
];
const summaryPaymentColumns = [
  { title: '決済方法', dataIndex: 'paymentMethodName', key: 'paymentMethodName',
    customRender: ({ text }) => PAYMENT_METHOD_LABELS[text] ?? text },
  { title: '件数',     dataIndex: 'count',    key: 'count', align: 'right' },
  { title: '売上（円）', dataIndex: 'amount', key: 'amount', align: 'right',
    customRender: ({ text }) => text.toLocaleString() },
];
const summaryPreorderColumns = [
  { title: '区分',     dataIndex: 'label',  key: 'label' },
  { title: '件数',     dataIndex: 'count',  key: 'count', align: 'right' },
  { title: '売上（円）', dataIndex: 'amount', key: 'amount', align: 'right',
    customRender: ({ text }) => text.toLocaleString() },
];

const GRANULARITY_SLICE = { day: 10, month: 7, year: 4 };
const summaryByGranularity = computed(() => {
  const sliceLen = GRANULARITY_SLICE[summaryGranularity.value];
  const map = new Map();
  for (const s of salesForSummary.value) {
    const bucket = (s.salesDate ?? '').slice(0, sliceLen);
    if (!bucket) continue;
    const cur = map.get(bucket) ?? { bucket, count: 0, quantity: 0, amount: 0 };
    cur.count += 1;
    cur.quantity += s.quantity ?? 0;
    cur.amount += s.amount ?? 0;
    map.set(bucket, cur);
  }
  return Array.from(map.values()).sort((a, b) => b.bucket.localeCompare(a.bucket));
});

const summaryBySku = computed(() => {
  const map = new Map();
  for (const s of salesForSummary.value) {
    const key = `${s.skuId}`;
    const cur = map.get(key) ?? {
      key,
      productName: s.productName,
      color: s.color,
      size: s.size,
      count: 0,
      quantity: 0,
      amount: 0,
    };
    cur.count += 1;
    cur.quantity += s.quantity ?? 0;
    cur.amount += s.amount ?? 0;
    map.set(key, cur);
  }
  return Array.from(map.values()).sort((a, b) => b.amount - a.amount);
});

const summaryByPayment = computed(() => {
  const map = new Map();
  for (const s of salesForSummary.value) {
    const key = s.paymentMethodName ?? '不明';
    const cur = map.get(key) ?? { paymentMethodName: key, count: 0, amount: 0 };
    cur.count += 1;
    cur.amount += s.amount ?? 0;
    map.set(key, cur);
  }
  return Array.from(map.values()).sort((a, b) => b.amount - a.amount);
});

const summaryByPreorder = computed(() => {
  const normal = { label: '通常購入', count: 0, amount: 0 };
  const preorder = { label: '予約購入', count: 0, amount: 0 };
  for (const s of sales.value) {
    const target = s.preorder ? preorder : normal;
    target.count += 1;
    target.amount += s.amount ?? 0;
  }
  return [normal, preorder];
});

onMounted(async () => {
  loading.value = true;
  try {
    const res = await listSales();
    sales.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('売上一覧の取得に失敗しました');
    sales.value = [];
  } finally {
    loading.value = false;
  }
});
</script>

<style scoped>
.search-row {
  display: grid;
  gap: 0 16px;
  align-items: end;
}

.search-row--basic {
  grid-template-columns: 1fr 1fr;
}

.search-row--status {
  grid-template-columns: auto auto auto 1fr;
}

.search-row--date {
  grid-template-columns: auto auto 1fr;
}

.search-row :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.range-group {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.range-sep {
  margin: 0 6px;
}

.search-clear {
  justify-self: end;
}

@media (max-width: 768px) {
  .search-row--basic,
  .search-row--status,
  .search-row--date {
    grid-template-columns: 1fr;
  }
  .search-clear {
    justify-self: start;
  }
}
</style>
