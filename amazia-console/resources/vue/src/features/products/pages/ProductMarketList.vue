<template>
  <div style="padding: 24px">
    <a-page-header
      title="商品一覧（SKU集約版）"
      sub-title="Marketに公開されているSKU集約データの確認用"
    />

    <a-table
      :columns="columns"
      :data-source="products"
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
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getMarketProducts } from '../api/products';
import { getProductSkus, getSkuPrices, getSkuStock } from '../../skus/api/skus';

const products = ref([]);
const loading = ref(false);
const expandedRowKeys = ref([]);
const skuMap = ref({});
const skuLoadingMap = ref({});

const columns = [
  { title: 'ID',         dataIndex: 'productId',   key: 'productId',   width: 80 },
  { title: '商品名',     dataIndex: 'productName', key: 'productName' },
  { title: 'メイン画像', key: 'mainImage',                              width: 100 },
  { title: '最低価格',   key: 'minPrice',                               width: 140 },
  { title: '合計在庫',   key: 'totalStock',                             width: 100 },
];

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
