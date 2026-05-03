<template>
  <div style="padding: 24px; max-width: 720px">
    <a-page-header
      title="商品一括登録"
      sub-title="Excelファイルをアップロードして複数商品を一括登録します"
      @back="$router.push('/')"
    />

    <a-card style="margin-top: 16px">
      <a-typography-paragraph>
        1行目をヘッダー行として読み込みます。列名は
        <a-tag>name</a-tag>
        <a-tag>description</a-tag>
        <a-tag>price</a-tag>
        <a-tag>stock</a-tag>
        の順で用意してください（name / price / stock は必須）。
      </a-typography-paragraph>

      <a-upload-dragger
        v-model:fileList="fileList"
        :before-upload="handleBeforeUpload"
        accept=".xlsx,.xls"
        :max-count="1"
        style="margin-top: 16px"
      >
        <p class="ant-upload-drag-icon">
          <inbox-outlined />
        </p>
        <p class="ant-upload-text">クリックまたはドラッグでExcelファイルを選択</p>
        <p class="ant-upload-hint">.xlsx / .xls のみ対応</p>
      </a-upload-dragger>

      <div style="margin-top: 16px; text-align: right">
        <a-button
          type="primary"
          :loading="uploading"
          :disabled="fileList.length === 0"
          @click="handleUpload"
        >
          一括登録を実行
        </a-button>
      </div>
    </a-card>

    <a-card v-if="result" style="margin-top: 16px" title="登録結果">
      <a-alert
        :message="`成功: ${result.succeeded} 件`"
        type="success"
        show-icon
        style="margin-bottom: 12px"
      />
      <template v-if="result.failed.length > 0">
        <a-alert
          :message="`失敗: ${result.failed.length} 件`"
          type="error"
          show-icon
          style="margin-bottom: 12px"
        />
        <a-table
          :columns="failColumns"
          :data-source="result.failed"
          :pagination="false"
          size="small"
          row-key="(r, i) => i"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'row'">
              {{ JSON.stringify(record.row) }}
            </template>
          </template>
        </a-table>
      </template>
    </a-card>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { InboxOutlined } from '@ant-design/icons-vue';
import { message } from 'ant-design-vue';
import axios from 'axios';

const fileList = ref([]);
const uploading = ref(false);
const result = ref(null);

const failColumns = [
  { title: '行データ', key: 'row', dataIndex: 'row' },
  { title: 'エラー理由', key: 'reason', dataIndex: 'reason' },
];

const handleBeforeUpload = (file) => {
  fileList.value = [file];
  return false;
};

const handleUpload = async () => {
  if (fileList.value.length === 0) return;

  const formData = new FormData();
  formData.append('file', fileList.value[0]);

  uploading.value = true;
  result.value = null;

  try {
    const response = await axios.post('/api/products/import', formData, {
      headers: { 'Content-Type': 'multipart/form-data' },
    });
    result.value = response.data;
    message.success(`${response.data.succeeded} 件登録しました`);
  } catch (e) {
    message.error('アップロードに失敗しました');
  } finally {
    uploading.value = false;
  }
};
</script>
