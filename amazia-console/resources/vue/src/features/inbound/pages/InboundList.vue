<template>
  <div style="padding: 24px; max-width: 1300px">
    <a-page-header title="入荷管理" sub-title="Amazia Console">
      <template #extra>
        <a-space>
          <a-button @click="goImport">Excel一括入荷</a-button>
          <a-button type="primary" @click="goCreate">入荷登録</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-card size="small" style="margin-bottom: 16px" :body-style="{ padding: '12px 16px' }">
      <div style="margin-bottom: 12px; display: flex; align-items: center; gap: 8px">
        <span style="font-weight: 500; min-width: 80px">追跡番号</span>
        <a-input
          v-model:value="searchForm.trackingCode"
          placeholder="部分一致で検索"
          allow-clear
          style="flex: 1; max-width: 480px"
        />
      </div>
      <a-form layout="inline" :model="searchForm">
        <a-form-item label="商品ID">
          <a-input-number
            v-model:value="searchForm.productId"
            placeholder="完全一致"
            :min="1"
            style="width: 110px"
          />
        </a-form-item>
        <a-form-item label="倉庫ID">
          <a-input-number
            v-model:value="searchForm.warehouseId"
            placeholder="完全一致"
            :min="1"
            style="width: 110px"
          />
        </a-form-item>
        <a-form-item label="仕入先ID">
          <a-input-number
            v-model:value="searchForm.supplierId"
            placeholder="完全一致"
            :min="1"
            style="width: 110px"
          />
        </a-form-item>
        <a-form-item label="入荷数量">
          <a-input-number
            v-model:value="searchForm.minQuantity"
            placeholder="最低"
            :min="0"
            style="width: 90px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-input-number
            v-model:value="searchForm.maxQuantity"
            placeholder="最高"
            :min="0"
            style="width: 90px"
          />
        </a-form-item>
        <a-form-item label="入荷日">
          <a-date-picker
            v-model:value="searchForm.inboundedAtFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 140px"
          />
          <span style="margin: 0 6px">〜</span>
          <a-date-picker
            v-model:value="searchForm.inboundedAtTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 140px"
          />
        </a-form-item>
        <a-form-item>
          <a-button @click="resetSearch">クリア</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-table
      :dataSource="filteredInbounds"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="{ pageSize: 50 }"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listInbounds } from '../api/inboundApi.js';

const router = useRouter();
const inbounds = ref([]);
const loading = ref(false);

const searchForm = ref({
  trackingCode: '',
  productId: null,
  warehouseId: null,
  supplierId: null,
  minQuantity: null,
  maxQuantity: null,
  inboundedAtFrom: null,
  inboundedAtTo: null,
});

const resetSearch = () => {
  searchForm.value = {
    trackingCode: '',
    productId: null,
    warehouseId: null,
    supplierId: null,
    minQuantity: null,
    maxQuantity: null,
    inboundedAtFrom: null,
    inboundedAtTo: null,
  };
};

const filteredInbounds = computed(() => {
  const f = searchForm.value;
  const tracking = (f.trackingCode || '').trim().toLowerCase();

  return inbounds.value.filter(i => {
    if (tracking && !(i.trackingCode || '').toLowerCase().includes(tracking)) return false;
    if (f.productId   != null && i.productId   !== f.productId)   return false;
    if (f.warehouseId != null && i.warehouseId !== f.warehouseId) return false;
    if (f.supplierId  != null && i.supplierId  !== f.supplierId)  return false;

    if (f.minQuantity != null && (i.quantity == null || i.quantity < f.minQuantity)) return false;
    if (f.maxQuantity != null && (i.quantity == null || i.quantity > f.maxQuantity)) return false;

    if (f.inboundedAtFrom) {
      if (!i.inboundedAt || i.inboundedAt < f.inboundedAtFrom) return false;
    }
    if (f.inboundedAtTo) {
      if (!i.inboundedAt || i.inboundedAt > f.inboundedAtTo) return false;
    }

    return true;
  });
});

const columns = [
  { title: 'ID',         dataIndex: 'id',           key: 'id',           width: 80 },
  { title: '商品ID',     dataIndex: 'productId',    key: 'productId',    width: 100 },
  { title: '倉庫ID',     dataIndex: 'warehouseId',  key: 'warehouseId',  width: 100 },
  { title: '仕入先ID',   dataIndex: 'supplierId',   key: 'supplierId',
    customRender: ({ text }) => text ?? '—' },
  { title: '入荷数量',   dataIndex: 'quantity',     key: 'quantity',     width: 100 },
  { title: '追跡番号',   dataIndex: 'trackingCode', key: 'trackingCode',
    customRender: ({ text }) => text ?? '—' },
  { title: '入荷日',     dataIndex: 'inboundedAt',  key: 'inboundedAt' },
  { title: '登録日時',   dataIndex: 'createdAt',    key: 'createdAt',
    customRender: ({ text }) => (text ?? '').replace('T', ' ').slice(0, 19) },
];

async function reload() {
  loading.value = true;
  try {
    const res = await listInbounds();
    inbounds.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('入荷一覧の取得に失敗しました');
    inbounds.value = [];
  } finally {
    loading.value = false;
  }
}

function goCreate() {
  router.push('/inbound/create');
}

function goImport() {
  router.push('/inbound/import');
}

onMounted(reload);
</script>
