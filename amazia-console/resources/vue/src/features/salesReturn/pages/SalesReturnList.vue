<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="返品管理" sub-title="Amazia Console" />

    <a-form layout="inline" style="margin-bottom: 16px">
      <a-form-item label="状態">
        <a-select
          v-model:value="statusFilter"
          style="width: 200px"
          allow-clear
          placeholder="すべて"
        >
          <a-select-option value="REQUESTED">申請中</a-select-option>
          <a-select-option value="APPROVED">承認済み</a-select-option>
          <a-select-option value="REJECTED">却下</a-select-option>
          <a-select-option value="REFUNDED">返金完了</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-button @click="fetchList" :loading="loading">再読込</a-button>
      </a-form-item>
    </a-form>

    <a-table
      :dataSource="filteredList"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="{ pageSize: 50 }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button
              v-if="record.status === 'REQUESTED'"
              type="primary"
              size="small"
              :loading="actionLoading[record.id] === 'approve'"
              @click="confirmAction(record, 'approve')"
            >承認</a-button>
            <a-button
              v-if="record.status === 'REQUESTED'"
              danger
              size="small"
              :loading="actionLoading[record.id] === 'reject'"
              @click="confirmAction(record, 'reject')"
            >却下</a-button>
            <a-button
              v-if="record.status === 'APPROVED'"
              type="primary"
              size="small"
              :loading="actionLoading[record.id] === 'refund'"
              @click="confirmAction(record, 'refund')"
            >返金完了</a-button>
            <span v-if="record.status === 'REJECTED' || record.status === 'REFUNDED'" style="color: #888">—</span>
          </a-space>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="STATUS_COLORS[record.status]">
            {{ STATUS_LABELS[record.status] ?? record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'reason'">
          <span :title="record.reason">{{ record.reason || '—' }}</span>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, computed, reactive, onMounted } from 'vue';
import { Modal, message } from 'ant-design-vue';
import {
  listSalesReturns,
  approveSalesReturn,
  rejectSalesReturn,
  refundSalesReturn,
} from '../api/salesReturnApi.js';

const STATUS_LABELS = {
  REQUESTED: '申請中',
  APPROVED: '承認済み',
  REJECTED: '却下',
  REFUNDED: '返金完了',
};
const STATUS_COLORS = {
  REQUESTED: 'orange',
  APPROVED: 'blue',
  REJECTED: 'default',
  REFUNDED: 'green',
};

const ACTION_DEF = {
  approve: { label: '承認',     fn: approveSalesReturn, success: '承認しました（配送ステータスを RETURN_REQUESTED に更新）' },
  reject:  { label: '却下',     fn: rejectSalesReturn,  success: '却下しました' },
  refund:  { label: '返金完了', fn: refundSalesReturn,  success: '返金完了しました（在庫を戻し、配送ステータスを RETURNED に更新）' },
};

const list = ref([]);
const loading = ref(false);
const statusFilter = ref(null);
const actionLoading = reactive({});

const columns = [
  { title: '申請日',     dataIndex: 'createdAt', key: 'createdAt',
    customRender: ({ text }) => text ? text.replace('T', ' ').slice(0, 16) : '—' },
  { title: '顧客',       dataIndex: 'customerName', key: 'customerName' },
  { title: '商品名',     dataIndex: 'productName',  key: 'productName' },
  { title: '色',         dataIndex: 'color',        key: 'color' },
  { title: 'サイズ',     dataIndex: 'size',         key: 'size' },
  { title: '数量',       dataIndex: 'quantity',     key: 'quantity' },
  { title: '理由',       dataIndex: 'reason',       key: 'reason', ellipsis: true, width: 200 },
  { title: '状態',       dataIndex: 'status',       key: 'status' },
  { title: '承認日',     dataIndex: 'approvedAt',   key: 'approvedAt',
    customRender: ({ text }) => text ? text.replace('T', ' ').slice(0, 16) : '—' },
  { title: '売上ID',     dataIndex: 'salesId',      key: 'salesId' },
  { title: '操作',       key: 'action', width: 200 },
];

const filteredList = computed(() => {
  if (!statusFilter.value) return list.value;
  return list.value.filter((r) => r.status === statusFilter.value);
});

async function fetchList() {
  loading.value = true;
  try {
    const res = await listSalesReturns();
    list.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('返品一覧の取得に失敗しました');
    list.value = [];
  } finally {
    loading.value = false;
  }
}

function confirmAction(record, action) {
  const def = ACTION_DEF[action];
  Modal.confirm({
    title: `${def.label}しますか？`,
    content: `売上ID ${record.salesId}（${record.productName} / ${record.color} / ${record.size}）の返品申請を${def.label}します。`,
    okText: def.label,
    cancelText: 'キャンセル',
    onOk: () => runAction(record, action),
  });
}

async function runAction(record, action) {
  const def = ACTION_DEF[action];
  actionLoading[record.id] = action;
  try {
    await def.fn(record.id);
    message.success(def.success);
    await fetchList();
  } catch (e) {
    const status = e?.response?.status;
    if (status === 409) {
      message.error('この返品申請は既に処理済みのため操作できません（再読込してください）');
    } else if (status === 403) {
      message.error('このロールでは操作できません');
    } else {
      message.error(`${def.label}に失敗しました`);
    }
  } finally {
    delete actionLoading[record.id];
  }
}

onMounted(fetchList);
</script>
