<template>
  <div style="padding: 24px">
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px">
      <h2 style="margin: 0">社員一覧</h2>
      <a-button type="primary" @click="$router.push('/users/new')">社員登録</a-button>
    </div>
    <a-table :dataSource="users" :columns="columns" :loading="loading" rowKey="id">
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'activeFlag'">
          <a-tag :color="record.activeFlag ? 'green' : 'red'">
            {{ record.activeFlag ? '有効' : '無効' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button size="small" @click="$router.push(`/users/${record.id}/edit`)">編集</a-button>
        </template>
      </template>
    </a-table>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue';
import { listUsers } from '../api/userApi.js';

const users   = ref([]);
const loading = ref(false);

const columns = [
  { title: '社員ID',        dataIndex: 'employeeId', key: 'employeeId' },
  { title: 'メールアドレス', dataIndex: 'email',      key: 'email' },
  { title: '名前',          dataIndex: 'name',       key: 'name' },
  { title: 'ロール',         dataIndex: 'role',       key: 'role' },
  { title: '状態',          dataIndex: 'activeFlag', key: 'activeFlag' },
  { title: '操作',          key: 'action' },
];

onMounted(async () => {
  loading.value = true;
  try {
    const res = await listUsers();
    users.value = res.data;
  } finally {
    loading.value = false;
  }
});
</script>
