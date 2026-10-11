<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue';
import UiButton from './UiButton.vue';
import UiIcon, { type UiIconName } from './UiIcon.vue';
withDefaults(
  defineProps<{
    title: string;
    description?: string;
    actionLabel?: string;
    kind?: 'body' | 'meal' | 'workout' | 'photo';
    icon?: UiIconName;
  }>(),
  { kind: 'body', description: undefined, actionLabel: undefined, icon: undefined },
);
defineEmits<{ action: [] }>();
</script>
<template>
  <EmptyState
    class="ui-empty-state"
    :class="`ui-empty-state--${kind}`"
    :title="title"
    :description="description"
    ><template #image><UiIcon :name="icon ?? kind" /></template
    ><template v-if="actionLabel" #action
      ><UiButton block @click="$emit('action')">{{ actionLabel }}</UiButton></template
    ></EmptyState
  >
</template>
<style scoped lang="scss">
.ui-empty-state {
  padding: 56px 24px;
  gap: 24px;
  background: var(--color-surface);
  border-radius: 20px;
  :deep(.empty-state__image) {
    width: 72px;
    height: 72px;
    border-radius: 24px;
    background: var(--ui-body-background);
    color: var(--ui-body-foreground);
  }
  :deep(.ui-icon) {
    width: 30px;
    height: 30px;
  }
  :deep(h2) {
    font-size: 19px;
  }
  :deep(p) {
    font-size: 13px;
  }
  :deep(.empty-state__action) {
    width: 100%;
    max-width: 260px;
    margin-top: 16px;
  }
  &--meal :deep(.empty-state__image) {
    background: var(--ui-meal-background);
    color: var(--ui-meal-foreground);
  }
  &--workout :deep(.empty-state__image) {
    background: var(--ui-workout-background);
    color: var(--ui-workout-foreground);
  }
  &--photo :deep(.empty-state__image) {
    background: var(--ui-photo-background);
    color: var(--ui-photo-foreground);
  }
}
</style>
