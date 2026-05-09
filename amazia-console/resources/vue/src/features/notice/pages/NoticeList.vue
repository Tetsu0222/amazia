<template>
  <div style="padding: 24px">
    <a-page-header title="お知らせ管理">
      <template #description>
        <span style="color: rgba(0, 0, 0, 0.65); font-size: 14px">{{ countLabel }}</span>
      </template>
      <template #extra>
        <a-button type="primary" @click="goCreate">新規作成</a-button>
      </template>
    </a-page-header>

    <a-card style="margin-bottom: 16px">
      <a-form layout="inline" @finish="onSearch">
        <a-form-item label="分類">
          <a-select
            v-model:value="searchForm.categoryId"
            allow-clear
            placeholder="すべて"
            style="width: 160px"
          >
            <a-select-option
              v-for="cat in categories"
              :key="cat.id"
              :value="cat.id"
            >{{ cat.label }}</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item label="未公開を含む">
          <a-switch v-model:checked="searchForm.includeUnpublished" />
        </a-form-item>
        <a-form-item label="削除済を含む">
          <a-switch v-model:checked="searchForm.includeDeleted" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit">検索</a-button>
          <a-button style="margin-left: 8px" @click="resetSearch">クリア</a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <a-table
      :columns="columns"
      :data-source="rows"
      :pagination="pagination"
      :loading="loading"
      row-key="id"
      @change="onTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'subject'">
          <router-link :to="`/notices/${record.id}/edit`">{{ record.subject }}</router-link>
        </template>
        <template v-else-if="column.dataIndex === 'category'">
          {{ record.category?.label }}
        </template>
        <template v-else-if="column.dataIndex === 'author'">
          {{ record.author?.name }}
        </template>
        <template v-else-if="column.dataIndex === 'publishState'">
          <a-tag :color="stateColor(record.publishState)">{{ record.publishState }}</a-tag>
        </template>
        <template v-else-if="column.dataIndex === 'updatedAt'">
          {{ formatDate(record.updatedAt) }}
        </template>
        <template v-else-if="column.dataIndex === 'actions'">
          <a-space>
            <a-button size="small" @click="goEdit(record.id)">編集</a-button>
            <a-popconfirm
              title="このお知らせを削除しますか？"
              ok-text="削除"
              cancel-text="キャンセル"
              @confirm="onDelete(record.id)"
            >
              <a-button size="small" danger :disabled="record.publishState === '削除済'">削除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { computed, reactive, ref, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { listNotices, deleteNotice, listNoticeCategories } from '../api/noticeApi.js';

const router = useRouter();

const loading = ref(false);
const rows = ref([]);
const total = ref(0);
const categories = ref([]);

const searchForm = reactive({
  categoryId: undefined,
  includeUnpublished: false,
  includeDeleted: false,
});

const pagination = reactive({
  current: 1,
  pageSize: 20,
  total: 0,
  showSizeChanger: false,
});

const columns = [
  { title: 'ID', dataIndex: 'id', width: 80 },
  { title: '件名', dataIndex: 'subject' },
  { title: '分類', dataIndex: 'category', width: 120 },
  { title: '投稿者', dataIndex: 'author', width: 160 },
  { title: '公開状態', dataIndex: 'publishState', width: 110 },
  { title: '最終更新', dataIndex: 'updatedAt', width: 180 },
  { title: '操作', dataIndex: 'actions', width: 160 },
];

const countLabel = computed(() => `全 ${total.value} 件`);

function stateColor(state) {
  switch (state) {
    case '公開中': return 'green';
    case '未公開': return 'blue';
    case '終了':   return 'default';
    case '削除済': return 'red';
    default:       return 'default';
  }
}

function formatDate(value) {
  if (!value) return '';
  const d = new Date(value);
  return Number.isNaN(d.getTime())
    ? value
    : `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
}

async function load() {
  loading.value = true;
  try {
    const { data } = await listNotices({
      page: pagination.current,
      per_page: pagination.pageSize,
      category_id: searchForm.categoryId,
      include_unpublished: searchForm.includeUnpublished || undefined,
      include_deleted: searchForm.includeDeleted || undefined,
    });
    rows.value = data?.content ?? [];
    total.value = data?.totalElements ?? 0;
    pagination.total = total.value;
  } catch (e) {
    message.error('お知らせ一覧の取得に失敗しました');
  } finally {
    loading.value = false;
  }
}

async function loadCategories() {
  try {
    const { data } = await listNoticeCategories();
    categories.value = Array.isArray(data) ? data : [];
  } catch (e) {
    // 分類マスタ取得失敗はフォーム機能の縮退で許容
  }
}

function onSearch() {
  pagination.current = 1;
  load();
}

function resetSearch() {
  searchForm.categoryId = undefined;
  searchForm.includeUnpublished = false;
  searchForm.includeDeleted = false;
  pagination.current = 1;
  load();
}

function onTableChange(p) {
  pagination.current = p.current;
  load();
}

function goCreate() {
  router.push('/notices/create');
}

function goEdit(id) {
  router.push(`/notices/${id}/edit`);
}

async function onDelete(id) {
  try {
    await deleteNotice(id);
    message.success('削除しました');
    load();
  } catch (e) {
    if (e?.response?.status === 410) {
      message.warning('既に削除済みです');
    } else {
      message.error('削除に失敗しました');
    }
  }
}

onMounted(() => {
  loadCategories();
  load();
});
</script>
