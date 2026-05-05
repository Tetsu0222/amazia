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
    >
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

const products = ref([]);
const loading = ref(false);

const columns = [
  { title: 'ID',         dataIndex: 'productId',   key: 'productId',   width: 80 },
  { title: '商品名',     dataIndex: 'productName', key: 'productName' },
  { title: 'メイン画像', key: 'mainImage',                              width: 100 },
  { title: '最低価格',   key: 'minPrice',                               width: 140 },
  { title: '合計在庫',   key: 'totalStock',                             width: 100 },
];

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
