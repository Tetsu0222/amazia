<template>
  <div style="padding: 24px; max-width: 800px">
    <a-page-header title="SKU価格管理" sub-title="Amazia Console" />

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
      <a-table
        :dataSource="prices"
        :columns="columns"
        :loading="pricesLoading"
        rowKey="id"
        style="margin-bottom: 24px"
      />

      <a-divider>価格を登録</a-divider>

      <a-form
        :model="form"
        :rules="rules"
        ref="formRef"
        layout="vertical"
        style="max-width: 480px"
        @finish="handleSubmit"
      >
        <a-form-item label="価格（円）" name="price">
          <a-input-number
            v-model:value="form.price"
            :min="0"
            style="width: 100%"
            placeholder="例: 1980"
          />
        </a-form-item>

        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="適用開始日" name="startDate">
              <a-date-picker
                v-model:value="form.startDate"
                value-format="YYYY-MM-DD"
                placeholder="開始日"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="適用終了日" name="endDate">
              <a-date-picker
                v-model:value="form.endDate"
                value-format="YYYY-MM-DD"
                placeholder="未設定 = 恒久適用"
                style="width: 100%"
              />
            </a-form-item>
          </a-col>
        </a-row>

        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="submitting">登録</a-button>
        </a-form-item>
      </a-form>
    </template>

    <a-empty v-else description="商品とSKUを選択してください" style="margin-top: 48px" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
// 管理画面では公開期間外の商品（予約開始前など）も対象にするため admin 一覧を使う
import { getAdminProducts } from '../../products/api/products';
import { getProductSkus, getCurrentSkuPrice, registerCurrentSkuPrice } from '../api/skus';

const products = ref([]);
const productsLoading = ref(false);
const skus = ref([]);
const skusLoading = ref(false);
const prices = ref([]);
const pricesLoading = ref(false);
const selectedProductId = ref(null);
const selectedSkuId = ref(null);
const submitting = ref(false);
const formRef = ref();

const form = ref({ price: null, startDate: null, endDate: null });

const rules = {
  price: [{ required: true, message: '価格は必須です' }],
  startDate: [{ required: true, message: '適用開始日は必須です' }],
};

const columns = [
  { title: '価格（円）', dataIndex: 'price', key: 'price' },
  { title: '適用開始日', dataIndex: 'startDate', key: 'startDate' },
  { title: '適用終了日', dataIndex: 'endDate', key: 'endDate', customRender: ({ text }) => text ?? '恒久' },
];

onMounted(async () => {
  productsLoading.value = true;
  try {
    products.value = await getAdminProducts();
  } catch {
    message.warning('商品一覧の取得に失敗しました');
  } finally {
    productsLoading.value = false;
  }
});

const onProductChange = async (productId) => {
  selectedSkuId.value = null;
  skus.value = [];
  prices.value = [];
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
  prices.value = [];
  if (!skuId) return;
  await fetchPrices(skuId);
};

const fetchPrices = async (skuId) => {
  pricesLoading.value = true;
  try {
    prices.value = await getCurrentSkuPrice(skuId);
  } catch {
    message.warning('価格一覧の取得に失敗しました');
  } finally {
    pricesLoading.value = false;
  }
};

const handleSubmit = async () => {
  submitting.value = true;
  try {
    await registerCurrentSkuPrice(selectedSkuId.value, form.value);
    message.success('価格を登録しました');
    form.value = { price: null, startDate: null, endDate: null };
    formRef.value.resetFields();
    await fetchPrices(selectedSkuId.value);
  } catch {
    message.error('価格登録に失敗しました');
  } finally {
    submitting.value = false;
  }
};
</script>
