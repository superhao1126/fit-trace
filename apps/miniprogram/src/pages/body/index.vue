<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow, onReachBottom } from '@dcloudio/uni-app';
import type { BodyRecord } from '@fit-trace/shared';
import { request, notify } from '../../session';
import { shortDate, weightChart } from '../../presentation';
import { toWeightParts, normalizeWeightParts, fromWeightParts } from '../../weight-picker';
import type { WeightParts } from '../../weight-picker';
import FitShell from '../../components/FitShell.vue';
import FitState from '../../components/FitState.vue';
const rows = ref<BodyRecord[]>([]),
  weightParts = ref<WeightParts>(toWeightParts()),
  editing = ref<string | null>(null),
  busy = ref(false),
  loading = ref(false),
  page = ref(1),
  more = ref(true),
  error = ref(''),
  editor = ref(false),
  deleteTarget = ref<string | null>(null);
const chart = computed(() => weightChart(rows.value));
const delta = computed(() =>
  rows.value.length > 1
    ? Math.round((rows.value[0].weight - rows.value[1].weight) * 100) / 100
    : null,
);
const chartRows = computed(() => rows.value.slice(0, 7));
const wholeOptions = Array.from({ length: 500 }, (_, i) => ({
  label: String(i + 1),
  value: i + 1,
}));
const decimalOptions = computed(() =>
  Array.from({ length: weightParts.value[0] === 500 ? 1 : 100 }, (_, i) => ({
    label: '.' + String(i).padStart(2, '0'),
    value: i,
  })),
);
function pickWeight(event: { value: Array<string | number> }) {
  if (!busy.value) weightParts.value = normalizeWeightParts(event.value);
}
async function load(reset = true) {
  if (loading.value) return;
  loading.value = true;
  try {
    const next = reset ? 1 : page.value;
    const result = await request<BodyRecord[]>('/body-records?page=' + next + '&pageSize=20');
    rows.value = reset ? result : [...rows.value, ...result];
    more.value = result.length === 20;
    page.value = next + 1;
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
function open(record?: BodyRecord) {
  if (loading.value || busy.value) return;
  editing.value = record?.id ?? null;
  weightParts.value = toWeightParts(record?.weight ?? rows.value[0]?.weight);
  editor.value = true;
}
async function save() {
  if (busy.value || loading.value) return;
  const value = fromWeightParts(weightParts.value);
  busy.value = true;
  try {
    await request(
      editing.value ? '/body-records/' + editing.value : '/body-records',
      editing.value ? 'PUT' : 'POST',
      { weight: value },
    );
    editor.value = false;
    editing.value = null;
    await load();
  } catch (e) {
    notify(e);
  } finally {
    busy.value = false;
  }
}
async function remove() {
  if (!deleteTarget.value || busy.value || loading.value) return;
  busy.value = true;
  try {
    await request('/body-records/' + deleteTarget.value, 'DELETE');
    deleteTarget.value = null;
    editor.value = false;
    await load();
  } catch (e) {
    notify(e);
  } finally {
    busy.value = false;
  }
}
onShow(() => {
  void load();
});
onReachBottom(() => {
  if (more.value) void load(false);
});
</script>
<template>
  <FitShell title="体重记录" tab="body">
    <view class="has-bottom-action">
      <FitState
        :loading="loading && !rows.length"
        :error="error"
        :empty="!loading && !rows.length ? '暂无体重记录' : ''"
        @retry="load()"
      />
      <template v-if="rows.length">
        <view class="card current-card"
          ><view class="between caption"
            ><text>最新记录</text><text>{{ shortDate(rows[0].recordedAt) }}</text></view
          ><view class="weight-value"
            ><text class="number">{{ rows[0].weight }}</text
            ><text class="sub">kg</text></view
          ><view v-if="delta !== null" class="accent sub">{{
            '较上次 ' + (delta === 0 ? '' : delta > 0 ? '+' : '−') + Math.abs(delta) + ' kg'
          }}</view></view
        >
        <view class="card trend-card"
          ><view class="between caption"
            ><text>近期记录</text><text>近 {{ chartRows.length }} 次</text></view
          ><view
            v-if="chart.points.length"
            class="weight-chart"
            role="img"
            :aria-label="
              '最近' +
              chartRows.length +
              '次体重从' +
              chartRows[chartRows.length - 1].weight +
              '到' +
              chartRows[0].weight +
              '千克'
            "
            ><view
              v-for="(segment, i) in chart.segments"
              :key="i"
              class="chart-segment"
              :style="{
                left: segment.x / 3 + '%',
                top: segment.y + '%',
                width: segment.length / 3 + '%',
                transform: 'rotate(' + segment.angle + 'deg)',
              }" /><view
              class="chart-dot"
              :style="{
                left: chart.points[chart.points.length - 1].x / 3 + '%',
                top: chart.points[chart.points.length - 1].y + '%',
              }" /></view
          ><view v-else class="chart-empty sub">记录两次后显示趋势</view
          ><view class="between caption"
            ><text>{{ shortDate(chartRows[chartRows.length - 1].recordedAt) }}</text
            ><text>{{ shortDate(rows[0].recordedAt) }}</text></view
          ></view
        >
        <view class="spaced">历史记录</view
        ><view class="card history-card"
          ><t-cell
            v-for="r in rows"
            :key="r.id"
            :title="shortDate(r.recordedAt)"
            :note="r.weight + ' kg'"
            :bordered="false"
            @click="open(r)"
        /></view>
        <view v-if="more" class="spaced"
          ><t-button theme="primary" variant="text" block :loading="loading" @click="load(false)"
            >查看更多记录</t-button
          ></view
        >
      </template>
      <view class="weight-action"
        ><t-button
          theme="primary"
          shape="round"
          size="large"
          block
          :disabled="loading || busy"
          @click="open()"
          >记录体重</t-button
        ></view
      >
    </view>
    <template #overlay>
      <t-popup
        :visible="editor"
        placement="bottom"
        :close-on-overlay-click="!busy"
        @visible-change="!busy && (editor = $event.visible)"
        ><view class="sheet weight-sheet"
          ><view class="between weight-header"
            ><view class="heading"
              >{{ editing ? '修改体重' : '记录体重' }}<text class="weight-unit">kg</text></view
            ><t-button variant="text" size="small" :disabled="busy" @click="editor = false"
              >取消</t-button
            ></view
          ><view class="weight-wheel" :class="{ 'wheel-busy': busy }"
            ><t-picker
              v-if="editor"
              :value="weightParts"
              :use-popup="false"
              :header="false"
              :item-height="44"
              :visible-item-count="5"
              @pick="pickWeight"
              ><t-picker-item :options="wholeOptions" /><t-picker-item
                :options="decimalOptions" /></t-picker></view
          ><view class="sheet-actions"
            ><t-button
              theme="primary"
              shape="round"
              size="large"
              block
              :loading="busy"
              :disabled="busy"
              @click="save"
              >保存</t-button
            ></view
          ><view v-if="editing" class="spaced"
            ><t-button
              theme="danger"
              variant="text"
              block
              :disabled="busy"
              @click="
                deleteTarget = editing;
                editor = false;
              "
              >删除记录</t-button
            ></view
          ></view
        ></t-popup
      >
      <t-dialog
        :visible="!!deleteTarget"
        title="删除这条体重记录？"
        content="删除后无法恢复，请确认后再继续。"
        :confirm-btn="{ content: '确认删除', theme: 'danger', loading: busy, disabled: busy }"
        cancel-btn="取消"
        @confirm="remove"
        @close="!busy && (deleteTarget = null)"
      />
    </template>
  </FitShell>
</template>
<style scoped>
.weight-action {
  --td-button-large-height: 48px;
  position: fixed;
  left: 0;
  right: 0;
  padding: 12px 20px 16px;
  background: #f7f8fa;
  /* Tab items have a 56px height plus 16rpx top and bottom margins. */
  bottom: calc(56px + 32rpx + env(safe-area-inset-bottom));
  z-index: 90;
}
.weight-sheet {
  background: #fff;
}
.weight-header {
  margin-bottom: 16px;
}
.weight-header .heading {
  margin-bottom: 0;
}
.weight-unit {
  margin-left: 6px;
  color: #777;
  font-size: 12px;
  font-weight: 400;
}
.weight-wheel {
  --td-picker-item-font-size: 24px;
  --td-picker-indicator-bg-color: #e6f8ee;
  --td-picker-indicator-border-radius: 12px;
}
.wheel-busy {
  pointer-events: none;
}
.current-card {
  padding: 20px;
}
.weight-value {
  display: flex;
  align-items: baseline;
  gap: 6px;
  margin: 14px 0;
}
.current-card .accent {
  color: #229d60;
}
.trend-card {
  padding: 20px;
}
.weight-chart {
  position: relative;
  width: 100%;
  aspect-ratio: 3;
  margin: 16px 0;
  background: repeating-linear-gradient(
    to bottom,
    transparent 0,
    transparent calc(33.333% - 1px),
    #e6eae8 calc(33.333% - 1px),
    #e6eae8 33.333%
  );
}
.chart-segment {
  position: absolute;
  height: 2px;
  background: #229d60;
  transform-origin: 0 50%;
  border-radius: 2px;
}
.chart-dot {
  position: absolute;
  width: 7px;
  height: 7px;
  margin: -3px 0 0 -3px;
  border-radius: 50%;
  background: #229d60;
}
.chart-empty {
  padding: 30px 0;
}
.history-card {
  --td-cell-height: 40px;
  --td-cell-vertical-padding: 8px;
  padding: 4px 20px;
}
</style>
