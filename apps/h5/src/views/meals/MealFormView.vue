<script setup lang="ts">
import { recordDateTime } from '@/utils/record-date';
import RecordDetailHeader from '@/components/RecordDetailHeader.vue';
import { showRequestError } from '@/utils/request-error';
import type { MealRecord, MealType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import {
  Button,
  DateTimePicker,
  Input,
  Loading,
  Popup,
  Textarea,
  ToastPlugin,
} from 'tdesign-mobile-vue';
import {
  AddIcon,
  CalendarIcon,
  CameraIcon,
  CheckIcon,
  ChevronRightIcon,
  DeleteIcon,
} from 'tdesign-icons-vue-next';
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
  <main class="view-page record-form meal-form-page">
    <RecordDetailHeader
      :title="isEdit ? '编辑饮食记录' : '记饮食'"
      subtitle="记下吃了什么，份量和热量选填"
    />

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取饮食记录"
    >
      <section class="surface-card meal-form-card">
        <div class="field-block">
          <span class="field-label">餐次</span>
          <div class="meal-type-grid">
            <button
              v-for="item in mealTypes"
              :key="item.value"
              type="button"
              :class="{ active: formData.type === item.value }"
              @click="formData.type = item.value"
            >
              <strong>{{ item.label }}</strong>
            </button>
          </div>
        </div>

        <button class="form-meta" type="button" @click="datePickerVisible = true">
          <span>记录时间</span><span>{{ recordedAtDisplay }} <CalendarIcon /></span>
        </button>

        <div class="entry-modes" role="group" aria-label="添加食物方式">
          <button
            v-for="mode in entryModes"
            :key="mode.value"
            type="button"
            :aria-pressed="entryMode === mode.value"
            :disabled="uploadingPhoto || recognizing"
            :class="{ active: entryMode === mode.value }"
            @click="entryMode = mode.value"
          >
            {{ mode.label }}
          </button>
        </div>
        <p v-if="templateSource" class="template-hint">
          已带入 {{ templateSource }}，确认下方内容后保存
        </p>
        <div v-if="entryMode === 'recent'" class="quick-start field-block">
          <span class="field-label">选择一餐，带入食物</span>
          <p v-if="!recentMeals.length" class="template-hint">
            还没有可复用的饮食记录，先手动记下第一餐
          </p>
          <button
            v-for="meal in recentMeals.slice(0, 5)"
            :key="meal.id"
            type="button"
            class="last-meal"
            @click="applyMealTemplate(meal)"
          >
            <span
              ><strong
                >{{ dayjs(meal.recordedAt).format('M月D日') }} ·
                {{ mealTypes.find((item) => item.value === meal.type)?.label }}</strong
              ><small>{{ meal.foods.map((food) => food.name).join('、') }}</small></span
            ><span>带入</span>
          </button>
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
              <Button
                size="small"
                variant="outline"
                :loading="recognizing"
                @click="recognizePhoto()"
              >
                重新识别
              </Button>
              <Button size="small" variant="text" theme="danger" @click="removePhoto">
                <DeleteIcon /> 移除
              </Button>
            </div>
          </div>

          <button v-else type="button" class="photo-picker" @click="pickPhoto">
            <CameraIcon />
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
              <Button size="small" variant="outline" @click="applySuggestions">确认并加入</Button>
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
                  <Input
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
              <Input
                v-model="hintText"
                :maxlength="200"
                placeholder="识别不对？直接告诉它，比如：左边那碗是鸡蛋羹"
              />
              <Button size="small" variant="outline" :loading="recognizing" @click="applyHint">
                纠正
              </Button>
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
              <span class="food-item__index" aria-hidden="true">{{ index + 1 }}</span>
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
                <DeleteIcon />
              </button>
            </div>
            <details class="food-item__extras" :open="isEdit">
              <summary>
                份量与热量
                <span>{{
                  [food.amount, food.calories !== '' ? `${food.calories} kcal` : '']
                    .filter(Boolean)
                    .join(' · ') || '选填'
                }}</span
                ><ChevronRightIcon class="expand-icon" />
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
          <summary>添加备注（选填）<ChevronRightIcon class="expand-icon" /></summary>
          <div class="field-block note-field">
            <span class="field-label">备注</span>
            <Textarea
              v-model="formData.note"
              :maxlength="500"
              :autosize="{ minRows: 3, maxRows: 6 }"
              placeholder="例如：自制、少油、外食等"
            />
          </div>
        </details>
        <Button
          theme="primary"
          size="large"
          block
          :loading="submitting"
          :disabled="uploadingPhoto || recognizing"
          @click="submit"
        >
          {{ isEdit ? '保存修改' : '保存饮食记录' }}
        </Button>
      </section>
    </Loading>

    <Popup v-model="datePickerVisible" placement="bottom">
      <DateTimePicker
        :value="formData.recordedAt"
        title="选择记录时间"
        :mode="['date', 'minute']"
        format="YYYY-MM-DD HH:mm:ss"
        @confirm="confirmRecordedAt"
        @cancel="datePickerVisible = false"
      />
    </Popup>
  </main>
</template>

<style scoped lang="scss">
.entry-modes {
  display: flex;
  gap: 4px;
  padding: 4px;
  margin-bottom: 20px;
  border-radius: 10px;
  background: var(--color-surface-muted);
  button {
    flex: 1;
    min-height: 40px;
    padding: 8px 4px;
    border: 0;
    border-radius: 7px;
    color: var(--color-text-secondary);
    background: transparent;
    font-size: 0.8125rem;
  }
  button.active {
    color: var(--color-text-primary);
    background: var(--color-surface);
    box-shadow: 0 1px 3px rgb(0 0 0 / 6%);
  }
}
.template-hint {
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  line-height: 1.6;
}
.note-details {
  margin: 18px 0;
  summary {
    cursor: pointer;
    padding: 8px 0;
    color: var(--color-text-secondary);
    font-size: 0.8125rem;
  }
}

.meal-form-card {
  width: 100%;
  padding: 18px;
}

.field-block {
  display: grid;
  gap: 8px;
  margin-bottom: 18px;
}

.field-label {
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  font-weight: 500;
}

.quick-start {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--color-border);

  &__heading {
    display: flex;
    align-items: flex-start;
    justify-content: space-between;
    gap: 12px;

    > div {
      display: grid;
      gap: 3px;
    }

    small,
    > span {
      color: var(--color-text-tertiary);
      font-size: 0.7rem;
      line-height: 1.45;
    }

    > span {
      flex: none;
      color: var(--color-accent-text);
    }
  }
}

.last-meal {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px;
  color: var(--color-text-primary);
  text-align: left;
  background: var(--color-surface-muted);
  border: 0;
  border-radius: 10px;

  > span:first-child {
    display: grid;
    min-width: 0;
    gap: 3px;
  }

  strong {
    font-size: 0.85rem;
  }

  small {
    overflow: hidden;
    color: var(--color-text-secondary);
    font-size: 0.72rem;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  > span:last-child {
    flex: none;
    color: var(--color-accent-text);
    font-size: 0.75rem;
    font-weight: 500;
  }
}

.recent-foods {
  display: grid;
  gap: 7px;

  > span {
    color: var(--color-text-tertiary);
    font-size: 0.72rem;
  }

  > div {
    display: flex;
    gap: 6px;
    overflow-x: auto;
    scrollbar-width: none;

    &::-webkit-scrollbar {
      display: none;
    }
  }

  button {
    flex: none;
    padding: 7px 10px;
    color: var(--color-text-secondary);
    font-size: 0.72rem;
    background: transparent;
    border: 1px solid var(--color-border);
    border-radius: 999px;
  }
}

.file-input {
  display: none;
}

.photo-picker {
  display: grid;
  justify-items: center;
  gap: 5px;
  padding: 20px 14px;
  color: var(--color-text-secondary);
  background: var(--color-surface-muted);
  border: 1px dashed var(--color-border);
  border-radius: var(--border-radius-md);

  svg {
    color: var(--color-text-primary);
    font-size: 1.4rem;
  }

  strong {
    color: var(--color-text-primary);
    font-size: 0.875rem;
  }

  span {
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
}

.photo-preview {
  display: grid;
  gap: 9px;

  img {
    width: 100%;
    max-height: 220px;
    object-fit: cover;
    background: var(--color-surface-muted);
    border-radius: var(--border-radius-md);
  }

  &__actions {
    display: flex;
    justify-content: flex-end;
    gap: 4px;
  }
}

.recognizing-hint {
  padding: 12px;
  color: var(--color-text-secondary);
  font-size: 0.875rem;
  text-align: center;
  background: var(--color-surface-muted);
  border-radius: var(--border-radius-md);
}

.suggestion-panel {
  margin-top: 3px;
  padding: 13px;
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--border-radius-md);

  > header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 10px;
    margin-bottom: 10px;

    > div {
      display: grid;
      gap: 3px;
    }

    strong {
      font-size: 0.875rem;
    }

    span {
      color: var(--color-text-secondary);
      font-size: 0.75rem;
    }
  }
}

.suggestion-list {
  display: grid;
  gap: 6px;
}

.suggestion-item {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 9px;
  padding: 10px 11px;
  background: var(--color-surface);
  border: 1px solid transparent;
  border-radius: 9px;

  &__check {
    display: grid;
    width: 18px;
    height: 18px;
    flex: none;
    place-items: center;
    color: var(--color-on-primary);
    font-size: 0.875rem;
    background: var(--color-surface);
    border: 1px solid var(--color-border);
    border-radius: 5px;
  }

  &__main {
    display: grid;
    min-width: 0;
    flex: 1;
    gap: 2px;

    span {
      color: var(--color-text-tertiary);
      font-size: 0.75rem;
    }
  }

  &__name {
    width: 100%;

    :deep(.t-input) {
      height: auto;
      padding: 0;
      background: transparent;
      border: 0;
      border-radius: 0;
      box-shadow: none;

      &:hover,
      &:focus-within {
        border: 0;
        box-shadow: none;
      }
    }

    :deep(input) {
      height: auto;
      padding: 0;
      color: var(--color-text-primary);
      font-size: 0.875rem;
      font-weight: 500;
    }
  }

  &__calories {
    flex: none;
    font-size: 0.875rem;
    font-weight: 500;
    font-variant-numeric: tabular-nums;

    small {
      margin-left: 2px;
      color: var(--color-text-tertiary);
      font-size: 0.75rem;
      font-weight: 500;
    }
  }

  &.selected {
    border-color: var(--color-primary);

    .suggestion-item__check {
      background: var(--color-primary);
      border-color: var(--color-primary);
    }
  }
}

.suggestion-tip {
  margin: 10px 0 0;
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  line-height: 1.6;
}

.correct-block {
  display: grid;
  gap: 7px;
  margin-top: 10px;

  &__row {
    display: flex;
    align-items: center;
    gap: 7px;

    :deep(.t-input) {
      flex: 1;
      min-width: 0;
      background: var(--color-surface-muted);
      border-radius: 9px;
    }
  }

  &__applied {
    margin: 0;
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
    line-height: 1.6;
  }
}

.meal-type-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  button {
    min-width: 0;
    min-height: 40px;
    padding: 8px 4px;
    color: var(--color-text-secondary);
    background: var(--color-surface-muted);
    border: 1px solid transparent;
    border-radius: 6px;
    strong {
      font-size: 0.8125rem;
      font-weight: 400;
    }
    &.active {
      color: var(--color-accent-text);
      background: var(--color-primary-light);
      border-color: var(--color-primary-border);
    }
  }
}

.foods-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 4px 12px;
  margin: 4px 0 8px;

  .field-label {
    color: var(--color-text-primary);
    font-weight: 550;
  }
  small,
  &__hint {
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
    font-weight: 400;
  }
  small {
    margin-left: 4px;
  }
}

.food-list {
  border-top: 1px solid var(--color-border);
}

.food-item {
  min-width: 0;
  padding: 8px 0 12px;
  border-bottom: 1px solid var(--color-border);

  input {
    width: 100%;
    min-width: 0;
    padding: 0;
    border: 0;
    border-radius: 0;
    background: transparent;
    color: var(--color-text-primary);
    font: inherit;
    outline: none;
    &::placeholder {
      color: var(--color-text-tertiary);
    }
  }

  &__main {
    display: flex;
    align-items: center;
    gap: 8px;
    min-height: 40px;
    border-radius: 6px;
    &:focus-within {
      box-shadow: 0 0 0 1px var(--color-primary-border);
    }
  }
  &__index {
    flex: none;
    width: 14px;
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
    text-align: center;
  }
  input.food-item__name {
    flex: 1;
    height: 40px;
    font-size: 1rem;
  }
  &__ai {
    color: var(--color-text-tertiary);
    font-size: 0.6875rem;
  }
  &__remove {
    display: grid;
    flex: none;
    place-items: center;
    width: 40px;
    height: 40px;
    padding: 0;
    border: 0;
    border-radius: 6px;
    background: transparent;
    color: var(--color-text-tertiary);
    font-size: 1rem;
    &:disabled {
      opacity: 0.3;
      cursor: default;
    }
    &:not(:disabled):hover {
      color: var(--color-danger);
      background: var(--color-surface-muted);
    }
  }
  &__details {
    display: grid;
    grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
    gap: 8px;
    margin-top: 4px;
    label {
      display: flex;
      align-items: center;
      gap: 6px;
      min-width: 0;
      min-height: 36px;
      padding: 0 8px;
      border-radius: 6px;
      background: var(--color-surface-muted);
      font-size: 0.75rem;
      &:focus-within {
        box-shadow: 0 0 0 1px var(--color-primary-border);
      }
    }
    span,
    small {
      flex: none;
      color: var(--color-text-secondary);
      font-size: 0.6875rem;
    }
    input {
      height: 36px;
    }
    input[type='number'] {
      appearance: textfield;
    }
    input::-webkit-inner-spin-button,
    input::-webkit-outer-spin-button {
      appearance: none;
      margin: 0;
    }
  }
}
.add-food {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  width: 100%;
  min-height: 40px;
  margin-top: 4px;
  padding: 0;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: var(--color-accent-text);
  font-size: 0.8125rem;
  &:hover {
    background: var(--color-surface-muted);
  }
}

.note-field {
  margin-top: 20px;
}

@media (max-width: 360px) {
  .meal-type-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}
.form-meta {
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin: -8px 0 16px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--color-text-secondary);
  font-size: 0.75rem;
  > span:last-child {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}
.food-item__extras {
  summary {
    display: flex;
    min-height: 36px;
    align-items: center;
    gap: 8px;
    color: var(--color-text-secondary);
    font-size: 0.75rem;
    span {
      color: var(--color-text-tertiary);
      font-size: 0.6875rem;
    }
  }
}
.suggestion-item input {
  width: 100%;
  min-width: 0;
  padding: 6px 0;
  color: var(--color-text-primary);
  border: 0;
  border-bottom: 1px solid var(--color-border);
  background: transparent;
  font: inherit;
}
.suggestion-item__calories input {
  width: 66px;
  text-align: right;
}
.correct-block summary {
  padding: 8px 0;
  color: var(--color-text-secondary);
  cursor: pointer;
}
</style>
