<template>
  <div style="display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f0f2f5">
    <a-card title="パスワード再発行" style="width: 400px">
      <a-alert v-if="sent" message="入力されたメールアドレスに再設定用リンクを送信しました。" type="success" style="margin-bottom: 12px" />
      <a-form v-else :model="form" @finish="onSubmit" layout="vertical">
        <a-form-item label="メールアドレス" name="email" :rules="[{ required: true, type: 'email' }]">
          <a-input v-model:value="form.email" placeholder="email@example.com" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" block>再発行メールを送信</a-button>
        </a-form-item>
        <div style="text-align: center">
          <router-link to="/login">ログイン画面に戻る</router-link>
        </div>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { requestPasswordReset } from '../api/authApi.js';

const form    = reactive({ email: '' });
const loading = ref(false);
const sent    = ref(false);

async function onSubmit() {
  loading.value = true;
  try {
    await requestPasswordReset(form.email);
    sent.value = true;
  } finally {
    loading.value = false;
  }
}
</script>
