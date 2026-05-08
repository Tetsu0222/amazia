<template>
  <div>
    <a-card size="small" title="現行価格" class="block-card">
      <a-spin v-if="currentLoading" />
      <template v-else>
        <a-descriptions :column="1" size="small" style="margin-bottom: 12px">
          <a-descriptions-item label="価格">
            {{ currentPrice?.price != null ? `${currentPrice.price.toLocaleString()} 円` : '未設定' }}
          </a-descriptions-item>
          <a-descriptions-item label="適用日">
            {{ currentPrice?.startDate ?? '未設定' }}
          </a-descriptions-item>
        </a-descriptions>
        <a-button type="primary" @click="openCurrentEditor">
          価格を変更（即時）
        </a-button>
      </template>
    </a-card>

    <a-card size="small" title="予約変更" class="block-card">
      <a-spin v-if="scheduledLoading" />
      <template v-else>
        <a-descriptions :column="1" size="small" style="margin-bottom: 12px">
          <a-descriptions-item label="変更価格">
            {{ scheduled?.scheduledPrice != null ? `${scheduled.scheduledPrice.toLocaleString()} 円` : '未設定' }}
          </a-descriptions-item>
          <a-descriptions-item label="変更予定日">
            {{ scheduled?.applyDate ?? '未設定' }}
          </a-descriptions-item>
        </a-descriptions>
        <a-space>
          <a-button type="primary" @click="openScheduledEditor">
            {{ scheduled ? '予約変更を更新' : '予約変更を設定' }}
          </a-button>
          <a-popconfirm
            v-if="scheduled"
            title="予約変更を取消しますか？"
            ok-text="取消"
            cancel-text="キャンセル"
            @confirm="handleClearScheduled"
          >
            <a-button :loading="scheduledClearing" danger>
              予約変更を取消
            </a-button>
          </a-popconfirm>
        </a-space>
      </template>
    </a-card>

    <a-card size="small" class="block-card">
      <template #title>
        <a-button type="text" size="small" @click="historyOpen = !historyOpen">
          {{ historyOpen ? '▼ 履歴' : '▶ 履歴' }}
        </a-button>
      </template>
      <div v-show="historyOpen">
        <a-table
          :dataSource="history"
          :columns="historyColumns"
          :loading="historyLoading"
          rowKey="id"
          size="small"
          :pagination="{ pageSize: 10, showSizeChanger: false }"
        >
          <template #bodyCell="{ column, text }">
            <template v-if="column.key === 'price'">
              {{ text != null ? `${text.toLocaleString()} 円` : '—' }}
            </template>
            <template v-if="column.key === 'endDate'">
              {{ text ?? '恒久' }}
            </template>
          </template>
        </a-table>
      </div>
    </a-card>

    <a-modal
      v-model:open="currentModalOpen"
      title="現行価格を変更（即時）"
      :confirm-loading="currentSubmitting"
      ok-text="登録"
      cancel-text="キャンセル"
      @ok="handleRegisterCurrent"
    >
      <a-form
        :model="currentForm"
        :rules="currentRules"
        ref="currentFormRef"
        layout="vertical"
      >
        <a-form-item label="価格（円）" name="price">
          <a-input-number
            v-model:value="currentForm.price"
            :min="0"
            style="width: 100%"
            placeholder="例: 1980"
          />
        </a-form-item>
        <a-form-item label="適用開始日" name="startDate">
          <a-date-picker
            v-model:value="currentForm.startDate"
            value-format="YYYY-MM-DD"
            placeholder="開始日"
            style="width: 100%"
          />
        </a-form-item>
        <a-form-item label="適用終了日">
          <a-date-picker
            v-model:value="currentForm.endDate"
            value-format="YYYY-MM-DD"
            placeholder="未設定 = 恒久"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <a-modal
      v-model:open="scheduledModalOpen"
      :title="scheduled ? '予約変更を更新' : '予約変更を設定'"
      :confirm-loading="scheduledSubmitting"
      ok-text="保存"
      cancel-text="キャンセル"
      @ok="handleSaveScheduled"
    >
      <a-form
        :model="scheduledForm"
        :rules="scheduledRules"
        ref="scheduledFormRef"
        layout="vertical"
      >
        <a-form-item label="変更価格（円）" name="scheduledPrice">
          <a-input-number
            v-model:value="scheduledForm.scheduledPrice"
            :min="0"
            style="width: 100%"
            placeholder="例: 2480"
          />
        </a-form-item>
        <a-form-item label="変更予定日" name="applyDate">
          <a-date-picker
            v-model:value="scheduledForm.applyDate"
            value-format="YYYY-MM-DD"
            :disabled-date="disabledPastDate"
            placeholder="今日以降の日付"
            style="width: 100%"
          />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue';
import { message } from 'ant-design-vue';
import {
  getCurrentSkuPrice, registerCurrentSkuPrice,
  getScheduledSkuPrice, setScheduledSkuPrice, clearScheduledSkuPrice,
  getSkuPriceHistory,
} from '../api/skus';

const props = defineProps({
  skuId: { type: Number, default: null },
});

const currentPrice = ref(null);
const currentLoading = ref(false);

const scheduled = ref(null);
const scheduledLoading = ref(false);
const scheduledClearing = ref(false);

const history = ref([]);
const historyLoading = ref(false);
const historyOpen = ref(false);

const historyColumns = [
  { title: '価格',   dataIndex: 'price',     key: 'price' },
  { title: '開始日', dataIndex: 'startDate', key: 'startDate' },
  { title: '終了日', dataIndex: 'endDate',   key: 'endDate' },
];

const currentModalOpen = ref(false);
const currentSubmitting = ref(false);
const currentFormRef = ref();
const currentForm = ref({ price: null, startDate: null, endDate: null });
const currentRules = {
  price:     [{ required: true, message: '価格は必須です' }],
  startDate: [{ required: true, message: '適用開始日は必須です' }],
};

const scheduledModalOpen = ref(false);
const scheduledSubmitting = ref(false);
const scheduledFormRef = ref();
const scheduledForm = ref({ scheduledPrice: null, applyDate: null });
const scheduledRules = {
  scheduledPrice: [{ required: true, message: '変更価格は必須です' }],
  applyDate:      [{ required: true, message: '変更予定日は必須です' }],
};

const disabledPastDate = (current) => {
  if (!current) return false;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  return current.toDate() < today;
};

const fetchAll = async (skuId) => {
  if (!skuId) {
    currentPrice.value = null;
    scheduled.value = null;
    history.value = [];
    return;
  }
  await Promise.all([
    fetchCurrent(skuId),
    fetchScheduled(skuId),
    fetchHistory(skuId),
  ]);
};

const fetchCurrent = async (skuId) => {
  currentLoading.value = true;
  try {
    currentPrice.value = await getCurrentSkuPrice(skuId);
  } catch {
    currentPrice.value = null;
    message.warning('現行価格の取得に失敗しました');
  } finally {
    currentLoading.value = false;
  }
};

const fetchScheduled = async (skuId) => {
  scheduledLoading.value = true;
  try {
    scheduled.value = await getScheduledSkuPrice(skuId);
  } catch {
    scheduled.value = null;
    message.warning('予約変更の取得に失敗しました');
  } finally {
    scheduledLoading.value = false;
  }
};

const fetchHistory = async (skuId) => {
  historyLoading.value = true;
  try {
    history.value = (await getSkuPriceHistory(skuId)) ?? [];
  } catch {
    history.value = [];
    message.warning('価格履歴の取得に失敗しました');
  } finally {
    historyLoading.value = false;
  }
};

const openCurrentEditor = () => {
  currentForm.value = {
    price: currentPrice.value?.price ?? null,
    startDate: null,
    endDate: null,
  };
  currentModalOpen.value = true;
};

const handleRegisterCurrent = async () => {
  try {
    await currentFormRef.value.validate();
  } catch {
    return;
  }
  currentSubmitting.value = true;
  try {
    await registerCurrentSkuPrice(props.skuId, currentForm.value);
    message.success('価格を登録しました');
    currentModalOpen.value = false;
    await Promise.all([fetchCurrent(props.skuId), fetchHistory(props.skuId)]);
  } catch {
    message.error('価格登録に失敗しました');
  } finally {
    currentSubmitting.value = false;
  }
};

const openScheduledEditor = () => {
  scheduledForm.value = {
    scheduledPrice: scheduled.value?.scheduledPrice ?? null,
    applyDate: scheduled.value?.applyDate ?? null,
  };
  scheduledModalOpen.value = true;
};

const handleSaveScheduled = async () => {
  try {
    await scheduledFormRef.value.validate();
  } catch {
    return;
  }
  scheduledSubmitting.value = true;
  try {
    await setScheduledSkuPrice(props.skuId, scheduledForm.value);
    message.success('予約変更を保存しました');
    scheduledModalOpen.value = false;
    await fetchScheduled(props.skuId);
  } catch (e) {
    if (e?.response?.status === 422) {
      message.error('変更予定日は今日以降を指定してください');
    } else {
      message.error('予約変更の保存に失敗しました');
    }
  } finally {
    scheduledSubmitting.value = false;
  }
};

const handleClearScheduled = async () => {
  scheduledClearing.value = true;
  try {
    await clearScheduledSkuPrice(props.skuId);
    message.success('予約変更を取消しました');
    scheduled.value = null;
  } catch {
    message.error('予約変更の取消に失敗しました');
  } finally {
    scheduledClearing.value = false;
  }
};

defineExpose({ refresh: () => fetchAll(props.skuId) });

watch(
  () => props.skuId,
  (id) => fetchAll(id),
  { immediate: true },
);
</script>

<style scoped>
.block-card + .block-card {
  margin-top: 12px;
}
</style>
