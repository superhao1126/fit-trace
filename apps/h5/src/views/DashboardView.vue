<script setup lang="ts">
import type { DashboardOverview, MealType, WorkoutType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Skeleton } from 'tdesign-mobile-vue';
import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import UiButton from '@/components/ui/UiButton.vue';
import UiPageHeader from '@/components/ui/UiPageHeader.vue';
import UiIcon from '@/components/ui/UiIcon.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
import UiDateSheet from '@/components/ui/UiDateSheet.vue';
import DiaryDateStrip from '@/components/DiaryDateStrip.vue';
import { recordDateTime } from '@/utils/record-date';
import { getDashboard } from '@/api/dashboard';
import { useAuthStore } from '@/stores/auth';

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
const auth = useAuthStore();
const router = useRouter();
const route = useRoute();
const selectedDate = ref(dayjs(recordDateTime(route.query.date)).format('YYYY-MM-DD'));
const isToday = computed(() => selectedDate.value === dayjs().format('YYYY-MM-DD'));
const todayPhotos = computed(() => overview.value?.today.photoPreview ?? []);
const todayWorkouts = computed(() => overview.value?.today.workoutPreview ?? []);
const photoLabels = { FRONT: '正面', SIDE: '侧面', BACK: '背面', OTHER: '其他' } as const;
function openDayArchive() {
  void router.push({ path: '/archive', query: { date: selectedDate.value } });
}

const loading = ref(true);
const overview = ref<DashboardOverview | null>(null);
const errorMessage = ref('');
const calendarVisible = ref(false);
const todayLabel = computed(() => {
  const weekdays = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];
  const date = dayjs(selectedDate.value);
  return `${date.format('M月D日')} ${weekdays[date.day()]}`;
});
const latestBody = computed(() => overview.value?.latestBodyRecord ?? null);
const todayBody = computed(() => overview.value?.today.bodyRecord ?? null);
const weightChange = computed(() => overview.value?.bodyChanges.weight ?? null);
const recentTrend = computed(() => overview.value?.recentTrend);
const recentWeightText = computed(() => {
  const change = recentTrend.value?.weightChange;
  if (change === null || change === undefined) return '—';
  if (change === 0) return '持平';
  return `${change > 0 ? '+' : '−'}${Number(Math.abs(change).toFixed(2))} kg`;
});
const todayMeals = computed(() => overview.value?.today.mealPreview ?? []);
const todayMealCount = computed(() => overview.value?.today.mealCount ?? 0);
const todayCalories = computed(() => overview.value?.today.calories ?? null);
const todayWorkoutCount = computed(() => overview.value?.today.workoutCount ?? 0);
const todayWorkoutMinutes = computed(() => overview.value?.today.workoutMinutes ?? 0);
const hasTodayRecords = computed(() =>
  Boolean(
    todayBody.value ||
    todayMealCount.value ||
    todayWorkoutCount.value ||
    overview.value?.today.photoCount,
  ),
);
const hasRecentRecords = computed(() =>
  Boolean(
    recentTrend.value &&
    (recentTrend.value.bodyRecordedDays ||
      recentTrend.value.mealRecordedDays ||
      recentTrend.value.workoutCount),
  ),
);
const goalLabels = {
  LOSE_FAT: '减脂',
  GAIN_MUSCLE: '增肌',
  MAINTAIN: '保持',
} as const;
const goalRemaining = computed(() => {
  const goal = auth.user?.goal;
  const current = latestBody.value?.weight;
  if (!goal || current === undefined) return null;
  const difference = Math.abs(current - goal.targetWeight);
  if (difference < 0.05) return '已经达到目标体重';
  if (goal.type === 'MAINTAIN') return `与目标相差 ${difference.toFixed(1)} kg`;
  return `距离目标还差 ${difference.toFixed(1)} kg`;
});
const goalDateText = computed(() => {
  const targetDate = auth.user?.goal?.targetDate;
  if (!targetDate) return '按自己的节奏推进';
  const days = dayjs(targetDate).startOf('day').diff(dayjs().startOf('day'), 'day');
  if (days < 0) return '目标日期已到，可重新调整';
  if (days === 0) return '目标日期是今天';
  return `还有 ${days} 天`;
});
function addRecord(path: string): void {
  void router.push({ path, query: { returnTo: '/dashboard', date: selectedDate.value } });
}

defineOptions({ name: 'DashboardView' });

let requestId = 0;
async function loadOverview(silent = false): Promise<void> {
  const request = ++requestId;
  const date = selectedDate.value;
  if (!silent) {
    loading.value = true;
    if (overview.value?.date !== date) overview.value = null;
  }
  errorMessage.value = '';
  try {
    const result = await getDashboard(date);
    if (request !== requestId) return;
    overview.value = result;
  } catch {
    if (request !== requestId) return;
    errorMessage.value = overview.value
      ? '刷新失败，当前展示的是上次加载的数据'
      : '首页加载失败，请稍后重试';
  } finally {
    if (request === requestId) loading.value = false;
  }
}

watch(selectedDate, () => {
  void router.replace({ path: '/dashboard', query: { date: selectedDate.value } });
  void loadOverview();
});
watch(
  () => route.query.date,
  (date) => {
    if (route.path === '/dashboard' && typeof date === 'string')
      selectedDate.value = dayjs(recordDateTime(date)).format('YYYY-MM-DD');
  },
);
onMounted(() => void loadOverview());
// 从别的 tab 切回来时静默刷新：保留原内容，不再闪加载态
let activated = false;
onActivated(() => {
  if (!activated) {
    activated = true;
    return;
  }
  void loadOverview(true);
});
</script>

<template>
  <main class="view-page ui-page dashboard">
    <UiPageHeader :title="isToday ? '今天' : '日记'" :subtitle="todayLabel">
      <template #action
        ><UiButton kind="text" aria-label="选择日记日期" @click="calendarVisible = true"
          >日历</UiButton
        ></template
      >
    </UiPageHeader>
    <UiDateSheet v-model:visible="calendarVisible" v-model="selectedDate" />
    <DiaryDateStrip v-model="selectedDate" />
    <Skeleton v-if="loading" :loading="true" animation="gradient" :row-col="[1, 1, 1]" />
    <section v-if="errorMessage" class="load-error" role="alert">
      <span>{{ errorMessage }}</span
      ><UiButton kind="text" :disabled="loading" @click="loadOverview(!!overview)">重试</UiButton>
    </section>

    <template v-if="!loading && overview">
      <section class="surface-card today-overview" aria-label="当天概览">
        <div class="overview-grid">
          <button
            type="button"
            @click="todayBody ? router.push(`/body/${todayBody.id}`) : addRecord('/body/create')"
          >
            <span>{{
              todayBody
                ? '体重'
                : latestBody
                  ? `最近体重 · ${dayjs(latestBody.recordedAt).format('M/D')}`
                  : '体重'
            }}</span
            ><strong>{{ (todayBody ?? latestBody)?.weight ?? '—' }}<small>kg</small></strong>
          </button>
          <button type="button" @click="openDayArchive">
            <span>已记录热量</span><strong>{{ todayCalories ?? '—' }}<small>kcal</small></strong>
          </button>
          <button type="button" @click="openDayArchive">
            <span>训练时长</span><strong>{{ todayWorkoutMinutes }}<small>分钟</small></strong>
          </button>
        </div>
        <button v-if="isToday" class="goal-row" type="button" @click="router.push('/goal')">
          <span v-if="auth.user?.goal"
            >{{ goalLabels[auth.user.goal.type] }} · 目标 {{ auth.user.goal.targetWeight }} kg</span
          ><span v-else>设置体重目标</span>
          <small>{{ auth.user?.goal ? goalRemaining || goalDateText : '给记录一个方向' }}</small>
        </button>
      </section>

      <nav class="quick-actions" aria-label="快速记录">
        <button type="button" @click="addRecord('/body/create')">
          <span class="quick-actions__icon quick-actions__icon--body"><UiIcon name="body" /></span
          ><span>记体重</span>
        </button>
        <button type="button" @click="addRecord('/meals/create')">
          <span class="quick-actions__icon quick-actions__icon--meal"><UiIcon name="meal" /></span
          ><span>记饮食</span>
        </button>
        <button type="button" @click="addRecord('/workouts/create')">
          <span class="quick-actions__icon quick-actions__icon--workout"
            ><UiIcon name="workout" /></span
          ><span>记训练</span>
        </button>
        <button type="button" @click="addRecord('/photos')">
          <span class="quick-actions__icon quick-actions__icon--photo"><UiIcon name="photo" /></span
          ><span>记照片</span>
        </button>
      </nav>

      <section v-if="!hasTodayRecords" class="start-today">
        <h2>{{ isToday ? '从今天的一次记录开始' : '补上一条当天记录' }}</h2>
        <p>体重、饮食、训练或照片，选一项记下来</p>
      </section>
      <section v-else class="content-section">
        <div class="section-heading">
          <h2>当天记录</h2>
          <button type="button" @click="openDayArchive">查看当天 <UiIcon name="right" /></button>
        </div>
        <div class="diary-list">
          <UiRecordRow
            v-if="todayBody"
            kind="body"
            title="身体数据"
            :subtitle="`${dayjs(todayBody.recordedAt).format('HH:mm')}${weightChange === null ? '' : ` · 较上次 ${weightChange > 0 ? '+' : ''}${weightChange.toFixed(1)} kg`}`"
            :value="todayBody.weight"
            unit="kg"
            @click="router.push(`/body/${todayBody.id}`)"
          />
          <UiRecordRow
            v-for="meal in todayMeals"
            :key="meal.id"
            kind="meal"
            :title="mealLabels[meal.type]"
            :subtitle="`${dayjs(meal.recordedAt).format('HH:mm')} · ${meal.foodNames.join('、')}`"
            :value="meal.totalCalories ?? '—'"
            unit="kcal"
            @click="router.push(`/meals/${meal.id}`)"
          />
          <UiButton
            v-if="todayMealCount > todayMeals.length"
            kind="secondary"
            block
            @click="openDayArchive"
            >查看全部 {{ todayMealCount }} 餐</UiButton
          >
          <UiRecordRow
            v-for="workout in todayWorkouts"
            :key="workout.id"
            kind="workout"
            :workout-type="workout.type"
            :title="workout.name"
            :subtitle="`${dayjs(workout.startedAt).format('HH:mm')} · ${workoutLabels[workout.type]}`"
            :value="workout.durationMinutes"
            unit="分钟"
            @click="router.push(`/workouts/${workout.id}`)"
          />
          <UiButton
            v-if="todayWorkoutCount > todayWorkouts.length"
            kind="secondary"
            block
            @click="openDayArchive"
            >查看全部 {{ todayWorkoutCount }} 次训练</UiButton
          >
          <UiRecordRow
            v-for="photo in todayPhotos"
            :key="photo.id"
            kind="photo"
            :title="`${photoLabels[photo.type]}照片`"
            :subtitle="`${dayjs(photo.recordedAt).format('HH:mm')} · 身材记录`"
            :image="photo.imageUrl"
            @click="router.push(`/photos/${photo.id}`)"
          />
          <UiButton
            v-if="(overview.today.photoCount ?? 0) > todayPhotos.length"
            kind="secondary"
            block
            @click="openDayArchive"
            >查看全部 {{ overview.today.photoCount }} 张照片</UiButton
          >
        </div>
      </section>

      <section v-if="recentTrend && hasRecentRecords" class="content-section">
        <div class="section-heading">
          <h2>近 7 天</h2>
          <button type="button" @click="router.push('/trends')">
            查看趋势 <UiIcon name="right" />
          </button>
        </div>
        <div class="trend-summary">
          <div>
            <span>体重变化</span><strong>{{ recentWeightText }}</strong>
          </div>
          <div>
            <span>饮食记录</span
            ><strong>{{ recentTrend.mealRecordedDays }} <small>天</small></strong>
          </div>
          <div>
            <span>训练时长</span
            ><strong>{{ recentTrend.workoutMinutes }} <small>分钟</small></strong>
          </div>
          <p>
            {{ dayjs(recentTrend.from).format('M/D') }}–{{ dayjs(recentTrend.to).format('M/D') }} ·
            {{
              recentTrend.bodyRecordedDays < 2
                ? '至少记录两天体重后可查看变化'
                : `体重按每天最后一次记录计算 · 已记录 ${recentTrend.bodyRecordedDays} 天`
            }}
          </p>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped lang="scss">
.today-overview {
  padding: 20px 16px 0;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 16px;
  button {
    display: grid;
    min-width: 0;
    gap: 8px;
    padding: 0;
    border: 0;
    text-align: left;
    color: var(--ui-text-primary);
    background: transparent;
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
  strong {
    display: flex;
    flex-direction: column;
    align-items: flex-start;
    gap: 5px;
    font-size: 29px;
    line-height: 1.2;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
  }
  small {
    font-size: 11px;
    font-weight: 400;
    color: var(--ui-text-secondary);
  }
  button > span:last-child {
    font-size: 10px;
  }
}
.goal-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  min-height: 50px;
  padding: 12px 0;
  border: 0;
  border-top: 1px solid var(--ui-border);
  text-align: left;
  background: transparent;
  color: var(--ui-text-secondary);
  font-size: 12px;
  small {
    display: inline;
    font-size: 11px;
    color: var(--ui-primary);
  }
  svg {
    flex: none;
  }
}
.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px;
  margin: 20px 0 28px;
  button {
    display: grid;
    place-items: center;
    align-content: center;
    gap: 8px;
    min-width: 0;
    min-height: 80px;
    padding: 10px 4px;
    border: 0;
    border-radius: 20px;
    background: var(--color-surface);
    color: var(--ui-text-primary);
    font-size: 12px;
  }
  &__icon {
    display: grid;
    place-items: center;
    width: 32px;
    height: 32px;
    border-radius: 12px;
    &--body {
      color: var(--ui-body-foreground);
      background: var(--ui-body-background);
    }
    &--meal {
      color: var(--ui-meal-foreground);
      background: var(--ui-meal-background);
    }
    &--workout {
      color: var(--ui-workout-foreground);
      background: var(--ui-workout-background);
    }
    &--photo {
      color: var(--ui-photo-foreground);
      background: var(--ui-photo-background);
    }
  }
}
.start-today {
  margin-top: 32px;
  padding: 24px 20px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  h2 {
    margin: 0 0 12px;
    font-size: 18px;
    font-weight: 600;
  }
  p {
    margin: 0;
    font-size: 13px;
    line-height: 1.8;
    color: var(--ui-text-secondary);
  }
}
.content-section {
  margin: 28px 0;
}
.section-heading .ui-icon {
  width: 16px;
  height: 16px;
}
.section-heading button {
  display: flex;
  align-items: center;
  min-height: 32px;
  padding: 0;
  background: transparent;
  border: 0;
  color: var(--ui-text-secondary);
  font-size: 12px;
}
.diary-list {
  display: grid;
  gap: 10px;
}
.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 8px;
  padding: 20px 16px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  > div {
    display: grid;
    gap: 8px;
  }
  span,
  small,
  p {
    color: var(--ui-text-secondary);
    font-size: 11px;
  }
  strong {
    font-size: 16px;
    font-weight: 600;
  }
  small {
    font-weight: 400;
  }
  p {
    grid-column: 1/-1;
    margin: 0;
    line-height: 1.6;
  }
}
.load-error {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  color: var(--ui-text-secondary);
  font-size: 13px;
}
@media (max-width: 360px) {
  .overview-grid strong {
    font-size: 25px;
  }
  .today-overview {
    padding-right: 14px;
    padding-left: 14px;
  }
  .quick-actions {
    gap: 6px;
  }
}
</style>
