<template>
  <div style="padding: 24px; max-width: 900px">
    <a-page-header
      :title="`配送詳細 #${id}`"
      sub-title="Amazia Console"
      @back="goBack"
    />

    <a-spin :spinning="loading">
      <a-descriptions
        v-if="delivery"
        bordered
        :column="2"
        size="small"
        style="margin-bottom: 16px"
      >
        <a-descriptions-item label="ID">{{ delivery.id }}</a-descriptions-item>
        <a-descriptions-item label="売上ID">{{ delivery.salesId }}</a-descriptions-item>
        <a-descriptions-item label="配送方法">
          {{ SHIPPING_METHOD_LABEL[delivery.shippingMethodId] ?? `#${delivery.shippingMethodId}` }}
        </a-descriptions-item>
        <a-descriptions-item label="配送ステータス">
          {{ STATUS_LABEL[delivery.shippingStatusId] ?? `#${delivery.shippingStatusId}` }}
        </a-descriptions-item>
        <a-descriptions-item label="配送先住所ID">{{ delivery.shippingAddressId }}</a-descriptions-item>
        <a-descriptions-item label="追跡番号">{{ delivery.trackingCode ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="配送予定日">
          {{ delivery.scheduledDate ?? '入荷待ち' }}
        </a-descriptions-item>
        <a-descriptions-item label="発送日">{{ delivery.shippedDate ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="配達完了日">{{ delivery.deliveredDate ?? '—' }}</a-descriptions-item>
        <a-descriptions-item label="作成日時">
          {{ (delivery.createdAt ?? '').replace('T', ' ').slice(0, 19) }}
        </a-descriptions-item>
      </a-descriptions>

      <a-space wrap>
        <a-button
          type="primary"
          :disabled="!canTransition"
          @click="openStatus = true"
        >
          ステータス更新
        </a-button>
        <a-button :disabled="!canChangeAddress" @click="openAddress = true">
          配送先住所変更
        </a-button>
        <a-button :disabled="!canChangeSchedule" @click="openSchedule = true">
          配送予定日変更
        </a-button>
        <a-button @click="openTracking = true">追跡番号登録</a-button>
      </a-space>
    </a-spin>

    <StatusUpdateDialog
      v-model:open="openStatus"
      :delivery-id="delivery?.id"
      :current-status-id="delivery?.shippingStatusId"
      @updated="reload"
    />
    <AddressUpdateDialog
      v-model:open="openAddress"
      :delivery-id="delivery?.id"
      :current-address-id="delivery?.shippingAddressId"
      @updated="reload"
    />
    <ScheduledDateUpdateDialog
      v-model:open="openSchedule"
      :delivery-id="delivery?.id"
      :current-date="delivery?.scheduledDate"
      @updated="reload"
    />
    <TrackingCodeRegisterDialog
      v-model:open="openTracking"
      :delivery-id="delivery?.id"
      :current-tracking-code="delivery?.trackingCode"
      @updated="reload"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { message } from 'ant-design-vue';
import { getDelivery } from '../api/deliveryApi.js';
import StatusUpdateDialog          from './dialogs/StatusUpdateDialog.vue';
import AddressUpdateDialog         from './dialogs/AddressUpdateDialog.vue';
import ScheduledDateUpdateDialog   from './dialogs/ScheduledDateUpdateDialog.vue';
import TrackingCodeRegisterDialog  from './dialogs/TrackingCodeRegisterDialog.vue';

const STATUS_LABEL = {
  1: '配送準備中（PENDING）',
  2: '配送済（SHIPPED）',
  3: '配送完了（DELIVERED）',
  4: '返品申請中（RETURN_REQUESTED）',
  5: '返品完了（RETURNED）',
};
const SHIPPING_METHOD_LABEL = {
  1: '宅配',
  2: 'コンビニ受取',
  3: '置き配',
};

const route = useRoute();
const router = useRouter();
const id = Number(route.params.id);

const delivery = ref(null);
const loading = ref(false);

const openStatus = ref(false);
const openAddress = ref(false);
const openSchedule = ref(false);
const openTracking = ref(false);

// RETURNED から先は遷移不可（設計書 §配送ステータス遷移ルール）
const canTransition = computed(() => delivery.value && delivery.value.shippingStatusId !== 5);
// 配達完了以降は住所変更不可（運用上の判断。Core 側でも将来的に拒否ルール追加可能）
const canChangeAddress = computed(() => delivery.value && delivery.value.shippingStatusId === 1);
// 配達完了以降は予定日変更しても無意味
const canChangeSchedule = computed(() => delivery.value && delivery.value.shippingStatusId <= 2);

async function reload() {
  loading.value = true;
  try {
    const res = await getDelivery(id);
    delivery.value = res.data;
  } catch (e) {
    message.warning('配送詳細の取得に失敗しました');
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.push('/delivery');
}

onMounted(reload);
</script>
