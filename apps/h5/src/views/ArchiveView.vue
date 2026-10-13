<script setup lang="ts">
import UiLoading from '@/components/ui/UiLoading.vue';
import EmptyState from '@/components/EmptyState.vue';
import type {
  BodyRecord,
  MealRecord,
  MealType,
  PhotoType,
  ProgressPhoto,
  WorkoutRecord,
  WorkoutType,
} from '@fit-trace/shared';
import dayjs from 'dayjs';
import UiPageHeader from '@/components/ui/UiPageHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiDateSheet from '@/components/ui/UiDateSheet.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { getBodyRecords } from '@/api/body-records';
import { getMeals } from '@/api/meals';
import { getProgressPhotos } from '@/api/progress-photos';
import { dateRange, recordDateTime } from '@/utils/record-date';
import { getWorkouts } from '@/api/workouts';

type ArchiveFilter = 'ALL' | 'BODY' | 'MEAL' | 'WORKOUT' | 'PHOTO';
type ArchiveItem =
  | { id: string; kind: 'BODY'; recordedAt: string; body: BodyRecord }
  | { id: string; kind: 'MEAL'; recordedAt: string; meal: MealRecord }
  | { id: string; kind: 'WORKOUT'; recordedAt: string; workout: WorkoutRecord }
  | { id: string; kind: 'PHOTO'; recordedAt: string; photo: ProgressPhoto };

const mealLabels: Record<MealType, string> = {
  BREAKFAST: '早餐',
  LUNCH: '午餐',
  DINNER: '晚餐',
  SNACK: '加餐',
};
const workoutLabels: Record<WorkoutType, string> = {
  STRENGTH: '力量',
  CARDIO: '有氧',
  RUNNING: '跑步',
  CYCLING: '骑行',
  SWIMMING: '游泳',
  OTHER: '其他',
};
const photoLabels: Record<PhotoType, string> = {
  FRONT: '正面',
  SIDE: '侧面',
  BACK: '背面',
  OTHER: '其他',
};
const router = useRouter();
const route = useRoute();
const initialDate =
  typeof route.query.date === 'string'
    ? dayjs(recordDateTime(route.query.date)).format('YYYY-MM-DD')
    : '';
const selectedDate = ref(initialDate);
const selectedMonth = ref(dayjs(initialDate || undefined).format('YYYY-MM'));
const page = ref(1);
const monthPickerVisible = ref(false);
const datePickerVisible = ref(false);
const calendarDate = computed({
  get: () => selectedDate.value || `${selectedMonth.value}-01`,
  set: (date: string) => {
    selectedDate.value = date;
  },
});
const loadingMore = ref(false);
const totals = ref({ BODY: 0, MEAL: 0, WORKOUT: 0, PHOTO: 0 });
const total = computed(() =>
  activeFilter.value === 'ALL'
    ? Object.values(totals.value).reduce((a, b) => a + b, 0)
    : totals.value[activeFilter.value],
);
const hasMore = computed(() => {
  const counts = {
    BODY: bodyRecords.value.length,
    MEAL: meals.value.length,
    WORKOUT: workouts.value.length,
    PHOTO: photos.value.length,
  };
  return activeFilter.value === 'ALL'
    ? (Object.keys(counts) as Array<keyof typeof counts>).some(
        (kind) => counts[kind] < totals.value[kind],
      )
    : counts[activeFilter.value] < totals.value[activeFilter.value];
});
function addRecord() {
  void router.push({ path: '/record', query: { date: selectedDate.value || undefined } });
}
function chooseMonth(value: string) {
  if (!/^\d{4}-\d{2}$/.test(value)) return;
  selectedDate.value = '';
  selectedMonth.value = value;
}

const loading = ref(true);
const loadError = ref('');
const activeFilter = ref<ArchiveFilter>('ALL');
const bodyRecords = ref<BodyRecord[]>([]);
const meals = ref<MealRecord[]>([]);
const workouts = ref<WorkoutRecord[]>([]);
const photos = ref<ProgressPhoto[]>([]);
const filters: Array<{ value: ArchiveFilter; label: string }> = [
  { value: 'ALL', label: '全部' },
  { value: 'BODY', label: '身体' },
  { value: 'MEAL', label: '饮食' },
  { value: 'WORKOUT', label: '训练' },
  { value: 'PHOTO', label: '照片' },
];
const items = computed<ArchiveItem[]>(() =>
  [
    ...bodyRecords.value.map((body) => ({
      id: `body-${body.id}`,
      kind: 'BODY' as const,
      recordedAt: body.recordedAt,
      body,
    })),
    ...meals.value.map((meal) => ({
      id: `meal-${meal.id}`,
      kind: 'MEAL' as const,
      recordedAt: meal.recordedAt,
      meal,
    })),
    ...workouts.value.map((workout) => ({
      id: `workout-${workout.id}`,
      kind: 'WORKOUT' as const,
      recordedAt: workout.startedAt,
      workout,
    })),
    ...photos.value.map((photo) => ({
      id: `photo-${photo.id}`,
      kind: 'PHOTO' as const,
      recordedAt: photo.recordedAt,
      photo,
    })),
  ]
    .filter((item) => activeFilter.value === 'ALL' || item.kind === activeFilter.value)
    .sort((a, b) => dayjs(b.recordedAt).valueOf() - dayjs(a.recordedAt).valueOf()),
);
const groups = computed(() => {
  const result = new Map<string, ArchiveItem[]>();
  for (const item of items.value) {
    const key = dayjs(item.recordedAt).format('YYYY-MM-DD');
    result.set(key, [...(result.get(key) ?? []), item]);
  }
  return [...result.entries()].map(([date, groupItems]) => ({ date, items: groupItems }));
});
function dateTitle(date: string): string {
  const value = dayjs(date);
  if (value.isSame(dayjs(), 'day')) return '今天';
  if (value.isSame(dayjs().subtract(1, 'day'), 'day')) return '昨天';
  return value.format('M月D日');
}
function openItem(item: ArchiveItem): void {
  if (item.kind === 'BODY') return void router.push(`/body/${item.body.id}`);
  if (item.kind === 'MEAL') return void router.push(`/meals/${item.meal.id}`);
  if (item.kind === 'WORKOUT') return void router.push(`/workouts/${item.workout.id}`);
  router.push(`/photos/${item.photo.id}`);
}
defineOptions({ name: 'ArchiveView' });

let requestId = 0;
async function loadRecords(silent = false, more = false): Promise<void> {
  const request = ++requestId;
  const nextPage = more ? page.value + 1 : 1;
  if (more) loadingMore.value = true;
  else if (!silent) loading.value = true;
  loadError.value = '';
  const range = dateRange(
    selectedDate.value || `${selectedMonth.value}-01`,
    selectedDate.value ? 'day' : 'month',
  );
  try {
    const [bodyResult, mealResult, workoutResult, photoResult] = await Promise.all([
      getBodyRecords({ page: nextPage, pageSize: 20, recordedAtOrder: 'desc', ...range }),
      getMeals({ page: nextPage, pageSize: 20, ...range }),
      getWorkouts({ page: nextPage, pageSize: 20, ...range }),
      getProgressPhotos({ page: nextPage, pageSize: 20, ...range }),
    ]);
    if (request !== requestId) return;
    bodyRecords.value = more ? [...bodyRecords.value, ...bodyResult.data] : bodyResult.data;
    meals.value = more ? [...meals.value, ...mealResult.data] : mealResult.data;
    workouts.value = more ? [...workouts.value, ...workoutResult.data] : workoutResult.data;
    photos.value = more ? [...photos.value, ...photoResult.data] : photoResult.data;
    totals.value = {
      BODY: bodyResult.meta.total,
      MEAL: mealResult.meta.total,
      WORKOUT: workoutResult.meta.total,
      PHOTO: photoResult.meta.total,
    };
    page.value = nextPage;
  } catch {
    if (request === requestId) loadError.value = '记录暂时无法加载，请重试';
  } finally {
    if (request === requestId) {
      loading.value = false;
      loadingMore.value = false;
    }
  }
}
watch(selectedDate, (date) => {
  if (date) selectedMonth.value = date.slice(0, 7);
});
watch([selectedMonth, selectedDate], () => {
  bodyRecords.value = [];
  meals.value = [];
  workouts.value = [];
  photos.value = [];
  totals.value = { BODY: 0, MEAL: 0, WORKOUT: 0, PHOTO: 0 };
  void loadRecords();
});
watch(
  () => route.query.date,
  (date) => {
    if (route.path !== '/archive') return;
    selectedDate.value =
      typeof date === 'string' ? dayjs(recordDateTime(date)).format('YYYY-MM-DD') : '';
    if (selectedDate.value) selectedMonth.value = selectedDate.value.slice(0, 7);
  },
);

onMounted(() => void loadRecords());
let activated = false;
onActivated(() => {
  if (!activated) {
    activated = true;
    return;
  }
  void loadRecords(true);
});
</script>

<template>
  <main class="view-page ui-page archive-page">
    <UiPageHeader
      title="档案"
      :subtitle="`${selectedDate ? dayjs(selectedDate).format('M月D日') : dayjs(`${selectedMonth}-01`).format('YYYY年M月')} · ${total} 条记录`"
      ><template #action
        ><UiButton kind="text" @click="addRecord">记录</UiButton></template
      ></UiPageHeader
    >
    <div class="archive-dates">
      <UiButton kind="secondary" aria-label="选择档案月份" @click="monthPickerVisible = true">
        <span class="archive-date-trigger"
          >{{ dayjs(`${selectedMonth}-01`).format('YYYY年M月') }}<UiIcon name="right"
        /></span>
      </UiButton>
      <UiButton kind="secondary" aria-label="查找记录日期" @click="datePickerVisible = true">
        <span class="archive-date-trigger"
          >{{ selectedDate ? dayjs(selectedDate).format('M月D日') : '查找日期'
          }}<UiIcon name="calendar"
        /></span>
      </UiButton>
    </div>
    <UiButton v-if="selectedDate" kind="text" @click="chooseMonth(selectedDate.slice(0, 7))"
      >查看整月记录</UiButton
    >
    <UiDateSheet
      v-model:visible="monthPickerVisible"
      :model-value="selectedMonth"
      selection="month"
      @update:model-value="chooseMonth"
    />
    <UiDateSheet v-model:visible="datePickerVisible" v-model="calendarDate" />
    <UiTabs
      :model-value="activeFilter"
      :options="filters"
      label="按记录类型筛选"
      @update:model-value="activeFilter = $event as ArchiveFilter"
    />
    <UiLoading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在整理记录"
    >
      <div v-if="loadError" class="archive-error" role="alert">
        <span>{{ loadError }}</span
        ><UiButton kind="text" @click="loadRecords()">重试</UiButton>
      </div>
      <EmptyState
        v-if="!loading && !loadError && items.length === 0"
        title="这段时间还没有记录"
        description="换一个日期查看，或为当天补上一条记录"
        action-label="添加记录"
        @action="addRecord"
      />
      <div v-else class="archive-list">
        <section v-for="group in groups" :key="group.date" class="archive-group">
          <header>
            <strong>{{ dateTitle(group.date) }}</strong
            ><span>{{ dayjs(group.date).format('M月D日') }}</span>
          </header>
          <div class="archive-group__body">
            <template v-for="item in group.items" :key="item.id">
              <UiRecordRow
                v-if="item.kind === 'BODY'"
                kind="body"
                title="身体数据"
                :subtitle="`${dayjs(item.recordedAt).format('HH:mm')} · ${[item.body.bodyFat !== null ? `体脂 ${item.body.bodyFat}%` : '', item.body.waist !== null ? `腰围 ${item.body.waist} cm` : '', item.body.note ?? ''].filter(Boolean).join(' · ')}`"
                :value="item.body.weight"
                unit="kg"
                @click="openItem(item)"
              />
              <UiRecordRow
                v-else-if="item.kind === 'MEAL'"
                kind="meal"
                :title="mealLabels[item.meal.type]"
                :subtitle="`${dayjs(item.recordedAt).format('HH:mm')} · ${item.meal.foods.map((food) => food.name).join('、')}`"
                :value="item.meal.totalCalories ?? '—'"
                unit="kcal"
                @click="openItem(item)"
              />
              <UiRecordRow
                v-else-if="item.kind === 'WORKOUT'"
                kind="workout"
                :workout-type="item.workout.type"
                :title="item.workout.name"
                :subtitle="`${dayjs(item.recordedAt).format('HH:mm')} · ${workoutLabels[item.workout.type]}`"
                :value="item.workout.durationMinutes"
                unit="分钟"
                @click="openItem(item)"
              />
              <UiRecordRow
                v-else
                kind="photo"
                :title="`${photoLabels[item.photo.type]}照片`"
                :subtitle="`${dayjs(item.recordedAt).format('HH:mm')} · ${item.photo.note || '身材记录'}`"
                :image="item.photo.imageUrl"
                @click="openItem(item)"
              />
            </template>
          </div>
        </section>
      </div>
      <UiButton
        v-if="hasMore && !loading"
        class="archive-more"
        kind="secondary"
        block
        :loading="loadingMore"
        @click="loadRecords(false, true)"
        >加载更多</UiButton
      >
      <p v-if="!loading && !loadError && total" class="archive-count">
        已显示 {{ items.length }} / {{ total }} 条{{ hasMore ? '，继续加载可查看更早的记录' : '' }}
      </p>
    </UiLoading>
  </main>
</template>
<style scoped lang="scss">
.archive-dates {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  margin-bottom: 20px;
}
.archive-date-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: 8px;
  font-size: 13px;
  font-weight: 400;
  .ui-icon {
    width: 18px;
    height: 18px;
    color: var(--ui-text-secondary);
  }
}
.archive-list {
  display: grid;
  gap: 28px;
}
.archive-group > header {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  margin-bottom: 16px;
  strong {
    font-size: 18px;
    font-weight: 600;
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
}
.archive-group__body {
  display: grid;
  gap: 10px;
}
.archive-more {
  margin-top: 24px;
}
.archive-count {
  text-align: center;
  color: var(--ui-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}
.archive-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--ui-text-secondary);
  font-size: 13px;
}
</style>
