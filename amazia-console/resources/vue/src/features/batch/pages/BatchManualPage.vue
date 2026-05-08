<template>
  <div style="padding: 24px; max-width: 900px">
    <a-page-header title="バッチ手動起動" sub-title="Amazia Console" />

    <a-alert
      type="warning"
      show-icon
      message="本画面は管理者相当ロール（admin / senior_admin / eternal_advisor）のみ操作できます。"
      description="ボタン押下から数秒〜数分でバッチが完了します。実行結果はバッチ実行履歴画面でも確認できます。"
      style="margin-bottom: 24px"
    />

    <a-list
      bordered
      :data-source="JOBS"
      :loading="false"
    >
      <template #renderItem="{ item }">
        <a-list-item>
          <a-list-item-meta :title="item.label" :description="item.description" />
          <template #actions>
            <a-popconfirm
              :title="`${item.label} を起動します。よろしいですか？`"
              ok-text="起動"
              cancel-text="キャンセル"
              @confirm="trigger(item)"
            >
              <a-button
                type="primary"
                :loading="runningJob === item.jobName"
                :disabled="runningJob !== null && runningJob !== item.jobName"
              >
                起動
              </a-button>
            </a-popconfirm>
          </template>
        </a-list-item>
      </template>
    </a-list>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { message } from 'ant-design-vue';
import { triggerBatchManual } from '../api/batchApi.js';

const JOBS = [
  {
    jobName: 'RebuildInventoriesJob',
    label: 'RebuildInventoriesJob（在庫再構築）',
    description: 'SKU TX を商品ロールアップで集計し、inventories を再構築します。重大不整合からの救済用。',
  },
  {
    jobName: 'RecalculateDeliveryScheduleJob',
    label: 'RecalculateDeliveryScheduleJob（配送スケジュール再計算）',
    description: '未確定の配送予定日を再計算します。',
  },
  {
    jobName: 'BootstrapInventoryAdjustmentJob',
    label: 'BootstrapInventoryAdjustmentJob（在庫補填）',
    description: 'product_sku_stocks.quantity を SKU TX に補填投入します。冪等性は reference_type=bootstrap で担保。',
  },
  {
    jobName: 'ApplyScheduledPricesJob',
    label: 'ApplyScheduledPricesJob（予約価格反映）',
    description: '予約変更登録された SKU 価格を即時反映します。通常は日次 03:30 に自動実行。',
  },
];

const runningJob = ref(null);

async function trigger(item) {
  runningJob.value = item.jobName;
  try {
    const res = await triggerBatchManual(item.jobName);
    message.success(`${item.label} を起動しました（${res.data?.message ?? 'triggered'}）`);
  } catch (e) {
    const status = e.response?.status;
    if (status === 503) {
      message.error('手動起動は無効化されています（BATCH_MANUAL_TRIGGER_ENABLED=false）');
    } else if (status === 404) {
      message.error('このジョブは現在の環境では利用できません（本番では一部ジョブが非公開）');
    } else if (status === 403) {
      message.error('権限がありません');
    } else {
      message.warning('起動に失敗しました');
    }
  } finally {
    runningJob.value = null;
  }
}
</script>
