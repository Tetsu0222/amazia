<template>
  <a-modal
    :open="open"
    title="リードタイム編集"
    ok-text="更新"
    cancel-text="キャンセル"
    :confirm-loading="submitting"
    @ok="onOk"
    @cancel="onCancel"
  >
    <a-alert
      message="0 日に設定すると無効化扱い（マスタ未登録と同等）。物理削除はできません。"
      type="info"
      show-icon
      style="margin-bottom: 12px"
    />
    <a-form layout="vertical">
      <a-form-item label="配送方法">
        <a-input :value="methodLabel" disabled />
      </a-form-item>
      <a-form-item label="都道府県">
        <a-input :value="record?.prefecture ?? ''" disabled />
      </a-form-item>
      <a-form-item label="リードタイム（日数）" required>
        <a-input-number
          v-model:value="form.leadTimeDays"
          :min="0"
          :max="365"
          style="width: 100%"
        />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, computed, watch } from 'vue';
import { message } from 'ant-design-vue';
import { updateShippingLeadTime } from '../../api/shippingLeadTimeApi.js';

const METHOD_LABEL = {
  1: '宅配',
  2: 'コンビニ受取',
  3: '置き配',
};

const props = defineProps({
  open: { type: Boolean, default: false },
  record: { type: Object, default: null },
});
const emit = defineEmits(['update:open', 'updated']);

const submitting = ref(false);
const form = reactive({
  leadTimeDays: 0,
});

const methodLabel = computed(() => {
  if (!props.record) return '';
  return METHOD_LABEL[props.record.shippingMethodId] ?? `#${props.record.shippingMethodId}`;
});

watch(() => props.open, (v) => {
  if (v && props.record) {
    form.leadTimeDays = props.record.leadTimeDays ?? 0;
  }
});

async function onOk() {
  if (form.leadTimeDays == null || form.leadTimeDays < 0) {
    message.warning('リードタイムは 0 以上の整数で入力してください');
    return;
  }
  submitting.value = true;
  try {
    await updateShippingLeadTime(props.record.id, { leadTimeDays: form.leadTimeDays });
    message.success('リードタイムを更新しました');
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
