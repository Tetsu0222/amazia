<template>
  <div style="padding: 24px">
    <a-page-header title="SKU管理" sub-title="Amazia Console" />

    <!-- 商品一覧（最初から表示・行展開で SKU を出す） -->
    <a-table
      :columns="productColumns"
      :data-source="products"
      :loading="productsLoading"
      row-key="id"
      :expand-row-by-click="true"
      :expanded-row-keys="expandedRowKeys"
      :row-class-name="(r) => (r.isActive ? '' : 'row-inactive')"
      @expand="onProductExpand"
    >
      <template #expandedRowRender="{ record }">
        <div style="padding: 8px 0">
          <a-spin v-if="skuLoadingMap[record.id]" />
          <template v-else>
            <a-empty
              v-if="!skuMap[record.id] || skuMap[record.id].length === 0"
              description="SKUが登録されていません"
              :image-style="{ height: '40px' }"
              style="margin: 8px 0"
            />
            <a-table
              v-else
              :columns="skuColumns"
              :data-source="skuMap[record.id]"
              row-key="id"
              size="small"
              :pagination="false"
              style="margin-bottom: 12px"
            >
              <template #bodyCell="{ column, record: sku }">
                <template v-if="column.key === 'releaseStatus'">
                  <a-tag :color="isReleased(record) ? 'green' : 'blue'">
                    {{ isReleased(record) ? '発売中' : '発売前' }}
                  </a-tag>
                </template>
                <template v-if="column.key === 'preorderStartDate'">
                  {{ record.preorderStartDate ?? '公開と同時' }}
                </template>
                <template v-if="column.key === 'releaseDate'">
                  {{ record.releaseDate ?? '未設定' }}
                </template>
                <template v-if="column.key === 'action'">
                  <a-button size="small" type="primary" @click.stop="openSkuModal(record, sku)">
                    選択
                  </a-button>
                </template>
              </template>
            </a-table>

            <!-- SKU 追加フォーム（その商品配下） -->
            <a-form
              :model="getSkuForm(record.id)"
              layout="inline"
              @finish="handleSkuSubmit(record.id)"
              @click.stop
            >
              <a-form-item label="色">
                <a-input
                  v-model:value="getSkuForm(record.id).color"
                  placeholder="例: Red"
                  style="width: 120px"
                />
              </a-form-item>
              <a-form-item label="サイズ">
                <a-input
                  v-model:value="getSkuForm(record.id).size"
                  placeholder="例: M"
                  style="width: 100px"
                />
              </a-form-item>
              <a-form-item>
                <a-button
                  type="primary"
                  html-type="submit"
                  :loading="!!skuSubmittingMap[record.id]"
                >
                  SKUを追加
                </a-button>
              </a-form-item>
            </a-form>
          </template>
        </div>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'skuCount'">
          <a-badge
            :count="record.skuCount"
            :number-style="{ backgroundColor: record.skuCount > 0 ? '#1677ff' : '#d9d9d9' }"
            show-zero
          />
        </template>
        <template v-if="column.key === 'releaseStatus'">
          <a-tag :color="isReleased(record) ? 'green' : 'blue'">
            {{ isReleased(record) ? '発売中' : '発売前' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'published'">
          <a-badge
            :status="isPublished(record) ? 'success' : 'default'"
            :text="isPublished(record) ? '公開中' : '非公開'"
          />
        </template>
        <template v-if="column.key === 'active'">
          <a-tag :color="record.isActive ? 'green' : 'red'">
            {{ record.isActive ? '有効' : '無効' }}
          </a-tag>
        </template>
      </template>
    </a-table>

    <!-- SKU詳細モーダル -->
    <a-modal
      v-model:open="skuModalOpen"
      :title="`SKU詳細：${selectedSkuLabel}`"
      width="780px"
      :footer="null"
      destroy-on-close
      @cancel="closeSkuModal"
    >
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

        <!-- 在庫管理タブ（参照のみ。入荷登録は「入荷管理」画面へ移譲） -->
        <a-tab-pane key="stock" tab="在庫管理">
          <a-alert
            message="入荷登録は「入荷管理」画面から行ってください。"
            type="info"
            show-icon
            style="margin-bottom: 12px"
          />
          <a-descriptions bordered size="small" :column="1" style="margin-bottom: 16px; max-width: 300px">
            <a-descriptions-item label="現在在庫">
              {{ currentStock != null ? currentStock + ' 個' : '—' }}
            </a-descriptions-item>
          </a-descriptions>
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
                :src="buildImageSrc(img)"
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
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { useRoute } from 'vue-router';
import { message } from 'ant-design-vue';
// 管理画面では公開期間外の商品（予約開始前など）も SKU 編集対象にするため admin 一覧を使う
import { getAdminProducts } from '../../products/api/products';
import {
  getProductSkus, createProductSku,
  getSkuPrices, createSkuPrice,
  getSkuStock, getSkuStockHistory,
  getSkuImages, uploadSkuImage,
} from '../api/skus';

const route = useRoute();

// 商品一覧
const products = ref([]);
const productsLoading = ref(false);
const expandedRowKeys = ref([]);
const skuMap = ref({});            // productId -> sku[]
const skuLoadingMap = ref({});     // productId -> bool

// SKU 追加フォーム（商品単位に独立したフォーム state を保持）
const skuForms = ref({});          // productId -> { color, size }
const skuSubmittingMap = ref({});  // productId -> bool
const getSkuForm = (productId) => {
  if (!skuForms.value[productId]) {
    skuForms.value[productId] = { color: '', size: '' };
  }
  return skuForms.value[productId];
};

// SKU モーダル
const skuModalOpen = ref(false);
const selectedSkuId = ref(null);
const selectedSkuLabel = ref('');
const activeTab = ref('price');
const loadedTabs = ref(new Set());

// 画像 URL ビルダー（モーダル内で利用）
const apiBase = `${import.meta.env.BASE_URL}api`;
const buildImageSrc = (img) =>
  `${apiBase}/skus/${selectedSkuId.value}/image-file/${img.imagePath.split('/').pop()}`;

// 商品列定義
const productColumns = [
  { title: 'ID',        dataIndex: 'id',         key: 'id',         width: 70 },
  { title: '商品名',    dataIndex: 'name',       key: 'name' },
  { title: 'SKU数',     key: 'skuCount',                            width: 80 },
  { title: '発売',      key: 'releaseStatus',                       width: 100 },
  { title: '公開状態',  key: 'published',                           width: 100 },
  { title: '有効/無効', key: 'active',                              width: 90 },
];

// SKU 列定義（Step 1.5 の確定列を踏襲）
const skuColumns = [
  { title: 'SKUコード',  dataIndex: 'skuCode', key: 'skuCode' },
  { title: '色',         dataIndex: 'color',   key: 'color' },
  { title: 'サイズ',     dataIndex: 'size',    key: 'size' },
  { title: 'ステータス', key: 'releaseStatus' },
  { title: '予約開始日', key: 'preorderStartDate' },
  { title: '発売日',     key: 'releaseDate' },
  { title: '',           key: 'action',        width: 90 },
];

// 商品単位の発売前後判定（Step 1.5 と同ロジック）
const isReleased = (product) => {
  const releaseDate = product?.releaseDate;
  if (!releaseDate) return true;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(releaseDate) <= today;
};

const isPublished = (product) => {
  const now = new Date();
  if (product.publishStart && new Date(product.publishStart) > now) return false;
  if (product.publishEnd   && new Date(product.publishEnd)   < now) return false;
  return true;
};

// モーダル内 state
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

// 在庫（参照のみ）
const currentStock = ref(null);
const stockHistory = ref([]);
const stockHistoryLoading = ref(false);
const stockHistoryColumns = [
  { title: '種別',   dataIndex: 'type',      key: 'type' },
  { title: '数量',   dataIndex: 'quantity',  key: 'quantity' },
  { title: '日時',   dataIndex: 'createdAt', key: 'createdAt' },
];

// 画像
const images = ref([]);
const imageUploading = ref(false);

onMounted(async () => {
  await fetchProducts();

  const initialProductId = route.query.productId
    ? Number(route.query.productId)
    : null;
  if (initialProductId) {
    const target = products.value.find(p => p.id === initialProductId);
    if (target) {
      expandedRowKeys.value = [...expandedRowKeys.value, target.id];
      await loadSkusFor(target.id);
    }
  }
});

const fetchProducts = async () => {
  productsLoading.value = true;
  try {
    products.value = await getAdminProducts();
  } catch {
    message.warning('商品一覧の取得に失敗しました');
  } finally {
    productsLoading.value = false;
  }
};

const onProductExpand = async (expanded, record) => {
  if (!expanded) {
    expandedRowKeys.value = expandedRowKeys.value.filter(k => k !== record.id);
    return;
  }
  expandedRowKeys.value = [...expandedRowKeys.value, record.id];
  if (skuMap.value[record.id]) return;
  await loadSkusFor(record.id);
};

const loadSkusFor = async (productId) => {
  skuLoadingMap.value[productId] = true;
  try {
    skuMap.value = { ...skuMap.value, [productId]: await getProductSkus(productId) };
  } catch {
    message.warning('SKU一覧の取得に失敗しました');
    skuMap.value = { ...skuMap.value, [productId]: [] };
  } finally {
    skuLoadingMap.value[productId] = false;
  }
};

// SKU 追加
const handleSkuSubmit = async (productId) => {
  const form = getSkuForm(productId);
  if (!form.color || !form.size) {
    message.warning('色とサイズは必須です');
    return;
  }
  skuSubmittingMap.value[productId] = true;
  try {
    await createProductSku(productId, form);
    message.success('SKUを追加しました');
    skuForms.value[productId] = { color: '', size: '' };
    await loadSkusFor(productId);
  } catch {
    message.error('SKUの追加に失敗しました');
  } finally {
    skuSubmittingMap.value[productId] = false;
  }
};

// SKU モーダル開閉
const openSkuModal = async (product, sku) => {
  selectedSkuId.value = sku.id;
  selectedSkuLabel.value = `${sku.skuCode}（${sku.color} / ${sku.size}）`;
  resetModalState();
  skuModalOpen.value = true;
  await fetchTabData('price', sku.id);
};

const closeSkuModal = () => {
  skuModalOpen.value = false;
  selectedSkuId.value = null;
  selectedSkuLabel.value = '';
  resetModalState();
};

const resetModalState = () => {
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
  if (!skuId) return;
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
:deep(.row-inactive) td {
  background-color: #fafafa;
  color: #999;
}
</style>
