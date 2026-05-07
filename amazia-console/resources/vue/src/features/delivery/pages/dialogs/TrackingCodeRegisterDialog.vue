<template>
  <a-modal
    :open="open"
    title="追跡番号登録"
    ok-text="登録"
    cancel-text="キャンセル"
    :confirm-loading="submitting"
    @ok="onOk"
    @cancel="onCancel"
  >
    <a-form layout="vertical">
      <a-form-item label="現在の追跡番号">
        <a-input :value="currentTrackingCode ?? '未登録'" disabled />
      </a-form-item>
      <a-form-item label="追跡番号" required>
        <a-input
          v-model:value="form.trackingCode"
          placeholder="例: YMT-1234-5678"
          :maxlength="100"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { message } from 'ant-design-vue';
import { registerTrackingCode } from '../../api/deliveryApi.js';

const props = defineProps({
  open: { type: Boolean, default: false },
  deliveryId: { type: Number, default: null },
  currentTrackingCode: { type: String, default: null },
});
const emit = defineEmits(['update:open', 'updated']);

const submitting = ref(false);
const form = reactive({
  trackingCode: '',
});

watch(() => props.open, (v) => {
  if (v) {
    form.trackingCode = props.currentTrackingCode ?? '';
  }
});

async function onOk() {
  if (!form.trackingCode || !form.trackingCode.trim()) {
    message.warning('追跡番号を入力してください');
    return;
  }
  submitting.value = true;
  try {
    await registerTrackingCode(props.deliveryId, {
      trackingCode: form.trackingCode.trim(),
    });
    message.success('追跡番号を登録しました');
    emit('updated');
    emit('update:open', false);
  } catch (e) {
    message.error(e.response?.data?.message ?? '登録に失敗しました');
  } finally {
    submitting.value = false;
  }
}

function onCancel() {
  emit('update:open', false);
}
</script>
