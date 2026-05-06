<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="売上管理" sub-title="Amazia Console" />

    <a-tabs v-model:activeKey="activeTab">
      <a-tab-pane key="list" tab="一覧">
        <a-table
          :dataSource="sales"
          :columns="listColumns"
          :loading="loading"
          rowKey="salesId"
          size="small"
          :pagination="{ pageSize: 50 }"
        />
      </a-tab-pane>

      <a-tab-pane key="summary" tab="集計">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-card title="月別売上" size="small">
              <a-table
                :dataSource="summaryByMonth"
                :columns="summaryMonthColumns"
                rowKey="month"
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

const summaryMonthColumns = [
  { title: '年月',     dataIndex: 'month',    key: 'month' },
  { title: '件数',     dataIndex: 'count',    key: 'count' },
  { title: '数量',     dataIndex: 'quantity', key: 'quantity' },
  { title: '売上（円）', dataIndex: 'amount',  key: 'amount',
    customRender: ({ text }) => text.toLocaleString() },
];
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

const summaryByMonth = computed(() => {
  const map = new Map();
  for (const s of sales.value) {
    const month = (s.salesDate ?? '').slice(0, 7);
    if (!month) continue;
    const cur = map.get(month) ?? { month, count: 0, quantity: 0, amount: 0 };
    cur.count += 1;
    cur.quantity += s.quantity ?? 0;
    cur.amount += s.amount ?? 0;
    map.set(month, cur);
  }
  return Array.from(map.values()).sort((a, b) => b.month.localeCompare(a.month));
});

const summaryBySku = computed(() => {
  const map = new Map();
  for (const s of sales.value) {
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
  for (const s of sales.value) {
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
