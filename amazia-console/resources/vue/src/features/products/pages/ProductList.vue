<template>
  <div style="padding: 24px">
    <a-page-header title="商品マスタ" sub-title="Amazia Console" />

    <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center">
      <a-space>
        <a-popconfirm
          title="選択した商品を削除しますか？"
          ok-text="削除"
          cancel-text="キャンセル"
          :disabled="selectedRowKeys.length === 0"
          @confirm="handleBulkDelete"
        >
          <a-button danger :disabled="selectedRowKeys.length === 0">
            一括削除（{{ selectedRowKeys.length }}件）
          </a-button>
        </a-popconfirm>
        <a-radio-group v-model:value="activeFilter" button-style="solid">
          <a-radio-button value="all">すべて</a-radio-button>
          <a-radio-button value="active">有効のみ</a-radio-button>
          <a-radio-button value="inactive">無効のみ</a-radio-button>
        </a-radio-group>
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

    <a-table
      :columns="columns"
      :data-source="filteredProducts"
      :loading="loading"
      row-key="id"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
      :expand-row-by-click="true"
      :expanded-row-keys="expandedRowKeys"
      :row-class-name="(record) => (record.isActive ? '' : 'row-inactive')"
      @expand="onExpand"
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
          {{ record.totalStock }} 個
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
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click.stop="$router.push(`/products/${record.id}/edit`)">
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
              <a-button size="small" danger @click.stop>削除</a-button>
            </a-popconfirm>
          </a-space>
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
const activeFilter = ref('all');

const filteredProducts = computed(() => {
  if (activeFilter.value === 'active')   return products.value.filter(p => p.isActive);
  if (activeFilter.value === 'inactive') return products.value.filter(p => !p.isActive);
  return products.value;
});

const isPublished = (product) => {
  const now = new Date();
  if (product.publishStart && new Date(product.publishStart) > now) return false;
  if (product.publishEnd   && new Date(product.publishEnd)   < now) return false;
  return true;
};

const columns = [
  { title: 'ID',       dataIndex: 'id',         key: 'id',         width: 70 },
  { title: '商品名',   dataIndex: 'name',        key: 'name' },
  { title: 'SKU数',    key: 'skuCount',                             width: 80 },
  { title: '価格帯',   key: 'price',                                width: 200 },
  { title: '合計在庫', key: 'totalStock',                           width: 100 },
  { title: '公開状態', key: 'published',                            width: 100 },
  { title: '有効/無効', key: 'active',                              width: 90 },
  { title: '操作',     key: 'action',                               width: 200 },
];

const skuColumns = [
  { title: 'SKUコード', dataIndex: 'skuCode', key: 'skuCode', width: 140 },
  { title: '色',        dataIndex: 'color',   key: 'color',   width: 80 },
  { title: 'サイズ',   dataIndex: 'size',    key: 'size',    width: 80 },
  { title: '価格',      key: 'price',                         width: 120 },
  { title: '在庫',      key: 'stock',                         width: 100 },
];

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
</style>
