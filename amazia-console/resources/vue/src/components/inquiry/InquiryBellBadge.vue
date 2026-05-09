<template>
  <a-badge
    :count="displayCount"
    :overflow-count="99"
    :offset="[10, 0]"
    :show-zero="false"
  >
    <span>問い合わせ</span>
  </a-badge>
</template>

<script setup>
import { computed } from 'vue';
import { useVisibilityPolling } from '../../composables/useVisibilityPolling.js';
import { getUnreadInquiryCount } from '../../features/inquiry/api/inquiryApi.js';

/**
 * フェーズ18: ベルマーク（未対応件数バッジ）。
 *
 * - 30 秒間隔ポーリング（VITE_INQUIRY_BELL_POLLING_INTERVAL_MS で上書き可能）
 * - タブ非表示時は停止 / 復帰時に即時 fetch + 再開（useVisibilityPolling）
 * - 99 を超えると 99+ 表記
 */
const intervalMs = Number(import.meta.env.VITE_INQUIRY_BELL_POLLING_INTERVAL_MS) || 30000;

const fetcher = async () => {
  const res = await getUnreadInquiryCount();
  return res.data;
};

const { data } = useVisibilityPolling(fetcher, intervalMs);

const displayCount = computed(() => data.value?.count ?? 0);
</script>
