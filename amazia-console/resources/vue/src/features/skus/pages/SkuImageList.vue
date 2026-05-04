<template>
  <div style="padding: 24px; max-width: 900px">
    <a-page-header title="SKU画像管理" sub-title="Amazia Console" />

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
      <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 24px">
        <div
          v-for="img in images"
          :key="img.id"
          style="position: relative; width: 120px"
        >
          <img
            :src="`/storage/Product/images/${img.imagePath}`"
            style="width: 120px; height: 120px; object-fit: contain; border: 1px solid #d9d9d9; border-radius: 4px"
          />
          <div style="text-align: center; font-size: 12px; color: #888; margin-top: 4px">
            {{ img.sortOrder === 1 ? 'メイン' : `順: ${img.sortOrder}` }}
          </div>
        </div>
        <a-empty
          v-if="images.length === 0"
          description="画像がありません"
          style="width: 120px"
        />
      </div>

      <a-divider>画像をアップロード（PNG / 200KB以下）</a-divider>

      <a-upload
        accept=".png"
        :show-upload-list="false"
        :before-upload="handleUpload"
        :disabled="uploading"
      >
        <a-button :loading="uploading">
          <template #icon><upload-outlined /></template>
          画像を選択
        </a-button>
      </a-upload>
    </template>

    <a-empty v-else description="商品とSKUを選択してください" style="margin-top: 48px" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { UploadOutlined } from '@ant-design/icons-vue';
import { getProducts } from '../../products/api/products';
import { getProductSkus, getSkuImages, uploadSkuImage } from '../api/skus';

const products = ref([]);
const productsLoading = ref(false);
const skus = ref([]);
const skusLoading = ref(false);
const images = ref([]);
const selectedProductId = ref(null);
const selectedSkuId = ref(null);
const uploading = ref(false);

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
  images.value = [];
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
  images.value = [];
  if (!skuId) return;
  await fetchImages(skuId);
};

const fetchImages = async (skuId) => {
  try {
    images.value = await getSkuImages(skuId);
  } catch {
    message.warning('画像一覧の取得に失敗しました');
  }
};

const handleUpload = async (file) => {
  uploading.value = true;
  try {
    await uploadSkuImage(selectedSkuId.value, file);
    message.success('画像をアップロードしました');
    await fetchImages(selectedSkuId.value);
  } catch {
    message.error('画像のアップロードに失敗しました（PNG・200KB以下を確認してください）');
  } finally {
    uploading.value = false;
  }
  return false;
};
</script>
