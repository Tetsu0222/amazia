<template>
  <div style="padding: 24px; max-width: 600px">
    <h2>社員登録</h2>
    <a-alert v-if="errorMsg" :message="errorMsg" type="error" style="margin-bottom: 12px" />
    <a-form :model="form" @finish="onSubmit" layout="vertical">
      <a-form-item label="社員ID" name="employeeId" :rules="[{ required: true }]">
        <a-input v-model:value="form.employeeId" />
      </a-form-item>
      <a-form-item label="メールアドレス" name="email" :rules="[{ required: true, type: 'email' }]">
        <a-input v-model:value="form.email" />
      </a-form-item>
      <a-form-item label="名前" name="name" :rules="[{ required: true }]">
        <a-input v-model:value="form.name" />
      </a-form-item>
      <a-form-item label="パスワード" name="password" :rules="[{ required: true }]">
        <a-input-password v-model:value="form.password" />
      </a-form-item>
      <a-form-item label="ロール" name="role" :rules="[{ required: true }]">
        <a-select v-model:value="form.role">
          <a-select-option value="admin">管理者</a-select-option>
          <a-select-option value="user">一般</a-select-option>
        </a-select>
      </a-form-item>
      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="loading">登録</a-button>
          <a-button @click="$router.push('/users')">キャンセル</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { createUser } from '../api/userApi.js';

const router   = useRouter();
const form     = reactive({ employeeId: '', email: '', name: '', password: '', role: 'user' });
const loading  = ref(false);
const errorMsg = ref('');

async function onSubmit() {
  loading.value  = true;
  errorMsg.value = '';
  try {
    await createUser({ ...form });
    router.push('/users');
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '登録に失敗しました。';
  } finally {
    loading.value = false;
  }
}
</script>
