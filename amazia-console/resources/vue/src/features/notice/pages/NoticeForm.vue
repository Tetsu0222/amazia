<template>
  <div style="padding: 24px">
    <a-page-header :title="isEdit ? 'お知らせ編集' : 'お知らせ新規作成'" @back="goBack" />

    <a-card>
      <a-form
        :model="form"
        :rules="rules"
        layout="vertical"
        @finish="onSubmit"
      >
        <a-form-item label="件名" name="subject">
          <a-input
            v-model:value="form.subject"
            :maxlength="subjectMaxLength"
            show-count
          />
        </a-form-item>

        <a-form-item label="分類" name="categoryId">
          <a-select v-model:value="form.categoryId" placeholder="分類を選択">
            <a-select-option
              v-for="cat in categories"
              :key="cat.id"
              :value="cat.id"
            >{{ cat.label }}</a-select-option>
          </a-select>
        </a-form-item>

        <a-form-item label="本文" name="body">
          <a-textarea
            v-model:value="form.body"
            :rows="10"
            :maxlength="bodyMaxLength"
            show-count
          />
        </a-form-item>

        <a-form-item label="公開開始日" name="publishStart">
          <a-date-picker v-model:value="form.publishStart" value-format="YYYY-MM-DD" />
        </a-form-item>

        <a-form-item label="公開終了日" name="publishEnd">
          <a-date-picker v-model:value="form.publishEnd" value-format="YYYY-MM-DD" />
        </a-form-item>

        <a-form-item v-if="isEdit" label="投稿者">
          <span>{{ form.authorName || '' }}</span>
        </a-form-item>

        <a-form-item>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="submitting">
              {{ isEdit ? '更新' : '作成' }}
            </a-button>
            <a-button @click="goBack">キャンセル</a-button>
          </a-space>
        </a-form-item>
      </a-form>
    </a-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import {
  createNotice,
  updateNotice,
  getNotice,
  listNoticeCategories,
} from '../api/noticeApi.js';

const route = useRoute();
const router = useRouter();

const isEdit = computed(() => !!route.params.id);
const submitting = ref(false);
const categories = ref([]);

// 上限値はサーバ側 config と整合（規約 4-1）。
// 環境変数経由で SPA に注入する場合は import.meta.env を参照する形に切替可。
const subjectMaxLength = 255;
const bodyMaxLength = 10000;

const form = reactive({
  subject: '',
  categoryId: undefined,
  body: '',
  publishStart: undefined,
  publishEnd: undefined,
  authorName: '',
});

const rules = {
  subject:      [{ required: true, message: '件名は必須です' }],
  categoryId:   [{ required: true, message: '分類を選択してください' }],
  body:         [{ required: true, message: '本文は必須です' }],
  publishStart: [{ required: true, message: '公開開始日は必須です' }],
  publishEnd:   [{ required: true, message: '公開終了日は必須です' }],
};

async function loadCategories() {
  try {
    const { data } = await listNoticeCategories();
    categories.value = Array.isArray(data) ? data : [];
  } catch (e) {
    message.warning('分類マスタの取得に失敗しました');
  }
}

async function loadNotice(id) {
  try {
    const { data } = await getNotice(id, { include_unpublished: true, include_deleted: true });
    form.subject = data.subject;
    form.categoryId = data.category?.id;
    form.body = data.body;
    form.publishStart = String(data.publishStart || '').slice(0, 10);
    form.publishEnd   = String(data.publishEnd   || '').slice(0, 10);
    form.authorName = data.author?.name || '';
  } catch (e) {
    message.error('お知らせ詳細の取得に失敗しました');
  }
}

async function onSubmit() {
  submitting.value = true;
  try {
    const payload = {
      subject: form.subject,
      categoryId: form.categoryId,
      body: form.body,
      publishStart: form.publishStart,
      publishEnd: form.publishEnd,
    };
    if (isEdit.value) {
      await updateNotice(Number(route.params.id), payload);
      message.success('更新しました');
    } else {
      await createNotice(payload);
      message.success('作成しました');
    }
    router.push('/notices');
  } catch (e) {
    const status = e?.response?.status;
    if (status === 422) {
      message.error('入力内容を確認してください');
    } else if (status === 410) {
      message.warning('対象のお知らせは削除済です');
    } else {
      message.error('保存に失敗しました');
    }
  } finally {
    submitting.value = false;
  }
}

function goBack() {
  router.push('/notices');
}

onMounted(async () => {
  await loadCategories();
  if (isEdit.value) {
    await loadNotice(route.params.id);
  }
});
</script>
