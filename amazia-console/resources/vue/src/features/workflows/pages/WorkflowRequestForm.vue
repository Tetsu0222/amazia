<template>
  <div style="padding: 24px">
    <a-page-header title="ワークフロー申請" sub-title="Amazia Console" @back="$router.push('/workflows')" />

    <a-form :model="form" layout="vertical" @finish="onSubmit" style="max-width: 720px">

      <a-form-item label="商品" :rules="[{ required: true }]" required>
        <a-select
          v-model:value="form.productId"
          placeholder="商品を選択"
          :loading="loadingProducts"
          show-search
          :filter-option="filterProductOption"
          @change="onProductChange"
        >
          <a-select-option v-for="p in products" :key="p.id" :value="p.id">
            #{{ p.id }} {{ p.name }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="対象種別" :rules="[{ required: true }]" required>
        <a-select
          v-model:value="form.targetType"
          placeholder="対象種別を選択"
          :disabled="!form.productId"
          @change="onTargetTypeChange"
        >
          <a-select-option v-for="opt in targetTypeOptions" :key="opt.value" :value="opt.value">
            {{ opt.label }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item v-if="needsSku" label="SKU" :rules="[{ required: true }]" required>
        <a-select
          v-model:value="form.skuId"
          placeholder="SKUを選択"
          :loading="loadingSkus"
          :disabled="!form.productId"
          @change="onSkuChange"
        >
          <a-select-option v-for="sku in skus" :key="sku.id" :value="sku.id">
            #{{ sku.id }} {{ sku.color }} / {{ sku.size }}（{{ sku.skuCode }}）
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-divider>差分</a-divider>

      <a-alert
        v-if="form.targetType && fieldsHint"
        :message="fieldsHint"
        type="info"
        show-icon
        style="margin-bottom: 16px"
      />

      <a-form-item v-for="(field, idx) in form.fields" :key="idx">
        <a-space>
          <a-input v-model:value="field.field" placeholder="フィールド名" style="width: 180px" />
          <a-input v-model:value="field.before" placeholder="現在値" style="width: 160px" />
          <a-input v-model:value="field.after"  placeholder="変更後" style="width: 160px" />
          <a-button danger size="small" @click="removeField(idx)" :disabled="form.fields.length <= 1">削除</a-button>
        </a-space>
      </a-form-item>
      <a-button type="dashed" block @click="addField" style="margin-bottom: 16px">+ 差分を追加</a-button>

      <a-form-item label="理由（任意）">
        <a-textarea v-model:value="form.reason" :rows="3" />
      </a-form-item>

      <a-alert v-if="errorMsg" :message="errorMsg" type="error" style="margin-bottom: 16px" />

      <a-space>
        <a-button type="primary" html-type="submit" :loading="submitting" :disabled="!canSubmit">申請する</a-button>
        <a-button v-if="canImmediateApply" type="primary" danger :loading="submitting" :disabled="!canSubmit" @click="onImmediateApply">
          即時反映
        </a-button>
        <a-button @click="$router.push('/workflows')">キャンセル</a-button>
      </a-space>
    </a-form>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { createWorkflow, immediateApply } from '../api/workflowApi.js';
import { getAdminProducts } from '../../products/api/products.js';
import { getProductSkus, getCurrentSkuPrice, getSkuStock } from '../../skus/api/skus.js';
import { authStore } from '../../../stores/authStore.js';

const router     = useRouter();
const submitting = ref(false);
const errorMsg   = ref('');

const products       = ref([]);
const skus           = ref([]);
const loadingProducts = ref(false);
const loadingSkus     = ref(false);

const form = reactive({
  productId:  null,
  targetType: null,
  skuId:      null,
  fields:     [{ field: '', before: '', after: '' }],
  reason:     '',
});

/**
 * 対象種別の選択肢。商品レベル / SKUレベル を出し分ける。
 * 商品が選ばれていない時は disabled なので空でも OK だが、
 * 構造はそのまま保ち、UI 側で disabled 制御する。
 */
const targetTypeOptions = computed(() => [
  { value: 'product', label: '商品公開（商品レベル）',     scope: 'product' },
  { value: 'stock',   label: '在庫数変更（SKUレベル）',    scope: 'sku' },
  { value: 'price',   label: '価格変更（SKUレベル）',      scope: 'sku' },
]);

const needsSku = computed(() => {
  const opt = targetTypeOptions.value.find(o => o.value === form.targetType);
  return opt?.scope === 'sku';
});

const fieldsHint = computed(() => {
  switch (form.targetType) {
    case 'product': return '差分のフィールド名は statusCode（公開状態）を想定しています。例: before=null, after="ON_SALE"';
    case 'stock':   return '差分のフィールド名は quantity を指定してください。';
    case 'price':   return '差分のフィールド名は price を指定してください。';
    default: return '';
  }
});

const canImmediateApply = computed(() => authStore.isApprover);

const canSubmit = computed(() => {
  if (!form.productId)  return false;
  if (!form.targetType) return false;
  if (needsSku.value && !form.skuId) return false;
  return form.fields.some(f => f.field);
});

function filterProductOption(input, option) {
  const text = String(option.children?.[0]?.children ?? '').toLowerCase();
  return text.includes(String(input).toLowerCase());
}

async function loadProducts() {
  loadingProducts.value = true;
  try {
    products.value = await getAdminProducts();
  } catch (e) {
    errorMsg.value = '商品一覧の取得に失敗しました';
  } finally {
    loadingProducts.value = false;
  }
}

async function loadSkus(productId) {
  loadingSkus.value = true;
  try {
    skus.value = await getProductSkus(productId);
  } catch (e) {
    skus.value = [];
    errorMsg.value = 'SKU一覧の取得に失敗しました';
  } finally {
    loadingSkus.value = false;
  }
}

async function onProductChange() {
  // 種別 / SKU / 差分 をリセット
  form.targetType = null;
  form.skuId      = null;
  form.fields     = [{ field: '', before: '', after: '' }];
  skus.value      = [];
  if (form.productId) {
    await loadSkus(form.productId);
  }
}

function onTargetTypeChange() {
  form.skuId  = null;
  form.fields = [{ field: defaultFieldName(form.targetType), before: '', after: '' }];
}

async function onSkuChange() {
  if (!form.skuId || !form.targetType) return;
  // 現在値を取得して before に自動セット
  try {
    if (form.targetType === 'price') {
      const price = await getCurrentSkuPrice(form.skuId);
      const current = price?.price;
      form.fields = [{ field: 'price', before: current ?? '', after: '' }];
    } else if (form.targetType === 'stock') {
      const stock = await getSkuStock(form.skuId);
      const current = stock?.quantity;
      form.fields = [{ field: 'quantity', before: current ?? '', after: '' }];
    }
  } catch {
    // 取得失敗は致命でないので無視（手動入力可）
  }
}

function defaultFieldName(targetType) {
  return ({ product: 'statusCode', stock: 'quantity', price: 'price' })[targetType] ?? '';
}

function addField() {
  form.fields.push({ field: '', before: '', after: '' });
}
function removeField(idx) {
  form.fields.splice(idx, 1);
}

function buildPayload() {
  const targetId = needsSku.value ? form.skuId : form.productId;
  return {
    targetType: form.targetType,
    targetId,
    fields: form.fields
      .filter(f => f.field)
      .map(f => ({ field: f.field, before: parseValue(f.before), after: parseValue(f.after) })),
    meta: form.reason ? { reason: form.reason } : null,
  };
}

function parseValue(v) {
  if (v === '' || v == null) return null;
  const n = Number(v);
  return Number.isFinite(n) && String(n) === String(v).trim() ? n : v;
}

async function onSubmit() {
  errorMsg.value = '';
  submitting.value = true;
  try {
    await createWorkflow(buildPayload());
    message.success('申請しました');
    router.push('/workflows');
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '申請に失敗しました';
  } finally {
    submitting.value = false;
  }
}

async function onImmediateApply() {
  errorMsg.value = '';
  submitting.value = true;
  try {
    await immediateApply(buildPayload());
    message.success('即時反映しました');
    router.push('/workflows');
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '即時反映に失敗しました';
  } finally {
    submitting.value = false;
  }
}

onMounted(loadProducts);
</script>
