<template>
  <a-modal
    :open="open"
    title="配送先住所変更"
    ok-text="更新"
    cancel-text="キャンセル"
    :confirm-loading="submitting"
    @ok="onOk"
    @cancel="onCancel"
  >
    <a-alert
      message="住所一覧 API は phase14 r2 / phase18 で追加予定。現状は対象 address_id を直接入力します（Core 側で sales.user_id 所有チェック済み）。"
      type="info"
      show-icon
      style="margin-bottom: 12px"
    />
    <a-form layout="vertical">
      <a-form-item label="現在の配送先住所ID">
        <a-input :value="currentAddressId" disabled />
      </a-form-item>
      <a-form-item label="新しい配送先住所ID" required>
        <a-input-number v-model:value="form.shippingAddressId" :min="1" style="width: 100%" />
      </a-form-item>
      <a-form-item label="変更理由（任意）">
        <a-textarea v-model:value="form.reason" :rows="3" />
      </a-form-item>
    </a-form>
  </a-modal>
</template>

<script setup>
import { ref, reactive, watch } from 'vue';
import { message } from 'ant-design-vue';
import { updateShippingAddress } from '../../api/deliveryApi.js';

const props = defineProps({
  open: { type: Boolean, default: false },
  deliveryId: { type: Number, default: null },
  currentAddressId: { type: Number, default: null },
});
const emit = defineEmits(['update:open', 'updated']);

const submitting = ref(false);
const form = reactive({
  shippingAddressId: null,
  reason: '',
});

watch(() => props.open, (v) => {
  if (v) {
    form.shippingAddressId = null;
    form.reason = '';
  }
});

async function onOk() {
  if (!form.shippingAddressId) {
    message.warning('住所IDを入力してください');
    return;
  }
  submitting.value = true;
  try {
    await updateShippingAddress(props.deliveryId, {
      shippingAddressId: form.shippingAddressId,
      reason: form.reason || null,
    });
    message.success('配送先住所を更新しました');
    emit('updated');
    emit('update:open', false);
  } catch (e) {
    const status = e.response?.status;
    const msg = e.response?.data?.message ?? '更新に失敗しました';
    if (status === 403) {
      message.error('購入者所有でない住所です: ' + msg);
    } else if (status === 400) {
      message.error('変更不可な状態です: ' + msg);
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
