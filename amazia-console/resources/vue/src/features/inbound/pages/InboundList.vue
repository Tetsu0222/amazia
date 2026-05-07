<template>
  <div style="padding: 24px; max-width: 1200px">
    <a-page-header title="入荷管理" sub-title="Amazia Console">
      <template #extra>
        <a-space>
          <a-button @click="goImport">Excel一括入荷</a-button>
          <a-button type="primary" @click="goCreate">入荷登録</a-button>
        </a-space>
      </template>
    </a-page-header>

    <a-form layout="inline" :model="filter" style="margin-bottom: 16px">
      <a-form-item label="商品ID">
        <a-input-number
          v-model:value="filter.productId"
          :min="1"
          placeholder="商品ID"
          style="width: 160px"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" @click="reload">検索</a-button>
      </a-form-item>
      <a-form-item>
        <a-button @click="reset">クリア</a-button>
      </a-form-item>
    </a-form>

    <a-table
      :dataSource="inbounds"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="{ pageSize: 50 }"
    />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listInbounds } from '../api/inboundApi.js';

const router = useRouter();
const inbounds = ref([]);
const loading = ref(false);
const filter = reactive({
  productId: undefined,
});

const columns = [
  { title: 'ID',         dataIndex: 'id',           key: 'id',           width: 80 },
  { title: '商品ID',     dataIndex: 'productId',    key: 'productId',    width: 100 },
  { title: '倉庫ID',     dataIndex: 'warehouseId',  key: 'warehouseId',  width: 100 },
  { title: '仕入先ID',   dataIndex: 'supplierId',   key: 'supplierId',
    customRender: ({ text }) => text ?? '—' },
  { title: '入荷数量',   dataIndex: 'quantity',     key: 'quantity',     width: 100 },
  { title: '入荷日',     dataIndex: 'inboundedAt',  key: 'inboundedAt' },
  { title: '登録日時',   dataIndex: 'createdAt',    key: 'createdAt',
    customRender: ({ text }) => (text ?? '').replace('T', ' ').slice(0, 19) },
];

async function reload() {
  loading.value = true;
  try {
    const params = {};
    if (filter.productId) {
      params.productId = filter.productId;
    }
    const res = await listInbounds(params);
    inbounds.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('入荷一覧の取得に失敗しました');
    inbounds.value = [];
  } finally {
    loading.value = false;
  }
}

function reset() {
  filter.productId = undefined;
  reload();
}

function goCreate() {
  router.push('/inbound/create');
}

function goImport() {
  router.push('/inbound/import');
}

onMounted(reload);
</script>
