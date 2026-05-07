<template>
  <a-modal
    :open="open"
    title="配送ステータス更新"
    ok-text="更新"
    cancel-text="キャンセル"
    :confirm-loading="submitting"
    @ok="onOk"
    @cancel="onCancel"
  >
    <a-form layout="vertical">
      <a-form-item label="現在のステータス">
        <a-input :value="STATUS_LABEL[currentStatusId] ?? `#${currentStatusId}`" disabled />
      </a-form-item>
      <a-form-item label="次のステータス" required>
        <a-select v-model:value="form.shippingStatusId" placeholder="選択してください">
          <a-select-option
            v-for="opt in availableNextStatuses"
            :key="opt.value"
            :value="opt.value"
          >
            {{ opt.label }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="理由（任意）">
        <a-textarea v-model:value="form.reason" :rows="3" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import { updateShippingStatus } from '../../api/deliveryApi.js';

const props = defineProps({
  open: { type: Boolean, default: false },
  deliveryId: { type: Number, default: null },
  currentStatusId: { type: Number, default: null },
});
const emit = defineEmits(['update:open', 'updated']);

const STATUS_LABEL = {
  1: '配送準備中（PENDING）',
  2: '配送済（SHIPPED）',
  3: '配送完了（DELIVERED）',
  4: '返品申請中（RETURN_REQUESTED）',
  5: '返品完了（RETURNED）',
};

// 設計書 §配送ステータス遷移ルール の遷移可否表（Vue 側でも制御。Core が最終判定）
const TRANSITIONS = {
  1: [2],
  2: [3],
  3: [4],
  4: [5],
  5: [],
};

const submitting = ref(false);
const form = reactive({
  shippingStatusId: undefined,
  reason: '',
});

const availableNextStatuses = computed(() => {
  const allowed = TRANSITIONS[props.currentStatusId] ?? [];
  return allowed.map((id) => ({ value: id, label: STATUS_LABEL[id] }));
});

watch(() => props.open, (v) => {
  if (v) {
    form.shippingStatusId = undefined;
    form.reason = '';
  }
});

async function onOk() {
  if (!form.shippingStatusId) {
    message.warning('遷移先ステータスを選択してください');
    return;
  }
  submitting.value = true;
  try {
    await updateShippingStatus(props.deliveryId, {
      shippingStatusId: form.shippingStatusId,
      reason: form.reason || null,
    });
    message.success('配送ステータスを更新しました');
    emit('updated');
    emit('update:open', false);
  } catch (e) {
    const status = e.response?.status;
    const msg = e.response?.data?.message ?? '更新に失敗しました';
    if (status === 409) {
      message.error('在庫不足のため出荷できません: ' + msg);
    } else if (status === 400) {
      message.error('不正な遷移です: ' + msg);
    } else {
      message.error(msg);
    }
  } finally {
    submitting.value = false;
  }
}

function onCancel() {
  emit('update:open', false);
}
</script>
