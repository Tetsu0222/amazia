<template>
  <div style="padding: 24px">
    <a-page-header title="入荷管理">
      <template #description>
        <span style="color: rgba(0, 0, 0, 0.45); font-size: 13px">
          {{ countLabel }}
        </span>
      </template>
      <template #extra>
        <a-space>
          <a-button @click="goImport">Excel一括入荷</a-button>
          <a-button type="primary" @click="goCreate">入荷登録</a-button>
        </a-space>
      </template>
    </a-page-header>

    <SearchCard
      wide-field-label="追跡番号"
      :wide-field-value="searchForm.trackingCode"
      @update:wide-field-value="searchForm.trackingCode = $event"
      @clear="resetSearch"
    >
      <div class="inbound-search-row">
        <a-form-item label="商品ID" class="inbound-search-row__item">
          <a-input-number
            v-model:value="searchForm.productId"
            placeholder="完全一致"
            :min="1"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="倉庫ID" class="inbound-search-row__item">
          <a-input-number
            v-model:value="searchForm.warehouseId"
            placeholder="完全一致"
            :min="1"
            style="width: 100%"
          />
        </a-form-item>
      </div>
      <div class="inbound-search-row">
        <a-form-item label="仕入先ID" class="inbound-search-row__item">
          <a-input-number
            v-model:value="searchForm.supplierId"
            placeholder="完全一致"
            :min="1"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="入荷数量" class="inbound-search-row__item">
          <a-input-group compact>
            <a-input-number
              v-model:value="searchForm.minQuantity"
              placeholder="最低"
              :min="0"
              style="width: 90px"
            />
            <span class="range-sep">〜</span>
            <a-input-number
              v-model:value="searchForm.maxQuantity"
              placeholder="最高"
              :min="0"
              style="width: 90px"
            />
          </a-input-group>
        </a-form-item>
        <a-form-item label="入荷日" class="inbound-search-row__item">
          <a-input-group compact>
            <a-date-picker
              v-model:value="searchForm.inboundedAtFrom"
              value-format="YYYY-MM-DD"
              placeholder="最早"
              style="width: 130px"
            />
            <span class="range-sep">〜</span>
            <a-date-picker
              v-model:value="searchForm.inboundedAtTo"
              value-format="YYYY-MM-DD"
              placeholder="最遅"
              style="width: 130px"
            />
          </a-input-group>
        </a-form-item>
      </div>
    </SearchCard>

    <a-table
      :dataSource="filteredInbounds"
      :columns="columns"
      :loading="loading"
      rowKey="id"
      size="small"
      :pagination="paginationConfig"
      :locale="{ emptyText: '該当データがありません' }"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listInbounds } from '../api/inboundApi.js';
import SearchCard from '../../../components/SearchCard.vue';

const router = useRouter();
const inbounds = ref([]);
const loading = ref(false);

const initialSearchForm = () => ({
  trackingCode: '',
  productId: null,
  warehouseId: null,
  supplierId: null,
  minQuantity: null,
  maxQuantity: null,
  inboundedAtFrom: null,
  inboundedAtTo: null,
});

const searchForm = ref(initialSearchForm());

const resetSearch = () => {
  searchForm.value = initialSearchForm();
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

const isFilterApplied = computed(() => {
  const f = searchForm.value;
  return !!(
    (f.trackingCode || '').trim() ||
    f.productId != null ||
    f.warehouseId != null ||
    f.supplierId != null ||
    f.minQuantity != null ||
    f.maxQuantity != null ||
    f.inboundedAtFrom ||
    f.inboundedAtTo
  );
});

const countLabel = computed(() => {
  const total = inbounds.value.length;
  const shown = filteredInbounds.value.length;
  return isFilterApplied.value
    ? `全 ${total} 件中 ${shown} 件を表示（フィルタ適用中）`
    : `全 ${total} 件中 ${shown} 件を表示`;
});

const columns = [
  { title: 'ID',         dataIndex: 'id',           key: 'id',           width: 80,  align: 'right' },
  { title: '商品ID',     dataIndex: 'productId',    key: 'productId',    width: 100, align: 'right' },
  { title: '倉庫ID',     dataIndex: 'warehouseId',  key: 'warehouseId',  width: 100, align: 'right' },
  { title: '仕入先ID',   dataIndex: 'supplierId',   key: 'supplierId',
    customRender: ({ text }) => text ?? '—' },
  { title: '入荷数量',   dataIndex: 'quantity',     key: 'quantity',     width: 100, align: 'right' },
  { title: '追跡番号',   dataIndex: 'trackingCode', key: 'trackingCode',
    customRender: ({ text }) => text ?? '—' },
  { title: '入荷日',     dataIndex: 'inboundedAt',  key: 'inboundedAt' },
  { title: '登録日時',   dataIndex: 'createdAt',    key: 'createdAt',
    customRender: ({ text }) => (text ?? '').replace('T', ' ').slice(0, 19) },
];

const paginationConfig = {
  defaultPageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
};

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

<style scoped>
.inbound-search-row {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  align-items: end;
}
.inbound-search-row__item {
  flex: 0 0 auto;
  min-width: 220px;
}
</style>
