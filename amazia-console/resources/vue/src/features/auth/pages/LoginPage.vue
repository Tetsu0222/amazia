<template>
  <div style="display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f0f2f5">
    <a-card title="Amazia Console ログイン" style="width: 400px">
      <a-form :model="form" @finish="onSubmit" layout="vertical">
        <a-form-item label="メールアドレス" name="email" :rules="[{ required: true, type: 'email' }]">
          <a-input v-model:value="form.email" placeholder="email@example.com" />
        </a-form-item>
        <a-form-item label="パスワード" name="password" :rules="[{ required: true }]">
          <a-input-password v-model:value="form.password" />
        </a-form-item>
        <a-alert v-if="errorMsg" :message="errorMsg" type="error" style="margin-bottom: 12px" />
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" block>ログイン</a-button>
        </a-form-item>
        <div style="text-align: center">
          <router-link to="/password/reset">パスワードを忘れた方はこちら</router-link>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { login } from '../api/authApi.js';
import { authStore } from '../../../stores/authStore.js';

const router  = useRouter();
const form    = reactive({ email: '', password: '' });
const loading = ref(false);
const errorMsg = ref('');

async function onSubmit() {
  loading.value  = true;
  errorMsg.value = '';
  try {
    const res = await login(form.email, form.password);
    authStore.setAuth(res.data.accessToken, res.data.role);
    router.push('/');
  } catch (e) {
    const status = e.response?.status;
    if (status === 423) {
      errorMsg.value = 'アカウントがロックされています。15分後に再試行してください。';
    } else if (status === 403) {
      errorMsg.value = 'アカウントが無効です。管理者にお問い合わせください。';
    } else {
      errorMsg.value = 'メールアドレスまたはパスワードが正しくありません。';
    }
  } finally {
    loading.value = false;
  }
}
</script>
