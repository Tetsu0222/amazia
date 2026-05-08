<template>
  <div style="padding: 24px">
    <a-page-header title="SKU管理">
      <template #extra>
        <a-space>
          <span style="color: rgba(0, 0, 0, 0.45); font-size: 13px">
            {{ countLabel }}
          </span>
          <a-button size="small" @click="expandAll">全展開</a-button>
          <a-button size="small" @click="collapseAll">全折りたたみ</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-card size="small" style="margin-bottom: 16px" :body-style="{ padding: '12px 16px' }">
      <a-form layout="vertical" :model="searchForm">
        <div class="search-row">
          <a-form-item label="商品名" class="search-item search-item--name">
            <a-input
              v-model:value="searchForm.name"
              placeholder="部分一致で検索"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="発売状態" class="search-item">
            <a-radio-group v-model:value="searchForm.releaseFilter" button-style="solid">
              <a-radio-button value="all">すべて</a-radio-button>
              <a-radio-button value="released">発売中のみ</a-radio-button>
              <a-radio-button value="preRelease">発売前のみ</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="有効/無効" class="search-item">
            <a-radio-group v-model:value="searchForm.activeFilter" button-style="solid">
              <a-radio-button value="all">すべて</a-radio-button>
              <a-radio-button value="active">有効のみ</a-radio-button>
              <a-radio-button value="inactive">無効のみ</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label=" " class="search-item search-item--clear">
            <a-button @click="resetSearch">クリア</a-button>
          </a-form-item>
        </div>
      </a-form>
    </a-card>

    <a-table
      :columns="productColumns"
      :data-source="filteredProducts"
      :loading="productsLoading"
      row-key="id"
      :expanded-row-keys="expandedRowKeys"
      :row-class-name="(r) => (r.isActive ? '' : 'row-inactive')"
      :pagination="paginationConfig"
      @expand="onProductExpand"
    >
      <template #expandIcon="{ expanded, record }">
        <a-button
          type="text"
          size="small"
          class="expand-toggle"
          @click.stop="onProductExpand(!expanded, record)"
        >
          {{ expanded ? '−' : '+' }}
        </a-button>
      </template>

      <template #expandedRowRender="{ record }">
        <div class="expand-body">
          <a-spin v-if="skuLoadingMap[record.id]" />
          <template v-else>
            <a-card size="small" title="既存 SKU 一覧" class="expand-card">
              <a-empty
                v-if="!skuMap[record.id] || skuMap[record.id].length === 0"
                description="SKUが登録されていません"
                :image-style="{ height: '32px' }"
                style="margin: 4px 0"
              />
              <a-table
                v-else
                :columns="skuColumns"
                :data-source="skuMap[record.id]"
                row-key="id"
                size="small"
                :pagination="false"
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
                    <a-button size="small" @click.stop="openSkuModal(record, sku)">
                      <template #icon><EditOutlined /></template>
                      詳細・編集
                    </a-button>
                  </template>
                </template>
              </a-table>
            </a-card>

            <a-card size="small" title="＋ SKU を追加" class="expand-card">
              <a-form
                :model="getSkuForm(record.id)"
                layout="inline"
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
                  <a-popconfirm
                    v-if="!getSkuForm(record.id).color && !getSkuForm(record.id).size"
                    title="色・サイズなしの SKU を作成しますか？"
                    ok-text="作成"
                    cancel-text="キャンセル"
                    @confirm="handleSkuSubmit(record.id)"
                  >
                    <a-button
                      type="primary"
                      :loading="!!skuSubmittingMap[record.id]"
                    >
                      SKUを追加
                    </a-button>
                  </a-popconfirm>
                  <a-button
                    v-else
                    type="primary"
                    :loading="!!skuSubmittingMap[record.id]"
                    @click="handleSkuSubmit(record.id)"
                  >
                    SKUを追加
                  </a-button>
                </a-form-item>
              </a-form>
            </a-card>
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
        <template v-if="column.key === 'status'">
          <div class="status-cell">
            <a-badge
              :status="isPublished(record) ? 'success' : 'default'"
              :text="isPublished(record) ? '公開中' : '非公開'"
            />
            <a-tag :color="record.isActive ? 'green' : 'red'" style="margin-top: 4px">
              {{ record.isActive ? '有効' : '無効' }}
            </a-tag>
          </div>
        </template>
      </template>
    </a-table>

    <a-modal
      v-model:open="skuModalOpen"
      :title="`SKU詳細：${selectedSkuLabel}`"
      :width="modalWidth"
      :footer="null"
      destroy-on-close
      @cancel="closeSkuModal"
    >
      <a-tabs v-model:activeKey="activeTab" @change="onTabChange">
        <a-tab-pane key="price" tab="価格管理">
          <PriceManagementTab :sku-id="selectedSkuId" />
        </a-tab-pane>

        <a-tab-pane key="stock" tab="在庫管理">
          <a-alert
            message="入荷登録は「入荷管理」画面から行ってください。"
            type="info"
            show-icon
            style="margin-bottom: 12px"
          >
            <template #action>
              <a-button size="small" @click="goToInboundCreate">
                入荷登録へ
              </a-button>
            </template>
          </a-alert>
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
import { ref, computed, onMounted, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { EditOutlined } from '@ant-design/icons-vue';
import { getAdminProducts } from '../../products/api/products';
import {
  getProductSkus, createProductSku,
  getSkuStock, getSkuStockHistory,
  getSkuImages, uploadSkuImage,
} from '../api/skus';
import PriceManagementTab from './PriceManagementTab.vue';

const route = useRoute();
const router = useRouter();

const products = ref([]);
const productsLoading = ref(false);
const expandedRowKeys = ref([]);
const skuMap = ref({});
const skuLoadingMap = ref({});

const skuForms = ref({});
const skuSubmittingMap = ref({});
const getSkuForm = (productId) => {
  if (!skuForms.value[productId]) {
    skuForms.value[productId] = { color: '', size: '' };
  }
  return skuForms.value[productId];
};

const skuModalOpen = ref(false);
const selectedSkuId = ref(null);
const selectedProductId = ref(null);
const selectedSkuLabel = ref('');
const activeTab = ref('price');
const loadedTabs = ref(new Set());

const apiBase = `${import.meta.env.BASE_URL}api`;
const buildImageSrc = (img) =>
  `${apiBase}/skus/${selectedSkuId.value}/image-file/${img.imagePath.split('/').pop()}`;

const searchForm = ref({
  name: '',
  releaseFilter: 'all',
  activeFilter: 'all',
});

const resetSearch = () => {
  searchForm.value = {
    name: '',
    releaseFilter: 'all',
    activeFilter: 'all',
  };
};

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

const filteredProducts = computed(() => {
  const { name, releaseFilter, activeFilter } = searchForm.value;
  const keyword = (name || '').trim().toLowerCase();

  return products.value.filter(p => {
    if (activeFilter === 'active'   && !p.isActive) return false;
    if (activeFilter === 'inactive' &&  p.isActive) return false;

    if (keyword && !(p.name || '').toLowerCase().includes(keyword)) return false;

    if (releaseFilter === 'released'   && !isReleased(p)) return false;
    if (releaseFilter === 'preRelease' &&  isReleased(p)) return false;

    return true;
  });
});

const isFilterApplied = computed(() => {
  const { name, releaseFilter, activeFilter } = searchForm.value;
  return (
    (name || '').trim() !== '' ||
    releaseFilter !== 'all' ||
    activeFilter !== 'all'
  );
});

const countLabel = computed(() => {
  const total = products.value.length;
  const shown = filteredProducts.value.length;
  return isFilterApplied.value
    ? `全 ${total} 件中 ${shown} 件を表示（フィルタ適用中）`
    : `全 ${total} 件中 ${shown} 件を表示`;
});

const productColumns = [
  { title: 'ID',       dataIndex: 'id',   key: 'id',   width: 70 },
  { title: '商品名',   dataIndex: 'name', key: 'name' },
  { title: 'SKU数',    key: 'skuCount',                width: 80 },
  { title: '発売',     key: 'releaseStatus',           width: 100 },
  { title: '状態',     key: 'status',                  width: 110 },
];

const skuColumns = [
  { title: 'SKUコード',  dataIndex: 'skuCode', key: 'skuCode' },
  { title: '色',         dataIndex: 'color',   key: 'color' },
  { title: 'サイズ',     dataIndex: 'size',    key: 'size' },
  { title: 'ステータス', key: 'releaseStatus' },
  { title: '予約開始日', key: 'preorderStartDate' },
  { title: '発売日',     key: 'releaseDate' },
  { title: '',           key: 'action',        width: 130 },
];

const paginationConfig = {
  pageSize: 20,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
};

const windowWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1280);
const onResize = () => { windowWidth.value = window.innerWidth; };
const modalWidth = computed(() => Math.min(900, Math.max(640, windowWidth.value - 80)));

const currentStock = ref(null);
const stockHistory = ref([]);
const stockHistoryLoading = ref(false);
const stockHistoryColumns = [
  { title: '種別',   dataIndex: 'type',      key: 'type' },
  { title: '数量',   dataIndex: 'quantity',  key: 'quantity' },
  { title: '日時',   dataIndex: 'createdAt', key: 'createdAt' },
];

const images = ref([]);
const imageUploading = ref(false);

onMounted(async () => {
  if (typeof window !== 'undefined') {
    window.addEventListener('resize', onResize);
  }
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

onBeforeUnmount(() => {
  if (typeof window !== 'undefined') {
    window.removeEventListener('resize', onResize);
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

const expandAll = async () => {
  const targets = filteredProducts.value;
  expandedRowKeys.value = targets.map(p => p.id);
  await Promise.all(
    targets
      .filter(p => !skuMap.value[p.id])
      .map(p => loadSkusFor(p.id))
  );
};

const collapseAll = () => {
  expandedRowKeys.value = [];
};

const handleSkuSubmit = async (productId) => {
  const form = getSkuForm(productId);
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

const openSkuModal = async (product, sku) => {
  selectedSkuId.value = sku.id;
  selectedProductId.value = product.id;
  selectedSkuLabel.value = `${sku.skuCode}（${sku.color} / ${sku.size}）`;
  resetModalState();
  skuModalOpen.value = true;
};

const closeSkuModal = () => {
  skuModalOpen.value = false;
  selectedSkuId.value = null;
  selectedProductId.value = null;
  selectedSkuLabel.value = '';
  resetModalState();
};

const resetModalState = () => {
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
  if (tab === 'stock') {
    await Promise.all([fetchStock(skuId), fetchStockHistory(skuId)]);
  } else if (tab === 'image') {
    await fetchImages(skuId);
  }
  loadedTabs.value.add(tab);
};

const goToInboundCreate = () => {
  if (!selectedSkuId.value) return;
  const query = { skuId: selectedSkuId.value };
  if (selectedProductId.value) query.productId = selectedProductId.value;
  router.push({ path: '/inbound/create', query });
};

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

.search-row {
  display: grid;
  grid-template-columns: 2fr auto auto 1fr;
  gap: 0 16px;
  align-items: end;
}

.search-row :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.search-item--clear {
  justify-self: end;
}

@media (max-width: 768px) {
  .search-row {
    grid-template-columns: 1fr;
  }
  .search-item--clear {
    justify-self: start;
  }
}

.expand-toggle {
  margin-right: 4px;
}

.status-cell {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  line-height: 1.4;
}

.expand-body {
  padding: 8px 0;
}

.expand-card + .expand-card {
  margin-top: 12px;
}
</style>
