<template>
  <div style="padding: 24px">
    <a-page-header :title="`#${id} ${detail?.subject || ''}`" @back="$router.push('/inquiries')">
      <template #extra>
        <a-space>
          <span>ステータス：</span>
          <a-select
            v-model:value="newStatus"
            style="width: 160px"
            :loading="statusUpdating"
            @change="onStatusChange"
          >
            <a-select-option
              v-for="opt in allowedNextStatuses"
              :key="opt.value"
              :value="opt.value"
            >
              {{ opt.label }}
            </a-select-option>
          </a-select>
        </a-space>
      </template>
      <template #description>
        <a-space :size="16">
          <span>顧客：{{ detail?.userName }}</span>
          <span v-if="detail?.targetLabel">対象：{{ detail.targetLabel }}</span>
        </a-space>
      </template>
    </a-page-header>

    <a-card title="スレッド" style="margin-bottom: 16px">
      <a-list :data-source="visibleMessages" :loading="loading" item-layout="vertical">
        <template #renderItem="{ item }">
          <a-list-item>
            <a-list-item-meta>
              <template #title>
                <a-space>
                  <a-tag v-if="item.isInternalNote" color="orange">内部メモ</a-tag>
                  <a-tag :color="item.senderType === 'admin_user' ? 'blue' : 'green'">
                    {{ item.senderType === 'admin_user' ? '管理者' : '顧客' }}
                  </a-tag>
                  <span>{{ item.senderName }}</span>
                </a-space>
              </template>
              <template #description>{{ formatDate(item.createdAt) }}</template>
            </a-list-item-meta>
            <div style="white-space: pre-wrap">{{ item.message }}</div>
          </a-list-item>
        </template>
      </a-list>
    </a-card>

    <a-card title="返信を送信" style="margin-bottom: 16px">
      <a-textarea v-model:value="reply" :rows="4" placeholder="メッセージを入力" />
      <div style="margin-top: 12px">
        <a-checkbox v-model:checked="replyInternal">内部メモとして投稿（顧客には非公開）</a-checkbox>
      </div>
      <a-button type="primary" :loading="replying" :disabled="!reply" style="margin-top: 12px"
        @click="onReply">送信</a-button>
    </a-card>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { getInquiry, replyInquiry, updateInquiryStatus } from '../api/inquiryApi.js';

const route = useRoute();
const id = Number(route.params.id);

const loading = ref(false);
const detail = ref(null);
const newStatus = ref(null);
const statusUpdating = ref(false);

const reply = ref('');
const replyInternal = ref(false);
const replying = ref(false);

const STATUS_LABELS = { NEW: '未対応', IN_PROGRESS: '対応中', DONE: '完了' };
const ALLOWED_TRANSITIONS = {
  NEW: ['IN_PROGRESS', 'DONE'],
  IN_PROGRESS: ['NEW', 'DONE'],
  DONE: ['NEW', 'IN_PROGRESS'],
};

const visibleMessages = computed(() => detail.value?.messages || []);

const allowedNextStatuses = computed(() => {
  const cur = detail.value?.status;
  if (!cur) return [];
  const tos = ALLOWED_TRANSITIONS[cur] || [];
  return [
    { value: cur, label: `${STATUS_LABELS[cur]}（現在）` },
    ...tos.map(t => ({ value: t, label: STATUS_LABELS[t] })),
  ];
});

function formatDate(s) {
  if (!s) return '';
  return new Date(s).toLocaleString('ja-JP');
}

async function fetchDetail() {
  loading.value = true;
  try {
    const res = await getInquiry(id);
    detail.value = res.data;
    newStatus.value = res.data.status;
  } finally {
    loading.value = false;
  }
}

async function onReply() {
  replying.value = true;
  try {
    await replyInquiry(id, { message: reply.value, isInternalNote: replyInternal.value });
    reply.value = '';
    replyInternal.value = false;
    message.success('返信を送信しました');
    await fetchDetail();
  } catch (e) {
    message.error('送信に失敗しました');
  } finally {
    replying.value = false;
  }
}

async function onStatusChange(value) {
  if (value === detail.value?.status) return;
  statusUpdating.value = true;
  try {
    await updateInquiryStatus(id, { newStatus: value });
    message.success('ステータスを変更しました');
    await fetchDetail();
  } catch (e) {
    newStatus.value = detail.value?.status;
    message.error('ステータス変更に失敗しました');
  } finally {
    statusUpdating.value = false;
  }
}

onMounted(fetchDetail);
</script>
