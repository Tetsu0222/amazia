<template>
  <div style="padding: 24px; max-width: 600px">
    <a-page-header
      title="入荷登録"
      sub-title="Amazia Console"
      @back="goBack"
    />

    <a-alert
      message="倉庫はバックエンドが既定値（id=1 'default'）を自動セットします（並行運用期）。"
      type="info"
      show-icon
      style="margin-bottom: 12px"
    />

    <a-form layout="vertical" :model="form">
      <a-form-item label="商品ID" required>
        <a-input-number
          v-model:value="form.productId"
          :min="1"
          style="width: 100%"
          placeholder="商品IDを入力"
        />
      </a-form-item>
      <a-form-item label="SKU ID" required>
        <a-input-number
          v-model:value="form.skuId"
          :min="1"
          style="width: 100%"
          placeholder="SKU IDを入力"
        />
      </a-form-item>
      <a-form-item label="入荷数量" required>
        <a-input-number
          v-model:value="form.quantity"
          :min="1"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="入荷日" required>
        <a-date-picker
          v-model:value="form.inboundedAt"
          value-format="YYYY-MM-DD"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item label="仕入先ID（任意）">
        <a-input-number
          v-model:value="form.supplierId"
          :min="1"
          style="width: 100%"
        />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" :loading="submitting" @click="onSubmit">登録</a-button>
          <a-button @click="goBack">キャンセル</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { registerInbound } from '../api/inboundApi.js';

const router = useRouter();
const submitting = ref(false);
const form = reactive({
  productId: null,
  skuId: null,
  quantity: 1,
  inboundedAt: null,
  supplierId: null,
});

async function onSubmit() {
  if (!form.productId || !form.skuId || !form.quantity || !form.inboundedAt) {
    message.warning('必須項目を入力してください');
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      productId: form.productId,
      skuId: form.skuId,
      quantity: form.quantity,
      inboundedAt: form.inboundedAt,
    };
    if (form.supplierId) {
      payload.supplierId = form.supplierId;
    }
    // RRRR-5：warehouseId は送らない（バックエンドが既定値を自動セット）
    await registerInbound(payload);
    message.success('入荷を登録しました');
    router.push('/inbound');
  } catch (e) {
    const status = e.response?.status;
    const msg = e.response?.data?.message ?? '登録に失敗しました';
    if (status === 404) {
      message.error('指定された商品またはSKUが見つかりません');
    } else if (status === 400) {
      message.error('SKUと商品の親子関係が一致しません: ' + msg);
    } else if (status === 422) {
      message.error('入力値に誤りがあります: ' + msg);
    } else {
      message.error(msg);
    }
  } finally {
    submitting.value = false;
  }
}

function goBack() {
  router.push('/inbound');
}
</script>
