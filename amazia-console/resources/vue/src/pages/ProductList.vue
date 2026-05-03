<template>
  <div style="padding: 24px">
    <a-page-header title="商品管理" sub-title="Amazia Console" />

    <div style="margin-bottom: 16px; text-align: right">
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
      :data-source="products"
      :loading="loading"
      row-key="id"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'price'">
          {{ record.price.toLocaleString() }} 円
        </template>
        <template v-if="column.key === 'action'">
          <a-space>
            <a-button size="small" @click="$router.push(`/products/${record.id}/edit`)">
              編集
            </a-button>
            <a-popconfirm
              title="削除しますか？"
              ok-text="削除"
              cancel-text="キャンセル"
              @confirm="handleDelete(record.id)"
            >
              <a-button size="small" danger>削除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getProducts, deleteProduct } from '../api/products';

const products = ref([]);
const loading = ref(false);

const columns = [
  { title: 'ID',   dataIndex: 'id',    key: 'id',    width: 80 },
  { title: '商品名', dataIndex: 'name',  key: 'name' },
  { title: '価格',  dataIndex: 'price', key: 'price', width: 140 },
  { title: '在庫数', dataIndex: 'stock', key: 'stock', width: 100 },
  { title: '操作',  key: 'action',      width: 160 },
];

const fetchProducts = async () => {
  loading.value = true;
  try {
    products.value = await getProducts();
  } catch {
    message.error('商品の取得に失敗しました');
  } finally {
    loading.value = false;
  }
};

const handleDelete = async (id) => {
  try {
    await deleteProduct(id);
    message.success('削除しました');
    await fetchProducts();
  } catch {
    message.error('削除に失敗しました');
  }
};

onMounted(fetchProducts);
</script>
