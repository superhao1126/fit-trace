<script setup lang="ts">
import UiPullRefresh from '@/components/ui/UiPullRefresh.vue';
import { uiToast } from '@/components/ui/toast';
import UiLoading from '@/components/ui/UiLoading.vue';
import UiPageHeader from '@/components/ui/UiPageHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import EmptyState from '@/components/EmptyState.vue';
import type {
  BodyTrendData,
  BodyTrendDays,
  BodyTrendMetric,
  BodyTrendStats,
  InsightOverview,
} from '@fit-trace/shared';
import dayjs from 'dayjs';
import { BarChart, LineChart, type BarSeriesOption, type LineSeriesOption } from 'echarts/charts';
import {
  GridComponent,
  TooltipComponent,
  type GridComponentOption,
  type TooltipComponentOption,
} from 'echarts/components';
import { init, use, type ComposeOption, type EChartsType } from 'echarts/core';
import { CanvasRenderer } from 'echarts/renderers';
import { ChartLineIcon, RefreshIcon } from 'tdesign-icons-vue-next';
import { computed, nextTick, onActivated, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { getBodyTrends } from '@/api/body-records';
import { getInsightOverview } from '@/api/insights';
import { resolvedTheme } from '@/utils/theme';

interface MetricOption {
  key: BodyTrendMetric;
  label: string;
  unit: string;
}

type ChartOption = ComposeOption<
  BarSeriesOption | LineSeriesOption | GridComponentOption | TooltipComponentOption
>;

use([LineChart, BarChart, GridComponent, TooltipComponent, CanvasRenderer]);

const router = useRouter();
const ranges: BodyTrendDays[] = [7, 30, 90];
const metrics: MetricOption[] = [
  { key: 'weight', label: '体重', unit: 'kg' },
  { key: 'bodyFat', label: '体脂', unit: '%' },
  { key: 'waist', label: '腰围', unit: 'cm' },
];
const selectedRange = ref<BodyTrendDays>(7);
const selectedMetric = ref<BodyTrendMetric>('weight');
const trendData = ref<BodyTrendData | null>(null);
const insights = ref<InsightOverview | null>(null);
const loading = ref(true);
const requestPending = ref(false);
const errorMessage = ref('');
const insightError = ref(false);
const chartElement = ref<HTMLDivElement | null>(null);
const insightElement = ref<HTMLDivElement | null>(null);
let chart: EChartsType | null = null;
let insightChart: EChartsType | null = null;
let resizeObserver: ResizeObserver | null = null;

const metric = computed(
  () => metrics.find((item) => item.key === selectedMetric.value) ?? metrics[0],
);
const series = computed(() => trendData.value?.[selectedMetric.value] ?? null);
const stats = computed(() => series.value?.stats ?? null);
const hasInsightRecords = computed(() =>
  Boolean(
    insights.value &&
    (insights.value.weight.recordCount ||
      insights.value.meals.count ||
      insights.value.workouts.count),
  ),
);
const hasInsightChartData = computed(
  () =>
    insights.value?.series.some((point) => point.weight !== null || point.calories !== null) ??
    false,
);

function formatValue(value: number): string {
  return Number.isInteger(value) ? String(value) : value.toFixed(1);
}

function formatChange(value: number): string {
  return `${value > 0 ? '+' : ''}${formatValue(value)}`;
}

/** ECharts 画在 canvas 上，认不了 CSS 变量，这里取值后再用，深浅色切换时重绘 */
function token(name: string, fallback: string): string {
  const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
  return value || fallback;
}

const insightCards = computed(() => {
  const data = insights.value;
  if (!data) return [];
  return [
    {
      label: '日均已记录热量',
      value: data.calories.average === null ? '—' : String(data.calories.average),
      unit: 'kcal',
    },
    { label: '训练次数', value: String(data.workouts.count), unit: '次' },
    { label: '训练时长', value: String(data.workouts.totalMinutes), unit: '分钟' },
    { label: '记录饮食天数', value: String(data.meals.recordedDays), unit: `/${data.days} 天` },
  ];
});

const insightSummary = computed(() => {
  const data = insights.value;
  if (!data) return '';
  const sentences: string[] = [];

  if (data.weight.change !== null) {
    const direction = data.weight.change > 0 ? '上升' : data.weight.change < 0 ? '下降' : '持平';
    const amount = Math.abs(data.weight.change).toFixed(1);
    sentences.push(
      data.weight.change === 0
        ? `这 ${data.days} 天体重基本持平`
        : `这 ${data.days} 天体重${direction} ${amount} kg`,
    );
  } else {
    sentences.push(
      data.weight.recordCount ? '至少记录两天体重后可查看变化' : `这 ${data.days} 天还没有身体数据`,
    );
  }

  if (data.calories.average !== null) {
    sentences.push(
      `有记录的 ${data.calories.recordedDays} 天里，日均已记录食物热量约 ${data.calories.average} kcal`,
    );
  } else {
    sentences.push('饮食记录里还没有可统计的热量');
  }

  sentences.push(
    data.workouts.count > 0
      ? `完成了 ${data.workouts.count} 次训练，共 ${data.workouts.totalMinutes} 分钟`
      : '这段区间里还没有训练记录',
  );

  return sentences.join('。');
});

function createChartOption(): ChartOption {
  const points = series.value?.points ?? [];
  return {
    animationDuration: 450,
    grid: { top: 24, right: 12, bottom: 30, left: 44 },
    tooltip: {
      trigger: 'axis',
      borderWidth: 0,
      backgroundColor: token('--color-ink', '#111715'),
      textStyle: { color: token('--color-on-ink', '#fff') },
      formatter: (params) => {
        const item = Array.isArray(params) ? params[0] : params;
        const point = points[item?.dataIndex ?? 0];
        if (!point) return '';
        return `${dayjs(point.recordedAt).format('M月D日 HH:mm')}<br/>${metric.value.label}：${formatValue(point.value)} ${metric.value.unit}`;
      },
    },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: points.map((point) => dayjs(point.recordedAt).format('M/D')),
      axisLine: { lineStyle: { color: token('--color-chart-axis', '#dce2dc') } },
      axisTick: { show: false },
      axisLabel: {
        color: token('--color-chart-label', '#6a746f'),
        fontSize: 12,
        hideOverlap: true,
      },
    },
    yAxis: {
      type: 'value',
      scale: true,
      splitNumber: 3,
      axisLabel: { color: token('--color-chart-label', '#6a746f'), fontSize: 12 },
      splitLine: { lineStyle: { color: token('--color-chart-grid', '#edf0ed'), type: 'dashed' } },
    },
    series: [
      {
        type: 'line',
        data: points.map((point) => point.value),
        smooth: true,
        symbol: 'circle',
        symbolSize: 7,
        showSymbol: points.length < 15,
        lineStyle: { color: token('--ui-body-foreground', '#4b74a5'), width: 2.5 },
        itemStyle: {
          color: token('--ui-body-foreground', '#4b74a5'),
          borderColor: token('--ui-body-foreground', '#4b74a5'),
          borderWidth: 2,
        },
      },
    ],
  };
}

function createInsightOption(): ChartOption {
  const points = insights.value?.series ?? [];
  return {
    animationDuration: 450,
    grid: { top: 26, right: 42, bottom: 28, left: 44 },
    tooltip: {
      trigger: 'axis',
      borderWidth: 0,
      backgroundColor: token('--color-ink', '#111715'),
      textStyle: { color: token('--color-on-ink', '#fff') },
      formatter: (params) => {
        const items = Array.isArray(params) ? params : [params];
        const point = points[items[0]?.dataIndex ?? 0];
        if (!point) return '';
        const lines = [dayjs(point.date).format('M月D日')];
        if (point.weight !== null) lines.push(`体重：${point.weight} kg`);
        if (point.calories !== null) lines.push(`热量：${point.calories} kcal`);
        lines.push(`训练：${point.workoutMinutes} 分钟`);
        if (point.mealCount === 0) lines.push('饮食：未记录');
        return lines.join('<br/>');
      },
    },
    xAxis: {
      type: 'category',
      data: points.map((point) => dayjs(point.date).format('M/D')),
      axisLine: { lineStyle: { color: token('--color-chart-axis', '#dce2dc') } },
      axisTick: { show: false },
      axisLabel: {
        color: token('--color-chart-label', '#6a746f'),
        fontSize: 12,
        hideOverlap: true,
      },
    },
    yAxis: [
      {
        type: 'value',
        splitNumber: 3,
        axisLabel: { color: token('--color-chart-label', '#6a746f'), fontSize: 12 },
        splitLine: {
          lineStyle: { color: token('--color-chart-grid', '#edf0ed'), type: 'dashed' },
        },
      },
      {
        type: 'value',
        scale: true,
        splitNumber: 3,
        axisLabel: { show: false },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '已记录热量',
        type: 'bar',
        data: points.map((point) => point.calories),
        barMaxWidth: 10,
        itemStyle: {
          color: token('--ui-meal-background', '#faede1'),
          borderRadius: [3, 3, 0, 0],
        },
      },
      {
        name: '体重',
        type: 'line',
        yAxisIndex: 1,
        data: points.map((point) => point.weight),
        smooth: true,
        connectNulls: true,
        symbol: 'circle',
        symbolSize: 6,
        lineStyle: { color: token('--ui-body-foreground', '#4b74a5'), width: 2.5 },
        itemStyle: {
          color: token('--ui-body-foreground', '#4b74a5'),
          borderColor: token('--ui-body-foreground', '#4b74a5'),
          borderWidth: 2,
        },
      },
    ],
  };
}

async function renderCharts(): Promise<void> {
  await nextTick();

  if (!chartElement.value || !series.value?.points.length) {
    chart?.dispose();
    chart = null;
  } else {
    chart ??= init(chartElement.value);
    chart.setOption(createChartOption(), true);
    chart.resize();
  }

  if (!insightElement.value) {
    insightChart?.dispose();
    insightChart = null;
  } else if (insights.value) {
    insightChart ??= init(insightElement.value);
    insightChart.setOption(createInsightOption(), true);
    insightChart.resize();
  }
}

let requestId = 0;
async function loadTrends(silent = false): Promise<void> {
  const request = ++requestId;
  const range = selectedRange.value;
  requestPending.value = true;
  if (!silent) loading.value = true;
  errorMessage.value = '';
  try {
    const [trend, insight] = await Promise.all([
      getBodyTrends(range),
      getInsightOverview(range).catch(() => null),
    ]);
    if (request !== requestId) return;
    trendData.value = trend;
    if (insight !== null || !silent) insights.value = insight;
    insightError.value = insight === null;
  } catch {
    if (request !== requestId) return;
    if (!silent) {
      trendData.value = null;
      insights.value = null;
    }
    errorMessage.value = silent
      ? '刷新失败，当前展示的是上次加载的数据'
      : '趋势数据加载失败，请稍后重试';
  } finally {
    if (request === requestId) {
      loading.value = false;
      requestPending.value = false;
    }
  }
}

async function refreshPage(): Promise<void> {
  await loadTrends(true);
  if (errorMessage.value) uiToast.error(errorMessage.value);
  else if (insightError.value) uiToast.warning('趋势已更新，分析暂时无法刷新，请稍后重试');
}

async function selectRange(range: BodyTrendDays): Promise<void> {
  if (range === selectedRange.value) return;
  selectedRange.value = range;
  await loadTrends();
}

function statItems(value: BodyTrendStats) {
  return [
    { label: '当前值', value: formatValue(value.current) },
    { label: '起始值', value: formatValue(value.start) },
    { label: '变化值', value: formatChange(value.change), change: value.change },
    { label: '最高值', value: formatValue(value.max) },
    { label: '最低值', value: formatValue(value.min) },
    { label: '平均值', value: formatValue(value.average) },
  ];
}

watch([series, selectedMetric, insights, resolvedTheme], renderCharts, {
  flush: 'post',
});

onMounted(async () => {
  await loadTrends();
  resizeObserver = new ResizeObserver(() => {
    chart?.resize();
    insightChart?.resize();
  });
  for (const element of [chartElement.value, insightElement.value]) {
    if (element) resizeObserver.observe(element);
  }
});

defineOptions({ name: 'TrendsView' });

// 从别的 tab 切回来时静默刷新，避免图表区域闪一下加载态
let activated = false;
onActivated(() => {
  if (!activated) {
    activated = true;
    return;
  }
  void loadTrends(true);
});

onBeforeUnmount(() => {
  resizeObserver?.disconnect();
  chart?.dispose();
  insightChart?.dispose();
});
</script>

<template>
  <UiPullRefresh :refresh="refreshPage" :disabled="requestPending">
    <main class="view-page ui-page trends-page">
      <UiPageHeader title="趋势" subtitle="看清变化，找到自己的节奏"
        ><template #action
          ><UiButton kind="text" @click="router.push('/body/create')">记录</UiButton></template
        ></UiPageHeader
      >
      <UiTabs
        :model-value="selectedRange"
        :options="ranges.map((range) => ({ value: range, label: `${range} 天` }))"
        label="趋势时间范围"
        @update:model-value="selectRange($event as BodyTrendDays)"
      />
      <UiLoading
        class="page-loading"
        :class="{ 'page-loading--active': loading }"
        :loading="loading"
        text="正在计算趋势"
      >
        <section class="surface-card trend-card">
          <UiTabs
            :model-value="selectedMetric"
            :options="metrics.map((item) => ({ value: item.key, label: item.label }))"
            label="趋势指标"
            tone="body"
            @update:model-value="selectedMetric = $event as BodyTrendMetric"
          />

          <div v-if="errorMessage" class="error-state">
            <span>{{ errorMessage }}</span>
            <UiButton kind="secondary" @click="loadTrends()"> <RefreshIcon /> 重新加载 </UiButton>
          </div>

          <template v-if="series?.points.length && stats">
            <div class="trend-heading">
              <div>
                <span>{{ selectedRange }} 天{{ metric.label }}趋势</span>
                <strong
                  >{{ formatValue(stats.current) }} <small>{{ metric.unit }}</small></strong
                >
              </div>
              <span
                class="change-badge"
                :class="{ rise: stats.change > 0, fall: stats.change < 0 }"
              >
                {{ formatChange(stats.change) }} {{ metric.unit }}
              </span>
            </div>

            <div
              ref="chartElement"
              data-pull-refresh-ignore
              class="trend-chart"
              role="img"
              :aria-label="`${metric.label}趋势图`"
            />

            <section class="trend-details" aria-label="详细统计">
              <h2>详细统计</h2>
              <div class="stats-grid">
                <div v-for="item in statItems(stats)" :key="item.label">
                  <span>{{ item.label }}</span>
                  <strong
                    :class="{
                      rise: item.change !== undefined && item.change > 0,
                      fall: item.change !== undefined && item.change < 0,
                    }"
                  >
                    {{ item.value }} <small>{{ metric.unit }}</small>
                  </strong>
                </div>
              </div>
            </section>
          </template>

          <EmptyState
            v-else-if="!loading"
            title="暂无该指标数据"
            :description="`${selectedRange} 天内还没有${metric.label}记录，记录后会自动生成趋势`"
          >
            <template #image><ChartLineIcon class="empty-icon" /></template>
            <template #action>
              <UiButton @click="router.push('/body/create')">记录身体数据</UiButton>
            </template>
          </EmptyState>
        </section>

        <section class="content-section">
          <div class="section-heading">
            <h2>饮食与训练</h2>
            <span>同期饮食与训练</span>
          </div>

          <div v-if="insightError" class="insight-error">饮食与训练数据暂时无法加载</div>
          <template v-else-if="insights && hasInsightRecords">
            <div class="insight-grid">
              <div v-for="card in insightCards" :key="card.label">
                <span>{{ card.label }}</span>
                <strong
                  >{{ card.value }} <small>{{ card.unit }}</small></strong
                >
              </div>
            </div>
            <p class="insight-summary">
              {{ insightSummary }}。热量仅汇总已填写的食物，不代表完整全天摄入。
            </p>
            <div
              v-if="hasInsightChartData"
              ref="insightElement"
              data-pull-refresh-ignore
              class="insight-chart"
              role="img"
              :aria-label="`${selectedRange} 天热量与体重对照图`"
            />
            <p v-if="hasInsightChartData" class="insight-legend">
              <span class="insight-legend__bar" />已记录热量
              <span class="insight-legend__line" />体重
            </p>
          </template>
          <p v-else-if="!loading && insights" class="insight-empty">
            记录饮食与训练后，在这里查看同期的饮食与训练
          </p>
        </section>
      </UiLoading>
    </main>
  </UiPullRefresh>
</template>

<style scoped lang="scss">
.trend-card {
  width: 100%;
  min-width: 0;
  padding: 18px 16px 24px;
}
.trend-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 0 0;
  > div {
    display: grid;
    gap: 8px;
    min-width: 0;
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 12px;
  }
  strong {
    font-size: 34px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    letter-spacing: -0.03em;
    small {
      margin-left: 6px;
      color: var(--ui-text-secondary);
      font-size: 13px;
      font-weight: 400;
    }
  }
}
.change-badge {
  padding: 8px 12px;
  color: var(--ui-body-foreground) !important;
  background: var(--ui-body-background);
  border-radius: 12px;
  white-space: nowrap;
}
.trend-chart {
  width: 100%;
  height: 220px;
  margin: 16px 0 20px;
}
.trend-details h2 {
  margin: 0 0 18px;
  font-size: 15px;
  font-weight: 600;
}
.stats-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 28px 12px;
  > div {
    display: grid;
    min-width: 0;
    gap: 8px;
  }
  span {
    color: var(--ui-text-secondary);
    font-size: 11px;
  }
  strong {
    font-size: 14px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    overflow-wrap: anywhere;
    small {
      font-size: 11px;
      font-weight: 400;
    }
  }
}
.content-section {
  margin-top: 28px;
}
.section-heading span {
  color: var(--ui-text-secondary);
  font-size: 11px;
}
.insight-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
  > div {
    display: grid;
    gap: 12px;
    padding: 18px 16px;
    background: var(--color-surface);
    border-radius: var(--ui-radius-card);
  }
  span {
    font-size: 11px;
    color: var(--ui-text-secondary);
  }
  strong {
    font-size: 22px;
    font-weight: 600;
    font-variant-numeric: tabular-nums;
    small {
      font-size: 12px;
      font-weight: 400;
    }
  }
}
.insight-summary {
  margin: 14px 0 0;
  color: var(--ui-text-secondary);
  font-size: 12px;
  line-height: 1.8;
}
.insight-chart {
  width: 100%;
  height: 200px;
  margin-top: 20px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
}
.insight-legend {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
  margin: 12px 0 0;
  color: var(--ui-text-secondary);
  font-size: 11px;
  span {
    display: inline-block;
    margin-left: 8px;
  }
  &__bar {
    width: 10px;
    height: 10px;
    background: var(--ui-meal-foreground);
    border-radius: 3px;
  }
  &__line {
    width: 14px;
    height: 2px;
    background: var(--ui-body-foreground);
    border-radius: 2px;
  }
}
.insight-error,
.insight-empty {
  margin: 0;
  padding: 24px 16px;
  color: var(--ui-text-secondary);
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  font-size: 13px;
  line-height: 1.7;
}
.error-state {
  display: grid;
  min-height: 180px;
  place-items: center;
  align-content: center;
  gap: 16px;
  color: var(--ui-text-secondary);
  font-size: 13px;
}
.empty-icon {
  color: var(--ui-body-foreground);
  font-size: 30px;
}
@media (max-width: 360px) {
  .stats-grid {
    gap: 22px 8px;
    strong {
      font-size: 13px;
    }
  }
  .insight-grid strong {
    font-size: 19px;
  }
}
</style>
