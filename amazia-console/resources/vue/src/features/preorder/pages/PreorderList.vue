<template>
  <div style="padding: 24px">
    <a-page-header title="予約管理">
      <template #description>
        <span style="color: rgba(0, 0, 0, 0.45); font-size: 13px">
          {{ countLabel }}
        </span>
      </template>
    </a-page-header>

    <SearchCard
      wide-field-label="商品名"
      wide-field-placeholder="部分一致で検索"
      v-model:wide-field-value="searchForm.name"
      @clear="resetSearch"
    >
      <a-form-item label="価格" class="span-2">
        <a-input-group compact>
          <a-input-number
            v-model:value="searchForm.minPrice"
            placeholder="最低"
            :min="0"
            :step="100"
            style="width: 120px"
          />
          <span class="range-sep">〜</span>
          <a-input-number
            v-model:value="searchForm.maxPrice"
            placeholder="最高"
            :min="0"
            :step="100"
            style="width: 120px"
          />
          <span class="range-unit">円</span>
        </a-input-group>
      </a-form-item>
      <a-form-item label="発売日" class="span-2">
        <a-input-group compact>
          <a-date-picker
            v-model:value="searchForm.releaseDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 150px"
          />
          <span class="range-sep">〜</span>
          <a-date-picker
            v-model:value="searchForm.releaseDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 150px"
          />
        </a-input-group>
      </a-form-item>
      <a-form-item label="予約開始日" class="span-2">
        <a-input-group compact>
          <a-date-picker
            v-model:value="searchForm.preorderStartDateFrom"
            value-format="YYYY-MM-DD"
            placeholder="最早"
            style="width: 150px"
          />
          <span class="range-sep">〜</span>
          <a-date-picker
            v-model:value="searchForm.preorderStartDateTo"
            value-format="YYYY-MM-DD"
            placeholder="最遅"
            style="width: 150px"
          />
        </a-input-group>
      </a-form-item>
    </SearchCard>

    <a-table
      :dataSource="filteredPreorders"
      :columns="columns"
      :loading="loading"
      rowKey="productId"
      size="small"
      :pagination="paginationConfig"
      :locale="{ emptyText: '該当データがありません' }"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'preorderStartDate'">
          {{ record.preorderStartDate ?? '公開と同時' }}
        </template>
        <template v-else-if="column.key === 'priceRange'">
          <span v-if="record.minPrice == null" style="color: #aaa">未設定</span>
          <span v-else-if="record.minPrice === record.maxPrice">
            {{ record.minPrice.toLocaleString() }} 円
          </span>
          <span v-else>
            {{ record.minPrice.toLocaleString() }} 〜 {{ record.maxPrice.toLocaleString() }} 円
          </span>
        </template>
        <template v-else-if="column.key === 'daysUntilRelease'">
          {{ formatDaysUntilRelease(record.daysUntilRelease) }}
        </template>
        <template v-else-if="column.key === 'acceptPreorder'">
          <a-tag :color="record.acceptPreorder ? 'green' : 'default'">
            {{ record.acceptPreorder ? '受付中' : '停止中' }}
          </a-tag>
        </template>
        <template v-else-if="column.key === 'preorderAmount'">
          {{ record.preorderAmount.toLocaleString() }}
        </template>
        <template v-else-if="column.key === 'isActive'">
          <a-tag :color="record.isActive ? 'blue' : 'red'">
            {{ record.isActive ? '公開中' : '非公開' }}
          </a-tag>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { message } from 'ant-design-vue';
import { listPreorders } from '../api/preorderApi.js';
import SearchCard from '../../../components/SearchCard.vue';

const preorders = ref([]);
const loading = ref(false);

const initialSearchForm = () => ({
  name: '',
  minPrice: null,
  maxPrice: null,
  releaseDateFrom: null,
  releaseDateTo: null,
  preorderStartDateFrom: null,
  preorderStartDateTo: null,
});

const searchForm = ref(initialSearchForm());

const resetSearch = () => {
  searchForm.value = initialSearchForm();
};

const filteredPreorders = computed(() => {
  const {
    name,
    minPrice,
    maxPrice,
    releaseDateFrom,
    releaseDateTo,
    preorderStartDateFrom,
    preorderStartDateTo,
  } = searchForm.value;
  const keyword = (name || '').trim().toLowerCase();

  return preorders.value.filter(p => {
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

    return true;
  });
});

const isFilterApplied = computed(() => {
  const f = searchForm.value;
  return !!(
    (f.name || '').trim() ||
    f.minPrice != null ||
    f.maxPrice != null ||
    f.releaseDateFrom ||
    f.releaseDateTo ||
    f.preorderStartDateFrom ||
    f.preorderStartDateTo
  );
});

const countLabel = computed(() => {
  const total = preorders.value.length;
  const shown = filteredPreorders.value.length;
  return isFilterApplied.value
    ? `全 ${total} 件中 ${shown} 件を表示（フィルタ適用中）`
    : `全 ${total} 件中 ${shown} 件を表示`;
});

const columns = [
  { title: '商品ID',       dataIndex: 'productId',         key: 'productId' },
  { title: '商品名',       dataIndex: 'productName',       key: 'productName' },
  { title: '価格帯',       key: 'priceRange' },
  { title: '予約開始日',   dataIndex: 'preorderStartDate', key: 'preorderStartDate' },
  { title: '発売日',       dataIndex: 'releaseDate',       key: 'releaseDate' },
  { title: '発売まで',     dataIndex: 'daysUntilRelease',  key: 'daysUntilRelease' },
  { title: '予約受付',     dataIndex: 'acceptPreorder',    key: 'acceptPreorder' },
  { title: '予約数',       dataIndex: 'preorderQuantity',  key: 'preorderQuantity', align: 'right' },
  { title: '予約金額（円）', dataIndex: 'preorderAmount',  key: 'preorderAmount',   align: 'right' },
  { title: 'Market 公開',  dataIndex: 'isActive',          key: 'isActive' },
];

const paginationConfig = {
  defaultPageSize: 50,
  showTotal: (total, range) => `${range[0]}-${range[1]} / 全 ${total} 件`,
  showSizeChanger: true,
  pageSizeOptions: ['50', '100', '200'],
};

function formatDaysUntilRelease(days) {
  if (days == null) return '—';
  if (days === 0) return '本日発売';
  if (days < 0) return `${-days}日経過`;
  return `${days}日`;
}

onMounted(async () => {
  loading.value = true;
  try {
    const res = await listPreorders();
    preorders.value = Array.isArray(res.data) ? res.data : [];
  } catch (e) {
    message.warning('予約商品一覧の取得に失敗しました');
    preorders.value = [];
  } finally {
    loading.value = false;
  }
});
</script>
