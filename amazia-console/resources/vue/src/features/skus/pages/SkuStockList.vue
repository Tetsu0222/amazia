<template>
  <div style="padding: 24px; max-width: 900px">
    <a-page-header title="SKU在庫管理" sub-title="Amazia Console" />

    <a-form layout="inline" style="margin-bottom: 16px">
      <a-form-item label="商品">
        <a-select
          v-model:value="selectedProductId"
          placeholder="商品を選択"
          style="width: 200px"
          :loading="productsLoading"
          @change="onProductChange"
          allow-clear
        >
          <a-select-option v-for="p in products" :key="p.id" :value="p.id">
            {{ p.name }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-form-item label="SKU">
        <a-select
          v-model:value="selectedSkuId"
          placeholder="SKUを選択"
          style="width: 240px"
          :loading="skusLoading"
          :disabled="!selectedProductId"
          @change="onSkuChange"
          allow-clear
        >
          <a-select-option v-for="s in skus" :key="s.id" :value="s.id">
            {{ s.skuCode }}（{{ s.color }} / {{ s.size }}）
          </a-select-option>
        </a-select>
      </a-form-item>
    </a-form>

    <template v-if="selectedSkuId">
      <a-descriptions bordered size="small" style="margin-bottom: 24px" :column="1">
        <a-descriptions-item label="現在在庫">
          {{ currentStock != null ? currentStock + ' 個' : '—' }}
        </a-descriptions-item>
      </a-descriptions>

      <a-divider>入荷登録</a-divider>

      <a-form
        :model="receiveForm"
        :rules="receiveRules"
        ref="receiveFormRef"
        layout="inline"
        style="margin-bottom: 24px"
        @finish="handleReceive"
      >
        <a-form-item label="入荷数" name="quantity">
          <a-input-number
            v-model:value="receiveForm.quantity"
            :min="1"
            style="width: 120px"
            placeholder="例: 100"
          />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="receiving">入荷</a-button>
        </a-form-item>
      </a-form>

      <a-divider>入荷履歴</a-divider>

      <a-table
        :dataSource="history"
        :columns="historyColumns"
        :loading="historyLoading"
        rowKey="id"
      />
    </template>

    <a-empty v-else description="商品とSKUを選択してください" style="margin-top: 48px" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getProducts } from '../../products/api/products';
import { getProductSkus, getSkuStock, receiveSkuStock, getSkuStockHistory } from '../api/skus';

const products = ref([]);
const productsLoading = ref(false);
const skus = ref([]);
const skusLoading = ref(false);
const selectedProductId = ref(null);
const selectedSkuId = ref(null);
const currentStock = ref(null);
const history = ref([]);
const historyLoading = ref(false);
const receiving = ref(false);
const receiveFormRef = ref();
const receiveForm = ref({ quantity: null });

const receiveRules = {
  quantity: [{ required: true, type: 'number', min: 1, message: '1以上の数を入力してください' }],
};

const historyColumns = [
  { title: '種別',   dataIndex: 'type',      key: 'type' },
  { title: '数量',   dataIndex: 'quantity',  key: 'quantity' },
  { title: '日時',   dataIndex: 'createdAt', key: 'createdAt' },
];

onMounted(async () => {
  productsLoading.value = true;
  try {
    products.value = await getProducts();
  } catch {
    message.warning('商品一覧の取得に失敗しました');
  } finally {
    productsLoading.value = false;
  }
});

const onProductChange = async (productId) => {
  selectedSkuId.value = null;
  skus.value = [];
  currentStock.value = null;
  history.value = [];
  if (!productId) return;
  skusLoading.value = true;
  try {
    skus.value = await getProductSkus(productId);
  } catch {
    message.warning('SKU一覧の取得に失敗しました');
  } finally {
    skusLoading.value = false;
  }
};

const onSkuChange = async (skuId) => {
  currentStock.value = null;
  history.value = [];
  if (!skuId) return;
  await Promise.all([fetchStock(skuId), fetchHistory(skuId)]);
};

const fetchStock = async (skuId) => {
  try {
    const data = await getSkuStock(skuId);
    currentStock.value = data?.quantity ?? 0;
  } catch {
    currentStock.value = 0;
  }
};

const fetchHistory = async (skuId) => {
  historyLoading.value = true;
  try {
    history.value = await getSkuStockHistory(skuId);
  } catch {
    message.warning('在庫履歴の取得に失敗しました');
  } finally {
    historyLoading.value = false;
  }
};

const handleReceive = async () => {
  receiving.value = true;
  try {
    await receiveSkuStock(selectedSkuId.value, receiveForm.value);
    message.success('入荷を登録しました');
    receiveForm.value = { quantity: null };
    receiveFormRef.value.resetFields();
    await Promise.all([fetchStock(selectedSkuId.value), fetchHistory(selectedSkuId.value)]);
  } catch {
    message.error('入荷登録に失敗しました');
  } finally {
    receiving.value = false;
  }
};
</script>
