<template>
  <div style="padding: 24px">
    <a-page-header title="商品マスタ">
      <template #extra>
        <span style="color: rgba(0, 0, 0, 0.45); font-size: 13px">
          {{ countLabel }}
        </span>
      </template>
    </a-page-header>

    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <a-space>
        <a-popconfirm
          v-if="selectedRowKeys.length > 0"
          title="選択した商品を削除しますか？"
          ok-text="削除"
          cancel-text="キャンセル"
          @confirm="handleBulkDelete"
        >
          <a-button danger>
            一括削除（{{ selectedRowKeys.length }}件）
          </a-button>
        </a-popconfirm>
      </a-space>
      <a-space>
        <a-button @click="$router.push('/products/import')">
          一括登録（Excel）
        </a-button>
        <a-button type="primary" @click="$router.push('/products/new')">
          + 新規登録
        </a-button>
      </a-space>
    </div>

    <a-card size="small" style="margin-bottom: 16px" :body-style="{ padding: '12px 16px' }">
      <a-form layout="vertical" :model="searchForm">
        <div class="search-row search-row--basic">
          <a-form-item label="商品名" class="search-item search-item--name">
            <a-input
              v-model:value="searchForm.name"
              placeholder="部分一致で検索"
              allow-clear
            />
          </a-form-item>
          <a-form-item label="価格" class="search-item search-item--price">
            <a-input-group compact class="price-range-group">
              <a-input-number
                v-model:value="searchForm.minPrice"
                placeholder="最低"
                :min="0"
                :step="100"
                class="price-input"
              />
              <span class="price-separator">〜</span>
              <a-input-number
                v-model:value="searchForm.maxPrice"
                placeholder="最高"
                :min="0"
                :step="100"
                class="price-input"
              />
              <span class="price-unit">円</span>
            </a-input-group>
          </a-form-item>
        </div>
        <div class="search-row search-row--status">
          <a-form-item label="在庫" class="search-item">
            <a-radio-group v-model:value="searchForm.stockFilter" button-style="solid">
              <a-radio-button value="all">すべて</a-radio-button>
              <a-radio-button value="inStock">在庫あり</a-radio-button>
              <a-radio-button value="outOfStock">在庫なし</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="有効/無効" class="search-item">
            <a-radio-group v-model:value="searchForm.activeFilter" button-style="solid">
              <a-radio-button value="all">すべて</a-radio-button>
              <a-radio-button value="active">有効のみ</a-radio-button>
              <a-radio-button value="inactive">無効のみ</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label="公開状態" class="search-item">
            <a-radio-group v-model:value="searchForm.publishStatus" button-style="solid">
              <a-radio-button value="all">すべて</a-radio-button>
              <a-radio-button value="published">公開中のみ</a-radio-button>
              <a-radio-button value="unpublished">非公開のみ</a-radio-button>
            </a-radio-group>
          </a-form-item>
          <a-form-item label=" " class="search-item search-item--clear">
            <a-button @click="resetSearch">クリア</a-button>
          </a-form-item>
        </div>
      </a-form>
    </a-card>

    <a-table
      :columns="columns"
      :data-source="filteredProducts"
      :loading="loading"
      row-key="id"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      :expanded-row-keys="expandedRowKeys"
      :row-class-name="(record) => (record.isActive ? '' : 'row-inactive')"
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
              style="margin: 0"
            >
              <template #bodyCell="{ column, record: sku }">
                <template v-if="column.key === 'price'">
                  {{ sku.price != null ? sku.price.toLocaleString() + ' 円' : '未設定' }}
                </template>
                <template v-if="column.key === 'stock'">
                  {{ sku.stock != null ? sku.stock + ' 個' : '0 個' }}
                </template>
              </template>
            </a-table>
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
        <template v-if="column.key === 'price'">
          <span v-if="record.minPrice == null" style="color: #aaa">未設定</span>
          <span v-else-if="record.minPrice === record.maxPrice">
            {{ record.minPrice.toLocaleString() }} 円
          </span>
          <span v-else>
            {{ record.minPrice.toLocaleString() }} 〜 {{ record.maxPrice.toLocaleString() }} 円
          </span>
        </template>
        <template v-if="column.key === 'totalStock'">
          <a-tag v-if="record.totalStock <= 0" color="red">在庫なし</a-tag>
          <span v-else>{{ record.totalStock }} 個</span>
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
        <template v-if="column.key === 'action'">
          <a-button-group>
            <a-button type="primary" size="small" @click.stop="$router.push(`/products/${record.id}/edit`)">
              編集
            </a-button>
            <a-button size="small" @click.stop="$router.push('/skus?productId=' + record.id)">
              SKU管理
            </a-button>
            <a-popconfirm
              title="削除しますか？"
              ok-text="削除"
              cancel-text="キャンセル"
              placement="topRight"
              @confirm="handleDelete(record.id)"
            >
              <a-button size="small" danger ghost @click.stop>削除</a-button>
            </a-popconfirm>
          </a-button-group>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getAdminProducts, deleteProduct, bulkDeleteProducts } from '../api/products';
import { getProductSkus, getSkuPrices, getSkuStock } from '../../skus/api/skus';

const products = ref([]);
const loading = ref(false);
const selectedRowKeys = ref([]);
const expandedRowKeys = ref([]);
const skuMap = ref({});
const skuLoadingMap = ref({});

const searchForm = ref({
  name: '',
  minPrice: null,
  maxPrice: null,
  stockFilter: 'all',
  activeFilter: 'all',
  publishStatus: 'all',
});

const resetSearch = () => {
  searchForm.value = {
    name: '',
    minPrice: null,
    maxPrice: null,
    stockFilter: 'all',
    activeFilter: 'all',
    publishStatus: 'all',
  };
};

const isPublished = (product) => {
  const now = new Date();
  if (product.publishStart && new Date(product.publishStart) > now) return false;
  if (product.publishEnd   && new Date(product.publishEnd)   < now) return false;
  return true;
};

const filteredProducts = computed(() => {
  const { name, minPrice, maxPrice, stockFilter, activeFilter, publishStatus } = searchForm.value;
  const keyword = (name || '').trim().toLowerCase();

  return products.value.filter(p => {
    if (activeFilter === 'active'   && !p.isActive) return false;
    if (activeFilter === 'inactive' &&  p.isActive) return false;

    if (keyword && !(p.name || '').toLowerCase().includes(keyword)) return false;

    if (minPrice != null) {
      if (p.maxPrice == null || p.maxPrice < minPrice) return false;
    }
    if (maxPrice != null) {
      if (p.minPrice == null || p.minPrice > maxPrice) return false;
    }

    if (stockFilter === 'inStock'    && !(p.totalStock > 0))  return false;
    if (stockFilter === 'outOfStock' && !(p.totalStock <= 0)) return false;

    if (publishStatus === 'published'   && !isPublished(p)) return false;
    if (publishStatus === 'unpublished' &&  isPublished(p)) return false;

    return true;
  });
});

const isFilterApplied = computed(() => {
  const { name, minPrice, maxPrice, stockFilter, activeFilter, publishStatus } = searchForm.value;
  return (
    (name || '').trim() !== '' ||
    minPrice != null ||
    maxPrice != null ||
    stockFilter !== 'all' ||
    activeFilter !== 'all' ||
    publishStatus !== 'all'
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
  { title: 'ID',       dataIndex: 'id',   key: 'id',   width: 70 },
  { title: '商品名',   dataIndex: 'name', key: 'name' },
  { title: 'SKU数',    key: 'skuCount',                width: 80 },
  { title: '価格帯',   key: 'price',                   width: 200, align: 'right' },
  { title: '合計在庫', key: 'totalStock',              width: 100, align: 'right' },
  { title: '状態',     key: 'status',                  width: 110 },
  { title: '操作',     key: 'action',                  width: 220 },
];

const skuColumns = [
  { title: 'SKUコード', dataIndex: 'skuCode', key: 'skuCode', width: 140 },
  { title: '色',        dataIndex: 'color',   key: 'color',   width: 80 },
  { title: 'サイズ',   dataIndex: 'size',    key: 'size',    width: 80 },
  { title: '価格',      key: 'price',                         width: 120 },
  { title: '在庫',      key: 'stock',                         width: 100 },
];

const paginationConfig = {
  pageSize: 20,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['20', '50', '100'],
};

const fetchProducts = async () => {
  loading.value = true;
  try {
    products.value = await getAdminProducts();
  } catch {
    message.error('商品の取得に失敗しました');
  } finally {
    loading.value = false;
  }
};

const onExpand = async (expanded, record) => {
  if (!expanded) {
    expandedRowKeys.value = expandedRowKeys.value.filter(k => k !== record.id);
    return;
  }
  expandedRowKeys.value = [...expandedRowKeys.value, record.id];
  if (skuMap.value[record.id]) return;

  skuLoadingMap.value[record.id] = true;
  try {
    const skus = await getProductSkus(record.id);
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
    skuMap.value = { ...skuMap.value, [record.id]: enriched };
  } catch {
    message.warning('SKU情報の取得に失敗しました');
    skuMap.value = { ...skuMap.value, [record.id]: [] };
  } finally {
    skuLoadingMap.value[record.id] = false;
  }
};

const onSelectChange = (keys) => {
  selectedRowKeys.value = keys;
};

const handleDelete = async (id) => {
  try {
    await deleteProduct(id);
    message.success('削除しました');
    selectedRowKeys.value = selectedRowKeys.value.filter(k => k !== id);
    delete skuMap.value[id];
    await fetchProducts();
  } catch {
    message.error('削除に失敗しました');
  }
};

const handleBulkDelete = async () => {
  try {
    await bulkDeleteProducts(selectedRowKeys.value);
    message.success(`${selectedRowKeys.value.length}件 削除しました`);
    selectedRowKeys.value.forEach(id => delete skuMap.value[id]);
    selectedRowKeys.value = [];
    await fetchProducts();
  } catch {
    message.error('一括削除に失敗しました');
  }
};

onMounted(fetchProducts);
</script>

<style scoped>
:deep(.row-inactive) td {
  background-color: #fafafa;
  color: #999;
}

.search-row {
  display: grid;
  gap: 0 16px;
  align-items: end;
}

.search-row--basic {
  grid-template-columns: 2fr 1fr;
}

.search-row--status {
  grid-template-columns: auto auto auto 1fr;
  justify-content: start;
}

.search-row :deep(.ant-form-item) {
  margin-bottom: 8px;
}

.search-item--clear {
  justify-self: end;
}

@media (max-width: 768px) {
  .search-row--basic,
  .search-row--status {
    grid-template-columns: 1fr;
  }
  .search-item--clear {
    justify-self: start;
  }
}

.price-range-group {
  display: inline-flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
}

.price-range-group .price-input {
  width: 100px;
}

.price-range-group .price-separator {
  margin: 0 6px;
}

.price-range-group .price-unit {
  margin-left: 4px;
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
</style>
