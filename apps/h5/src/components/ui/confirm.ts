import { createApp, h, nextTick, reactive, ref } from 'vue';
import type { ButtonProps } from 'tdesign-mobile-vue';
import UiConfirmDialog from './UiConfirmDialog.vue';
type ConfirmOptions = {
  title: string;
  content: string;
  confirmBtn?: string | ButtonProps;
  cancelBtn?: string | ButtonProps;
  onConfirm?: () => void | Promise<void>;
};
export function confirmDialog(options: ConfirmOptions) {
  const opener = document.activeElement instanceof HTMLElement ? document.activeElement : null;
  const root = document.createElement('div');
  document.body.appendChild(root);
  const current = reactive({ ...options });
  const visible = ref(true);
  let destroyed = false;
  function destroy() {
    if (destroyed) return;
    destroyed = true;
    visible.value = false;
    void nextTick(() => {
      app.unmount();
      root.remove();
      opener?.focus();
    });
  }
  const app = createApp({
    render: () =>
      h(UiConfirmDialog, {
        ...current,
        visible: visible.value,
        onConfirm: () => {
          void current.onConfirm?.();
        },
        onCancel: destroy,
      }),
  });
  app.mount(root);
  return { destroy, update: (patch: Partial<ConfirmOptions>) => Object.assign(current, patch) };
}
