<template>
  <div style="padding: 24px">
    <a-page-header title="返品管理">
      <template #description>
        <a-space :size="8">
          <span style="color: rgba(0, 0, 0, 0.65); font-size: 14px">{{ countLabel }}</span>
          <a-tag v-if="isFilterApplied" color="blue">フィルタ適用中</a-tag>
        </a-space>
      </template>
      <template #extra>
        <a-button @click="fetchList" :loading="loading">再読込</a-button>
      </template>
    </a-page-header>

    <SearchCard @clear="resetSearch">
      <a-form-item label="状態">
        <a-select
          v-model:value="searchForm.status"
          allow-clear
          placeholder="すべて"
          style="width: 100%"
        >
          <a-select-option value="REQUESTED">申請中</a-select-option>
          <a-select-option value="APPROVED">承認済み</a-select-option>
          <a-select-option value="REJECTED">却下</a-select-option>
          <a-select-option value="REFUNDED">返金完了</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="顧客名">
        <a-input
          v-model:value="searchForm.customerKeyword"
          placeholder="部分一致で検索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="商品名">
        <a-input
          v-model:value="searchForm.productKeyword"
          placeholder="部分一致で検索"
          allow-clear
        />
      </a-form-item>
      <a-form-item label="売上ID">
        <a-input-number
          v-model:value="searchForm.salesId"
          placeholder="完全一致"
          :min="1"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="申請日" class="span-2">
        <a-input-group compact class="range-group">
          <a-date-picker
            v-model:value="searchForm.createdAtFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="flex: 1; min-width: 0"
          />
          <span class="range-sep">〜</span>
          <a-date-picker
            v-model:value="searchForm.createdAtTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="flex: 1; min-width: 0"
          />
        </a-input-group>
      </a-form-item>
      <a-form-item label="理由">
        <a-input
          v-model:value="searchForm.reasonKeyword"
          placeholder="部分一致で検索"
          allow-clear
        />
      </a-form-item>
    </SearchCard>

    <a-table
      :dataSource="filteredList"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="paginationConfig"
      :locale="{ emptyText: '該当する返品申請がありません' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'createdAt'">
          <div class="datetime-block">
            <div>{{ record.createdAt?.slice(0, 10) ?? '—' }}</div>
            <div class="datetime-time">{{ record.createdAt?.slice(11, 16) ?? '' }}</div>
          </div>
        </template>
        <template v-else-if="column.key === 'approvedAt'">
          <div class="datetime-block">
            <div>{{ record.approvedAt?.slice(0, 10) ?? '—' }}</div>
            <div class="datetime-time">{{ record.approvedAt?.slice(11, 16) ?? '' }}</div>
          </div>
        </template>
        <template v-else-if="column.key === 'customerName'">
          {{ normalizeName(record.customerName) ?? '' }}
        </template>
        <template v-else-if="column.key === 'reason'">
          <a-tooltip v-if="record.reason" :title="record.reason" placement="topLeft">
            <span class="reason-cell">{{ record.reason }}</span>
          </a-tooltip>
        </template>
        <template v-else-if="column.key === 'status'">
          <a-tag :color="STATUS_COLORS[record.status]" style="margin: 0">
            {{ STATUS_LABELS[record.status] ?? record.status }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space :size="4">
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
          </a-space>
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
import SearchCard from '../../../components/SearchCard.vue';
import { normalizeName } from '../../../utils/normalizeName.js';

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
const actionLoading = reactive({});

const initialSearchForm = () => ({
  status: undefined,
  customerKeyword: '',
  productKeyword: '',
  salesId: null,
  createdAtFrom: null,
  createdAtTo: null,
  reasonKeyword: '',
});

const searchForm = ref(initialSearchForm());

const resetSearch = () => {
  searchForm.value = initialSearchForm();
};

const filteredList = computed(() => {
  const f = searchForm.value;
  const customerKeyword = (f.customerKeyword || '').trim();
  const productKeyword = (f.productKeyword || '').trim().toLowerCase();
  const reasonKeyword = (f.reasonKeyword || '').trim().toLowerCase();

  return list.value.filter(r => {
    if (f.status && r.status !== f.status) return false;
    if (customerKeyword && !(normalizeName(r.customerName) || '').includes(customerKeyword)) return false;
    if (productKeyword && !(r.productName || '').toLowerCase().includes(productKeyword)) return false;
    if (f.salesId != null && r.salesId !== f.salesId) return false;
    if (f.createdAtFrom && (r.createdAt?.slice(0, 10) ?? '') < f.createdAtFrom) return false;
    if (f.createdAtTo   && (r.createdAt?.slice(0, 10) ?? '') > f.createdAtTo)   return false;
    if (reasonKeyword && !(r.reason || '').toLowerCase().includes(reasonKeyword)) return false;
    return true;
  });
});

const isFilterApplied = computed(() => {
  const f = searchForm.value;
  return !!(
    f.status ||
    (f.customerKeyword || '').trim() ||
    (f.productKeyword || '').trim() ||
    f.salesId != null ||
    f.createdAtFrom ||
    f.createdAtTo ||
    (f.reasonKeyword || '').trim()
  );
});

const countLabel = computed(() => {
  const total = list.value.length;
  const shown = filteredList.value.length;
  const suffix = isFilterApplied.value ? '（フィルタ適用中）' : '';
  return `全 ${total} 件中 ${shown} 件を表示${suffix}`;
});

const columns = [
  { title: '申請日',   dataIndex: 'createdAt',    key: 'createdAt',    width: 140 },
  { title: '顧客',     dataIndex: 'customerName', key: 'customerName', width: 120 },
  { title: '商品名',   dataIndex: 'productName',  key: 'productName' },
  { title: '色',       dataIndex: 'color',        key: 'color' },
  { title: 'サイズ',   dataIndex: 'size',         key: 'size' },
  { title: '数量',     dataIndex: 'quantity',     key: 'quantity', align: 'right' },
  { title: '理由',     dataIndex: 'reason',       key: 'reason', width: 240,
    ellipsis: { showTitle: false } },
  { title: '状態',     dataIndex: 'status',       key: 'status' },
  { title: '承認日',   dataIndex: 'approvedAt',   key: 'approvedAt',   width: 140 },
  { title: '売上ID',   dataIndex: 'salesId',      key: 'salesId', align: 'right' },
  { title: '操作',     key: 'action', width: 180 },
];

const paginationConfig = {
  defaultPageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
};

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
    content: `売上ID ${record.salesId}：${record.productName}（${record.color} / ${record.size}）`,
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

<style scoped>
.datetime-block {
  line-height: 1.3;
}
.datetime-time {
  color: #999;
  font-size: 12px;
}
.reason-cell {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: bottom;
}
:deep(.range-group) {
  display: flex;
  align-items: center;
  width: 100%;
}
</style>
