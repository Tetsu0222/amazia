<template>
  <div style="padding: 24px">
    <a-page-header
      title="商品一覧（SKU集約版）"
      sub-title="Marketに公開されているSKU集約データの確認用"
    />

    <a-card size="small" style="margin-bottom: 16px" :body-style="{ padding: '12px 16px' }">
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="商品名">
          <a-input
            v-model:value="searchForm.name"
            placeholder="部分一致で検索"
            allow-clear
            style="width: 200px"
          />
        </a-form-item>
        <a-form-item label="価格">
          <a-input-number
            v-model:value="searchForm.minPrice"
            placeholder="最低"
            :min="0"
            :step="100"
            style="width: 110px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-input-number
            v-model:value="searchForm.maxPrice"
            placeholder="最高"
            :min="0"
            :step="100"
            style="width: 110px"
          />
          <span style="margin-left: 4px">円</span>
        </a-form-item>
        <a-form-item label="発売日">
          <a-date-picker
            v-model:value="searchForm.releaseDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 140px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-date-picker
            v-model:value="searchForm.releaseDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item label="予約開始日">
          <a-date-picker
            v-model:value="searchForm.preorderStartDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 140px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-date-picker
            v-model:value="searchForm.preorderStartDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item label="在庫">
          <a-radio-group v-model:value="searchForm.stockFilter" button-style="solid">
            <a-radio-button value="all">すべて</a-radio-button>
            <a-radio-button value="inStock">在庫あり</a-radio-button>
            <a-radio-button value="outOfStock">在庫なし</a-radio-button>
          </a-radio-group>
        </a-form-item>
        <a-form-item>
          <a-button @click="resetSearch">クリア</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-table
      :columns="columns"
      :data-source="filteredProducts"
      :loading="loading"
      row-key="productId"
      :expand-row-by-click="true"
      :expanded-row-keys="expandedRowKeys"
      @expand="onExpand"
    >
      <template #expandedRowRender="{ record }">
        <div style="padding: 8px 0">
          <a-spin v-if="skuLoadingMap[record.productId]" />
          <template v-else>
            <a-empty
              v-if="!skuMap[record.productId] || skuMap[record.productId].length === 0"
              description="SKUが登録されていません"
              :image-style="{ height: '40px' }"
              style="margin: 8px 0"
            />
            <a-table
              v-else
              :columns="skuColumns"
              :data-source="skuMap[record.productId]"
              row-key="id"
              size="small"
              :pagination="false"
              style="margin: 0"
            >
              <template #bodyCell="{ column, record: sku }">
                <template v-if="column.key === 'price'">
                  {{ sku.price != null ? sku.price.toLocaleString() + ' 円' : '未設定' }}
                </template>
                <template v-if="column.key === 'stock'">
                  {{ sku.stock != null ? sku.stock + ' 個' : '0 個' }}
                </template>
                <template v-if="column.key === 'status'">
                  <a-tag :color="sku.status === 'ACTIVE' ? 'green' : 'default'">
                    {{ sku.status }}
                  </a-tag>
                </template>
              </template>
            </a-table>
          </template>
        </div>
      </template>

      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'mainImage'">
          <a-image
            v-if="record.mainImage"
            :src="record.mainImage"
            :width="64"
            :height="64"
            :preview="true"
            style="object-fit: contain; border: 1px solid #f0f0f0; border-radius: 4px"
          />
          <span v-else style="color: #aaa">画像なし</span>
        </template>
        <template v-if="column.key === 'minPrice'">
          <span v-if="record.minPrice == null" style="color: #aaa">未設定</span>
          <span v-else>{{ record.minPrice.toLocaleString() }} 円</span>
        </template>
        <template v-if="column.key === 'totalStock'">
          {{ record.totalStock != null ? record.totalStock + ' 個' : '0 個' }}
        </template>
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
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getMarketProducts } from '../api/products';
import { getProductSkus, getSkuPrices, getSkuStock } from '../../skus/api/skus';

const products = ref([]);
const loading = ref(false);
const expandedRowKeys = ref([]);
const skuMap = ref({});
const skuLoadingMap = ref({});

const searchForm = ref({
  name: '',
  minPrice: null,
  maxPrice: null,
  releaseDateFrom: null,
  releaseDateTo: null,
  preorderStartDateFrom: null,
  preorderStartDateTo: null,
  stockFilter: 'all',
});

const resetSearch = () => {
  searchForm.value = {
    name: '',
    minPrice: null,
    maxPrice: null,
    releaseDateFrom: null,
    releaseDateTo: null,
    preorderStartDateFrom: null,
    preorderStartDateTo: null,
    stockFilter: 'all',
  };
};

const filteredProducts = computed(() => {
  const {
    name,
    minPrice,
    maxPrice,
    releaseDateFrom,
    releaseDateTo,
    preorderStartDateFrom,
    preorderStartDateTo,
    stockFilter,
  } = searchForm.value;
  const keyword = (name || '').trim().toLowerCase();

  return products.value.filter(p => {
    if (keyword && !(p.productName || '').toLowerCase().includes(keyword)) return false;

    if (minPrice != null) {
      if (p.maxPrice == null || p.maxPrice < minPrice) return false;
    }
    if (maxPrice != null) {
      if (p.minPrice == null || p.minPrice > maxPrice) return false;
    }

    if (releaseDateFrom) {
      if (!p.releaseDate || p.releaseDate < releaseDateFrom) return false;
    }
    if (releaseDateTo) {
      if (!p.releaseDate || p.releaseDate > releaseDateTo) return false;
    }

    if (preorderStartDateFrom) {
      if (!p.preorderStartDate || p.preorderStartDate < preorderStartDateFrom) return false;
    }
    if (preorderStartDateTo) {
      if (!p.preorderStartDate || p.preorderStartDate > preorderStartDateTo) return false;
    }

    if (stockFilter === 'inStock'    && !(p.totalStock > 0))  return false;
    if (stockFilter === 'outOfStock' && !(p.totalStock <= 0)) return false;

    return true;
  });
});

const columns = [
  { title: 'ID',         dataIndex: 'productId',   key: 'productId',   width: 80 },
  { title: '商品名',     dataIndex: 'productName', key: 'productName' },
  { title: 'メイン画像', key: 'mainImage',                              width: 100 },
  { title: '最低価格',   key: 'minPrice',                               width: 140 },
  { title: '合計在庫',   key: 'totalStock',                             width: 100 },
  { title: 'ステータス', key: 'releaseStatus',                          width: 110 },
  { title: '予約開始日', key: 'preorderStartDate',                      width: 130 },
  { title: '発売日',     key: 'releaseDate',                            width: 130 },
];

const isReleased = (record) => {
  if (!record.releaseDate) return true;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return new Date(record.releaseDate) <= today;
};

const skuColumns = [
  { title: 'SKUコード', dataIndex: 'skuCode', key: 'skuCode', width: 140 },
  { title: '色',        dataIndex: 'color',   key: 'color',   width: 80 },
  { title: 'サイズ',   dataIndex: 'size',    key: 'size',    width: 80 },
  { title: '価格',      key: 'price',                         width: 120 },
  { title: '在庫',      key: 'stock',                         width: 100 },
  { title: 'ステータス', key: 'status',                        width: 100 },
];

const onExpand = async (expanded, record) => {
  if (!expanded) {
    expandedRowKeys.value = expandedRowKeys.value.filter(k => k !== record.productId);
    return;
  }
  expandedRowKeys.value = [...expandedRowKeys.value, record.productId];
  if (skuMap.value[record.productId]) return;

  skuLoadingMap.value[record.productId] = true;
  try {
    const skus = await getProductSkus(record.productId);
    const enriched = await Promise.all(
      skus.map(async (sku) => {
        const [priceData, stockData] = await Promise.allSettled([
          getSkuPrices(sku.id),
          getSkuStock(sku.id),
        ]);
        return {
          ...sku,
          price: priceData.status === 'fulfilled' ? priceData.value?.price ?? null : null,
          stock: stockData.status === 'fulfilled' ? stockData.value?.quantity ?? 0 : 0,
        };
      })
    );
    skuMap.value = { ...skuMap.value, [record.productId]: enriched };
  } catch {
    message.warning('SKU情報の取得に失敗しました');
    skuMap.value = { ...skuMap.value, [record.productId]: [] };
  } finally {
    skuLoadingMap.value[record.productId] = false;
  }
};

onMounted(async () => {
  loading.value = true;
  try {
    products.value = await getMarketProducts();
  } catch {
    message.error('商品一覧（SKU集約版）の取得に失敗しました');
  } finally {
    loading.value = false;
  }
});
</script>
