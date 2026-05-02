<template>
  <div style="padding: 24px; max-width: 600px">
    <a-page-header
      :title="isEdit ? '商品編集' : '商品登録'"
      @back="$router.push('/')"
    />

    <a-form
      :model="form"
      :rules="rules"
      ref="formRef"
      layout="vertical"
      @finish="handleSubmit"
    >
      <a-form-item label="商品名" name="name">
        <a-input v-model:value="form.name" placeholder="商品名を入力" />
      </a-form-item>

      <a-form-item label="説明" name="description">
        <a-textarea v-model:value="form.description" :rows="3" placeholder="説明を入力（任意）" />
      </a-form-item>

      <a-form-item label="価格（円）" name="price">
        <a-input-number
          v-model:value="form.price"
          :min="0"
          style="width: 100%"
          placeholder="価格を入力"
        />
      </a-form-item>

      <a-form-item label="在庫数" name="stock">
        <a-input-number
          v-model:value="form.stock"
          :min="0"
          style="width: 100%"
          placeholder="在庫数を入力"
        />
      </a-form-item>

      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="submitting">
            {{ isEdit ? '更新' : '登録' }}
          </a-button>
          <a-button @click="$router.push('/')">キャンセル</a-button>
        </a-space>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { getProduct, createProduct, updateProduct } from '../api/products';

const route = useRoute();
const router = useRouter();
const formRef = ref();
const submitting = ref(false);

const isEdit = computed(() => route.params.id !== 'new');

const form = ref({ name: '', description: '', price: null, stock: null });

const rules = {
  name:  [{ required: true, message: '商品名は必須です' }],
  price: [{ required: true, message: '価格は必須です' }],
  stock: [{ required: true, message: '在庫数は必須です' }],
};

onMounted(async () => {
  if (isEdit.value) {
    try {
      const product = await getProduct(route.params.id);
      form.value = {
        name:        product.name,
        description: product.description ?? '',
        price:       product.price,
        stock:       product.stock,
      };
    } catch {
      message.error('商品データの取得に失敗しました');
      router.push('/');
    }
  }
});

const handleSubmit = async () => {
  submitting.value = true;
  try {
    if (isEdit.value) {
      await updateProduct(route.params.id, form.value);
      message.success('更新しました');
    } else {
      await createProduct(form.value);
      message.success('登録しました');
    }
    router.push('/');
  } catch {
    message.error(isEdit.value ? '更新に失敗しました' : '登録に失敗しました');
  } finally {
    submitting.value = false;
  }
};
</script>
