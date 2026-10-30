<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow, onReachBottom } from '@dcloudio/uni-app';
import type { WorkoutRecord } from '@fit-trace/shared';
import { request } from '../../session';
import { dateTime, exerciseSummary, monthRange, workoutNames } from '../../presentation';
import FitShell from '../../components/FitShell.vue';
import FitState from '../../components/FitState.vue';
const open = (id: string) =>
  uni.navigateTo({ url: '/pages/history/detail?id=' + encodeURIComponent(id) });
const records = ref<WorkoutRecord[]>([]),
  page = ref(1),
  more = ref(true),
  busy = ref(false),
  error = ref(''),
  filter = ref('all'),
  limit = ref(20);
const date = new Date();
const month = ref(date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0'));
const filters = [
  { value: 'all', label: '全部' },
  { value: 'strength', label: '力量' },
  { value: 'cardio', label: '有氧' },
];
const filtered = computed(() =>
  records.value.filter(
    (r) =>
      filter.value === 'all' ||
      (filter.value === 'strength'
        ? r.type === 'STRENGTH'
        : ['CARDIO', 'RUNNING', 'CYCLING', 'SWIMMING'].includes(r.type)),
  ),
);
const visible = computed(() => filtered.value.slice(0, limit.value));
const minutes = computed(() => records.value.reduce((n, r) => n + r.durationMinutes, 0));
const canMore = computed(() => more.value || limit.value < filtered.value.length);
async function load(reset = false) {
  if (busy.value) return;
  busy.value = true;
  try {
    const next = reset ? 1 : page.value;
    const rows = await request<WorkoutRecord[]>(
      '/workouts' + monthRange(month.value) + '&page=' + next + '&pageSize=100',
    );
    records.value = reset ? rows : [...records.value, ...rows];
    page.value = next + 1;
    more.value = rows.length === 100;
    if (reset) limit.value = 20;
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    busy.value = false;
  }
}
async function loadMore() {
  if (busy.value) return;
  if (limit.value < filtered.value.length) limit.value += 20;
  else if (more.value) {
    await load();
    limit.value += 20;
  }
}
function changeMonth(e: { detail: { value: string } }) {
  if (busy.value) return;
  month.value = e.detail.value;
  records.value = [];
  void load(true);
}
onShow(() => {
  void load(true);
});
onReachBottom(() => {
  if (canMore.value) void loadMore();
});
</script>
<template>
  <FitShell title="训练记录" tab="history">
    <view class="section-intro"
      ><view class="title">每一次，都算数</view><view class="sub">训练历史</view></view
    >
    <view class="row filters"
      ><view v-for="f in filters" :key="f.value" class="filter-item"
        ><t-button
          :theme="filter === f.value ? 'primary' : 'default'"
          :variant="filter === f.value ? 'light' : 'outline'"
          shape="round"
          block
          @click="
            filter = f.value;
            limit = 20;
          "
          >{{ f.label }}</t-button
        ></view
      ></view
    >
    <view class="card tint month-card"
      ><view class="between"
        ><text>{{ month.replace('-', '年') }}月</text
        ><picker mode="date" fields="month" :value="month" :disabled="busy" @change="changeMonth"
          ><text class="link">切换月份 ›</text></picker
        ></view
      ><view class="heading spaced"
        >{{ records.length }}{{ more ? '+' : '' }} 次训练 · {{ minutes
        }}{{ more ? '+' : '' }} 分钟</view
      ></view
    >
    <FitState
      :loading="busy && !records.length"
      :error="error"
      :empty="
        !busy && !visible.length && !more
          ? '这个月还没有' +
            (filter === 'strength' ? '力量' : filter === 'cardio' ? '有氧' : '') +
            '训练记录'
          : ''
      "
      @retry="load(true)"
    />
    <view v-for="r in visible" :key="r.id" class="card record-card" @tap="open(r.id)"
      ><view class="heading"
        >{{ r.name
        }}<text v-if="!r.name.includes(workoutNames[r.type])" class="record-type">
          · {{ workoutNames[r.type] }}</text
        ></view
      ><view class="caption">{{ dateTime(r.startedAt) }}</view
      ><view class="sub">{{ r.durationMinutes }} 分钟 · {{ exerciseSummary(r) }}</view></view
    >
    <view v-if="canMore" class="spaced"
      ><t-button theme="primary" variant="text" block :loading="busy" @click="loadMore"
        >查看更多记录</t-button
      ></view
    >
    <view v-else-if="records.length" class="caption spaced">已显示本月全部记录</view>
  </FitShell>
</template>
<style scoped>
.filters {
  gap: 12px;
}
.filter-item {
  width: 100px;
}
.month-card {
  color: #229d60;
  font-size: 13px;
  padding: 14px 20px 18px;
}
.month-card .spaced {
  margin-top: 6px;
}
.record-card {
  padding: 18px;
}
.record-card .heading {
  line-height: 1.45;
  margin-bottom: 6px;
}
.record-card .sub {
  margin-top: 6px;
}
.record-type {
  font-size: 15px;
}
</style>
