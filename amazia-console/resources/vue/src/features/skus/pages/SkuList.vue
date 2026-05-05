<template>
  <div style="padding: 24px; max-width: 960px">
    <a-page-header title="SKU管理" sub-title="Amazia Console">
      <template #extra>
        <a-button @click="$router.push('/skus/stocks/import')">
          Excel一括入荷
        </a-button>
      </template>
    </a-page-header>

    <!-- 商品選択 -->
    <a-form layout="inline" style="margin-bottom: 24px">
      <a-form-item label="商品">
        <a-select
          v-model:value="selectedProductId"
          placeholder="商品を選択"
          style="width: 280px"
          :loading="productsLoading"
          @change="onProductChange"
          allow-clear
        >
          <a-select-option v-for="p in products" :key="p.id" :value="p.id">
            {{ p.name }}
          </a-select-option>
        </a-select>
      </a-form-item>
    </a-form>

    <template v-if="selectedProductId">
      <!-- SKU一覧 -->
      <a-table
        :dataSource="skus"
        :columns="skuColumns"
        :loading="skusLoading"
        rowKey="id"
        :row-class-name="(r) => r.id === selectedSkuId ? 'selected-row' : ''"
        style="margin-bottom: 8px"
        @row-click="onSkuRowClick"
        :custom-row="(r) => ({ onClick: () => onSkuRowClick(r) })"
      />

      <!-- SKU追加フォーム -->
      <a-form
        :model="skuForm"
        :rules="skuRules"
        ref="skuFormRef"
        layout="inline"
        style="margin-bottom: 32px"
        @finish="handleSkuSubmit"
      >
        <a-form-item label="色" name="color">
          <a-input v-model:value="skuForm.color" placeholder="例: Red" style="width: 120px" />
        </a-form-item>
        <a-form-item label="サイズ" name="size">
          <a-input v-model:value="skuForm.size" placeholder="例: M" style="width: 100px" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="skuSubmitting">SKUを追加</a-button>
        </a-form-item>
      </a-form>

      <!-- SKU詳細パネル -->
      <template v-if="selectedSkuId">
        <a-divider>選択中のSKU：{{ selectedSkuLabel }}</a-divider>

        <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
          <!-- 価格管理タブ -->
          <a-tab-pane key="price" tab="価格管理">
            <a-table
              :dataSource="prices"
              :columns="priceColumns"
              :loading="pricesLoading"
              rowKey="id"
              size="small"
              style="margin-bottom: 16px"
            />
            <a-form
              :model="priceForm"
              :rules="priceRules"
              ref="priceFormRef"
              layout="inline"
              @finish="handlePriceSubmit"
            >
              <a-form-item label="価格（円）" name="price">
                <a-input-number v-model:value="priceForm.price" :min="0" style="width: 120px" placeholder="例: 1980" />
              </a-form-item>
              <a-form-item label="適用開始日" name="startDate">
                <a-date-picker v-model:value="priceForm.startDate" value-format="YYYY-MM-DD" placeholder="開始日" />
              </a-form-item>
              <a-form-item label="適用終了日">
                <a-date-picker v-model:value="priceForm.endDate" value-format="YYYY-MM-DD" placeholder="未設定 = 恒久" />
              </a-form-item>
              <a-form-item>
                <a-button type="primary" html-type="submit" :loading="priceSubmitting">登録</a-button>
              </a-form-item>
            </a-form>
          </a-tab-pane>

          <!-- 在庫管理タブ -->
          <a-tab-pane key="stock" tab="在庫管理">
            <a-descriptions bordered size="small" :column="1" style="margin-bottom: 16px; max-width: 300px">
              <a-descriptions-item label="現在在庫">
                {{ currentStock != null ? currentStock + ' 個' : '—' }}
              </a-descriptions-item>
            </a-descriptions>
            <a-form
              :model="stockForm"
              :rules="stockRules"
              ref="stockFormRef"
              layout="inline"
              style="margin-bottom: 16px"
              @finish="handleStockReceive"
            >
              <a-form-item label="入荷数" name="quantity">
                <a-input-number v-model:value="stockForm.quantity" :min="1" style="width: 120px" placeholder="例: 100" />
              </a-form-item>
              <a-form-item>
                <a-button type="primary" html-type="submit" :loading="stockReceiving">入荷登録</a-button>
              </a-form-item>
            </a-form>
            <a-table
              :dataSource="stockHistory"
              :columns="stockHistoryColumns"
              :loading="stockHistoryLoading"
              rowKey="id"
              size="small"
            />
          </a-tab-pane>

          <!-- 画像管理タブ -->
          <a-tab-pane key="image" tab="画像管理">
            <div style="display: flex; gap: 12px; flex-wrap: wrap; margin-bottom: 16px">
              <div v-for="img in images" :key="img.id" style="position: relative; width: 120px">
                <img
                  :src="`/api/skus/${selectedSkuId}/image-file/${img.imagePath.split('/').pop()}`"
                  style="width: 120px; height: 120px; object-fit: contain; border: 1px solid #d9d9d9; border-radius: 4px"
                />
                <div style="text-align: center; font-size: 12px; color: #888; margin-top: 4px">
                  {{ img.sortOrder === 1 ? 'メイン' : `順: ${img.sortOrder}` }}
                </div>
              </div>
              <a-empty v-if="images.length === 0" description="画像がありません" style="width: 120px" />
            </div>
            <a-upload
              accept=".png"
              :show-upload-list="false"
              :before-upload="handleImageUpload"
              :disabled="imageUploading"
            >
              <a-button :loading="imageUploading">
                画像を選択（PNG / 200KB以下）
              </a-button>
            </a-upload>
          </a-tab-pane>
        </a-tabs>
      </template>

      <a-empty v-else description="SKUを選択すると価格・在庫・画像を管理できます" style="margin-top: 24px" />
    </template>

    <a-empty v-else description="商品を選択してください" style="margin-top: 48px" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
import { getProducts } from '../../products/api/products';
import {
  getProductSkus, createProductSku,
  getSkuPrices, createSkuPrice,
  getSkuStock, receiveSkuStock, getSkuStockHistory,
  getSkuImages, uploadSkuImage,
} from '../api/skus';

const route = useRoute();
const products = ref([]);
const productsLoading = ref(false);
const skus = ref([]);
const skusLoading = ref(false);
const selectedProductId = ref(null);
const selectedSkuId = ref(null);
const selectedSkuLabel = ref('');

// SKU追加
const skuFormRef = ref();
const skuForm = ref({ color: '', size: '' });
const skuSubmitting = ref(false);
const skuRules = {
  color: [{ required: true, message: '色は必須です' }],
  size:  [{ required: true, message: 'サイズは必須です' }],
};
const skuColumns = [
  { title: 'SKUコード', dataIndex: 'skuCode', key: 'skuCode' },
  { title: '色',        dataIndex: 'color',   key: 'color' },
  { title: 'サイズ',    dataIndex: 'size',    key: 'size' },
  { title: 'ステータス', dataIndex: 'status', key: 'status' },
  { title: '',          key: 'action',
    customRender: ({ record }) => record.id === selectedSkuId.value ? '◀ 選択中' : '選択',
  },
];

// 価格
const prices = ref([]);
const pricesLoading = ref(false);
const priceFormRef = ref();
const priceForm = ref({ price: null, startDate: null, endDate: null });
const priceSubmitting = ref(false);
const priceRules = {
  price:     [{ required: true, message: '価格は必須です' }],
  startDate: [{ required: true, message: '適用開始日は必須です' }],
};
const priceColumns = [
  { title: '価格（円）', dataIndex: 'price',     key: 'price' },
  { title: '適用開始日', dataIndex: 'startDate', key: 'startDate' },
  { title: '適用終了日', dataIndex: 'endDate',   key: 'endDate',
    customRender: ({ text }) => text ?? '恒久' },
];

// 在庫
const currentStock = ref(null);
const stockForm = ref({ quantity: null });
const stockFormRef = ref();
const stockReceiving = ref(false);
const stockHistory = ref([]);
const stockHistoryLoading = ref(false);
const stockRules = {
  quantity: [{ required: true, type: 'number', min: 1, message: '1以上の数を入力してください' }],
};
const stockHistoryColumns = [
  { title: '種別',   dataIndex: 'type',      key: 'type' },
  { title: '数量',   dataIndex: 'quantity',  key: 'quantity' },
  { title: '日時',   dataIndex: 'createdAt', key: 'createdAt' },
];

// 画像
const images = ref([]);
const imageUploading = ref(false);

// タブ
const activeTab = ref('price');
const loadedTabs = ref(new Set());

onMounted(async () => {
  productsLoading.value = true;
  try {
    products.value = await getProducts();
  } catch {
    message.warning('商品一覧の取得に失敗しました');
    return;
  } finally {
    productsLoading.value = false;
  }

  const initialProductId = route.query.productId
    ? Number(route.query.productId)
    : null;
  if (initialProductId) {
    selectedProductId.value = initialProductId;
    await fetchSkus(initialProductId);
  }
});

const onProductChange = async (productId) => {
  selectedSkuId.value = null;
  selectedSkuLabel.value = '';
  skus.value = [];
  clearSkuDetail();
  if (!productId) return;
  await fetchSkus(productId);
};

const fetchSkus = async (productId) => {
  skusLoading.value = true;
  try {
    skus.value = await getProductSkus(productId);
  } catch {
    message.warning('SKU一覧の取得に失敗しました');
  } finally {
    skusLoading.value = false;
  }
};

const onSkuRowClick = async (record) => {
  selectedSkuId.value = record.id;
  selectedSkuLabel.value = `${record.skuCode}（${record.color} / ${record.size}）`;
  clearSkuDetail();
  await fetchTabData('price', record.id);
};

const clearSkuDetail = () => {
  prices.value = [];
  currentStock.value = null;
  stockHistory.value = [];
  images.value = [];
  activeTab.value = 'price';
  loadedTabs.value = new Set();
};

const onTabChange = async (tab) => {
  await fetchTabData(tab, selectedSkuId.value);
};

const fetchTabData = async (tab, skuId) => {
  if (loadedTabs.value.has(tab)) return;
  if (tab === 'price') {
    await fetchPrices(skuId);
  } else if (tab === 'stock') {
    await Promise.all([fetchStock(skuId), fetchStockHistory(skuId)]);
  } else if (tab === 'image') {
    await fetchImages(skuId);
  }
  loadedTabs.value.add(tab);
};

// SKU追加
const handleSkuSubmit = async () => {
  skuSubmitting.value = true;
  try {
    await createProductSku(selectedProductId.value, skuForm.value);
    message.success('SKUを追加しました');
    skuForm.value = { color: '', size: '' };
    skuFormRef.value.resetFields();
    await fetchSkus(selectedProductId.value);
  } catch {
    message.error('SKUの追加に失敗しました');
  } finally {
    skuSubmitting.value = false;
  }
};

// 価格
const fetchPrices = async (skuId) => {
  pricesLoading.value = true;
  try {
    const data = await getSkuPrices(skuId);
    prices.value = data ? [data] : [];
  } catch {
    prices.value = [];
  } finally {
    pricesLoading.value = false;
  }
};

const handlePriceSubmit = async () => {
  priceSubmitting.value = true;
  try {
    await createSkuPrice(selectedSkuId.value, priceForm.value);
    message.success('価格を登録しました');
    priceForm.value = { price: null, startDate: null, endDate: null };
    priceFormRef.value.resetFields();
    await fetchPrices(selectedSkuId.value);
  } catch {
    message.error('価格登録に失敗しました');
  } finally {
    priceSubmitting.value = false;
  }
};

// 在庫
const fetchStock = async (skuId) => {
  try {
    const data = await getSkuStock(skuId);
    currentStock.value = data?.quantity ?? 0;
  } catch {
    currentStock.value = 0;
  }
};

const fetchStockHistory = async (skuId) => {
  stockHistoryLoading.value = true;
  try {
    stockHistory.value = await getSkuStockHistory(skuId) ?? [];
  } catch {
    stockHistory.value = [];
    message.warning('在庫履歴の取得に失敗しました');
  } finally {
    stockHistoryLoading.value = false;
  }
};

const handleStockReceive = async () => {
  stockReceiving.value = true;
  try {
    await receiveSkuStock(selectedSkuId.value, stockForm.value);
    message.success('入荷を登録しました');
    stockForm.value = { quantity: null };
    stockFormRef.value.resetFields();
    await Promise.all([fetchStock(selectedSkuId.value), fetchStockHistory(selectedSkuId.value)]);
  } catch {
    message.error('入荷登録に失敗しました');
  } finally {
    stockReceiving.value = false;
  }
};

// 画像
const fetchImages = async (skuId) => {
  try {
    images.value = await getSkuImages(skuId) ?? [];
  } catch {
    images.value = [];
  }
};

const handleImageUpload = async (file) => {
  imageUploading.value = true;
  try {
    await uploadSkuImage(selectedSkuId.value, file);
    message.success('画像をアップロードしました');
    await fetchImages(selectedSkuId.value);
  } catch {
    message.error('画像のアップロードに失敗しました（PNG・200KB以下を確認してください）');
  } finally {
    imageUploading.value = false;
  }
  return false;
};
</script>

<style scoped>
:deep(.selected-row) {
  background-color: #e6f4ff;
}
:deep(.ant-table-row) {
  cursor: pointer;
}
</style>
