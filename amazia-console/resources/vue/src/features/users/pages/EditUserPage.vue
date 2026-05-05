<template>
  <div style="padding: 24px; max-width: 600px">
    <h2>社員編集</h2>
    <a-alert v-if="errorMsg" :message="errorMsg" type="error" style="margin-bottom: 12px" />
    <a-form :model="form" @finish="onSubmit" layout="vertical">
      <a-form-item label="メールアドレス" name="email" :rules="[{ required: true, type: 'email' }]">
        <a-input v-model:value="form.email" />
      </a-form-item>
      <a-form-item label="名前" name="name" :rules="[{ required: true }]">
        <a-input v-model:value="form.name" />
      </a-form-item>
      <a-form-item label="ロール" name="role" :rules="[{ required: true }]">
        <a-select v-model:value="form.role">
          <a-select-option value="admin">管理者</a-select-option>
          <a-select-option value="user">一般</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item label="有効/無効" name="activeFlag">
        <a-switch v-model:checked="form.activeFlag" checked-children="有効" un-checked-children="無効" />
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">更新</a-button>
          <a-button @click="$router.push('/users')">キャンセル</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { reactive, ref, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { listUsers, updateUser } from '../api/userApi.js';

const route    = useRoute();
const router   = useRouter();
const form     = reactive({ email: '', name: '', role: 'user', activeFlag: true });
const loading  = ref(false);
const errorMsg = ref('');

onMounted(async () => {
  try {
    const res  = await listUsers();
    const user = res.data.find(u => String(u.id) === String(route.params.id));
    if (user) {
      form.email      = user.email;
      form.name       = user.name;
      form.role       = user.role;
      form.activeFlag = user.activeFlag;
    }
  } catch {
    errorMsg.value = '社員情報の取得に失敗しました。';
  }
});

async function onSubmit() {
  loading.value  = true;
  errorMsg.value = '';
  try {
    await updateUser(route.params.id, { ...form });
    router.push('/users');
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '更新に失敗しました。';
  } finally {
    loading.value = false;
  }
}
</script>
