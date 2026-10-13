<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { uiToast as ToastPlugin } from '@/components/ui/toast';
import UiBottomSheet from '@/components/ui/UiBottomSheet.vue';
import UiNumberInput from '@/components/ui/UiNumberInput.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import {
  foodLibrary,
  foodCategories,
  searchFoods,
  foodCalories,
  type LibraryFood,
} from '@/data/food-library';
import { authSession } from '@/api/session';
defineProps<{ visible: boolean; mealLabel: string }>();
const emit = defineEmits<{
  'update:visible': [value: boolean];
  select: [food: { name: string; amount: string; calories: number }];
}>();
const query = ref('');
const category = ref('全部');
const selected = ref<LibraryFood | null>(null);
const grams = ref<string | number>(100);
const storageKey = `fit-trace:food-favorites:${authSession.snapshot?.user?.id ?? 'guest'}`;
function readFavorites(): string[] {
  try {
    const value: unknown = JSON.parse(localStorage.getItem(storageKey) ?? '[]');
    return Array.isArray(value) ? value.filter((v): v is string => typeof v === 'string') : [];
  } catch {
    return [];
  }
}
const favorites = ref(readFavorites());
const categories = ['全部', '常用', ...foodCategories];
const foods = computed(() => searchFoods(query.value, category.value, favorites.value));
const foodList = ref<HTMLElement | null>(null);
const calories = computed(() => {
  if (!selected.value) return null;
  try {
    return foodCalories(selected.value, Number(grams.value));
  } catch {
    return null;
  }
});
watch(
  () => category.value,
  () => {
    selected.value = null;
    foodList.value?.scrollTo({ top: 0 });
  },
);
function toggleFavorite(food: LibraryFood) {
  const next = favorites.value.includes(food.id)
    ? favorites.value.filter((id) => id !== food.id)
    : [...favorites.value, food.id];
  try {
    localStorage.setItem(storageKey, JSON.stringify(next));
    favorites.value = next;
  } catch {
    ToastPlugin.warning('常用食物保存失败，请检查存储空间');
  }
}
function choose(food: LibraryFood) {
  selected.value = food;
  grams.value = 100;
}
function confirm() {
  if (!selected.value || calories.value === null) return;
  emit('select', {
    name: selected.value.name,
    amount: `${Number(grams.value)} g`,
    calories: calories.value,
  });
  selected.value = null;
  emit('update:visible', false);
}
</script>
<template>
  <UiBottomSheet
    :visible="visible"
    :title="selected ? '选择份量' : '食品库'"
    :subtitle="mealLabel"
    @update:visible="emit('update:visible', $event)"
  >
    <template v-if="selected" #leading
      ><UiButton kind="text" @click="selected = null">返回</UiButton></template
    >
    <section class="food-library ui-page" aria-label="食品库">
      <template v-if="selected">
        <div class="library-card">
          <div class="selected-food">
            <img :src="selected.imageUrl" alt="" width="80" height="80" />
            <div>
              <h3>{{ selected.name }}</h3>
              <p>{{ selected.kcalPer100g }} kcal / 100 g</p>
            </div>
          </div>
          <small>{{ selected.source }} · {{ selected.ndbNumber }}</small
          ><a :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer">查看数据来源</a>
        </div>
        <label class="library-field"
          >份量（克）<UiNumberInput
            v-model="grams"
            unit="g"
            type="number"
            inputmode="decimal"
            :min="0.1"
            :max="10000"
            :step="0.1"
            aria-label="食品份量（克）"
        /></label>
        <UiTabs
          :model-value="Number(grams)"
          :options="[50, 100, 150, 200].map((value) => ({ value, label: `${value} g` }))"
          label="常用份量"
          @update:model-value="grams = $event"
        />
        <div class="library-card">
          <h3>本次热量 {{ calories ?? '—' }} kcal</h3>
          <p v-if="calories !== null" class="nutrients">
            蛋白质 {{ ((selected.proteinPer100g * Number(grams)) / 100).toFixed(1) }} g · 脂肪
            {{ ((selected.fatPer100g * Number(grams)) / 100).toFixed(1) }} g · 碳水
            {{ ((selected.carbsPer100g * Number(grams)) / 100).toFixed(1) }} g
          </p>
          <p>按实际可食份量换算，加入后仍可修改。</p>
        </div>
        <p v-if="calories === null" class="library-hint">份量需大于 0 且不超过 10000 克</p>
        <UiButton block :disabled="calories === null" @click="confirm"
          >加入{{ mealLabel }}</UiButton
        >
      </template>
      <template v-else>
        <UiInput v-model="query" placeholder="搜索食物名称" aria-label="搜索食品库" />
        <div class="library-browser">
          <nav class="library-categories" aria-label="食品分类">
            <button
              v-for="value in categories"
              :key="value"
              type="button"
              :aria-pressed="category === value"
              :class="{ active: category === value }"
              @click="category = value"
            >
              {{ value }}
            </button>
          </nav>
          <div ref="foodList" class="library-list">
            <p class="list-summary">{{ category }} · {{ foods.length }} 种</p>
            <article v-for="food in foods" :key="food.id" class="library-row">
              <button type="button" class="food-select" @click="choose(food)">
                <img
                  :src="food.imageUrl"
                  alt=""
                  width="52"
                  height="52"
                  loading="lazy"
                  decoding="async"
                />
                <span class="food-info"
                  ><strong>{{ food.name }}</strong
                  ><span>{{ food.kcalPer100g }} kcal / 100 g</span></span
                >
              </button>
              <button
                type="button"
                class="favorite-button"
                :class="{ active: favorites.includes(food.id) }"
                :aria-pressed="favorites.includes(food.id)"
                :aria-label="`${favorites.includes(food.id) ? '移除常用' : '设为常用'}${food.name}`"
                @click="toggleFavorite(food)"
              >
                <UiIcon name="star" />
              </button>
            </article>
            <p v-if="!foods.length" class="library-hint">
              {{
                category === '常用'
                  ? '暂无常用食物，可点击食物右侧星标添加'
                  : '未找到食物，可以返回手动填写'
              }}
            </p>
          </div>
        </div>
        <p class="library-hint">
          基础食品库 · {{ foodLibrary.length }} 种 /
          {{ foodCategories.length }} 类<br />数值为参考值，注意生熟状态与用油；图片为食品示意。
        </p>
      </template>
    </section>
  </UiBottomSheet>
</template>
<style scoped lang="scss">
.food-library {
  padding: 0;
  background: transparent;
}
.library-browser {
  display: grid;
  grid-template-columns: 76px minmax(0, 1fr);
  gap: 10px;
  height: min(49dvh, 520px);
  min-height: 200px;
  margin-top: 16px;
}
.library-categories,
.library-list {
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
}
.library-categories {
  border-radius: 12px;
  background: var(--ui-border);
  button {
    display: block;
    width: 100%;
    min-height: 46px;
    padding: 10px 4px;
    border: 0;
    background: transparent;
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
  .active {
    color: var(--ui-primary);
    background: var(--ui-meal-background);
    font-weight: 600;
    border-radius: 8px;
  }
}
.list-summary {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--ui-text-secondary);
}
.library-card {
  background: var(--color-surface);
  border-radius: 16px;
}
.selected-food {
  display: flex;
  align-items: center;
  gap: 16px;
  img {
    border-radius: 12px;
    object-fit: cover;
  }
  div {
    min-width: 0;
  }
}
.library-card {
  background: var(--color-surface);
  border-radius: 16px;
}
.library-card {
  padding: 16px;
  margin-bottom: 20px;
  h3 {
    margin: 0;
    font-size: 17px;
  }
  p {
    font-size: 14px;
  }
  small,
  a {
    display: block;
    margin-top: 8px;
    font-size: 12px;
    color: var(--ui-text-secondary);
  }
  a {
    color: var(--ui-primary);
  }
}
.library-row {
  display: flex;
  align-items: center;
  background: var(--color-surface);
  border-radius: 12px;
  margin-bottom: 4px;
  padding: 8px;
  gap: 4px;
}
.food-select {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: 1;
  min-width: 0;
  padding: 0;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--ui-text-primary);
  cursor: pointer;
  img {
    flex-shrink: 0;
    border-radius: 8px;
    object-fit: cover;
  }
}
.food-info {
  min-width: 0;
  strong,
  span {
    display: block;
  }
  strong {
    font-size: 13px;
    font-weight: 500;
    line-height: 1.5;
  }
  span {
    font-size: 11px;
    color: var(--ui-text-secondary);
    margin-top: 4px;
  }
}
.favorite-button {
  display: grid;
  place-items: center;
  width: 32px;
  min-height: 44px;
  padding: 0;
  flex-shrink: 0;
  border: 0;
  background: transparent;
  color: var(--ui-text-secondary);
  cursor: pointer;
  &.active {
    color: var(--ui-primary);
    background: var(--ui-meal-background);
    border-radius: 8px;
  }
}
.library-field {
  display: block;
  font-size: 14px;
  margin-bottom: 16px;
  :deep(.ui-input) {
    margin-top: 8px;
  }
}
.library-hint {
  font-size: 12px;
  color: var(--ui-text-secondary);
  line-height: 1.7;
  padding: 12px 0;
}
@media (max-width: 360px) {
  .food-library {
    padding-left: 16px;
    padding-right: 16px;
  }
}
</style>
