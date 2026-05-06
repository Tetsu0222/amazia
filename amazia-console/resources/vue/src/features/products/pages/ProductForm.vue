<template>
  <div style="padding: 24px; max-width: 640px">
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

      <a-form-item label="ステータス" name="statusCode">
        <a-select
          v-model:value="form.statusCode"
          placeholder="ステータスを選択"
          :loading="statusesLoading"
          allow-clear
        >
          <a-select-option
            v-for="s in statuses"
            :key="s.code"
            :value="s.code"
          >
            {{ s.name }}
          </a-select-option>
        </a-select>
      </a-form-item>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="公開開始日時" name="publishStart">
            <a-date-picker
              v-model:value="form.publishStart"
              show-time
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="未設定 = 即時公開"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="公開終了日時" name="publishEnd">
            <a-date-picker
              v-model:value="form.publishEnd"
              show-time
              format="YYYY-MM-DD HH:mm"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="未設定 = 恒久公開"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-divider orientation="left">予約・発売</a-divider>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item label="予約開始日" name="preorderStartDate">
            <a-date-picker
              v-model:value="form.preorderStartDate"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="未設定 = 公開と同時に予約可"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item label="発売日" name="releaseDate">
            <a-date-picker
              v-model:value="form.releaseDate"
              format="YYYY-MM-DD"
              value-format="YYYY-MM-DD"
              placeholder="未設定 = 公開即発売"
              style="width: 100%"
            />
          </a-form-item>
        </a-col>
      </a-row>

      <a-row :gutter="16">
        <a-col :span="12">
          <a-form-item name="acceptPreorder">
            <a-checkbox v-model:checked="form.acceptPreorder">予約購入を受け付ける</a-checkbox>
          </a-form-item>
        </a-col>
        <a-col :span="12">
          <a-form-item name="acceptBackorder">
            <a-checkbox v-model:checked="form.acceptBackorder">在庫切れ時に予約継続する</a-checkbox>
          </a-form-item>
        </a-col>
      </a-row>

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
import {
  getProduct, createProduct, updateProduct, getProductStatuses,
} from '../api/products';

const route = useRoute();
const router = useRouter();
const formRef = ref();
const submitting = ref(false);
const statuses = ref([]);
const statusesLoading = ref(false);

const isEdit = computed(() => route.path !== '/products/new');

const form = ref({
  name: '',
  description: '',
  statusCode: null,
  publishStart: null,
  publishEnd: null,
  preorderStartDate: null,
  releaseDate: null,
  acceptPreorder: false,
  acceptBackorder: false,
});

const rules = {
  name: [{ required: true, message: '商品名は必須です' }],
};

onMounted(async () => {
  statusesLoading.value = true;
  try {
    statuses.value = await getProductStatuses();
  } catch {
    message.warning('ステータス一覧の取得に失敗しました');
  } finally {
    statusesLoading.value = false;
  }

  if (isEdit.value) {
    try {
      const product = await getProduct(route.params.id);
      form.value = {
        name:              product.name,
        description:       product.description ?? '',
        statusCode:        product.statusCode ?? null,
        publishStart:      product.publishStart ?? null,
        publishEnd:        product.publishEnd ?? null,
        preorderStartDate: product.preorderStartDate ?? null,
        releaseDate:       product.releaseDate ?? null,
        acceptPreorder:    product.acceptPreorder ?? false,
        acceptBackorder:   product.acceptBackorder ?? false,
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
