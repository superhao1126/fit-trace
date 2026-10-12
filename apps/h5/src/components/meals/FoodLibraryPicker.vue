<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { Popup, ToastPlugin } from 'tdesign-mobile-vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import { foodLibrary, foodCalories, type LibraryFood } from '@/data/food-library';
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
const categories = ['全部', '常用', ...new Set(foodLibrary.map((f) => f.category))].map(
  (value) => ({ value, label: value }),
);
const foods = computed(() =>
  foodLibrary.filter(
    (food) =>
      (category.value === '全部' ||
        (category.value === '常用'
          ? favorites.value.includes(food.id)
          : food.category === category.value)) &&
      food.name.includes(query.value.trim()),
  ),
);
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
  <Popup :visible="visible" placement="bottom" @update:visible="emit('update:visible', $event)">
    <section class="food-library ui-page" aria-label="食品库">
      <header>
        <UiButton
          kind="text"
          @click="selected ? (selected = null) : emit('update:visible', false)"
          >{{ selected ? '返回' : '取消' }}</UiButton
        >
        <h2>{{ selected ? '选择份量' : '食品库' }}</h2>
        <span>{{ mealLabel }}</span>
      </header>
      <template v-if="selected">
        <div class="library-card">
          <h3>{{ selected.name }}</h3>
          <p>{{ selected.kcalPer100g }} kcal / 100 g</p>
          <small>{{ selected.source }}</small
          ><a :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer">查看数据来源</a>
        </div>
        <label class="library-field"
          >份量（克）<UiInput
            v-model="grams"
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
          <p>按实际可食份量换算，加入后仍可修改。</p>
        </div>
        <p v-if="calories === null" class="library-hint">份量需大于 0 且不超过 10000 克</p>
        <UiButton block :disabled="calories === null" @click="confirm"
          >加入{{ mealLabel }}</UiButton
        >
      </template>
      <template v-else>
        <UiInput v-model="query" placeholder="搜索食物名称" aria-label="搜索食品库" />
        <div class="category-scroll">
          <UiTabs v-model="category" :options="categories" label="食品分类" />
        </div>
        <div class="library-list">
          <article v-for="food in foods" :key="food.id" class="library-row">
            <button type="button" class="food-select" @click="choose(food)">
              <strong>{{ food.name }}</strong
              ><span>{{ food.kcalPer100g }} kcal / 100 g</span>
            </button>
            <UiButton
              kind="text"
              :aria-pressed="favorites.includes(food.id)"
              :aria-label="`${favorites.includes(food.id) ? '移除常用' : '设为常用'}${food.name}`"
              @click="toggleFavorite(food)"
              >{{ favorites.includes(food.id) ? '已常用' : '常用' }}</UiButton
            >
          </article>
          <p v-if="!foods.length" class="library-hint">
            {{
              category === '常用'
                ? '暂无常用食物，可在其他分类中添加'
                : '未找到食物，可以返回手动填写'
            }}
          </p>
        </div>
        <p class="library-hint">基础食品库 · 数值均为参考值；注意生熟状态与实际烹饪用油。</p>
      </template>
    </section>
  </Popup>
</template>
<style scoped lang="scss">
.food-library {
  max-height: 85dvh;
  overflow-y: auto;
  padding: 20px 24px calc(24px + var(--app-safe-area-bottom));
  border-radius: 20px 20px 0 0;
  background: var(--ui-background);
}
header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 20px;
  h2 {
    flex: 1;
    margin: 0;
    font-size: 20px;
  }
  span {
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
}
.category-scroll {
  overflow-x: auto;
  margin-top: 16px;
  :deep(.ui-tabs) {
    min-width: 480px;
    margin-bottom: 16px;
  }
}
.library-list,
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
  border-bottom: 1px solid var(--ui-border);
  padding: 4px 16px;
  gap: 8px;
  &:last-child {
    border-bottom: 0;
  }
}
.food-select {
  flex: 1;
  min-width: 0;
  padding: 14px 0;
  text-align: left;
  border: 0;
  background: transparent;
  color: var(--ui-text-primary);
  cursor: pointer;
  strong,
  span {
    display: block;
  }
  strong {
    font-size: 15px;
  }
  span {
    font-size: 12px;
    color: var(--ui-text-secondary);
    margin-top: 6px;
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
