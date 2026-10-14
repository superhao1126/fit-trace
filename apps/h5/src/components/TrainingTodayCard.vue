<script setup lang="ts">
import { computed, onActivated, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import dayjs from 'dayjs';
import type { TrainingTemplate } from '@fit-trace/shared';
import { getTrainingPlanState, getTrainingTemplates } from '@/api/training';
import { authSession } from '@/api/session';
import { latestTrainingDraft } from '@/utils/training-draft';
import UiButton from '@/components/ui/UiButton.vue';
const props = defineProps<{ completedCount?: number }>();
const router = useRouter();
const configured = ref(false);
const today = ref<TrainingTemplate | null>(null);
const state = ref<'loading' | 'ready' | 'error'>('loading');
const draft = ref<ReturnType<typeof latestTrainingDraft>>(null);
const title = computed(() =>
  draft.value
    ? `训练中 · ${draft.value.draft.name}`
    : state.value === 'loading'
      ? '正在读取今日计划…'
      : state.value === 'error'
        ? '今日计划加载失败'
        : today.value
          ? `今日计划 · ${today.value.name}`
          : configured.value
            ? '今日计划 · 休息日'
            : '今日计划 · 尚未安排',
);
const summary = computed(() =>
  draft.value
    ? `${dayjs(draft.value.draft.startedAt).format('M月D日 HH:mm')} 开始 · 已完成 ${draft.value.draft.exercises.reduce((n, e) => n + e.sets.filter((s) => s.completed).length, 0)} 组`
    : today.value
      ? `${today.value.exercises.length} 个动作 · 预计 ${today.value.durationMinutes} 分钟`
      : configured.value
        ? '今天可以休息，也可以自由训练'
        : '安排每周计划，或直接开始自由训练',
);
let requestId = 0;
async function load() {
  const request = ++requestId;
  const ownerId = authSession.snapshot?.user?.id;
  const generation = authSession.generation;
  draft.value = ownerId ? latestTrainingDraft(localStorage, ownerId) : null;
  state.value = 'loading';
  try {
    const [plan, templates] = await Promise.all([getTrainingPlanState(), getTrainingTemplates()]);
    if (request !== requestId || generation !== authSession.generation) return;
    configured.value = plan.configured;
    today.value =
      templates.find(
        (t) => t.id === plan.days.find((d) => d.weekday === dayjs().day())?.templateId,
      ) ?? null;
    state.value = 'ready';
  } catch {
    if (request === requestId) state.value = 'error';
  }
}
function start() {
  const template = draft.value?.templateId ?? today.value?.id;
  void router.push({ path: '/training/session', query: template ? { template } : {} });
}
onMounted(load);
let activated = false;
onActivated(() => {
  if (activated) void load();
  else activated = true;
});
</script>
<template>
  <section class="training-today">
    <h2>{{ title }}</h2>
    <p v-if="draft || state === 'ready'">{{ summary }}</p>
    <small v-if="props.completedCount">今天已完成 {{ props.completedCount }} 次训练</small>
    <div class="training-today__actions">
      <UiButton v-if="draft || (state === 'ready' && today)" kind="primary" @click="start">{{
        draft ? '继续训练' : '开始今日训练'
      }}</UiButton>
      <UiButton
        v-if="!draft"
        :kind="state === 'ready' && today ? 'text' : 'primary'"
        @click="router.push('/training/session')"
        >自由训练</UiButton
      >
      <UiButton v-if="state === 'error'" kind="text" @click="load">重试</UiButton>
      <UiButton kind="text" @click="router.push('/training/plan')">{{
        configured ? '调整计划' : '安排计划'
      }}</UiButton>
    </div>
  </section>
</template>
<style scoped lang="scss">
.training-today {
  padding: 14px 16px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  h2 {
    margin: 0;
    font-size: 15px;
    overflow-wrap: anywhere;
  }
  p,
  small {
    margin: 6px 0 0;
    color: var(--ui-text-secondary);
    font-size: 12px;
    line-height: 1.6;
  }
}
.training-today__actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 10px;
}
.training-today .training-today__actions {
  :deep(.ui-button.t-button) {
    height: 40px;
    padding: 0 12px;
    font-size: 13px;
    border-radius: 12px;
  }
  :deep(.ui-button--primary) {
    min-width: 128px;
  }
}
</style>
