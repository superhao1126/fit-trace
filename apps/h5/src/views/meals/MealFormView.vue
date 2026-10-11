<script setup lang="ts">
import { recordDateTime } from '@/utils/record-date';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiTextarea from '@/components/ui/UiTextarea.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiDateTimePicker from '@/components/ui/UiDateTimePicker.vue';
import { showRequestError } from '@/utils/request-error';
import type { MealRecord, MealType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Loading, ToastPlugin } from 'tdesign-mobile-vue';
import { AddIcon, CheckIcon, DeleteIcon } from 'tdesign-icons-vue-next';
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { recognizeFood } from '@/api/ai';
import { createMeal, getMeal, getMeals, updateMeal, type MealInput } from '@/api/meals';
import { uploadImage } from '@/api/uploads';

interface EditableFood {
  key: number;
  name: string;
  amount: string;
  calories: string | number;
  aiGenerated: boolean;
}

interface FoodSuggestion extends EditableFood {
  selected: boolean;
}

const mealTypes: Array<{ value: MealType; label: string; time: string }> = [
  { value: 'BREAKFAST', label: '早餐', time: '06:00–10:00' },
  { value: 'LUNCH', label: '午餐', time: '11:00–14:00' },
  { value: 'DINNER', label: '晚餐', time: '17:00–21:00' },
  { value: 'SNACK', label: '加餐', time: '其他时间' },
];

const route = useRoute();
const router = useRouter();
const mealId = computed(() => (typeof route.params.id === 'string' ? route.params.id : null));
const copyFromId = computed(() =>
  typeof route.query.copyFrom === 'string' ? route.query.copyFrom : null,
);
const isEdit = computed(() => Boolean(mealId.value));
const loading = ref(Boolean(mealId.value || copyFromId.value));
const submitting = ref(false);
const datePickerVisible = ref(false);
const uploadingPhoto = ref(false);
const recognizing = ref(false);
const imageUrl = ref('');
const recognitionProvider = ref('');
const suggestions = ref<FoodSuggestion[]>([]);
const hintText = ref('');
const appliedHint = ref('');
const fileInput = ref<HTMLInputElement | null>(null);
const recentMeals = ref<MealRecord[]>([]);
const templateSource = ref('');
const entryMode = ref<'manual' | 'photo' | 'recent'>('manual');
const entryModes = [
  { value: 'manual', label: '手动填写' },
  { value: 'photo', label: '拍照识别' },
  { value: 'recent', label: '最近吃过' },
] as const;
const hour = dayjs().hour();
let foodKey = 1;
const formData = reactive({
  type: (hour < 10
    ? 'BREAKFAST'
    : hour < 15
      ? 'LUNCH'
      : hour < 21
        ? 'DINNER'
        : 'SNACK') as MealType,
  recordedAt: recordDateTime(route.query.date),
  note: '',
  foods: [
    { key: foodKey, name: '', amount: '', calories: '', aiGenerated: false },
  ] as EditableFood[],
});
const recordedAtDisplay = computed(() => dayjs(formData.recordedAt).format('YYYY-MM-DD HH:mm'));
const recentFoods = computed(() => {
  const result: Array<{ name: string; amount: string; calories: number | null }> = [];
  const names = new Set<string>();
  for (const meal of recentMeals.value) {
    for (const food of meal.foods) {
      const key = food.name.trim().toLocaleLowerCase();
      if (!key || names.has(key)) continue;
      names.add(key);
      result.push({ name: food.name, amount: food.amount ?? '', calories: food.calories });
      if (result.length >= 8) return result;
    }
  }
  return result;
});

function addFood(): void {
  foodKey += 1;
  formData.foods.push({ key: foodKey, name: '', amount: '', calories: '', aiGenerated: false });
}

function removeFood(key: number): void {
  if (formData.foods.length === 1) {
    ToastPlugin.warning('至少保留一种食物');
    return;
  }
  formData.foods = formData.foods.filter((food) => food.key !== key);
}

function applyMealTemplate(meal: MealRecord, announce = true): void {
  formData.note = meal.note ?? '';
  formData.foods = meal.foods.map((food) => ({
    key: ++foodKey,
    name: food.name,
    amount: food.amount ?? '',
    calories: food.calories ?? '',
    aiGenerated: false,
  }));
  imageUrl.value = '';
  suggestions.value = [];
  templateSource.value = `${dayjs(meal.recordedAt).format('M月D日')}的${mealTypes.find((item) => item.value === meal.type)?.label ?? '记录'}`;
  entryMode.value = 'manual';
  if (announce) ToastPlugin.success('已带入食物，请确认后保存');
}

function addRecentFood(food: { name: string; amount: string; calories: number | null }): void {
  entryMode.value = 'manual';
  const next = {
    key: ++foodKey,
    name: food.name,
    amount: food.amount,
    calories: food.calories ?? '',
    aiGenerated: false,
  };
  const emptyIndex = formData.foods.findIndex((item) => !item.name.trim());
  if (emptyIndex >= 0) formData.foods.splice(emptyIndex, 1, next);
  else formData.foods.push(next);
}

function confirmRecordedAt(value: string | number): void {
  formData.recordedAt = dayjs(value).format('YYYY-MM-DD HH:mm:ss');
  datePickerVisible.value = false;
}

function pickPhoto(): void {
  if (uploadingPhoto.value) return;
  fileInput.value?.click();
}

async function onPhotoChange(event: Event): Promise<void> {
  const target = event.target as HTMLInputElement;
  const file = target.files?.[0];
  target.value = '';
  if (!file) return;

  uploadingPhoto.value = true;
  try {
    const uploaded = await uploadImage(file);
    imageUrl.value = uploaded.url;
    await recognizePhoto();
  } catch (error) {
    showRequestError(error, '照片上传失败，请稍后重试');
  } finally {
    uploadingPhoto.value = false;
  }
}

async function recognizePhoto(hint?: string): Promise<void> {
  if (!imageUrl.value || recognizing.value) return;
  const correction = hint?.trim() ?? '';
  recognizing.value = true;
  try {
    const result = await recognizeFood(imageUrl.value, correction || undefined);
    recognitionProvider.value = result.provider;
    appliedHint.value = correction;
    suggestions.value = result.foods.map((food) => ({
      key: ++foodKey,
      name: food.name,
      amount: food.estimatedAmount ?? '',
      calories: food.estimatedCalories ?? '',
      aiGenerated: true,
      selected: true,
    }));
    if (suggestions.value.length === 0) {
      ToastPlugin.warning('没有识别到食物，请手动添加');
    }
  } catch (error) {
    showRequestError(error, '识别失败，请手动填写');
  } finally {
    recognizing.value = false;
  }
}

function applyHint(): void {
  const correction = hintText.value.trim();
  if (!correction) {
    ToastPlugin.warning('请先说明哪里识别错了');
    return;
  }
  void recognizePhoto(correction);
}

function removePhoto(): void {
  imageUrl.value = '';
  recognitionProvider.value = '';
  suggestions.value = [];
  hintText.value = '';
  appliedHint.value = '';
}

function toggleSuggestion(key: number): void {
  suggestions.value = suggestions.value.map((item) =>
    item.key === key ? { ...item, selected: !item.selected } : item,
  );
}

function applySuggestions(): void {
  const picked = suggestions.value.filter((item) => item.selected);
  if (picked.length === 0) {
    ToastPlugin.warning('请先勾选要添加的食物');
    return;
  }
  if (
    picked.some(
      (item) =>
        !item.name.trim() ||
        (item.calories !== '' &&
          (!Number.isFinite(Number(item.calories)) || Number(item.calories) < 0)),
    )
  ) {
    ToastPlugin.warning('请核对食物名称和热量，热量需为非负数字');
    return;
  }
  const isEmptyRow = formData.foods.every((food) => !food.name.trim());
  const nextFoods = isEmptyRow ? [] : [...formData.foods];
  for (const item of picked) {
    foodKey += 1;
    nextFoods.push({
      key: foodKey,
      name: item.name,
      amount: item.amount,
      calories: item.calories,
      aiGenerated: true,
    });
  }
  formData.foods = nextFoods;
  suggestions.value = [];
  entryMode.value = 'manual';
  ToastPlugin.success(`已添加 ${picked.length} 种食物，可继续修改`);
}

function buildInput(): MealInput | null {
  const foods = formData.foods
    .map((food) => ({
      name: food.name.trim(),
      amount: food.amount.trim() || undefined,
      calories:
        food.calories === '' || !Number.isFinite(Number(food.calories))
          ? undefined
          : Number(food.calories),
      aiGenerated: food.aiGenerated,
    }))
    .filter((food) => food.name);
  if (foods.length === 0) {
    ToastPlugin.warning('请至少填写一种食物');
    return null;
  }
  if (foods.length !== formData.foods.length) {
    ToastPlugin.warning('请填写所有食物名称，或删除空白项');
    return null;
  }
  return {
    type: formData.type,
    recordedAt: dayjs(formData.recordedAt).toISOString(),
    note: formData.note.trim() || undefined,
    imageUrl: imageUrl.value || undefined,
    foods,
  };
}

async function submit(): Promise<void> {
  if (submitting.value) return;
  const input = buildInput();
  if (!input) return;
  submitting.value = true;
  try {
    if (mealId.value) {
      await updateMeal(mealId.value, input);
      ToastPlugin.success('饮食记录已更新');
      await router.replace(`/meals/${mealId.value}`);
    } else {
      const created = await createMeal(input);
      ToastPlugin.success('饮食记录已保存');
      await router.replace(
        route.query.returnTo === '/dashboard'
          ? { path: '/dashboard', query: { date: dayjs(formData.recordedAt).format('YYYY-MM-DD') } }
          : `/meals/${created.id}`,
      );
    }
  } catch (error) {
    showRequestError(error, '保存失败，请稍后重试');
  } finally {
    submitting.value = false;
  }
}

onMounted(async () => {
  if (isEdit.value) {
    void getMeals({ page: 1, pageSize: 12 })
      .then((result) => {
        recentMeals.value = result.data;
      })
      .catch(() => undefined);
  }
  try {
    if (mealId.value) {
      const meal = await getMeal(mealId.value);
      formData.type = meal.type;
      formData.recordedAt = dayjs(meal.recordedAt).format('YYYY-MM-DD HH:mm:ss');
      formData.note = meal.note ?? '';
      imageUrl.value = meal.imageUrl ?? '';
      if (imageUrl.value) entryMode.value = 'photo';
      formData.foods = meal.foods.map((food) => ({
        key: ++foodKey,
        name: food.name,
        amount: food.amount ?? '',
        calories: food.calories ?? '',
        aiGenerated: food.aiGenerated,
      }));
      return;
    }

    const result = await getMeals({ page: 1, pageSize: 12 });
    recentMeals.value = result.data;
    if (copyFromId.value) {
      const source =
        result.data.find((meal) => meal.id === copyFromId.value) ??
        (await getMeal(copyFromId.value));
      applyMealTemplate(source, false);
    }
  } catch {
    if (mealId.value || copyFromId.value) await router.replace('/meals');
  } finally {
    loading.value = false;
  }
});
</script>

<template>
  <main class="view-page ui-page ui-form meal-form-page">
    <UiDetailHeader
      :title="isEdit ? '编辑饮食记录' : '记饮食'"
      subtitle="记下吃了什么，份量和热量选填"
    />

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取饮食记录"
    >
      <section class="meal-form-card">
        <UiTabs
          :model-value="formData.type"
          :options="mealTypes"
          tone="meal"
          label="餐次"
          @update:model-value="formData.type = $event as MealType"
        />
        <div class="form-meta">
          <span class="field-label">记录时间</span
          ><button type="button" @click="datePickerVisible = true">
            {{ recordedAtDisplay }}<UiIcon name="calendar" />
          </button>
        </div>
        <UiTabs
          :model-value="entryMode"
          :options="entryModes"
          label="添加食物方式"
          :disabled="uploadingPhoto || recognizing"
          @update:model-value="entryMode = $event as 'manual' | 'photo' | 'recent'"
        />
        <p v-if="templateSource" class="template-hint">
          已带入 {{ templateSource }}，确认下方内容后保存
        </p>
        <div v-if="entryMode === 'recent'" class="quick-start field-block">
          <span class="field-label">选择一餐，带入食物</span>
          <p v-if="!recentMeals.length" class="template-hint">
            还没有可复用的饮食记录，先手动记下第一餐
          </p>
          <UiRecordRow
            v-for="meal in recentMeals.slice(0, 5)"
            :key="meal.id"
            kind="meal"
            :title="mealTypes.find((item) => item.value === meal.type)?.label || '饮食'"
            :subtitle="`${dayjs(meal.recordedAt).format('M月D日')} · ${meal.foods.map((food) => food.name).join('、')}`"
            value="带入"
            @click="applyMealTemplate(meal)"
          />
          <div v-if="recentFoods.length" class="recent-foods">
            <span>也可以只加一种食物</span>
            <div>
              <button
                v-for="food in recentFoods"
                :key="food.name"
                type="button"
                @click="addRecentFood(food)"
              >
                + {{ food.name }}
              </button>
            </div>
          </div>
        </div>

        <div v-show="entryMode === 'photo'" class="field-block">
          <span class="field-label">餐食照片（可选）</span>

          <div v-if="imageUrl" class="photo-preview">
            <img :src="imageUrl" alt="餐食照片" />
            <div class="photo-preview__actions">
              <UiButton kind="secondary" :loading="recognizing" @click="recognizePhoto()">
                重新识别
              </UiButton>
              <UiButton kind="danger" @click="removePhoto"> <DeleteIcon /> 移除 </UiButton>
            </div>
          </div>

          <button v-else type="button" class="photo-picker" @click="pickPhoto">
            <UiIcon name="photo" />
            <strong>{{ uploadingPhoto ? '正在上传…' : '拍照或选择餐食照片' }}</strong>
            <span>上传后先由 AI 识别，再由你确认</span>
          </button>

          <div v-if="recognizing && !suggestions.length" class="recognizing-hint">
            正在识别这张照片…
          </div>

          <div v-if="suggestions.length" class="suggestion-panel">
            <header>
              <div>
                <strong>识别结果</strong>
                <span>{{
                  recognitionProvider === 'mock'
                    ? '当前为模拟识别，不会分析照片，请手动调整'
                    : '请核对名称、份量和热量后加入明细'
                }}</span>
              </div>
              <UiButton kind="secondary" @click="applySuggestions">确认并加入</UiButton>
            </header>
            <div class="suggestion-list">
              <div
                v-for="(item, index) in suggestions"
                :key="item.key"
                class="suggestion-item"
                :class="{ selected: item.selected }"
              >
                <button
                  type="button"
                  class="suggestion-item__check"
                  :aria-pressed="item.selected"
                  :aria-label="item.selected ? `取消选择${item.name}` : `选择${item.name}`"
                  @click="toggleSuggestion(item.key)"
                >
                  <CheckIcon v-if="item.selected" />
                </button>
                <span class="suggestion-item__main">
                  <UiInput
                    v-model="item.name"
                    class="suggestion-item__name"
                    :maxlength="100"
                    placeholder="食物名称"
                  />
                  <input
                    v-model="item.amount"
                    :aria-label="`识别食物 ${index + 1} 份量`"
                    maxlength="50"
                    placeholder="份量选填"
                  />
                </span>
                <span class="suggestion-item__calories">
                  <input
                    v-model="item.calories"
                    :aria-label="`识别食物 ${index + 1} 热量`"
                    type="number"
                    min="0"
                    placeholder="选填"
                  /><small>kcal</small>
                </span>
              </div>
            </div>
            <p class="suggestion-tip">AI 热量为估算值；改动食物名称后，请同时核对份量和热量。</p>
          </div>

          <details v-if="imageUrl" class="correct-block">
            <summary>用文字重新识别</summary>
            <div class="correct-block__row">
              <UiInput
                v-model="hintText"
                :maxlength="200"
                placeholder="识别不对？直接告诉它，比如：左边那碗是鸡蛋羹"
              />
              <UiButton kind="secondary" :loading="recognizing" @click="applyHint"> 纠正 </UiButton>
            </div>
            <p v-if="appliedHint" class="correct-block__applied">
              已按你的说明重新识别：{{ appliedHint }}
            </p>
          </details>
        </div>

        <input
          ref="fileInput"
          class="file-input"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          capture="environment"
          @change="onPhotoChange"
        />

        <div class="foods-heading">
          <span class="field-label"
            >食物明细 <small>{{ formData.foods.length }} 项</small></span
          >
          <span class="foods-heading__hint">份量、热量选填</span>
        </div>

        <div class="food-list">
          <section v-for="(food, index) in formData.foods" :key="food.key" class="food-item">
            <div class="food-item__main">
              <input
                v-model="food.name"
                class="food-item__name"
                :aria-label="`食物 ${index + 1} 名称`"
                maxlength="100"
                placeholder="食物名称，如鸡胸肉"
              />
              <span v-if="food.aiGenerated" class="food-item__ai">AI</span>
              <button
                type="button"
                class="food-item__remove"
                :aria-label="`删除食物 ${index + 1}`"
                :disabled="formData.foods.length === 1"
                @click="removeFood(food.key)"
              >
                移除
              </button>
            </div>
            <details class="food-item__extras" :open="true">
              <summary>
                份量与热量
                <span>{{
                  [food.amount, food.calories !== '' ? `${food.calories} kcal` : '']
                    .filter(Boolean)
                    .join(' · ') || '选填'
                }}</span
                ><UiIcon name="right" class="expand-icon" />
              </summary>
              <div class="food-item__details">
                <label>
                  <span>份量</span>
                  <input
                    v-model="food.amount"
                    :aria-label="`食物 ${index + 1} 份量`"
                    maxlength="50"
                    placeholder="如 150g"
                  />
                </label>
                <label>
                  <span>热量</span>
                  <input
                    v-model="food.calories"
                    :aria-label="`食物 ${index + 1} 热量`"
                    type="number"
                    inputmode="decimal"
                    placeholder="选填"
                  />
                  <small>kcal</small>
                </label>
              </div>
            </details>
          </section>
          <button type="button" class="add-food" @click="addFood"><AddIcon /> 添加食物</button>
        </div>

        <details class="note-details" :open="isEdit && !!formData.note">
          <summary>添加备注（选填）<UiIcon name="right" class="expand-icon" /></summary>
          <div class="field-block note-field">
            <span class="field-label">备注</span>
            <UiTextarea
              v-model="formData.note"
              :maxlength="500"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="例如：自制、少油、外食等"
            />
          </div>
        </details>
        <UiButton
          block
          :loading="submitting"
          :disabled="uploadingPhoto || recognizing"
          @click="submit"
        >
          {{ isEdit ? '保存修改' : '保存饮食记录' }}
        </UiButton>
      </section>
    </Loading>

    <UiDateTimePicker
      v-model:visible="datePickerVisible"
      :value="formData.recordedAt"
      @confirm="confirmRecordedAt"
    />
  </main>
</template>

<style scoped lang="scss">
.meal-form-card {
  padding: 0;
  background: transparent;
}
.template-hint,
.suggestion-tip,
.correct-block__applied {
  color: var(--ui-text-secondary);
  font-size: 12px;
  line-height: 1.7;
}
.quick-start {
  display: grid;
  gap: 12px;
}
.recent-foods {
  display: grid;
  gap: 12px;
  font-size: 13px;
  color: var(--ui-text-secondary);
  > div {
    display: flex;
    gap: 8px;
    overflow-x: auto;
  }
  button {
    flex: none;
    padding: 10px 14px;
    border: 0;
    border-radius: 12px;
    background: var(--color-surface);
    color: var(--ui-meal-foreground);
  }
}
.file-input {
  display: none;
}
.photo-picker {
  display: grid;
  place-items: center;
  gap: 20px;
  min-height: 230px;
  width: 100%;
  padding: 24px 16px;
  border: 0;
  border-radius: 20px;
  background: var(--ui-meal-background);
  color: var(--ui-meal-foreground);
  .ui-icon {
    width: 36px;
    height: 36px;
  }
  strong {
    color: var(--ui-text-primary);
    font-size: 16px;
  }
  span {
    font-size: 12px;
    color: var(--ui-text-secondary);
  }
}
.photo-preview {
  display: grid;
  gap: 12px;
  img {
    width: 100%;
    max-height: 240px;
    object-fit: cover;
    border-radius: 20px;
  }
  &__actions {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
}
.recognizing-hint {
  padding: 16px;
  color: var(--ui-meal-foreground);
  font-size: 13px;
}
.suggestion-panel {
  margin-top: 12px;
  > header {
    display: grid;
    gap: 16px;
    padding: 20px;
    border-radius: 20px;
    background: var(--ui-meal-background);
    > div {
      display: grid;
      gap: 8px;
    }
    strong {
      font-size: 16px;
    }
    span {
      font-size: 12px;
      color: var(--ui-text-secondary);
      line-height: 1.7;
    }
  }
}
.suggestion-list {
  display: grid;
  gap: 12px;
  margin-top: 16px;
}
.suggestion-item {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) 70px;
  align-items: center;
  gap: 10px;
  padding: 16px;
  border: 1px solid transparent;
  border-radius: 20px;
  background: var(--color-surface);
  &__check {
    width: 24px;
    height: 24px;
    padding: 0;
    display: grid;
    place-items: center;
    border: 1px solid var(--ui-border);
    border-radius: 8px;
    background: transparent;
    color: var(--color-on-primary);
  }
  &.selected {
    border-color: var(--ui-primary);
    .suggestion-item__check {
      background: var(--ui-primary);
    }
  }
  &__main {
    display: grid;
    gap: 8px;
    min-width: 0;
  }
  &__calories {
    display: grid;
    gap: 8px;
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
  input {
    width: 100%;
    min-width: 0;
    border: 0;
    padding: 8px 0;
    background: transparent;
    color: var(--ui-text-primary);
    font-size: 14px;
  }
}
.correct-block {
  margin: 20px 0;
  summary {
    font-size: 14px;
    color: var(--ui-primary);
    min-height: 44px;
    cursor: pointer;
  }
  &__row {
    display: grid;
    gap: 12px;
    margin-top: 10px;
  }
}
.foods-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin: 28px 0 16px;
  .field-label {
    font-size: 17px;
    font-weight: 600;
    color: var(--ui-text-primary);
  }
  small,
  &__hint {
    font-size: 12px;
    font-weight: 400;
    color: var(--ui-text-secondary);
  }
}
.food-list {
  display: grid;
  gap: 12px;
}
.food-item {
  padding: 16px;
  border-radius: 20px;
  background: var(--color-surface);
  min-width: 0;
  input {
    width: 100%;
    min-width: 0;
    border: 0;
    background: transparent;
    color: var(--ui-text-primary);
    font: inherit;
    padding: 0;
    &::placeholder {
      color: var(--ui-text-tertiary);
    }
  }
  &__main {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 36px;
  }
  &__name {
    flex: 1;
    font-size: 16px;
    font-weight: 600;
  }
  &__ai {
    color: var(--ui-meal-foreground);
    font-size: 11px;
  }
  &__remove {
    flex: none;
    border: 0;
    background: transparent;
    color: var(--ui-text-secondary);
    font-size: 12px;
    min-height: 36px;
    padding: 0 4px;
    &:disabled {
      opacity: 0.3;
    }
  }
  &__extras {
    margin-top: 12px;
    summary {
      display: flex;
      align-items: center;
      gap: 8px;
      min-height: 36px;
      color: var(--ui-text-secondary);
      font-size: 12px;
      span {
        margin-left: auto;
      }
      .ui-icon {
        width: 14px;
        height: 14px;
      }
    }
  }
  &__details {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 16px;
    margin-top: 12px;
    label {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 8px;
      min-width: 0;
      color: var(--ui-text-secondary);
      font-size: 12px;
      > span {
        grid-column: 1/-1;
      }
      input {
        height: 32px;
        color: var(--ui-text-primary);
        font-size: 14px;
      }
      small {
        align-self: center;
      }
    }
  }
}
.add-food {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 44px;
  border: 0;
  background: transparent;
  color: var(--ui-primary);
  font-size: 14px;
  padding: 0;
}
.note-details {
  margin: 12px 0 28px;
  summary {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 44px;
    color: var(--ui-primary);
    font-size: 14px;
    cursor: pointer;
    .ui-icon {
      width: 16px;
      height: 16px;
    }
  }
  .field-block {
    margin-top: 12px;
  }
}
.meal-form-card > .ui-button:last-child {
  margin-top: 20px;
}
</style>
