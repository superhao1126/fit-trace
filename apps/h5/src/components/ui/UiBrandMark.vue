<script setup lang="ts">
withDefaults(defineProps<{ size?: number; animated?: boolean }>(), { size: 40, animated: true });
</script>
<template>
  <svg
    class="ui-brand-mark"
    :class="{ 'ui-brand-mark--animated': animated }"
    :width="size"
    :height="size"
    viewBox="0 0 64 64"
    fill="none"
    aria-hidden="true"
    focusable="false"
  >
    <path
      d="M13 56V22C13 12 21 7 30 7H44M13 29H45"
      stroke="currentColor"
      stroke-width="10"
      stroke-linecap="round"
    />
    <circle class="ui-brand-mark__dot" cx="58" cy="7" r="5" fill="currentColor" />
  </svg>
</template>
<style scoped lang="scss">
.ui-brand-mark {
  display: block;
  flex: 0 0 auto;
  overflow: visible;
}
.ui-brand-mark--animated .ui-brand-mark__dot {
  animation: brand-dot 1.4s ease-in-out infinite;
}
@keyframes brand-dot {
  0%,
  100% {
    transform: translateY(0);
    opacity: 1;
  }
  50% {
    transform: translateY(8px);
    opacity: 0.45;
  }
}
@media (prefers-reduced-motion: reduce) {
  .ui-brand-mark--animated .ui-brand-mark__dot {
    animation: none;
  }
}
</style>
