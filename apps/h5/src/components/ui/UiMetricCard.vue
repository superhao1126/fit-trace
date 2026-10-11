<script setup lang="ts">
import type { WorkoutType } from '@fit-trace/shared';
import SportIcon from '@/components/SportIcon.vue';
import UiIcon from './UiIcon.vue';
withDefaults(
  defineProps<{
    kind: 'body' | 'meal' | 'workout';
    label: string;
    value: string | number;
    unit: string;
    hint?: string;
    workoutType?: WorkoutType;
  }>(),
  { hint: undefined, workoutType: undefined },
);
</script>
<template>
  <section class="ui-metric-card" :class="`ui-metric-card--${kind}`">
    <header>
      <span class="icon"
        ><SportIcon v-if="workoutType" :type="workoutType" /><UiIcon v-else :name="kind" /></span
      ><span>{{ label }}</span>
    </header>
    <div class="value">
      {{ value }}<small>{{ unit }}</small>
    </div>
    <p v-if="hint">{{ hint }}</p>
    <slot />
  </section>
</template>
<style scoped lang="scss">
.ui-metric-card {
  padding: 20px;
  border-radius: 20px;
  background: var(--color-surface);
  header {
    display: flex;
    align-items: center;
    gap: 12px;
    color: var(--ui-text-secondary);
    font-size: 14px;
  }
  .icon {
    display: grid;
    place-items: center;
    width: 40px;
    height: 40px;
    border-radius: 13px;
    background: var(--ui-body-background);
    color: var(--ui-body-foreground);
  }
  &--meal .icon {
    background: var(--ui-meal-background);
    color: var(--ui-meal-foreground);
  }
  &--workout .icon {
    background: var(--ui-workout-background);
    color: var(--ui-workout-foreground);
  }
  .value {
    display: flex;
    align-items: baseline;
    gap: 14px;
    margin-top: 16px;
    font-size: 48px;
    line-height: 1.3;
    font-weight: 600;
    letter-spacing: -1px;
    font-variant-numeric: tabular-nums;
    small {
      color: var(--ui-text-secondary);
      font-size: 16px;
      font-weight: 400;
      letter-spacing: 0;
    }
  }
  p {
    margin: 18px 0 0;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
</style>
