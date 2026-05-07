<template>
  <div style="padding: 24px">
    <a-page-header
      title="商品一覧（SKU集約版）"
      sub-title="Marketに公開されているSKU集約データの確認用"
    >
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
        <div class="search-grid">
          <a-form-item label="商品名">
            <a-input
              v-model:value="searchForm.name"
              placeholder="部分一致で検索"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="価格">
            <a-input-group compact class="range-group">
              <a-input-number
                v-model:value="searchForm.minPrice"
                placeholder="最低"
                :min="0"
                :step="100"
                style="width: 100px"
              />
              <span class="range-sep">〜</span>
              <a-input-number
                v-model:value="searchForm.maxPrice"
                placeholder="最高"
                :min="0"
                :step="100"
                style="width: 100px"
              />
              <span class="range-unit">円</span>
            </a-input-group>
          </a-form-item>
          <a-form-item label="発売日">
            <a-input-group compact class="range-group">
              <a-date-picker
                v-model:value="searchForm.releaseDateFrom"
                value-format="YYYY-MM-DD"
                placeholder="最早"
                style="width: 130px"
              />
              <span class="range-sep">〜</span>
              <a-date-picker
                v-model:value="searchForm.releaseDateTo"
                value-format="YYYY-MM-DD"
                placeholder="最遅"
                style="width: 130px"
              />
            </a-input-group>
          </a-form-item>
          <a-form-item label="予約開始日">
            <a-input-group compact class="range-group">
              <a-date-picker
                v-model:value="searchForm.preorderStartDateFrom"
                value-format="YYYY-MM-DD"
                placeholder="最早"
                style="width: 130px"
              />
              <span class="range-sep">〜</span>
              <a-date-picker
                v-model:value="searchForm.preorderStartDateTo"
                value-format="YYYY-MM-DD"
                placeholder="最遅"
                style="width: 130px"
              />
            </a-input-group>
          </a-form-item>
          <a-form-item label="在庫">
            <a-radio-group v-model:value="searchForm.stockFilter" button-style="solid">
              <a-radio-button value="all">すべて</a-radio-button>
              <a-radio-button value="inStock">在庫あり</a-radio-button>
              <a-radio-button value="outOfStock">在庫なし</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label=" " class="search-clear">
            <a-button @click="resetSearch">クリア</a-button>
          </a-form-item>
        </div>
      </a-form>
    </a-card>

    <a-table
      :columns="columns"
      :data-source="filteredProducts"
      :loading="loading"
      row-key="productId"
      :expanded-row-keys="expandedRowKeys"
      :pagination="paginationConfig"
      @expand="onExpand"
    >
      <template #expandIcon="{ expanded, record }">
        <a-button
          type="text"
          size="small"
          class="expand-toggle"
          @click.stop="onExpand(!expanded, record)"
        >
          {{ expanded ? '−' : '+' }}
        </a-button>
      </template>

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
            :width="48"
            :height="48"
            :preview="true"
            style="object-fit: contain; border: 1px solid #f0f0f0; border-radius: 4px"
          />
          <div v-else class="image-placeholder" />
        </template>
        <template v-if="column.key === 'minPrice'">
          <span v-if="record.minPrice == null" style="color: #aaa">未設定</span>
          <span v-else>{{ record.minPrice.toLocaleString() }} 円</span>
        </template>
        <template v-if="column.key === 'totalStock'">
          <template v-if="(record.totalStock ?? 0) <= 0">
            <span style="margin-right: 6px">0 個</span>
            <a-tag color="red">在庫なし</a-tag>
          </template>
          <template v-else>
            {{ record.totalStock + ' 個' }}
          </template>
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

const isFilterApplied = computed(() => {
  const f = searchForm.value;
  return (
    (f.name || '').trim() !== '' ||
    f.minPrice != null ||
    f.maxPrice != null ||
    f.releaseDateFrom != null ||
    f.releaseDateTo != null ||
    f.preorderStartDateFrom != null ||
    f.preorderStartDateTo != null ||
    f.stockFilter !== 'all'
  );
});

const countLabel = computed(() => {
  const total = products.value.length;
  const shown = filteredProducts.value.length;
  return isFilterApplied.value
    ? `全 ${total} 件中 ${shown} 件を表示（フィルタ適用中）`
    : `全 ${total} 件中 ${shown} 件を表示`;
});

const columns = [
  { title: 'ID',         dataIndex: 'productId',   key: 'productId',   width: 70 },
  { title: '商品名',     dataIndex: 'productName', key: 'productName' },
  { title: '画像',       key: 'mainImage',                              width: 80 },
  { title: '最低価格',   key: 'minPrice',                               width: 140, align: 'right' },
  { title: '合計在庫',   key: 'totalStock',                             width: 140, align: 'right' },
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

const paginationConfig = {
  pageSize: 20,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
};

const onExpand = async (expanded, record) => {
  if (!expanded) {
    expandedRowKeys.value = expandedRowKeys.value.filter(k => k !== record.productId);
    return;
  }
  expandedRowKeys.value = [...expandedRowKeys.value, record.productId];
  if (skuMap.value[record.productId]) return;
  await loadSkusFor(record.productId);
};

const loadSkusFor = async (productId) => {
  skuLoadingMap.value[productId] = true;
  try {
    const skus = await getProductSkus(productId);
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
    skuMap.value = { ...skuMap.value, [productId]: enriched };
  } catch {
    message.warning('SKU情報の取得に失敗しました');
    skuMap.value = { ...skuMap.value, [productId]: [] };
  } finally {
    skuLoadingMap.value[productId] = false;
  }
};

const expandAll = async () => {
  const targets = filteredProducts.value;
  expandedRowKeys.value = targets.map(p => p.productId);
  await Promise.all(
    targets
      .filter(p => !skuMap.value[p.productId])
      .map(p => loadSkusFor(p.productId))
  );
};

const collapseAll = () => {
  expandedRowKeys.value = [];
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

<style scoped>
.search-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 16px;
  row-gap: 8px;
  align-items: end;
}

.search-grid :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.range-group {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.range-sep {
  margin: 0 6px;
}

.range-unit {
  margin-left: 4px;
}

.search-clear {
  justify-self: end;
}

.expand-toggle {
  margin-right: 4px;
}

.image-placeholder {
  width: 48px;
  height: 48px;
  background: #fafafa;
  border: 1px dashed #d9d9d9;
  border-radius: 4px;
}

@media (max-width: 768px) {
  .search-clear {
    justify-self: start;
  }
}
</style>
