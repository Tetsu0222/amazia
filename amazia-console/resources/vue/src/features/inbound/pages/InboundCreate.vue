<template>
  <div style="padding: 24px; max-width: 600px">
    <a-page-header
      title="入荷登録"
      sub-title="Amazia Console"
      @back="goBack"
    />

    <a-alert
      message="商品とSKUを選択し、入荷数量を入力してください。入荷日は登録時の本日付で記録されます。倉庫は自動でデフォルトが設定されます。"
      type="info"
      show-icon
      style="margin-bottom: 12px"
    />

    <a-form layout="vertical" :model="form">
      <a-form-item label="商品" required>
        <a-select
          v-model:value="form.productId"
          placeholder="商品を選択"
          :loading="productsLoading"
          @change="onProductChange"
          allow-clear
          show-search
          :filter-option="filterProductOption"
        >
          <a-select-option v-for="p in products" :key="p.id" :value="p.id">
            {{ p.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="SKU" required>
        <a-select
          v-model:value="form.skuId"
          placeholder="SKUを選択"
          :loading="skusLoading"
          :disabled="!form.productId"
          allow-clear
        >
          <a-select-option v-for="s in skus" :key="s.id" :value="s.id">
            {{ s.skuCode }}（{{ s.color }} / {{ s.size }}）
          </a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="入荷数量" required>
        <a-input-number
          v-model:value="form.quantity"
          :min="1"
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
import { ref, reactive, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { registerInbound } from '../api/inboundApi.js';
import { getAdminProducts } from '../../products/api/products.js';
import { getProductSkus } from '../../skus/api/skus.js';

const route = useRoute();
const router = useRouter();
const submitting = ref(false);

const products = ref([]);
const productsLoading = ref(false);
const skus = ref([]);
const skusLoading = ref(false);

const form = reactive({
  productId: null,
  skuId: null,
  quantity: 1,
  supplierId: null,
});

onMounted(async () => {
  productsLoading.value = true;
  try {
    products.value = await getAdminProducts();
  } catch {
    message.warning('商品一覧の取得に失敗しました');
  } finally {
    productsLoading.value = false;
  }

  await applyInitialSelection();
});

async function applyInitialSelection() {
  const querySkuId = route.query.skuId ? Number(route.query.skuId) : null;
  const queryProductId = route.query.productId ? Number(route.query.productId) : null;
  if (!querySkuId) return;

  let productId = queryProductId;
  if (!productId) {
    for (const p of products.value) {
      try {
        const list = await getProductSkus(p.id);
        if (list.some(s => s.id === querySkuId)) {
          productId = p.id;
          skus.value = list;
          break;
        }
      } catch {
        // 個別商品の SKU 取得失敗は無視して次へ
      }
    }
  }
  if (!productId) return;

  form.productId = productId;
  if (skus.value.length === 0) {
    skusLoading.value = true;
    try {
      skus.value = await getProductSkus(productId);
    } catch {
      message.warning('SKU一覧の取得に失敗しました');
    } finally {
      skusLoading.value = false;
    }
  }
  if (skus.value.some(s => s.id === querySkuId)) {
    form.skuId = querySkuId;
  }
}

async function onProductChange(productId) {
  form.skuId = null;
  skus.value = [];
  if (!productId) return;
  skusLoading.value = true;
  try {
    skus.value = await getProductSkus(productId);
  } catch {
    message.warning('SKU一覧の取得に失敗しました');
  } finally {
    skusLoading.value = false;
  }
}

function filterProductOption(input, option) {
  const label = String(option.children?.[0]?.children ?? '').toLowerCase();
  return label.includes(String(input).toLowerCase());
}

async function onSubmit() {
  if (!form.productId || !form.skuId || !form.quantity) {
    message.warning('必須項目を入力してください');
    return;
  }
  submitting.value = true;
  try {
    const payload = {
      productId: form.productId,
      skuId: form.skuId,
      quantity: form.quantity,
    };
    if (form.supplierId) {
      payload.supplierId = form.supplierId;
    }
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
