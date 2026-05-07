<template>
  <a-modal
    :open="open"
    title="配送予定日変更"
    ok-text="更新"
    cancel-text="キャンセル"
    :confirm-loading="submitting"
    @ok="onOk"
    @cancel="onCancel"
  >
    <a-alert
      message="[manual] プレフィックスは Core 側 Service が自動付与します。理由は素のテキストで入力してください。"
      type="info"
      show-icon
      style="margin-bottom: 12px"
    />
    <a-form layout="vertical">
      <a-form-item label="現在の配送予定日">
        <a-input :value="currentDate ?? '未確定'" disabled />
      </a-form-item>
      <a-form-item label="新しい配送予定日" required>
        <a-date-picker
          v-model:value="form.scheduledDate"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="変更理由（任意）">
        <a-textarea v-model:value="form.reason" :rows="3" placeholder="出荷遅延・倉庫都合 など" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { message } from 'ant-design-vue';
import { updateScheduledDate } from '../../api/deliveryApi.js';

const props = defineProps({
  open: { type: Boolean, default: false },
  deliveryId: { type: Number, default: null },
  currentDate: { type: String, default: null },
});
const emit = defineEmits(['update:open', 'updated']);

const submitting = ref(false);
const form = reactive({
  scheduledDate: null,
  reason: '',
});

watch(() => props.open, (v) => {
  if (v) {
    form.scheduledDate = null;
    form.reason = '';
  }
});

async function onOk() {
  if (!form.scheduledDate) {
    message.warning('配送予定日を選択してください');
    return;
  }
  submitting.value = true;
  try {
    await updateScheduledDate(props.deliveryId, {
      scheduledDate: form.scheduledDate,
      reason: form.reason || null,
    });
    message.success('配送予定日を更新しました');
    emit('updated');
    emit('update:open', false);
  } catch (e) {
    message.error(e.response?.data?.message ?? '更新に失敗しました');
  } finally {
    submitting.value = false;
  }
}

function onCancel() {
  emit('update:open', false);
}
</script>
