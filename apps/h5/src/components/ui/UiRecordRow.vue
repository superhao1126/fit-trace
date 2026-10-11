<script setup lang="ts">
import { Button } from 'tdesign-mobile-vue';
import type { WorkoutType } from '@fit-trace/shared';
import SportIcon from '@/components/SportIcon.vue';
import UiIcon, { type UiIconName } from './UiIcon.vue';
withDefaults(
  defineProps<{
    kind: 'body' | 'meal' | 'workout' | 'photo';
    title: string;
    subtitle?: string;
    value?: string | number;
    unit?: string;
    workoutType?: WorkoutType;
    image?: string;
    icon?: UiIconName;
    tone?: 'category' | 'neutral';
    informational?: boolean;
  }>(),
  {
    subtitle: '',
    unit: '',
    value: undefined,
    workoutType: undefined,
    image: undefined,
    icon: undefined,
    tone: 'category',
  },
);
</script>
<template>
  <component
    :is="informational ? 'div' : Button"
    class="ui-record-row"
    :class="[`ui-record-row--${kind}`, { 'ui-record-row--neutral': tone === 'neutral' }]"
    :variant="informational ? undefined : 'text'"
    :block="informational ? undefined : true"
  >
    <span class="ui-record-row__layout"
      ><span class="ui-record-row__icon"
        ><img v-if="image" :src="image" alt="" /><SportIcon
          v-else-if="kind === 'workout' && workoutType"
          :type="workoutType" /><UiIcon v-else :name="icon ?? kind"
      /></span>
      <span class="ui-record-row__content"
        ><strong>{{ title }}</strong
        ><span v-if="subtitle">{{ subtitle }}</span></span
      >
      <span v-if="value !== undefined" class="ui-record-row__value"
        >{{ value }}<small v-if="unit"> {{ unit }}</small></span
      ><UiIcon v-else name="right" class="ui-record-row__chevron"
    /></span>
  </component>
</template>
<style scoped lang="scss">
.ui-record-row {
  display: block;
  width: 100%;
  height: auto;
  min-height: 80px;
  padding: 16px;
  color: var(--ui-text-primary);
  background: var(--color-surface);
  border: 0;
  border-radius: var(--ui-radius-card);
  text-align: left;
  font-weight: 400;
  &:focus-visible {
    outline: 2px solid var(--ui-primary);
    outline-offset: 2px;
  }
  :deep(.t-button__content) {
    display: block;
    width: 100%;
  }
}
.ui-record-row__layout {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.ui-record-row__icon {
  display: grid;
  place-items: center;
  flex: none;
  width: 42px;
  height: 42px;
  overflow: hidden;
  border-radius: 14px;
  color: var(--ui-body-foreground);
  background: var(--ui-body-background);
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
  :deep(.sport-icon) {
    width: 24px;
    height: 24px;
  }
}
.ui-record-row--meal .ui-record-row__icon {
  color: var(--ui-meal-foreground);
  background: var(--ui-meal-background);
}
.ui-record-row--workout .ui-record-row__icon {
  color: var(--ui-workout-foreground);
  background: var(--ui-workout-background);
}
.ui-record-row--photo .ui-record-row__icon {
  color: var(--ui-photo-foreground);
  background: var(--ui-photo-background);
}
.ui-record-row--neutral .ui-record-row__icon {
  color: var(--ui-text-secondary);
  background: var(--color-surface-muted);
}
.ui-record-row__content {
  display: grid;
  min-width: 0;
  flex: 1;
  gap: 6px;
  strong {
    color: var(--ui-text-primary);
    font-size: 15px;
    font-weight: 600;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  > span {
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    font-size: 12px;
    color: var(--ui-text-secondary);
  }
}
.ui-record-row__value {
  max-width: 40%;
  flex: none;
  font-size: 14px;
  font-variant-numeric: tabular-nums;
  overflow-wrap: anywhere;
  small {
    margin-left: 4px;
    font-size: 12px;
  }
}
.ui-record-row__chevron {
  width: 16px;
  height: 16px;
  color: var(--ui-text-tertiary);
}
@media (max-width: 360px) {
  .ui-record-row {
    padding: 14px 12px;
  }
  .ui-record-row__layout {
    gap: 10px;
  }
  .ui-record-row__icon {
    width: 36px;
    height: 36px;
  }
  .ui-record-row__content strong {
    font-size: 14px;
  }
}
</style>
