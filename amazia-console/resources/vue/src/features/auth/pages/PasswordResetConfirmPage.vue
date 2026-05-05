<template>
  <div style="display: flex; justify-content: center; align-items: center; min-height: 100vh; background: #f0f2f5">
    <a-card title="新しいパスワードを設定" style="width: 400px">
      <a-alert v-if="done" message="パスワードを変更しました。ログイン画面からログインしてください。" type="success" style="margin-bottom: 12px" />
      <a-alert v-if="errorMsg" :message="errorMsg" type="error" style="margin-bottom: 12px" />
      <a-form v-if="!done" :model="form" @finish="onSubmit" layout="vertical">
        <a-form-item label="新しいパスワード" name="newPassword" :rules="[{ required: true }]">
          <a-input-password v-model:value="form.newPassword" />
        </a-form-item>
        <a-form-item label="新しいパスワード（確認）" name="confirm" :rules="[{ required: true }]">
          <a-input-password v-model:value="form.confirm" />
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" :loading="loading" block>パスワードを更新</a-button>
        </a-form-item>
      </a-form>
      <div v-if="done" style="text-align: center; margin-top: 12px">
        <router-link to="/login">ログイン画面へ</router-link>
      </div>
    </a-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue';
import { useRoute } from 'vue-router';
import { confirmPasswordReset } from '../api/authApi.js';

const route    = useRoute();
const form     = reactive({ newPassword: '', confirm: '' });
const loading  = ref(false);
const done     = ref(false);
const errorMsg = ref('');

async function onSubmit() {
  if (form.newPassword !== form.confirm) {
    errorMsg.value = 'パスワードが一致しません。';
    return;
  }
  loading.value  = true;
  errorMsg.value = '';
  try {
    await confirmPasswordReset(route.query.token, form.newPassword);
    done.value = true;
  } catch (e) {
    errorMsg.value = e.response?.status === 422
      ? 'パスワードがポリシーに違反しています。（8文字以上・英大小文字・数字を含む）'
      : 'リンクが無効か期限切れです。再度お試しください。';
  } finally {
    loading.value = false;
  }
}
</script>
