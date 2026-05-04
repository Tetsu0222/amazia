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

      <a-form-item>
        <a-space>
          <a-button type="primary" html-type="submit" :loading="submitting">
            {{ isEdit ? '更新' : '登録' }}
          </a-button>
          <a-button @click="$router.push('/')">キャンセル</a-button>
        </a-space>
      </a-form-item>
    </a-form>

    <!-- 画像管理（編集モードのみ） -->
    <template v-if="isEdit">
      <a-divider>商品画像</a-divider>
      <a-form-item label="画像をアップロード（PNG・200KB以下）">
        <a-upload
          accept=".png,image/png"
          :show-upload-list="false"
          :custom-request="handleImageUpload"
          :disabled="imageUploading"
        >
          <a-button :loading="imageUploading">画像を選択</a-button>
        </a-upload>
      </a-form-item>
      <a-space wrap style="margin-top: 8px">
        <div
          v-for="img in images"
          :key="img.id"
          style="display: flex; flex-direction: column; align-items: center; gap: 4px"
        >
          <div style="position: relative; width: 80px; height: 80px">
            <img
              :src="`/storage/Product/images/${img.imagePath}`"
              style="width: 80px; height: 80px; object-fit: contain; border: 1px solid #ddd; border-radius: 4px"
            />
            <a-tag
              v-if="img.sortOrder === 1"
              color="blue"
              style="position: absolute; top: 2px; left: 2px; font-size: 10px; padding: 0 4px"
            >
              メイン
            </a-tag>
          </div>
          <a-button size="small" danger @click="handleImageDelete(img.id)">削除</a-button>
        </div>
      </a-space>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  getProduct, createProduct, updateProduct, getProductStatuses,
  getProductImages, uploadProductImage, deleteProductImage,
} from '../api/products';

const route = useRoute();
const router = useRouter();
const formRef = ref();
const submitting = ref(false);
const statuses = ref([]);
const statusesLoading = ref(false);
const images = ref([]);
const imageUploading = ref(false);

const isEdit = computed(() => route.path !== '/products/new');

const form = ref({
  name: '',
  description: '',
  statusCode: null,
  publishStart: null,
  publishEnd: null,
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
        name:         product.name,
        description:  product.description ?? '',
        statusCode:   product.statusCode ?? null,
        publishStart: product.publishStart ?? null,
        publishEnd:   product.publishEnd ?? null,
      };
    } catch {
      message.error('商品データの取得に失敗しました');
      router.push('/');
    }
    await fetchImages();
  }
});

const fetchImages = async () => {
  try {
    images.value = await getProductImages(route.params.id);
  } catch {
    message.warning('画像一覧の取得に失敗しました');
  }
};

const handleImageUpload = async ({ file }) => {
  imageUploading.value = true;
  try {
    await uploadProductImage(route.params.id, file);
    await fetchImages();
  } catch {
    message.error('画像のアップロードに失敗しました');
  } finally {
    imageUploading.value = false;
  }
};

const handleImageDelete = async (imageId) => {
  try {
    await deleteProductImage(imageId);
    await fetchImages();
  } catch {
    message.error('画像の削除に失敗しました');
  }
};

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
