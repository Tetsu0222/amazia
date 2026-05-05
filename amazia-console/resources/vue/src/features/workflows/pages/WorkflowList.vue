<template>
  <div style="padding: 24px">
    <a-page-header title="ワークフロー一覧" sub-title="Amazia Console" />

    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <a-radio-group v-model:value="status" @change="reload">
        <a-radio-button value="">すべて</a-radio-button>
        <a-radio-button value="pending">承認中</a-radio-button>
        <a-radio-button value="approved">承認済</a-radio-button>
        <a-radio-button value="rejected">否認</a-radio-button>
        <a-radio-button value="canceled">取下</a-radio-button>
      </a-radio-group>
      <a-button type="primary" @click="$router.push('/workflows/new')">+ 新規申請</a-button>
    </div>

    <a-table
      :columns="columns"
      :data-source="workflows"
      :loading="loading"
      row-key="id"
      @row-click="(record) => $router.push(`/workflows/${record.id}`)"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
        </template>
        <template v-if="column.key === 'currentStep'">
          {{ formatCurrentStep(record) }}
        </template>
        <template v-if="column.key === 'actions'">
          <a-button type="link" @click.stop="$router.push(`/workflows/${record.id}`)">詳細</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue';
import { listWorkflows } from '../api/workflowApi.js';

const workflows = ref([]);
const status    = ref('');
const loading   = ref(false);

const columns = [
  { title: 'ID', dataIndex: 'id', width: 70 },
  { title: '対象', dataIndex: 'targetType', width: 100 },
  { title: '対象ID', dataIndex: 'targetId', width: 90 },
  { title: 'ステータス', key: 'status', width: 110 },
  { title: '現ステップ', key: 'currentStep', width: 200 },
  { title: '申請日時', dataIndex: 'createdAt', width: 180 },
  { title: '完了日時', dataIndex: 'completedAt', width: 180 },
  { title: '', key: 'actions', width: 80 },
];

function statusColor(s) {
  return { pending: 'blue', approved: 'green', rejected: 'red', canceled: 'default' }[s] || 'default';
}
function statusLabel(s) {
  return { pending: '承認中', approved: '承認済', rejected: '否認', canceled: '取下' }[s] || s;
}
function formatCurrentStep(record) {
  if (!record.details || record.details.length === 0) return '-';
  const pending = record.details.find(d => d.status === 'pending');
  if (!pending) return '-';
  return `step${pending.stepNumber} (${pending.targetRole}${pending.destinationName ? ' / ' + pending.destinationName : ''})`;
}

async function reload() {
  loading.value = true;
  try {
    const res = await listWorkflows(status.value || null);
    workflows.value = res.data || [];
  } finally {
    loading.value = false;
  }
}

onMounted(reload);
</script>
