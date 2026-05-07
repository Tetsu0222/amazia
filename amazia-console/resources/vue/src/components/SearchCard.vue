<template>
  <a-card size="small" class="search-card" :body-style="{ padding: '12px 16px' }">
    <div v-if="hasWideField" class="search-card__wide-row">
      <span v-if="wideFieldLabel" class="search-card__wide-label">{{ wideFieldLabel }}</span>
      <slot name="wide-field">
        <a-input
          :value="wideFieldValue"
          :placeholder="wideFieldPlaceholder ?? '部分一致で検索'"
          allow-clear
          class="search-card__wide-input"
          @update:value="$emit('update:wideFieldValue', $event)"
        />
      </slot>
    </div>

    <div class="search-card__grid">
      <slot />
      <div class="search-card__actions">
        <slot name="extra" />
        <a-button @click="$emit('clear')">クリア</a-button>
      </div>
    </div>
  </a-card>
</template>

<script setup>
import { computed, useSlots } from 'vue';

const props = defineProps({
  wideFieldLabel:       { type: String,  default: '' },
  wideFieldPlaceholder: { type: String,  default: '' },
  wideFieldValue:       { type: String,  default: '' },
});

defineEmits(['clear', 'update:wideFieldValue']);

const slots = useSlots();
const hasWideField = computed(() => !!props.wideFieldLabel || !!slots['wide-field']);
</script>

<style scoped>
.search-card {
  margin-bottom: 16px;
}
.search-card__wide-row {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}
.search-card__wide-label {
  font-weight: 500;
  min-width: 80px;
  flex-shrink: 0;
}
.search-card__wide-input {
  flex: 1;
  max-width: 480px;
}
.search-card__grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(220px, 1fr));
  column-gap: 16px;
  row-gap: 8px;
  align-items: end;
}
.search-card__grid :deep(.span-2),
.search-card__grid > .span-2 {
  grid-column: span 2;
}
@media (max-width: 480px) {
  .search-card__grid :deep(.span-2),
  .search-card__grid > .span-2 {
    grid-column: span 1;
  }
}
.search-card__actions {
  display: flex;
  gap: 8px;
  align-items: end;
  justify-self: end;
  grid-column: 1 / -1;
}
:deep(.ant-form-item) {
  margin-bottom: 0;
}
:deep(.range-sep) {
  display: inline-flex;
  align-items: center;
  padding: 0 6px;
  color: rgba(0, 0, 0, 0.45);
}
:deep(.range-unit) {
  display: inline-flex;
  align-items: center;
  padding-left: 4px;
  color: rgba(0, 0, 0, 0.65);
}
</style>
