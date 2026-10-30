<script setup lang="ts">
import { ref } from 'vue';
import router from './router';
import { useAuthStore } from '@/stores/auth';
import UiToastHost from '@/components/ui/UiToastHost.vue';
import UiButton from '@/components/ui/UiButton.vue';

const auth = useAuthStore();
const ready = ref(false);
const startupError = ref(false);

async function start(): Promise<void> {
  try {
    await router.isReady();
    ready.value = true;
  } catch {
    startupError.value = true;
  }
  if (ready.value && auth.token) {
    void auth.fetchCurrentUser().catch(() => undefined);
  }
}
function retry(): void {
  window.location.reload();
}
void start();
</script>

<template>
  <UiToastHost />
  <RouterView v-if="ready" />
  <main v-else-if="startupError" class="startup-error" role="alert">
    <p>启动失败，请重新打开循形Fitness</p>
    <UiButton @click="retry">重新加载</UiButton>
  </main>
</template>
<style scoped lang="scss">
.startup-error {
  display: grid;
  place-content: center;
  gap: 16px;
  min-height: 100dvh;
  padding: 24px;
  text-align: center;
  color: var(--ui-text-secondary);
}
</style>
