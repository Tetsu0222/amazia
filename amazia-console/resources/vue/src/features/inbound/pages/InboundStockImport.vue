<template>
  <div style="padding: 24px; max-width: 720px">
    <a-page-header
      title="Excel一括入荷"
      sub-title="ExcelファイルをアップロードしてSKU単位で在庫を加算します"
      @back="$router.push('/inbound')"
    />

    <a-card style="margin-top: 16px">
      <a-typography-paragraph>
        1行目をヘッダー行として読み込みます。列名は
        <a-tag>sku_code</a-tag>
        <a-tag>quantity</a-tag>
        を必須とし、任意で
        <a-tag>tracking_code</a-tag>
        を含められます。
      </a-typography-paragraph>
      <a-typography-paragraph type="secondary" style="font-size: 12px">
        ・同じ sku_code は quantity が加算されます<br />
        ・存在しない sku_code はエラー行として返されます<br />
        ・quantity は 1 以上の整数のみ（減算は不可）<br />
        ・tracking_code は任意。空欄なら入荷管理画面では「—」表示<br />
        ・各行は入荷管理画面に1件として表示されます（追跡番号で検索可能）
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
          一括入荷を実行
        </a-button>
      </div>
    </a-card>

    <a-card v-if="result" style="margin-top: 16px" title="入荷結果">
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
          :row-key="(_, i) => i"
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
import { importSkuStock } from '../../skus/api/skus.js';

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

  const file = fileList.value[0].originFileObj ?? fileList.value[0];

  uploading.value = true;
  result.value = null;

  try {
    result.value = await importSkuStock(file);
    message.success(`${result.value.succeeded} 件の入荷を登録しました`);
  } catch {
    message.error('アップロードに失敗しました');
  } finally {
    uploading.value = false;
  }
};
</script>
