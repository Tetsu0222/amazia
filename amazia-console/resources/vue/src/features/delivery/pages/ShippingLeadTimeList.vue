<template>
  <div style="padding: 24px">
    <a-page-header title="都道府県別リードタイム">
      <template #description>
        <span style="color: rgba(0, 0, 0, 0.65); font-size: 14px">
          配送方法 × 都道府県でリードタイム（日数）を管理します。0 日は無効化扱い。
        </span>
      </template>
    </a-page-header>

    <a-radio-group
      v-model:value="activeMethodId"
      button-style="solid"
      style="margin-bottom: 16px"
      @change="reload"
    >
      <a-radio-button v-for="opt in methodOptions" :key="opt.value" :value="opt.value">
        {{ opt.label }}
      </a-radio-button>
    </a-radio-group>

    <a-table
      :dataSource="rows"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="false"
      :locale="{ emptyText: '該当データがありません' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'leadTimeDays'">
          <span :style="record.leadTimeDays === 0 ? 'color: #d4380d' : ''">
            {{ record.leadTimeDays }} 日<span v-if="record.leadTimeDays === 0"> (無効化)</span>
          </span>
        </template>
        <template v-if="column.key === 'actions'">
          <a-button type="default" size="small" @click="openEdit(record)">編集</a-button>
        </template>
      </template>
    </a-table>

    <ShippingLeadTimeEditDialog
      v-model:open="editOpen"
      :record="editingRecord"
      @updated="reload"
    />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listShippingLeadTimes } from '../api/shippingLeadTimeApi.js';
import ShippingLeadTimeEditDialog from './dialogs/ShippingLeadTimeEditDialog.vue';

const methodOptions = [
  { value: 1, label: '宅配' },
  { value: 2, label: 'コンビニ受取' },
  { value: 3, label: '置き配' },
];

const activeMethodId = ref(1);
const rows = ref([]);
const loading = ref(false);

const editOpen = ref(false);
const editingRecord = ref(null);

const columns = [
  { title: '都道府県',   dataIndex: 'prefecture',   key: 'prefecture',   width: 200 },
  { title: 'リードタイム', dataIndex: 'leadTimeDays', key: 'leadTimeDays', width: 200 },
  { title: '操作',       key: 'actions',                              width: 120 },
];

async function reload() {
  loading.value = true;
  try {
    const res = await listShippingLeadTimes({ shippingMethodId: activeMethodId.value });
    rows.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('リードタイム一覧の取得に失敗しました');
    rows.value = [];
  } finally {
    loading.value = false;
  }
}

function openEdit(record) {
  editingRecord.value = { ...record };
  editOpen.value = true;
}

onMounted(reload);
</script>
