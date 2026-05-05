<template>
  <div style="padding: 24px">
    <a-page-header
      :title="`ワークフロー #${id}`"
      sub-title="Amazia Console"
      @back="$router.push('/workflows')"
    />

    <a-spin v-if="loading" />
    <template v-else-if="workflow">
      <a-descriptions bordered :column="2" size="middle" style="margin-bottom: 24px">
        <a-descriptions-item label="対象種別">{{ workflow.targetType }}</a-descriptions-item>
        <a-descriptions-item label="対象ID">{{ workflow.targetId }}</a-descriptions-item>
        <a-descriptions-item label="ステータス">
          <a-tag :color="statusColor(workflow.status)">{{ statusLabel(workflow.status) }}</a-tag>
        </a-descriptions-item>
        <a-descriptions-item label="申請者ID">{{ workflow.requestedBy }}</a-descriptions-item>
        <a-descriptions-item label="申請日時">{{ workflow.createdAt }}</a-descriptions-item>
        <a-descriptions-item label="完了日時">{{ workflow.completedAt || '-' }}</a-descriptions-item>
        <a-descriptions-item v-if="reason" label="申請理由" :span="2">
          {{ reason }}
        </a-descriptions-item>
      </a-descriptions>

      <h3>差分内容</h3>
      <a-table
        :columns="diffColumns"
        :data-source="diffRows"
        row-key="field"
        :pagination="false"
        size="small"
        style="margin-bottom: 24px"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'before'">
            <span :style="{ color: '#999' }">{{ formatValue(record.before) }}</span>
          </template>
          <template v-if="column.key === 'arrow'">→</template>
          <template v-if="column.key === 'after'">
            <strong>{{ formatValue(record.after) }}</strong>
          </template>
        </template>
      </a-table>

      <a-collapse :bordered="false" style="margin-bottom: 24px; background: transparent">
        <a-collapse-panel key="raw" header="生データ（payload JSON）を表示">
          <pre style="margin: 0; white-space: pre-wrap; background: #fafafa; padding: 12px; border-radius: 4px">{{ formattedPayload }}</pre>
        </a-collapse-panel>
      </a-collapse>

      <h3>承認ステップ</h3>
      <a-table
        :columns="stepColumns"
        :data-source="workflow.details"
        row-key="id"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="detailStatusColor(record, workflow.status)">
              {{ detailStatusLabel(record, workflow.status) }}
            </a-tag>
          </template>
          <template v-if="column.key === 'destination'">
            {{ record.destinationName || record.targetRole + '（全員）' }}
          </template>
          <template v-if="column.key === 'actions'">
            <a-space v-if="canActOn(record)">
              <a-popconfirm title="このステップを承認しますか？" @confirm="approve(record.stepNumber)">
                <a-button type="primary" size="small">承認</a-button>
              </a-popconfirm>
              <a-popconfirm title="このステップを否認しますか？" @confirm="reject(record.stepNumber)">
                <a-button danger size="small">否認</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>

      <div style="margin-top: 24px; display: flex; justify-content: flex-end">
        <a-popconfirm
          v-if="canCancel"
          title="このワークフローを取り下げますか？"
          @confirm="cancel"
        >
          <a-button danger>取り下げ</a-button>
        </a-popconfirm>
      </div>

      <a-alert v-if="errorMsg" :message="errorMsg" type="error" style="margin-top: 16px" />
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  getWorkflow, approveWorkflowStep, rejectWorkflowStep, cancelWorkflow,
} from '../api/workflowApi.js';
import { authStore } from '../../../stores/authStore.js';

const route    = useRoute();
const id       = route.params.id;
const workflow = ref(null);
const loading  = ref(false);
const errorMsg = ref('');

const stepColumns = [
  { title: 'Step', dataIndex: 'stepNumber', width: 70 },
  { title: '承認対象ロール', dataIndex: 'targetRole', width: 150 },
  { title: '承認対象', key: 'destination', width: 200 },
  { title: 'ステータス', key: 'status', width: 130 },
  { title: '承認者', dataIndex: 'approverName', width: 150 },
  { title: '更新日時', dataIndex: 'updatedAt', width: 180 },
  { title: '', key: 'actions', width: 160 },
];

const diffColumns = [
  { title: 'フィールド', dataIndex: 'field', width: 200 },
  { title: '変更前',     key: 'before',      width: 240 },
  { title: '',           key: 'arrow',       width: 40, align: 'center' },
  { title: '変更後',     key: 'after' },
];

const parsedPayload = computed(() => {
  if (!workflow.value?.payload) return null;
  try {
    return JSON.parse(workflow.value.payload);
  } catch {
    return null;
  }
});

const diffRows = computed(() => parsedPayload.value?.fields ?? []);
const reason   = computed(() => parsedPayload.value?.meta?.reason ?? null);

const formattedPayload = computed(() => {
  if (!workflow.value?.payload) return '';
  try {
    return JSON.stringify(JSON.parse(workflow.value.payload), null, 2);
  } catch {
    return workflow.value.payload;
  }
});

function formatValue(v) {
  if (v === null || v === undefined || v === '') return '（未設定）';
  return String(v);
}

const canCancel = computed(() => {
  if (!workflow.value || workflow.value.status !== 'pending') return false;
  const isOwner    = String(workflow.value.requestedBy) === String(authStore.userId);
  const isApprover = authStore.isApprover;
  return isOwner || isApprover;
});

function statusColor(s) {
  return { pending: 'blue', approved: 'green', rejected: 'red', canceled: 'default' }[s] || 'default';
}
function statusLabel(s) {
  return { pending: '承認中', approved: '承認済', rejected: '否認', canceled: '取下' }[s] || s;
}

/**
 * 設計書 5.3：並列ステップで他が reject になった場合、
 * 残った waiting のものは「承認不要」と表示する。
 */
function detailStatusLabel(detail, parentStatus) {
  if (parentStatus === 'rejected' && detail.status === 'waiting') return '承認不要';
  return statusLabel(detail.status) || detail.status;
}
function detailStatusColor(detail, parentStatus) {
  if (parentStatus === 'rejected' && detail.status === 'waiting') return 'default';
  return statusColor(detail.status);
}

function canActOn(detail) {
  if (!workflow.value || workflow.value.status !== 'pending') return false;
  if (detail.status !== 'pending') return false;
  if (authStore.role === 'eternal_advisor') return true;
  if (detail.destinationUserId != null) {
    return String(detail.destinationUserId) === String(authStore.userId);
  }
  return detail.targetRole === authStore.role;
}

async function load() {
  loading.value = true;
  errorMsg.value = '';
  try {
    const res = await getWorkflow(id);
    workflow.value = res.data;
  } catch (e) {
    errorMsg.value = e.response?.data?.message || 'ワークフローの取得に失敗しました';
  } finally {
    loading.value = false;
  }
}

async function approve(stepNumber) {
  errorMsg.value = '';
  try {
    await approveWorkflowStep(id, stepNumber);
    message.success('承認しました');
    await load();
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '承認に失敗しました';
  }
}

async function reject(stepNumber) {
  errorMsg.value = '';
  try {
    await rejectWorkflowStep(id, stepNumber);
    message.success('否認しました');
    await load();
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '否認に失敗しました';
  }
}

async function cancel() {
  errorMsg.value = '';
  try {
    await cancelWorkflow(id);
    message.success('取り下げました');
    await load();
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '取り下げに失敗しました';
  }
}

onMounted(load);
</script>
