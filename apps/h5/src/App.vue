<script setup lang="ts">
import { onMounted, ref } from 'vue';
import UiLoading from '@/components/ui/UiLoading.vue';
import router from './router';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const ready = ref(false);
void router.isReady().finally(() => {
  ready.value = true;
});

onMounted(async () => {
  await router.isReady();
  if (auth.token) {
    void auth.fetchCurrentUser().catch(() => undefined);
  }
});
</script>

<template>
  <RouterView v-if="ready" />
  <UiLoading v-else class="page-loading page-loading--active" text="正在恢复登录" />
</template>
