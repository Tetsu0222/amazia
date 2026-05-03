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
        <a-button
          :disabled="selectedRowKeys.length === 0"
          @click="openBulkEditModal"
        >
          一括編集（在庫数）
        </a-button>
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
      :data-source="products"
      :loading="loading"
      row-key="id"
      :row-selection="{ selectedRowKeys, onChange: onSelectChange }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'price'">
          {{ record.price.toLocaleString() }} 円
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="statusColor(record.statusCode)">
            {{ statusLabel(record.statusCode) }}
          </a-tag>
        </template>
        <template v-if="column.key === 'published'">
          <a-badge
            :status="isPublished(record) ? 'success' : 'default'"
            :text="isPublished(record) ? '公開中' : '非公開'"
          />
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

    <!-- 一括編集モーダル -->
    <a-modal
      v-model:open="bulkEditVisible"
      title="在庫数 一括編集"
      ok-text="保存"
      cancel-text="キャンセル"
      :confirm-loading="bulkEditLoading"
      @ok="handleBulkEditSave"
    >
      <a-table
        :columns="bulkEditColumns"
        :data-source="bulkEditItems"
        row-key="id"
        :pagination="false"
        size="small"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'stock'">
            <a-input-number
              v-model:value="record.newStock"
              :min="0"
              style="width: 100px"
            />
          </template>
        </template>
      </a-table>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { getAdminProducts, deleteProduct, bulkDeleteProducts, bulkUpdateStock } from '../api/products';

const products = ref([]);
const loading = ref(false);
const selectedRowKeys = ref([]);

const bulkEditVisible = ref(false);
const bulkEditLoading = ref(false);
const bulkEditItems = ref([]);

const STATUS_MAP = {
  WAITING:     { label: '入荷待',    color: 'default' },
  RESERVATION: { label: '予約受付中', color: 'blue' },
  ON_SALE:     { label: '販売中',    color: 'green' },
};

const statusLabel = (code) => STATUS_MAP[code]?.label ?? '未設定';
const statusColor = (code) => STATUS_MAP[code]?.color ?? 'default';

const isPublished = (product) => {
  const now = new Date();
  if (product.publishStart && new Date(product.publishStart) > now) return false;
  if (product.publishEnd   && new Date(product.publishEnd)   < now) return false;
  return true;
};

const columns = [
  { title: 'ID',     dataIndex: 'id',    key: 'id',        width: 70 },
  { title: '商品名',  dataIndex: 'name',  key: 'name' },
  { title: '価格',   dataIndex: 'price', key: 'price',     width: 130 },
  { title: '在庫数',  dataIndex: 'stock', key: 'stock',     width: 90 },
  { title: 'ステータス', key: 'status',                      width: 120 },
  { title: '公開状態', key: 'published',                     width: 100 },
  { title: '操作',   key: 'action',                         width: 140 },
];

const bulkEditColumns = [
  { title: '商品名',    dataIndex: 'name',  key: 'name' },
  { title: '現在の在庫', dataIndex: 'stock', key: 'currentStock', width: 120 },
  { title: '変更後の在庫', key: 'stock',                          width: 140 },
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

const onSelectChange = (keys) => {
  selectedRowKeys.value = keys;
};

const handleDelete = async (id) => {
  try {
    await deleteProduct(id);
    message.success('削除しました');
    selectedRowKeys.value = selectedRowKeys.value.filter(k => k !== id);
    await fetchProducts();
  } catch {
    message.error('削除に失敗しました');
  }
};

const handleBulkDelete = async () => {
  try {
    await bulkDeleteProducts(selectedRowKeys.value);
    message.success(`${selectedRowKeys.value.length}件 削除しました`);
    selectedRowKeys.value = [];
    await fetchProducts();
  } catch {
    message.error('一括削除に失敗しました');
  }
};

const openBulkEditModal = () => {
  bulkEditItems.value = products.value
    .filter(p => selectedRowKeys.value.includes(p.id))
    .map(p => ({ ...p, newStock: p.stock }));
  bulkEditVisible.value = true;
};

const handleBulkEditSave = async () => {
  bulkEditLoading.value = true;
  try {
    const updates = bulkEditItems.value.map(item => ({ id: item.id, stock: item.newStock }));
    await bulkUpdateStock(updates);
    message.success('在庫数を更新しました');
    bulkEditVisible.value = false;
    selectedRowKeys.value = [];
    await fetchProducts();
  } catch {
    message.error('一括編集に失敗しました');
  } finally {
    bulkEditLoading.value = false;
  }
};

onMounted(fetchProducts);
</script>
