<script setup lang="ts">
import { ref, watch } from 'vue';
import UiBottomSheet from '@/components/ui/UiBottomSheet.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import { createMealFavorite, type FoodItemInput } from '@/api/meals';
import { showRequestError } from '@/utils/request-error';
import { uiToast } from '@/components/ui/toast';
const props = defineProps<{ visible: boolean; foods: FoodItemInput[]; defaultName?: string }>();
const emit = defineEmits<{ 'update:visible': [visible: boolean]; saved: [] }>();
const name = ref('');
const saving = ref(false);
watch(
  () => props.visible,
  (visible) => {
    if (visible) name.value = props.defaultName ?? '';
  },
);
async function save() {
  if (saving.value) return;
  if (!name.value.trim() || name.value.trim().length > 60) {
    uiToast.warning('请填写 1 至 60 字的常用餐名称');
    return;
  }
  saving.value = true;
  try {
    await createMealFavorite(name.value.trim(), props.foods);
    emit('saved');
    emit('update:visible', false);
    uiToast.success('已收藏这餐');
  } catch (e) {
    showRequestError(e, '收藏失败，请重试');
  } finally {
    saving.value = false;
  }
}
</script>
<template>
  <UiBottomSheet
    :visible="visible"
    title="收藏这餐"
    subtitle="保存食物搭配，下次带入后可调整份量"
    @update:visible="!saving && emit('update:visible', $event)"
  >
    <label for="favorite-name">常用餐名称</label>
    <UiInput
      id="favorite-name"
      v-model="name"
      maxlength="60"
      placeholder="例如：工作日早餐"
      :disabled="saving"
    />
    <p class="favorite-foods">{{ foods.map((food) => food.name).join('、') }}</p>
    <template #footer><UiButton block :loading="saving" @click="save">收藏</UiButton></template>
  </UiBottomSheet>
</template>
<style scoped lang="scss">
label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
}
.favorite-foods {
  color: var(--ui-text-secondary);
  font-size: 12px;
  overflow-wrap: anywhere;
}
</style>
