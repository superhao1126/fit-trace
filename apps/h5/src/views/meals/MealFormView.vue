<script setup lang="ts">
import MealFavorites from '@/components/meals/MealFavorites.vue';
import MealFavoriteSave from '@/components/meals/MealFavoriteSave.vue';
import { mealDraftKey, readMealDraft, type MealDraft } from '@/utils/meal-draft';
import { authSession } from '@/api/session';
import { confirmDialog } from '@/components/ui/confirm';
import UiLoading from '@/components/ui/UiLoading.vue';
import { applyFoodEstimate, correctFoodName } from '@/utils/food-correction';
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
import type { MealFavorite, MealRecord, MealType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { ActionSheet } from 'tdesign-mobile-vue';
import { uiToast as ToastPlugin } from '@/components/ui/toast';
import { AddIcon, CheckIcon, DeleteIcon } from 'tdesign-icons-vue-next';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { analyzeMealText, recognizeFood } from '@/api/ai';
import {
  createMeal,
  getMeal,
  getMeals,
  updateMeal,
  type FoodItemInput,
  type MealInput,
} from '@/api/meals';
import { uploadImage } from '@/api/uploads';
import FoodLibraryPicker from '@/components/meals/FoodLibraryPicker.vue';

interface EditableFood {
  key: number;
  name: string;
  amount: string;
  calories: string | number;
  aiGenerated: boolean;
  sourceImageUrl?: string;
  sourceName?: string;
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
const estimatingFoods = ref(new Set<number>());
const suggestions = ref<FoodSuggestion[]>([]);
const fileInput = ref<HTMLInputElement | null>(null);
const cameraInput = ref<HTMLInputElement | null>(null);
const photoSourceVisible = ref(false);
const recentMeals = ref<MealRecord[]>([]);
const templateSource = ref('');
type EntryMode = 'text' | 'library' | 'manual' | 'photo' | 'recent' | 'favorites';
const description = ref('');
const entryMode = ref<EntryMode>(
  route.query.mode === 'photo' ? 'photo' : mealId.value ? 'manual' : 'text',
);
const libraryVisible = ref(false);
const entryModes = [
  { value: 'text', label: '一句话' },
  { value: 'photo', label: '拍照' },
  { value: 'favorites', label: '常用餐' },
] as const;
const moreModes = [
  { value: 'library', label: '食品库' },
  { value: 'recent', label: '最近吃过' },
  { value: 'manual', label: '手动填写' },
] as const;
const allModes = [...entryModes, ...moreModes];
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

const ownerId = authSession.snapshot?.user?.id ?? '';
const sessionGeneration = authSession.generation;
const draftKey = mealDraftKey(ownerId, mealId.value);
const pendingDraft = ref<MealDraft | null>(readMealDraft(localStorage, draftKey));
const draftReady = ref(!loading.value);
const draftSaved = ref(false);
const draftStorageError = ref(false);
const favoriteVisible = ref(false);
const favoriteFoods = ref<FoodItemInput[]>([]);
const favoritesPanel = ref<InstanceType<typeof MealFavorites> | null>(null);
function restoreDraft() {
  const draft = pendingDraft.value;
  if (!draft) return;
  Object.assign(formData, {
    type: draft.type,
    recordedAt: draft.recordedAt,
    note: draft.note,
    foods: draft.foods.map((food) => ({ ...food })),
  });
  imageUrl.value = draft.imageUrl;
  recognitionProvider.value =
    typeof draft.recognitionProvider === 'string' ? draft.recognitionProvider : '';
  suggestions.value = draft.suggestions.map((food) => ({ ...food }));
  description.value = typeof draft.description === 'string' ? draft.description : '';
  foodKey = Math.max(0, ...draft.foods.map((f) => f.key), ...draft.suggestions.map((f) => f.key));
  entryMode.value = allModes.some((mode) => mode.value === draft.entryMode)
    ? (draft.entryMode as EntryMode)
    : 'manual';
  pendingDraft.value = null;
  draftSaved.value = true;
  persistDraft();
}
function discardDraft() {
  const dialog = confirmDialog({
    title: '丢弃这份饮食草稿？',
    content: '草稿中尚未保存的修改会被清除，已有饮食记录会保留。',
    confirmBtn: '丢弃草稿',
    onConfirm: () => {
      try {
        localStorage.removeItem(draftKey);
        pendingDraft.value = null;
        draftSaved.value = false;
        dialog.destroy();
      } catch {
        ToastPlugin.warning('草稿清除失败，请重试');
      }
    },
  });
}
function persistDraft() {
  if (
    !draftReady.value ||
    pendingDraft.value ||
    !ownerId ||
    authSession.generation !== sessionGeneration
  )
    return;
  const meaningful =
    formData.foods.some((f) => f.name || f.amount || f.calories !== '') ||
    formData.note ||
    imageUrl.value ||
    suggestions.value.length ||
    description.value;
  try {
    if (!meaningful) {
      localStorage.removeItem(draftKey);
      draftSaved.value = false;
      return;
    }
    localStorage.setItem(
      draftKey,
      JSON.stringify({
        ...formData,
        imageUrl: imageUrl.value,
        recognitionProvider: recognitionProvider.value,
        suggestions: suggestions.value,
        entryMode: entryMode.value,
        description: description.value,
        savedAt: new Date().toISOString(),
      }),
    );
    draftSaved.value = true;
    draftStorageError.value = false;
  } catch {
    draftSaved.value = false;
    if (!draftStorageError.value) ToastPlugin.warning('本机草稿保存失败，请保持页面打开');
    draftStorageError.value = true;
  }
}
watch([formData, imageUrl, suggestions, entryMode, description], persistDraft, {
  deep: true,
  flush: 'sync',
});

async function analyzeDescription() {
  const text = description.value.trim();
  if (!text || recognizing.value || suggestions.value.length) return;
  recognizing.value = true;
  try {
    const result = await analyzeMealText(text);
    if (authSession.generation !== sessionGeneration || description.value.trim() !== text) return;
    recognitionProvider.value = result.provider;
    suggestions.value = result.foods.map((food) => ({
      key: ++foodKey,
      name: food.name,
      amount: food.estimatedAmount ?? '',
      calories: food.estimatedCalories ?? '',
      aiGenerated: true,
      selected: true,
    }));
    if (!suggestions.value.length) ToastPlugin.warning('没有找到餐食信息，可直接记录原文');
  } catch (error) {
    showRequestError(error, '分析失败，可直接记录原文');
  } finally {
    recognizing.value = false;
  }
}
function recordDescription() {
  const text = description.value.trim();
  if (!text) return;
  if (text.length > 100) {
    const note = [formData.note.trim(), text].filter(Boolean).join('\n');
    if (note.length > 500) {
      ToastPlugin.warning('备注空间不足以保留完整原文，请精简备注后重试');
      return;
    }
    formData.note = note;
  }
  const food = {
    key: ++foodKey,
    name: text.slice(0, 100),
    amount: '',
    calories: '',
    aiGenerated: false,
  };
  formData.foods = formData.foods.every((f) => !f.name.trim()) ? [food] : [...formData.foods, food];
  description.value = '';
  ToastPlugin.success('已添加原文，热量留空，可直接保存');
}
function openFavorite() {
  const input = buildInput();
  if (!input) return;
  favoriteFoods.value = input.foods;
  favoriteVisible.value = true;
}
function applyFavorite(meal: MealFavorite) {
  const apply = () => {
    formData.foods = meal.foods.map((f) => ({
      key: ++foodKey,
      name: f.name,
      amount: f.amount ?? '',
      calories: f.calories ?? '',
      aiGenerated: f.aiGenerated,
    }));
    templateSource.value = meal.name;
    entryMode.value = 'manual';
    ToastPlugin.success('已带入常用餐，餐次和日期保持不变');
  };
  if (formData.foods.some((f) => f.name.trim())) {
    const dialog = confirmDialog({
      title: '带入这份常用餐？',
      content: '会替换表单里的食物，餐次、日期和备注保持不变。',
      confirmBtn: '替换食物',
      onConfirm: () => {
        apply();
        dialog.destroy();
      },
    });
  } else apply();
}

if (mealTypes.some((meal) => meal.value === route.query.type))
  formData.type = route.query.type as MealType;
function addLibraryFood(food: { name: string; amount: string; calories: number }) {
  addRecentFood(food);
  entryMode.value = 'library';
}
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
  if (uploadingPhoto.value || recognizing.value) return;
  photoSourceVisible.value = true;
}

function selectPhotoSource(_selected: unknown, index: number): void {
  photoSourceVisible.value = false;
  if (index === 0) cameraInput.value?.click();
  else fileInput.value?.click();
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

async function recognizePhoto(): Promise<void> {
  if (!imageUrl.value || recognizing.value || suggestions.value.length) return;
  recognizing.value = true;
  try {
    const result = await recognizeFood(imageUrl.value);
    recognitionProvider.value = result.provider;
    suggestions.value = result.foods.map((food) => ({
      key: ++foodKey,
      name: food.name,
      amount: food.estimatedAmount ?? '',
      calories: food.estimatedCalories ?? '',
      aiGenerated: true,
      sourceImageUrl: imageUrl.value,
      sourceName: food.name,
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

async function estimateFood(food: EditableFood): Promise<void> {
  const name = food.name.trim();
  const photo = food.sourceImageUrl;
  if (!photo || !name || estimatingFoods.value.has(food.key)) return;
  const snapshot = { name: food.name, amount: food.amount, calories: food.calories };
  estimatingFoods.value.add(food.key);
  try {
    const hint = `只估算一道菜：原名称“${(food.sourceName ?? '').slice(0, 30)}”，用户确认名称为“${name}”。结合照片估算份量和热量，foods 仅返回此项并使用确认名称；无法定位返回空数组。`;
    const result = await recognizeFood(photo, hint);
    const current = [...suggestions.value, ...formData.foods].find((item) => item.key === food.key);
    if (!current || current.sourceImageUrl !== photo) return;
    if (
      result.provider === 'mock' ||
      result.foods.length !== 1 ||
      result.foods[0].name.trim() !== name
    ) {
      ToastPlugin.warning('未能估算这道菜，请重试或手动填写');
      return;
    }
    if (applyFoodEstimate(current, snapshot, result.foods[0])) {
      current.sourceName = name;
      ToastPlugin.success('已更新这道菜的估算，可继续调整');
    } else if (
      current.name === snapshot.name &&
      current.amount === snapshot.amount &&
      current.calories === snapshot.calories
    ) {
      ToastPlugin.warning('估算信息不完整，请重试或手动填写');
    }
  } catch (error) {
    showRequestError(error, '估算失败，请重试或手动填写');
  } finally {
    estimatingFoods.value.delete(food.key);
  }
}

function removePhoto(): void {
  imageUrl.value = '';
  recognitionProvider.value = '';
  suggestions.value = [];
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
      aiGenerated: item.aiGenerated,
      sourceImageUrl: item.sourceImageUrl,
      sourceName: item.sourceName,
    });
  }
  formData.foods = nextFoods;
  suggestions.value = [];
  if (entryMode.value === 'text') description.value = '';
  else entryMode.value = 'manual';
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

function clearSavedDraft() {
  draftReady.value = false;
  try {
    localStorage.removeItem(draftKey);
  } catch {
    ToastPlugin.warning('记录已保存，本机草稿清理失败');
  }
}

async function submit(): Promise<void> {
  if (submitting.value) return;
  const input = buildInput();
  if (!input) return;
  submitting.value = true;
  try {
    if (mealId.value) {
      await updateMeal(mealId.value, input);
      clearSavedDraft();
      ToastPlugin.success('饮食记录已更新');
      await router.replace(`/meals/${mealId.value}`);
    } else {
      const created = await createMeal(input);
      clearSavedDraft();
      ToastPlugin.success('饮食记录已保存');
      await router.replace(
        route.query.returnTo === '/dashboard'
          ? { path: '/dashboard', query: { date: dayjs(formData.recordedAt).format('YYYY-MM-DD') } }
          : route.query.returnTo === '/meals'
            ? { path: '/meals', query: { date: dayjs(formData.recordedAt).format('YYYY-MM-DD') } }
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
        sourceImageUrl: food.aiGenerated ? (meal.imageUrl ?? undefined) : undefined,
        sourceName: food.aiGenerated ? food.name : undefined,
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
    draftReady.value = true;
  }
});
</script>

<template>
  <main class="view-page ui-page ui-form meal-form-page">
    <UiDetailHeader
      :title="isEdit ? '编辑饮食记录' : '记饮食'"
      subtitle="记下吃了什么，份量和热量选填"
    />

    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在读取饮食记录"
    >
      <section v-if="pendingDraft" class="draft-banner" role="status">
        <strong>有一份未保存的饮食草稿</strong>
        <p>
          {{ dayjs(pendingDraft.savedAt).format('M月D日 HH:mm') }} ·
          {{ pendingDraft.foods.filter((f) => f.name.trim()).length }} 种食物
        </p>
        <div>
          <UiButton kind="primary" @click="restoreDraft">恢复草稿</UiButton
          ><UiButton kind="text" @click="discardDraft">丢弃</UiButton>
        </div>
      </section>
      <section v-show="!pendingDraft" class="meal-form-card">
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
          :disabled="uploadingPhoto || recognizing || estimatingFoods.size > 0"
          @update:model-value="entryMode = $event as EntryMode"
        />
        <div v-if="entryMode === 'text'" class="field-block meal-description">
          <UiTextarea
            v-model="description"
            :maxlength="500"
            aria-label="这一餐吃了什么"
            placeholder="例如：中午一碗牛肉面，加一个鸡蛋"
            :disabled="recognizing || suggestions.length > 0"
          />
          <div>
            <UiButton
              :loading="recognizing"
              :disabled="!description.trim() || suggestions.length > 0"
              @click="analyzeDescription"
              >AI 分析这一餐</UiButton
            ><UiButton
              kind="text"
              :disabled="!description.trim() || recognizing || suggestions.length > 0"
              @click="recordDescription"
              >直接记录原文</UiButton
            >
          </div>
          <p class="suggestion-tip">AI 估算后先确认；直接记录热量留空，较长原文保存在备注。</p>
        </div>
        <details class="meal-more">
          <summary>食品库、最近与手动填写</summary>
          <UiTabs
            :model-value="entryMode"
            :options="moreModes"
            label="其他添加方式"
            :disabled="recognizing || uploadingPhoto"
            @update:model-value="entryMode = $event as EntryMode"
          />
        </details>
        <div v-if="entryMode === 'library'" class="field-block">
          <UiButton kind="secondary" block @click="libraryVisible = true"
            >从食品库添加食物</UiButton
          >
          <p class="suggestion-tip">按实际份量换算热量，找不到的食物可手动填写。</p>
        </div>
        <FoodLibraryPicker
          v-model:visible="libraryVisible"
          :meal-label="mealTypes.find((meal) => meal.value === formData.type)?.label ?? '本餐'"
          @select="addLibraryFood"
        />
        <p v-if="templateSource" class="template-hint">
          已带入 {{ templateSource }}，确认下方内容后保存
        </p>
        <MealFavorites
          v-if="entryMode === 'favorites'"
          ref="favoritesPanel"
          @select="applyFavorite"
        />
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
              <UiButton
                v-if="!suggestions.length"
                kind="secondary"
                :loading="recognizing"
                @click="recognizePhoto()"
              >
                重新识别
              </UiButton>
              <UiButton
                kind="danger"
                :disabled="recognizing || uploadingPhoto"
                @click="removePhoto"
              >
                <DeleteIcon /> 移除
              </UiButton>
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
        </div>
        <div v-if="suggestions.length" class="suggestion-panel">
          <header>
            <div>
              <strong>识别结果</strong>
              <span>{{
                recognitionProvider === 'mock'
                  ? '当前为模拟识别，不会分析照片，请手动调整'
                  : '估算仅供参考，请核对名称、份量与热量'
              }}</span>
            </div>
            <UiButton
              kind="secondary"
              :disabled="estimatingFoods.size > 0"
              @click="applySuggestions"
              >确认并加入</UiButton
            >
            <UiButton kind="text" :disabled="estimatingFoods.size > 0" @click="suggestions = []"
              >放弃分析结果</UiButton
            >
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
                  :model-value="item.name"
                  :aria-label="`识别食物 ${index + 1} 名称`"
                  class="suggestion-item__name"
                  :maxlength="100"
                  placeholder="食物名称"
                  @update:model-value="correctFoodName(item, String($event))"
                />
                <input
                  v-model="item.amount"
                  :aria-label="`识别食物 ${index + 1} 份量`"
                  maxlength="50"
                  placeholder="份量选填"
                />
                <button
                  v-if="item.sourceImageUrl"
                  type="button"
                  class="food-estimate"
                  :disabled="!item.name.trim() || estimatingFoods.has(item.key)"
                  @click="estimateFood(item)"
                >
                  {{ estimatingFoods.has(item.key) ? '估算中…' : 'AI 重新估算' }}
                </button>
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
          <p class="suggestion-tip">
            修改 AI 菜名会清除旧估算。可手动补充；照片识别的菜品也可单独重新估算。
          </p>
        </div>

        <input
          ref="fileInput"
          class="file-input"
          type="file"
          accept="image/jpeg,image/png,image/webp"
          @change="onPhotoChange"
        />
        <input
          ref="cameraInput"
          class="file-input"
          type="file"
          accept="image/*"
          capture="environment"
          @change="onPhotoChange"
        />
        <MealFavoriteSave
          v-model:visible="favoriteVisible"
          :foods="favoriteFoods"
          @saved="favoritesPanel?.load()"
        />
        <ActionSheet
          v-model="photoSourceVisible"
          :items="['拍照', '从相册选择']"
          cancel-text="取消"
          @selected="selectPhotoSource"
        />

        <template v-if="entryMode === 'manual' || formData.foods.some((food) => food.name.trim())">
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
                  :value="food.name"
                  class="food-item__name"
                  :aria-label="`食物 ${index + 1} 名称`"
                  maxlength="100"
                  placeholder="食物名称，如鸡胸肉"
                  @input="correctFoodName(food, ($event.target as HTMLInputElement).value)"
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
              <button
                v-if="food.sourceImageUrl"
                type="button"
                class="food-estimate"
                :disabled="!food.name.trim() || estimatingFoods.has(food.key)"
                @click="estimateFood(food)"
              >
                {{ estimatingFoods.has(food.key) ? '估算中…' : 'AI 重新估算' }}
              </button>
              <details class="food-item__extras" :open="isEdit">
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
        </template>

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
          kind="text"
          :disabled="uploadingPhoto || recognizing || estimatingFoods.size > 0"
          @click="openFavorite"
          >收藏这餐</UiButton
        >
        <p v-if="draftSaved" class="template-hint" role="status">已自动保存到本机草稿</p>
        <UiButton
          block
          :loading="submitting"
          :disabled="uploadingPhoto || recognizing || estimatingFoods.size > 0"
          @click="submit"
        >
          {{ isEdit ? '保存修改' : '保存饮食记录' }}
        </UiButton>
      </section>
    </UiLoading>

    <UiDateTimePicker
      v-model:visible="datePickerVisible"
      :value="formData.recordedAt"
      @confirm="confirmRecordedAt"
    />
  </main>
</template>

<style scoped lang="scss">
.meal-description > div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.meal-more {
  margin: 12px 0;
  color: var(--ui-text-secondary);
  font-size: 13px;
}
.meal-more summary {
  cursor: pointer;
  margin-bottom: 8px;
}
.draft-banner {
  background: var(--ui-primary-light);
  border-radius: 14px;
  padding: 16px;
  strong {
    font-size: 15px;
  }
  p {
    font-size: 12px;
    color: var(--ui-text-secondary);
  }
  > div {
    display: flex;
    align-items: center;
    gap: 12px;
  }
}

.food-estimate {
  display: block;
  padding: 8px 0;
  border: 0;
  background: transparent;
  color: var(--ui-text-secondary);
  font-size: 12px;
  &:disabled {
    opacity: 0.5;
  }
}

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
