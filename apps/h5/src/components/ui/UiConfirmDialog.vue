<script setup lang="ts">
import { Popup, type ButtonProps } from 'tdesign-mobile-vue';
import { computed, nextTick, ref, watch } from 'vue';
import UiButton from './UiButton.vue';
const props = defineProps<{
  visible: boolean;
  title: string;
  content: string;
  confirmBtn?: string | ButtonProps;
  cancelBtn?: string | ButtonProps;
}>();
const emit = defineEmits<{ confirm: []; cancel: [] }>();
const confirmation = computed(() =>
  typeof props.confirmBtn === 'string'
    ? { content: props.confirmBtn }
    : (props.confirmBtn ?? { content: '确定' }),
);
const cancellation = computed(() =>
  typeof props.cancelBtn === 'string'
    ? { content: props.cancelBtn }
    : (props.cancelBtn ?? { content: '取消' }),
);
const panel = ref<HTMLElement | null>(null);
watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      await nextTick();
      panel.value?.focus();
    }
  },
  { immediate: true },
);
function handleKey(event: KeyboardEvent) {
  if (event.key === 'Escape' && !confirmation.value.loading) emit('cancel');
  if (event.key !== 'Tab') return;
  const controls = panel.value?.querySelectorAll<HTMLButtonElement>('button:not(:disabled)');
  const first = controls?.[0],
    last = controls?.[controls.length - 1];
  if (!first || !last) {
    event.preventDefault();
    return;
  }
  if (
    event.shiftKey &&
    (document.activeElement === first || document.activeElement === panel.value)
  ) {
    event.preventDefault();
    last.focus();
  } else if (
    !event.shiftKey &&
    (document.activeElement === last || document.activeElement === panel.value)
  ) {
    event.preventDefault();
    first.focus();
  }
}
</script>
<template>
  <Popup :visible="visible" placement="center" :z-index="2500">
    <section
      ref="panel"
      class="ui-confirm-dialog"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      tabindex="-1"
      @keydown="handleKey"
    >
      <h2>{{ title }}</h2>
      <p>{{ content }}</p>
      <footer>
        <UiButton
          kind="secondary"
          :disabled="confirmation.loading || cancellation.disabled"
          @click="emit('cancel')"
          >{{ cancellation.content }}</UiButton
        >
        <UiButton
          :kind="confirmation.theme === 'danger' ? 'danger' : 'primary'"
          :loading="confirmation.loading"
          :disabled="confirmation.disabled"
          @click="emit('confirm')"
          >{{ confirmation.content }}</UiButton
        >
      </footer>
    </section>
  </Popup>
</template>
<style scoped lang="scss">
.ui-confirm-dialog {
  width: min(342px, calc(100vw - 48px));
  padding: 24px;
  box-sizing: border-box;
  border-radius: 20px;
  background: var(--color-surface);
  color: var(--ui-text-primary);
  outline: none;
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
    line-height: 1.4;
  }
  p {
    margin: 12px 0 24px;
    font-size: 14px;
    line-height: 1.7;
    color: var(--ui-text-secondary);
    overflow-wrap: anywhere;
    max-height: 50dvh;
    overflow-y: auto;
  }
  footer {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  :deep(.ui-button.t-button.ui-button--secondary) {
    background: var(--ui-background);
  }
  :deep(.ui-button.t-button.ui-button--danger) {
    height: 48px;
    padding: 0 12px;
    font-size: 15px;
    font-weight: 600;
    color: white;
    background: var(--color-danger);
  }
}
</style>
