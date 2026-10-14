<script setup lang="ts">
import { onMounted, ref } from 'vue';
import type { MealFavorite } from '@fit-trace/shared';
import { deleteMealFavorite, getMealFavorites } from '@/api/meals';
import UiButton from '@/components/ui/UiButton.vue';
import { confirmDialog } from '@/components/ui/confirm';
import { showRequestError } from '@/utils/request-error';
import { authSession } from '@/api/session';
const emit = defineEmits<{ select: [meal: MealFavorite] }>();
const favorites = ref<MealFavorite[]>([]);
const loading = ref(true);
const error = ref(false);
let requestId = 0;
async function load() {
  const request = ++requestId;
  const generation = authSession.generation;
  loading.value = true;
  error.value = false;
  try {
    const result = await getMealFavorites();
    if (request === requestId && generation === authSession.generation) favorites.value = result;
  } catch {
    if (request === requestId) error.value = true;
  } finally {
    if (request === requestId) loading.value = false;
  }
}
function remove(meal: MealFavorite) {
  let deleting = false;
  const dialog = confirmDialog({
    title: '移除这份常用餐？',
    content: `${meal.name}将从常用餐中移除，已有饮食记录会保留。`,
    confirmBtn: '移除',
    onConfirm: async () => {
      if (deleting) return;
      deleting = true;
      dialog.update({ confirmBtn: { content: '移除中…', loading: true } });
      try {
        await deleteMealFavorite(meal.id);
        favorites.value = favorites.value.filter((f) => f.id !== meal.id);
        dialog.destroy();
      } catch (e) {
        deleting = false;
        dialog.update({ confirmBtn: '移除' });
        showRequestError(e, '移除失败，请重试');
      }
    },
  });
}
onMounted(load);
defineExpose({ load });
</script>
<template>
  <section class="meal-favorites">
    <p v-if="loading">正在读取常用餐…</p>
    <div v-else-if="error">常用餐加载失败<UiButton kind="text" @click="load">重试</UiButton></div>
    <p v-else-if="!favorites.length">还没有常用餐。填写食物后点击“收藏这餐”，下次可以直接带入。</p>
    <article v-for="meal in favorites" v-else :key="meal.id">
      <strong>{{ meal.name }}</strong>
      <p>{{ meal.foods.map((food) => food.name).join('、') }}</p>
      <div>
        <UiButton kind="text" @click="emit('select', meal)">带入这餐</UiButton
        ><UiButton kind="text" @click="remove(meal)">移除</UiButton>
      </div>
    </article>
  </section>
</template>
<style scoped lang="scss">
.meal-favorites {
  display: grid;
  gap: 12px;
  min-width: 0;
  p {
    color: var(--ui-text-secondary);
    font-size: 12px;
    line-height: 1.6;
    overflow-wrap: anywhere;
  }
  article {
    background: var(--ui-background);
    padding: 12px;
    border-radius: 12px;
    min-width: 0;
  }
  strong {
    font-size: 15px;
    overflow-wrap: anywhere;
  }
  article > div {
    display: flex;
    justify-content: space-between;
  }
}
</style>
