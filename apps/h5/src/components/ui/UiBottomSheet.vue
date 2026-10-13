<script setup lang="ts">
import { Popup } from 'tdesign-mobile-vue';
import { nextTick, ref, watch } from 'vue';
import UiButton from './UiButton.vue';
import UiIcon from './UiIcon.vue';
const props = withDefaults(defineProps<{ visible: boolean; title: string; subtitle?: string }>(), {
  subtitle: '',
});
const emit = defineEmits<{ 'update:visible': [value: boolean] }>();
const panel = ref<HTMLElement | null>(null);
let opener: HTMLElement | null = null;
watch(
  () => props.visible,
  async (visible) => {
    if (visible) {
      opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      await nextTick();
      panel.value?.focus();
    } else {
      opener?.focus();
    }
  },
  { immediate: true },
);
function close() {
  emit('update:visible', false);
}
function handleKey(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    close();
    return;
  }
  if (event.key !== 'Tab') return;
  const controls = [
    ...(panel.value?.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), select:not(:disabled), a[href], [tabindex="0"]',
    ) ?? []),
  ].filter((el) => el.getClientRects().length);
  const first = controls[0],
    last = controls.at(-1);
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
  <Popup
    :visible="visible"
    placement="bottom"
    :z-index="1000"
    @update:visible="emit('update:visible', $event)"
  >
    <section
      ref="panel"
      class="ui-bottom-sheet"
      role="dialog"
      aria-modal="true"
      :aria-label="title"
      tabindex="-1"
      @keydown="handleKey"
    >
      <div class="ui-bottom-sheet__handle" />
      <header>
        <slot name="leading" />
        <div class="ui-bottom-sheet__heading">
          <h2>{{ title }}</h2>
          <p v-if="subtitle">{{ subtitle }}</p>
        </div>
        <UiButton kind="text" aria-label="关闭弹层" @click="close"
          ><UiIcon name="close"
        /></UiButton>
      </header>
      <div class="ui-bottom-sheet__body"><slot /></div>
      <footer v-if="$slots.footer"><slot name="footer" /></footer>
    </section>
  </Popup>
</template>
<style scoped lang="scss">
.ui-bottom-sheet {
  display: flex;
  flex-direction: column;
  width: min(100%, 560px);
  max-height: 85dvh;
  margin: auto;
  padding: 10px 24px calc(24px + var(--app-safe-area-bottom));
  box-sizing: border-box;
  border-radius: 28px 28px 0 0;
  background: var(--color-surface);
  color: var(--ui-text-primary);
  outline: none;
  header {
    display: flex;
    flex: 0 0 auto;
    align-items: center;
    gap: 12px;
    margin-bottom: 16px;
  }
  h2 {
    margin: 0;
    font-size: 20px;
    font-weight: 600;
  }
  p {
    margin: 4px 0 0;
    font-size: 12px;
    color: var(--ui-text-secondary);
  }
  footer {
    flex: 0 0 auto;
    padding-top: 16px;
  }
}
.ui-bottom-sheet__handle {
  width: 36px;
  height: 4px;
  margin: 0 auto 12px;
  flex: 0 0 auto;
  border-radius: 4px;
  background: var(--ui-border);
}
.ui-bottom-sheet__heading {
  flex: 1;
  min-width: 0;
}
.ui-bottom-sheet__body {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
</style>
