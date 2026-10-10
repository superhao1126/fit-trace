<script setup lang="ts">
import type { DashboardOverview, MealType } from '@fit-trace/shared';
import dayjs from 'dayjs';
import { Button, Skeleton } from 'tdesign-mobile-vue';
import {
  ActivityIcon,
  ChevronRightIcon,
  ForkIcon,
  MeasurementIcon,
  CameraIcon,
} from 'tdesign-icons-vue-next';
import { computed, onActivated, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
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
const displayName = computed(
  () => auth.user?.nickname || auth.user?.email?.split('@')[0] || '朋友',
);
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
  <main class="view-page dashboard">
    <header class="primary-header dashboard-header">
      <div>
        <h1>{{ isToday ? '今天' : '日记' }}</h1>
        <p>{{ todayLabel }}</p>
      </div>
      <button class="avatar" type="button" aria-label="我的资料" @click="router.push('/profile')">
        {{ displayName.slice(0, 1).toUpperCase() }}
      </button>
    </header>

    <DiaryDateStrip v-model="selectedDate" />
    <Skeleton v-if="loading" :loading="true" animation="gradient" :row-col="[1, 1, 1]" />
    <section v-if="errorMessage" class="load-error" role="alert">
      <span>{{ errorMessage }}</span
      ><Button variant="text" size="small" :disabled="loading" @click="loadOverview(!!overview)"
        >重试</Button
      >
    </section>

    <template v-if="!loading && overview">
      <section class="surface-card today-overview" aria-label="当天概览">
        <div class="section-heading">
          <h2>当天概览</h2>
          <span>{{ hasTodayRecords ? '已开始记录' : '还没有当天记录' }}</span>
        </div>
        <div class="overview-grid">
          <button
            type="button"
            @click="todayBody ? router.push(`/body/${todayBody.id}`) : addRecord('/body/create')"
          >
            <span>{{ todayBody ? '当天体重' : latestBody ? '最近体重' : '体重' }}</span
            ><strong>{{ (todayBody ?? latestBody)?.weight ?? '—' }}<small>kg</small></strong
            ><span>{{
              todayBody
                ? '当天已记录'
                : latestBody
                  ? dayjs(latestBody.recordedAt).format('M月D日')
                  : '尚未记录'
            }}</span>
          </button>
          <button type="button" @click="openDayArchive">
            <span>已记录热量</span><strong>{{ todayCalories ?? '—' }}<small>kcal</small></strong
            ><span>{{ todayMealCount }} 餐</span>
          </button>
          <button type="button" @click="openDayArchive">
            <span>训练时长</span><strong>{{ todayWorkoutMinutes }}<small>分钟</small></strong
            ><span>{{ todayWorkoutCount }} 次训练</span>
          </button>
        </div>
        <button v-if="isToday" class="goal-row" type="button" @click="router.push('/goal')">
          <span v-if="auth.user?.goal"
            >{{ goalLabels[auth.user.goal.type] }} · 目标
            {{ auth.user.goal.targetWeight }} kg<small>{{
              goalRemaining || goalDateText
            }}</small></span
          ><span v-else>设置体重目标<small>给记录一个方向</small></span
          ><ChevronRightIcon />
        </button>
      </section>

      <nav class="quick-actions" aria-label="快速记录">
        <button type="button" @click="addRecord('/body/create')">
          <MeasurementIcon /><span>记体重</span>
        </button>
        <button type="button" @click="addRecord('/meals/create')">
          <ForkIcon /><span>记饮食</span>
        </button>
        <button type="button" @click="addRecord('/workouts/create')">
          <ActivityIcon /><span>记训练</span>
        </button>
        <button type="button" @click="addRecord('/photos')">
          <CameraIcon /><span>记照片</span>
        </button>
      </nav>

      <section v-if="!hasTodayRecords" class="start-today">
        <h2>{{ isToday ? '从今天的一次记录开始' : '补上一条当天记录' }}</h2>
        <p>体重、饮食、训练或照片，选一项记下来</p>
      </section>
      <section v-else class="content-section">
        <div class="section-heading">
          <h2>当天记录</h2>
          <button type="button" @click="openDayArchive">查看当天 <ChevronRightIcon /></button>
        </div>
        <div class="surface-card diary-list">
          <button
            v-if="todayBody"
            class="diary-row"
            type="button"
            @click="router.push(`/body/${todayBody.id}`)"
          >
            <MeasurementIcon /><span class="diary-row__main"
              ><strong>身体数据</strong
              ><span
                >{{ dayjs(todayBody.recordedAt).format('HH:mm')
                }}<template v-if="weightChange !== null">
                  · 较上次 {{ weightChange > 0 ? '+' : ''
                  }}{{ weightChange.toFixed(1) }} kg</template
                ></span
              ></span
            ><span class="diary-row__value">{{ todayBody.weight }}<small>kg</small></span>
          </button>
          <button
            v-for="meal in todayMeals"
            :key="meal.id"
            class="diary-row"
            type="button"
            @click="router.push(`/meals/${meal.id}`)"
          >
            <ForkIcon /><span class="diary-row__main"
              ><strong>{{ mealLabels[meal.type] }}</strong
              ><span>{{ meal.foodNames.join('、') }}</span></span
            ><span class="diary-row__value"
              >{{ meal.totalCalories ?? '—' }}<small>kcal</small></span
            >
          </button>
          <button
            v-if="todayMealCount > todayMeals.length"
            class="more-row"
            type="button"
            @click="openDayArchive"
          >
            查看全部 {{ todayMealCount }} 餐 <ChevronRightIcon />
          </button>
          <button
            v-for="workout in todayWorkouts"
            :key="workout.id"
            class="diary-row"
            type="button"
            @click="router.push(`/workouts/${workout.id}`)"
          >
            <ActivityIcon /><span class="diary-row__main"
              ><strong>{{ workout.name }}</strong
              ><span>{{ dayjs(workout.startedAt).format('HH:mm') }} · 训练</span></span
            ><span class="diary-row__value">{{ workout.durationMinutes }}<small>分钟</small></span>
          </button>
          <button
            v-if="todayWorkoutCount > todayWorkouts.length"
            class="more-row"
            type="button"
            @click="openDayArchive"
          >
            查看全部 {{ todayWorkoutCount }} 次训练 <ChevronRightIcon />
          </button>
          <button
            v-for="photo in todayPhotos"
            :key="photo.id"
            class="diary-row"
            type="button"
            @click="router.push(`/photos/${photo.id}`)"
          >
            <img class="diary-thumb" :src="photo.imageUrl" :alt="photoLabels[photo.type]" /><span
              class="diary-row__main"
              ><strong>{{ photoLabels[photo.type] }}照片</strong
              ><span>{{ dayjs(photo.recordedAt).format('HH:mm') }} · 身材记录</span></span
            ><ChevronRightIcon />
          </button>
          <button
            v-if="(overview.today.photoCount ?? 0) > todayPhotos.length"
            class="more-row"
            type="button"
            @click="openDayArchive"
          >
            查看全部 {{ overview.today.photoCount }} 张照片 <ChevronRightIcon />
          </button>
        </div>
      </section>

      <section v-if="recentTrend && hasRecentRecords" class="content-section">
        <div class="section-heading">
          <h2>近 7 天</h2>
          <button type="button" @click="router.push('/trends')">
            查看趋势 <ChevronRightIcon />
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
.diary-thumb {
  width: 36px;
  height: 44px;
  object-fit: cover;
  border-radius: 5px;
}
.dashboard-header .avatar {
  display: grid;
  width: 40px;
  height: 40px;
  place-items: center;
  border: 0;
  border-radius: 50%;
  background: var(--color-primary-light);
  color: var(--color-accent-text);
  font-size: 1rem;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 16px;
  h2 {
    margin: 0;
    font-size: 0.9375rem;
    font-weight: 600;
  }
  > span,
  button {
    color: var(--color-text-tertiary);
    font-size: 0.75rem;
  }
  button {
    display: flex;
    align-items: center;
    min-height: 32px;
    padding: 0;
    border: 0;
    background: transparent;
  }
}
.today-overview {
  padding: 18px 16px 0;
}
.overview-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
  margin-bottom: 20px;
  button {
    display: grid;
    min-width: 0;
    gap: 8px;
    padding: 0;
    text-align: left;
    border: 0;
    background: transparent;
    color: var(--color-text-primary);
  }
  span {
    color: var(--color-text-secondary);
    font-size: 0.75rem;
  }
  strong {
    font-size: 1.5rem;
    font-weight: 550;
    font-variant-numeric: tabular-nums;
    line-height: 1.4;
    white-space: nowrap;
  }
  small {
    margin-left: 3px;
    font-size: 0.625rem;
    font-weight: 400;
    color: var(--color-text-tertiary);
  }
}
.goal-row {
  display: flex;
  width: 100%;
  min-height: 54px;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 12px 0;
  color: var(--color-text-secondary);
  text-align: left;
  background: transparent;
  border: 0;
  border-top: 1px solid var(--color-border);
  font-size: 0.8125rem;
  small {
    display: block;
    margin-top: 4px;
    color: var(--color-text-tertiary);
    font-size: 0.6875rem;
  }
}
.quick-actions {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 6px;
  margin: 16px 0 24px;
  button {
    display: flex;
    min-height: 60px;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 8px;
    border: 1px solid var(--color-border);
    border-radius: var(--border-radius-sm);
    background: var(--color-surface);
    color: var(--color-text-primary);
    font-size: 0.8125rem;
  }
  svg {
    color: var(--color-accent-text);
    font-size: 20px;
  }
}
.start-today {
  padding: 12px 0 24px;
  h2 {
    margin: 0 0 8px;
    font-size: 0.9375rem;
    font-weight: 500;
  }
  p {
    margin: 0;
    color: var(--color-text-secondary);
    font-size: 0.8125rem;
    line-height: 1.7;
  }
}
.content-section {
  margin: 24px 0;
}
.diary-row {
  display: flex;
  width: 100%;
  min-height: 72px;
  align-items: center;
  gap: 12px;
  padding: 14px;
  border: 0;
  background: transparent;
  color: var(--color-text-primary);
  text-align: left;
  + .diary-row {
    border-top: 1px solid var(--color-border);
  }
  > svg {
    flex: none;
    color: var(--color-text-tertiary);
    font-size: 19px;
  }
  &__main {
    display: grid;
    min-width: 0;
    flex: 1;
    gap: 5px;
    strong {
      font-size: 0.875rem;
      font-weight: 500;
    }
    span {
      overflow: hidden;
      color: var(--color-text-secondary);
      font-size: 0.75rem;
      text-overflow: ellipsis;
      white-space: nowrap;
    }
  }
  &__value {
    font-size: 0.9375rem;
    font-variant-numeric: tabular-nums;
    white-space: nowrap;
    small {
      display: block;
      color: var(--color-text-tertiary);
      font-size: 0.625rem;
      text-align: right;
    }
  }
}
.more-row {
  display: flex;
  width: 100%;
  min-height: 44px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-top: 1px solid var(--color-border);
  color: var(--color-accent-text);
  background: transparent;
  font-size: 0.8125rem;
}
.trend-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px 8px;
  padding: 16px 0;
  border-top: 1px solid var(--color-border);
  border-bottom: 1px solid var(--color-border);
  > div {
    display: grid;
    gap: 8px;
  }
  span,
  small,
  p {
    color: var(--color-text-secondary);
    font-size: 0.75rem;
  }
  strong {
    font-size: 1rem;
    font-weight: 500;
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
  color: var(--color-text-secondary);
  font-size: 0.8125rem;
}
@media (max-width: 360px) {
  .overview-grid strong {
    font-size: 1.25rem;
  }
  .today-overview {
    padding-right: 12px;
    padding-left: 12px;
  }
}
</style>
