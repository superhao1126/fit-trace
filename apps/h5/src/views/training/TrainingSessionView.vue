<script setup lang="ts">
import { confirmDialog } from '@/components/ui/confirm';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ActionSheet } from 'tdesign-mobile-vue';
import { uiToast as ToastPlugin } from '@/components/ui/toast';
import {
  exerciseLibrary,
  makeTrainingExercise,
  trainingPresets,
  type TrainingExercise,
  type PreviousExercise,
} from '@fit-trace/shared';
import { getPreviousExercise, getTrainingTemplates } from '@/api/training';
import { createWorkout } from '@/api/workouts';
import { authSession } from '@/api/session';
import { readTrainingDraft, trainingDraftKey } from '@/utils/training-draft';
import { completedTraining, trainingValidation } from '@/utils/training-validation';
import { carryPreviousSets, type EditableTrainingSet } from '@/utils/previous-training';
import {
  elapsedMinutes,
  newTrainingClock,
  restSeconds,
  setRest,
  toggleRestPause,
  toggleTrainingPause,
} from '@/utils/training-clock';
import { showRequestError } from '@/utils/request-error';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import TrainingExerciseEditor from '@/components/TrainingExerciseEditor.vue';
const route = useRoute();
const router = useRouter();
const templateId = typeof route.query.template === 'string' ? route.query.template : '';
const ownerId = authSession.snapshot?.user?.id ?? '';
const draftKey = trainingDraftKey(ownerId, templateId);
const sessionGeneration = authSession.generation;
const previous = ref<Record<string, PreviousExercise | null>>({});
const historyErrors = ref<string[]>([]);
const historyPending = new Set<string>();
async function loadPrevious(id: string) {
  if (historyPending.has(id)) return;
  historyPending.add(id);
  historyErrors.value = historyErrors.value.filter((item) => item !== id);
  try {
    const result = await getPreviousExercise(id);
    if (authSession.generation === sessionGeneration) {
      previous.value[id] = result;
      if (result && result.unit === exercises.value.find((e) => e.exerciseId === id)?.unit) {
        exercises.value = exercises.value.map((e) =>
          e.exerciseId === id
            ? { ...e, sets: carryPreviousSets(e.sets as EditableTrainingSet[], result.sets) }
            : e,
        );
      }
    }
  } catch {
    historyErrors.value = [...historyErrors.value, id];
  } finally {
    historyPending.delete(id);
  }
}
const name = ref('自由训练');
const exercises = ref<TrainingExercise[]>([]);
watch(
  () => exercises.value.map((item) => item.exerciseId),
  (ids) => {
    for (const id of new Set(ids))
      if (!(id in previous.value) && !historyErrors.value.includes(id)) void loadPrevious(id);
  },
);
const startedAt = ref(new Date().toISOString());
const duration = ref<string | number>('');
const ready = ref(false);
const error = ref(false);
const picker = ref(false);
const saving = ref(false);
const saved = ref(false);
const resumed = ref(false);
const now = ref(Date.now());
const clock = ref(newTrainingClock());
const autoRest = ref(true);
let storageWarning = false;
const ticker = window.setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => window.clearInterval(ticker));
const rest = computed(() => restSeconds(clock.value, now.value));
const elapsed = computed(() => elapsedMinutes(startedAt.value, clock.value, now.value));
function pauseTraining() {
  now.value = Date.now();
  clock.value = toggleTrainingPause(clock.value, now.value);
}
function pauseRest() {
  clock.value = toggleRestPause(clock.value, Date.now());
}
function adjustRest(seconds: number) {
  const value = Math.max(0, Math.min(600, rest.value + seconds));
  if (clock.value.restPausedSeconds !== null)
    clock.value = { ...clock.value, restPausedSeconds: value || null, restUntil: 0 };
  else clock.value = setRest(clock.value, value, Date.now());
}
const completed = computed(() =>
  exercises.value.reduce((n, e) => n + e.sets.filter((s) => s.completed).length, 0),
);
const total = computed(() => exercises.value.reduce((n, e) => n + e.sets.length, 0));
async function load() {
  ready.value = false;
  error.value = false;
  try {
    const draft = readTrainingDraft(localStorage, draftKey);
    if (draft) {
      name.value = draft.name;
      startedAt.value = draft.startedAt;
      exercises.value = draft.exercises.map((e) => ({
        ...e,
        sets: e.sets.map((set) => ({ ...set, edited: true })),
      }));
      duration.value =
        typeof draft.duration === 'number' || typeof draft.duration === 'string'
          ? draft.duration
          : '';
      resumed.value = true;
      clock.value = draft.clock ?? newTrainingClock();
      autoRest.value = draft.autoRest !== false;
    } else if (templateId) {
      const template =
        trainingPresets.find((t) => t.id === templateId) ??
        (await getTrainingTemplates()).find((t) => t.id === templateId);
      if (!template) throw Error('模板不存在');
      name.value = template.name;
      exercises.value = structuredClone(template.exercises).map((e) => ({
        ...e,
        sets: e.sets.map((s) => ({
          ...s,
          weightKg:
            template.preset &&
            exerciseLibrary.find((item) => item.id === e.exerciseId)?.equipment !== '徒手'
              ? -1
              : s.weightKg,
          completed: false,
        })),
      }));
    }
    ready.value = true;
  } catch (e) {
    error.value = true;
    showRequestError(e, '训练加载失败');
  }
}
watch(
  [name, exercises, startedAt, duration, clock, autoRest, ready],
  () => {
    if (!ready.value || saved.value || !ownerId || authSession.generation !== sessionGeneration)
      return;
    try {
      localStorage.setItem(
        draftKey,
        JSON.stringify({
          name: name.value,
          exercises: exercises.value,
          startedAt: startedAt.value,
          duration: duration.value,
          clock: clock.value,
          autoRest: autoRest.value,
        }),
      );
    } catch {
      if (!storageWarning) {
        ToastPlugin.warning('本机草稿保存失败，请保持页面打开');
        storageWarning = true;
      }
    }
  },
  { deep: true },
);
function add(_selected: unknown, index: number) {
  picker.value = false;
  if (exercises.value.length >= 20) return;
  const item = exerciseLibrary[index];
  const exercise = makeTrainingExercise(item);
  if (item.equipment !== '徒手') exercise.sets = exercise.sets.map((s) => ({ ...s, weightKg: -1 }));
  const last = previous.value[item.id];
  if (last?.unit === item.unit) exercise.sets = carryPreviousSets(exercise.sets, last.sets);
  exercises.value.push(exercise);
}
function startRest(seconds: number) {
  now.value = Date.now();
  clock.value = setRest(clock.value, seconds, now.value);
}
function finish() {
  if (saving.value) return;
  const validation = completed.value
    ? trainingValidation(completedTraining(exercises.value))
    : null;
  if (validation) {
    ToastPlugin.warning(validation);
    return;
  }
  if (!completed.value) {
    ToastPlugin.warning('请先标记至少一组完成');
    return;
  }
  const dialog = confirmDialog({
    title: '完成本次训练？',
    content: `已完成 ${completed.value} / ${total.value} 组，只保存标记完成的组。`,
    confirmBtn: '完成并保存',
    cancelBtn: '继续训练',
    onConfirm: () => {
      dialog.destroy();
      void save();
    },
  });
}
async function save() {
  if (saving.value) return;
  const minutes = duration.value === '' ? elapsed.value : Number(duration.value);
  if (!name.value.trim()) {
    ToastPlugin.warning('请输入训练名称');
    return;
  }
  if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) {
    ToastPlugin.warning('实际时长需为 1 至 1440 分钟的整数');
    return;
  }
  saving.value = true;
  try {
    const workout = await createWorkout({
      type: 'STRENGTH',
      name: name.value.trim(),
      startedAt: startedAt.value,
      durationMinutes: minutes,
      exercises: completedTraining(exercises.value),
    });
    saved.value = true;
    try {
      localStorage.removeItem(draftKey);
    } catch {
      /* 记录已保存，存储失败不影响服务器结果。 */
    }
    ToastPlugin.success('训练记录已保存');
    await router.replace(`/workouts/${workout.id}`);
  } catch (e) {
    showRequestError(e, '保存失败，训练草稿已保留');
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <main class="view-page ui-page training-session">
    <UiDetailHeader :title="name" subtitle="记录实际完成的组数、次数和重量" />
    <p v-if="error">训练加载失败<UiButton kind="text" @click="load">重试</UiButton></p>
    <p v-else-if="!ready">正在准备训练…</p>
    <template v-else
      ><p v-if="resumed" class="session-hint">
        已恢复 {{ new Date(startedAt).toLocaleString() }} 的训练草稿。
      </p>
      <section class="session-summary">
        <strong>已完成 {{ completed }} / {{ total }} 组</strong
        ><span>已进行 {{ elapsed }} 分钟</span
        ><UiButton kind="text" @click="pauseTraining">{{
          clock.pausedAt === null ? '暂停训练' : '继续训练'
        }}</UiButton>
      </section>
      <p v-if="clock.pausedAt !== null" class="session-hint">
        训练已暂停，暂停时间不计入实际时长。
      </p>
      <section v-if="rest" class="session-rest" aria-live="polite">
        <strong
          >休息{{ clock.restPausedSeconds !== null || clock.pausedAt !== null ? '已暂停' : '中' }} ·
          {{ rest }} 秒</strong
        >
        <div>
          <UiButton kind="text" @click="adjustRest(-15)">−15 秒</UiButton
          ><UiButton kind="text" @click="adjustRest(15)">+15 秒</UiButton
          ><UiButton kind="text" :disabled="clock.pausedAt !== null" @click="pauseRest">{{
            clock.restPausedSeconds === null ? '暂停' : '继续'
          }}</UiButton
          ><UiButton kind="text" @click="startRest(0)">跳过</UiButton>
        </div>
      </section>
      <TrainingExerciseEditor
        v-model="exercises"
        session
        :previous="previous"
        :history-errors="historyErrors"
        :auto-rest="autoRest"
        :paused="clock.pausedAt !== null"
        @retry-history="loadPrevious"
        @rest="startRest"
      />
      <details class="session-settings">
        <summary>补充设置与动作管理</summary>
        <label>训练名称<UiInput v-model="name" :maxlength="60" aria-label="训练名称" /></label>
        <label class="auto-rest"
          ><input v-model="autoRest" type="checkbox" />完成一组后自动休息</label
        >
        <UiButton kind="secondary" block :disabled="exercises.length >= 20" @click="picker = true"
          >＋ 添加动作</UiButton
        ><label
          >实际时长（分钟）<UiInput
            v-model="duration"
            type="number"
            inputmode="numeric"
            :min="1"
            :max="1440"
            :placeholder="`自动记录 ${elapsed} 分钟`"
            aria-label="实际训练时长"
        /></label>
      </details>
      <UiButton block :loading="saving" @click="finish">完成并保存训练</UiButton></template
    ><ActionSheet
      v-model="picker"
      :items="exerciseLibrary.map((e) => `${e.name} · ${e.equipment}`)"
      cancel-text="取消"
      @selected="add"
    />
  </main>
</template>
<style scoped lang="scss">
.training-session {
  display: grid;
  gap: 16px;
}
.session-settings {
  padding: 16px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
}
.session-settings summary {
  font-size: 14px;
  cursor: pointer;
}
.session-settings[open] {
  display: grid;
  gap: 14px;
}
.training-session .auto-rest {
  display: flex;
  align-items: center;
}
.session-rest {
  padding: 12px 16px;
  background: var(--ui-primary-light);
  border-radius: 14px;
}
.session-rest div {
  display: flex;
  flex-wrap: wrap;
}
.training-session label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: var(--ui-text-secondary);
}
.session-summary {
  padding: 20px;
  border-radius: var(--ui-radius-card);
  background: var(--color-surface);
  display: grid;
  gap: 12px;
  span {
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
}
.session-hint {
  font-size: 12px;
  line-height: 1.7;
  color: var(--ui-text-secondary);
}
</style>
