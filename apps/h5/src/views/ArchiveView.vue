<script setup lang="ts">
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
import { Button, Loading } from 'tdesign-mobile-vue';
import { ActivityIcon, AddIcon, ForkIcon, MeasurementIcon } from 'tdesign-icons-vue-next';
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
  <main class="view-page archive-page">
    <header class="primary-header archive-header">
      <div>
        <h1>记录档案</h1>
        <p>
          {{
            selectedDate
              ? dayjs(selectedDate).format('M月D日')
              : dayjs(`${selectedMonth}-01`).format('YYYY年M月')
          }}
          · {{ total }} 条记录
        </p>
      </div>
      <Button theme="primary" size="small" variant="text" @click="addRecord"
        ><AddIcon /> 记录</Button
      >
    </header>
    <div class="archive-dates">
      <label
        >月份<input
          type="month"
          aria-label="档案月份"
          :value="selectedMonth"
          :max="dayjs().format('YYYY-MM')"
          @change="chooseMonth(($event.target as HTMLInputElement).value)"
      /></label>
      <label
        >日期<input
          v-model="selectedDate"
          type="date"
          aria-label="查找记录日期"
          :max="dayjs().format('YYYY-MM-DD')"
      /></label>
      <button
        v-if="selectedDate"
        type="button"
        @click="
          selectedMonth = selectedDate.slice(0, 7);
          selectedDate = '';
        "
      >
        整月
      </button>
    </div>
    <div class="archive-filter filter-tabs" aria-label="按记录类型筛选">
      <button
        v-for="filter in filters"
        :key="filter.value"
        type="button"
        :class="{ active: activeFilter === filter.value }"
        :aria-pressed="activeFilter === filter.value"
        @click="activeFilter = filter.value"
      >
        {{ filter.label }}
      </button>
    </div>

    <Loading
      class="page-loading"
      :class="{ 'page-loading--active': loading }"
      :loading="loading"
      text="正在整理记录"
    >
      <div v-if="loadError" class="archive-error" role="alert">
        <span>{{ loadError }}</span
        ><Button variant="text" size="small" @click="loadRecords()">重新加载</Button>
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
            ><span>{{ dayjs(group.date).format('YYYY年M月D日') }}</span>
          </header>
          <div class="archive-group__body">
            <button
              v-for="item in group.items"
              :key="item.id"
              type="button"
              class="archive-row"
              @click="openItem(item)"
            >
              <span class="archive-row__time">{{ dayjs(item.recordedAt).format('HH:mm') }}</span>
              <span
                class="archive-row__icon"
                :class="{ 'archive-row__icon--photo': item.kind === 'PHOTO' }"
              >
                <img
                  v-if="item.kind === 'PHOTO'"
                  :src="item.photo.imageUrl"
                  :alt="`${photoLabels[item.photo.type]}照片`"
                  loading="lazy"
                />
                <MeasurementIcon v-else-if="item.kind === 'BODY'" />
                <ForkIcon v-else-if="item.kind === 'MEAL'" />
                <ActivityIcon v-else-if="item.kind === 'WORKOUT'" />
              </span>
              <span v-if="item.kind === 'BODY'" class="archive-row__content">
                <small>身体</small>
                <strong>{{ item.body.weight }} kg</strong>
                <span v-if="item.body.bodyFat !== null || item.body.waist !== null">
                  {{
                    [
                      item.body.bodyFat !== null ? `体脂 ${item.body.bodyFat}%` : '',
                      item.body.waist !== null ? `腰围 ${item.body.waist} cm` : '',
                    ]
                      .filter(Boolean)
                      .join(' · ')
                  }}
                </span>
              </span>
              <span v-else-if="item.kind === 'MEAL'" class="archive-row__content">
                <small>饮食 · {{ mealLabels[item.meal.type] }}</small>
                <strong>{{ item.meal.foods.map((food) => food.name).join('、') }}</strong>
                <span>
                  {{
                    item.meal.totalCalories === null
                      ? '未记录热量'
                      : `${item.meal.totalCalories} kcal`
                  }}
                </span>
              </span>
              <span v-else-if="item.kind === 'WORKOUT'" class="archive-row__content">
                <small>训练 · {{ workoutLabels[item.workout.type] }}</small>
                <strong>{{ item.workout.name }}</strong>
                <span>
                  {{ item.workout.durationMinutes }} 分钟<template
                    v-if="item.workout.calories !== null"
                  >
                    · {{ item.workout.calories }} kcal</template
                  >
                </span>
              </span>
              <span v-else class="archive-row__content">
                <small>照片 · {{ photoLabels[item.photo.type] }}</small>
                <strong>{{ item.photo.note || `${photoLabels[item.photo.type]}记录` }}</strong>
                <span>{{ dayjs(item.photo.recordedAt).format('YYYY年M月D日') }}</span>
              </span>
            </button>
          </div>
        </section>
      </div>
      <Button
        v-if="hasMore && !loading"
        class="archive-more"
        variant="outline"
        block
        :loading="loadingMore"
        @click="loadRecords(false, true)"
        >继续加载历史记录</Button
      >
      <p v-if="!loading && !loadError && total" class="archive-count">
        已显示 {{ items.length }} / {{ total }} 条{{ hasMore ? '，继续加载可查看更早的记录' : '' }}
      </p>
    </Loading>
  </main>
</template>

<style scoped lang="scss">
.archive-dates {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 14px;
  label {
    display: grid;
    min-width: 0;
    flex: 1;
    gap: 5px;
    color: var(--color-text-secondary);
    font-size: 0.6875rem;
  }
  input {
    width: 100%;
    min-width: 0;
    height: 40px;
    padding: 0 7px;
    border: 1px solid var(--color-border);
    border-radius: 6px;
    color: var(--color-text-primary);
    background: var(--color-surface);
    font-size: 0.8125rem;
  }
  button {
    min-height: 40px;
    padding: 0 4px;
    margin-top: 16px;
    color: var(--color-accent-text);
    background: transparent;
    border: 0;
    font-size: 0.75rem;
  }
}
.archive-more {
  margin-top: 18px;
}
.archive-count {
  text-align: center;
  color: var(--color-text-tertiary);
  font-size: 0.75rem;
  line-height: 1.6;
}

.archive-list {
  display: grid;
  gap: 24px;
}
.archive-group > header {
  display: flex;
  align-items: baseline;
  gap: 8px;
  margin-bottom: 9px;
  strong {
    font-size: 1rem;
  }
  span {
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
}
.archive-group__body {
  overflow: hidden;
  background: var(--color-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--border-radius-md);
}
.archive-row {
  display: flex;
  width: 100%;
  align-items: center;
  gap: 10px;
  padding: 14px 12px;
  text-align: left;
  background: transparent;
  border: 0;
  + .archive-row {
    border-top: 1px solid var(--color-border);
  }
  &__time {
    width: 36px;
    flex: none;
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
    font-variant-numeric: tabular-nums;
  }
  &__icon {
    display: grid;
    width: 40px;
    height: 40px;
    flex: none;
    place-items: center;
    color: var(--color-text-primary);
    background: var(--color-primary-light);
    border-radius: 9px;

    &--photo {
      overflow: hidden;
      background: var(--color-surface-muted);

      img {
        width: 100%;
        height: 100%;
        object-fit: cover;
      }
    }
  }
  &__content {
    display: grid;
    min-width: 0;
    flex: 1;
    gap: 2px;
    small {
      color: var(--color-text-tertiary);
      font-size: 0.75rem;
    }
    strong,
    span {
      overflow: hidden;
      text-overflow: ellipsis;
      white-space: nowrap;
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
.archive-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}
</style>
