<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="売上管理" sub-title="Amazia Console" />

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="list" tab="一覧">
        <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 16px; flex-wrap: wrap">
          <a-checkbox v-model:checked="excludePreorderInList">予約を除外</a-checkbox>
          <span>
            売上日：
            <a-date-picker
              v-model:value="salesDateFrom"
              value-format="YYYY-MM-DD"
              placeholder="最早"
              style="width: 140px"
            />
            <span style="margin: 0 6px">〜</span>
            <a-date-picker
              v-model:value="salesDateTo"
              value-format="YYYY-MM-DD"
              placeholder="最遅"
              style="width: 140px"
            />
            <a-button size="small" style="margin-left: 8px" @click="resetSalesDateRange">クリア</a-button>
          </span>
        </div>
        <a-table
          :dataSource="filteredSalesForList"
          :columns="listColumns"
          :loading="loading"
          rowKey="salesId"
          size="small"
          :pagination="{ pageSize: 50 }"
        />
      </a-tab-pane>

      <a-tab-pane key="summary" tab="集計">
        <div style="margin-bottom: 12px">
          <a-button
            :type="includePreorderInSummary ? 'primary' : 'default'"
            @click="includePreorderInSummary = !includePreorderInSummary"
          >
            {{ includePreorderInSummary ? '見込み表示中（予約含む）' : '見込み表示' }}
          </a-button>
          <span v-if="includePreorderInSummary" style="margin-left: 12px; color: #faad14">
            ※ 予約購入を含む見込み値です
          </span>
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
import { listSales } from '../api/salesApi.js';

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

const sales = ref([]);
const loading = ref(false);
const activeTab = ref('list');

// フェーズ16 Step2: 一覧タブの予約除外フィルタ
const excludePreorderInList = ref(false);
// フェーズ16 Step2: 集計タブの「見込み表示」トグル（既定 OFF＝予約除外）
const includePreorderInSummary = ref(false);

// フェーズ16 Step6-3: 一覧タブの売上日帯域検索
const salesDateFrom = ref(null);
const salesDateTo = ref(null);
const resetSalesDateRange = () => {
  salesDateFrom.value = null;
  salesDateTo.value = null;
};

// フェーズ16 Step6-3: 集計タブの「月別売上」カードの粒度切替（既定: 月）
const summaryGranularity = ref('month');

const filteredSalesForList = computed(() => {
  return sales.value.filter(s => {
    if (excludePreorderInList.value && s.preorder) return false;
    if (salesDateFrom.value) {
      if (!s.salesDate || s.salesDate < salesDateFrom.value) return false;
    }
    if (salesDateTo.value) {
      if (!s.salesDate || s.salesDate > salesDateTo.value) return false;
    }
    return true;
  });
});

// 集計タブの元データ：見込み表示中なら全件、通常時は予約除外
const salesForSummary = computed(() =>
  includePreorderInSummary.value ? sales.value : sales.value.filter(s => !s.preorder)
);

const listColumns = [
  { title: '売上日',     dataIndex: 'salesDate',    key: 'salesDate' },
  { title: '配送日',     dataIndex: 'shippingDate', key: 'shippingDate',
    customRender: ({ text }) => text ?? '—' },
  { title: 'ユーザ名',   dataIndex: 'customerName', key: 'customerName' },
  { title: '商品名',     dataIndex: 'productName',  key: 'productName' },
  { title: '色',         dataIndex: 'color',        key: 'color' },
  { title: 'サイズ',     dataIndex: 'size',         key: 'size' },
  { title: '数量',       dataIndex: 'quantity',     key: 'quantity' },
  { title: '金額（円）', dataIndex: 'amount',       key: 'amount',
    customRender: ({ text }) => text != null ? text.toLocaleString() : '—' },
  { title: '配送方法',   dataIndex: 'shippingMethodId', key: 'shippingMethodId',
    customRender: ({ text }) => SHIPPING_METHOD_LABELS[text] ?? `#${text}` },
  { title: '決済方法',   dataIndex: 'paymentMethodName', key: 'paymentMethodName' },
  { title: '配送ステータス', dataIndex: 'shippingStatusCode', key: 'shippingStatusCode',
    customRender: ({ text }) => SHIPPING_STATUS_LABELS[text] ?? text },
  { title: '区分',       dataIndex: 'preorder',     key: 'preorder',
    customRender: ({ text }) => text ? '予約' : '通常' },
];

const GRANULARITY_LABELS = {
  day:   { card: '日別売上', column: '日付' },
  month: { card: '月別売上', column: '年月' },
  year:  { card: '年別売上', column: '年' },
};
const granularityCardTitle = computed(() => GRANULARITY_LABELS[summaryGranularity.value].card);
const summaryGranularityColumns = computed(() => [
  { title: GRANULARITY_LABELS[summaryGranularity.value].column, dataIndex: 'bucket', key: 'bucket' },
  { title: '件数',     dataIndex: 'count',    key: 'count' },
  { title: '数量',     dataIndex: 'quantity', key: 'quantity' },
  { title: '売上（円）', dataIndex: 'amount',  key: 'amount',
    customRender: ({ text }) => text.toLocaleString() },
]);
const summarySkuColumns = [
  { title: '商品名',   dataIndex: 'productName', key: 'productName' },
  { title: '色',       dataIndex: 'color',       key: 'color' },
  { title: 'サイズ',   dataIndex: 'size',        key: 'size' },
  { title: '件数',     dataIndex: 'count',       key: 'count' },
  { title: '数量',     dataIndex: 'quantity',    key: 'quantity' },
  { title: '売上（円）', dataIndex: 'amount',    key: 'amount',
    customRender: ({ text }) => text.toLocaleString() },
];
const summaryPaymentColumns = [
  { title: '決済方法', dataIndex: 'paymentMethodName', key: 'paymentMethodName' },
  { title: '件数',     dataIndex: 'count',    key: 'count' },
  { title: '売上（円）', dataIndex: 'amount', key: 'amount',
    customRender: ({ text }) => text.toLocaleString() },
];
const summaryPreorderColumns = [
  { title: '区分',     dataIndex: 'label',  key: 'label' },
  { title: '件数',     dataIndex: 'count',  key: 'count' },
  { title: '売上（円）', dataIndex: 'amount', key: 'amount',
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
