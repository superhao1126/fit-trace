<script setup lang="ts">
import { computed, ref } from 'vue';
import { onShow } from '@dcloudio/uni-app';
import type { TrainingTemplate, TrainingPlanState } from '@fit-trace/shared';
import { request, notify } from '../../session';
import { dayNames } from '../../presentation';
import { useTrainingLauncher } from '../../training-launcher';
import FitShell from '../../components/FitShell.vue';
import FitState from '../../components/FitState.vue';
const templates = ref<TrainingTemplate[]>([]),
  plan = ref<TrainingPlanState | null>(null),
  loading = ref(false),
  saving = ref(false),
  error = ref(''),
  editing = ref<number | null>(null),
  browsing = ref(false);
const { start, resume, draftPrompt } = useTrainingLauncher();
const weekdays = [1, 2, 3, 4, 5, 6, 0];
const today = new Date().getDay();
const forDay = (day: number) =>
  templates.value.find((t) => t.id === plan.value?.days.find((d) => d.weekday === day)?.templateId);
const trainingDays = computed(() => plan.value?.days.filter((d) => d.templateId).length ?? 0);
const selectedTemplateId = computed(
  () => plan.value?.days.find((d) => d.weekday === editing.value)?.templateId ?? null,
);
async function load() {
  loading.value = true;
  try {
    [templates.value, plan.value] = await Promise.all([
      request<TrainingTemplate[]>('/training/templates'),
      request<TrainingPlanState>('/training/plan/state'),
    ]);
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
function closeSelection() {
  if (!saving.value) {
    editing.value = null;
    browsing.value = false;
  }
}
function chooseTemplate(t: TrainingTemplate) {
  if (browsing.value) {
    browsing.value = false;
    start(t);
  } else void setTemplate(t.id);
}
async function setTemplate(templateId: string | null) {
  if (!plan.value || editing.value === null || saving.value) return;
  saving.value = true;
  try {
    const days = plan.value.days.map((d) =>
      d.weekday === editing.value ? { ...d, templateId } : d,
    );
    plan.value = { configured: true, days: await request('/training/plan', 'PUT', { days }) };
    editing.value = null;
  } catch (e) {
    notify(e);
  } finally {
    saving.value = false;
  }
}
onShow(() => {
  void load();
});
</script>
<template>
  <FitShell title="每周计划" back tab="today">
    <view class="section-intro"
      ><view class="between"
        ><view class="title">我的每周计划</view
        ><text class="link" @tap="browsing = true">训练模板</text></view
      ></view
    >
    <FitState :loading="loading && !plan" :error="error" @retry="load" />
    <template v-if="plan">
      <view class="card tint plan-note"
        ><view class="heading accent">{{
          plan.configured ? '每周 ' + trainingDays + ' 次训练' : '设置每周计划'
        }}</view></view
      >
      <view class="card day-list"
        ><view v-for="day in weekdays" :key="day" class="day-row" @tap="editing = day"
          ><text class="day-name" :class="{ accent: day === today }"
            >{{ dayNames[day] }}{{ day === today ? ' · 今天' : '' }}</text
          ><view class="flex-1"
            ><view>{{ forDay(day)?.name ?? '休息日' }}</view
            ><view v-if="forDay(day)" class="caption">{{
              forDay(day)!.durationMinutes + ' 分钟'
            }}</view></view
          ><t-icon name="chevron-right" size="16px" color="#777" /></view
      ></view>
      <view class="spaced"
        ><t-button theme="primary" size="large" shape="round" block @click="start(forDay(today))">{{
          forDay(today) ? '按今日计划开始训练' : '开始自由训练'
        }}</t-button></view
      >
    </template>
    <template #overlay>
      <t-popup
        :visible="editing !== null || browsing"
        placement="bottom"
        :close-on-overlay-click="!saving"
        @visible-change="!$event.visible && closeSelection()"
        ><view class="sheet template-sheet"
          ><view class="between sheet-header"
            ><view class="heading">{{
              browsing ? '训练模板' : (editing === null ? '' : dayNames[editing]) + ' · 安排训练'
            }}</view
            ><t-button variant="text" size="small" :disabled="saving" @click="closeSelection"
              >取消</t-button
            ></view
          ><scroll-view scroll-y enhanced :show-scrollbar="false" class="template-scroll"
            ><view
              v-if="!browsing"
              class="template-option rest-option"
              :class="{ selected: selectedTemplateId === null }"
              ><t-cell
                title="休息日"
                :bordered="false"
                :right-icon="selectedTemplateId === null ? 'check' : ''"
                @click="setTemplate(null)" /></view
            ><view
              v-for="t in templates"
              :key="t.id"
              class="template-option"
              :class="{ selected: !browsing && selectedTemplateId === t.id }"
              ><t-cell
                :title="t.name"
                :description="t.durationMinutes + ' 分钟 · ' + t.exercises.length + ' 个动作'"
                :bordered="false"
                :right-icon="!browsing && selectedTemplateId === t.id ? 'check' : ''"
                @click="chooseTemplate(t)" /></view></scroll-view
          ><view v-if="saving" class="spaced"
            ><t-loading theme="circular" text="正在保存计划…" /></view></view
      ></t-popup>
      <t-dialog
        :visible="draftPrompt"
        title="继续未完成的训练？"
        content="当前账号已有训练草稿，继续后可以保留上次的进度。"
        confirm-btn="继续训练"
        cancel-btn="暂时不用"
        @confirm="resume"
        @close="draftPrompt = false"
      />
    </template>
  </FitShell>
</template>
<style scoped>
.plan-note {
  padding: 18px 20px;
}
.template-sheet {
  background: #fff;
}
.sheet-header {
  margin-bottom: 16px;
}
.sheet-header .heading {
  margin-bottom: 0;
}
.template-scroll {
  height: 50vh;
}
.template-option {
  --td-cell-bg-color: #f7f8fa;
  --td-cell-horizontal-padding: 16px;
  --td-cell-vertical-padding: 12px;
  --td-cell-description-font-size: 12px;
  --td-cell-right-icon-color: #229d60;
  margin-bottom: 8px;
  border-radius: 14px;
  overflow: hidden;
}
.template-option.selected {
  --td-cell-bg-color: #e6f8ee;
}
.rest-option {
  --td-cell-vertical-padding: 16px;
}
.day-list {
  padding: 12px 20px;
}
.day-row {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 48px;
  padding: 6px 0;
  font-size: 13px;
}
.day-name {
  width: 72px;
  color: #777;
  font-size: 11px;
  flex-shrink: 0;
}
.day-name.accent {
  color: #229d60;
}
</style>
