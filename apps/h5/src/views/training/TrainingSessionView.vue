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
} from '@fit-trace/shared';
import { getTrainingTemplates } from '@/api/training';
import { createWorkout } from '@/api/workouts';
import { authSession } from '@/api/session';
import { completedTraining, trainingValidation } from '@/utils/training-validation';
import { showRequestError } from '@/utils/request-error';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiInput from '@/components/ui/UiInput.vue';
import TrainingExerciseEditor from '@/components/TrainingExerciseEditor.vue';
const route = useRoute();
const router = useRouter();
const templateId = typeof route.query.template === 'string' ? route.query.template : '';
const draftKey = `fit-trace:training-draft:${authSession.snapshot?.user?.id ?? 'guest'}:${templateId || 'free'}`;
const name = ref('自由训练');
const exercises = ref<TrainingExercise[]>([]);
const startedAt = ref(new Date().toISOString());
const duration = ref<string | number>('');
const ready = ref(false);
const error = ref(false);
const picker = ref(false);
const saving = ref(false);
const saved = ref(false);
const resumed = ref(false);
const now = ref(Date.now());
const restUntil = ref(0);
let storageWarning = false;
const ticker = window.setInterval(() => (now.value = Date.now()), 1000);
onUnmounted(() => window.clearInterval(ticker));
const rest = computed(() => Math.max(0, Math.ceil((restUntil.value - now.value) / 1000)));
const elapsed = computed(() =>
  Math.max(1, Math.round((now.value - new Date(startedAt.value).getTime()) / 60000)),
);
const completed = computed(() =>
  exercises.value.reduce((n, e) => n + e.sets.filter((s) => s.completed).length, 0),
);
const total = computed(() => exercises.value.reduce((n, e) => n + e.sets.length, 0));
function readDraft() {
  try {
    const draft = JSON.parse(localStorage.getItem(draftKey) ?? 'null');
    if (
      !draft ||
      typeof draft.name !== 'string' ||
      typeof draft.startedAt !== 'string' ||
      !Number.isFinite(Date.parse(draft.startedAt)) ||
      !Array.isArray(draft.exercises) ||
      draft.exercises.length > 20
    )
      return null;
    if (
      !draft.exercises.every(
        (e: TrainingExercise) =>
          e &&
          exerciseLibrary.some((item) => item.id === e.exerciseId && item.unit === e.unit) &&
          Array.isArray(e.sets) &&
          e.sets.length <= 20 &&
          e.sets.every(
            (s) =>
              s &&
              typeof s.completed === 'boolean' &&
              typeof s.reps === 'number' &&
              typeof s.weightKg === 'number',
          ),
      )
    )
      return null;
    if (draft.exercises.length && trainingValidation(draft.exercises)) return null;
    return draft as {
      name: string;
      startedAt: string;
      exercises: TrainingExercise[];
      duration: string | number;
    };
  } catch {
    return null;
  }
}
async function load() {
  ready.value = false;
  error.value = false;
  try {
    const draft = readDraft();
    if (draft) {
      name.value = draft.name;
      startedAt.value = draft.startedAt;
      exercises.value = draft.exercises;
      duration.value =
        typeof draft.duration === 'number' || typeof draft.duration === 'string'
          ? draft.duration
          : '';
      resumed.value = true;
    } else if (templateId) {
      const template =
        trainingPresets.find((t) => t.id === templateId) ??
        (await getTrainingTemplates()).find((t) => t.id === templateId);
      if (!template) throw Error('模板不存在');
      name.value = template.name;
      exercises.value = structuredClone(template.exercises).map((e) => ({
        ...e,
        sets: e.sets.map((s) => ({ ...s, completed: false })),
      }));
    }
    ready.value = true;
  } catch (e) {
    error.value = true;
    showRequestError(e, '训练加载失败');
  }
}
watch(
  [name, exercises, startedAt, duration],
  () => {
    if (!ready.value || saved.value) return;
    try {
      localStorage.setItem(
        draftKey,
        JSON.stringify({
          name: name.value,
          exercises: exercises.value,
          startedAt: startedAt.value,
          duration: duration.value,
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
  if (exercises.value.length < 20)
    exercises.value.push(makeTrainingExercise(exerciseLibrary[index]));
}
function startRest(seconds: number) {
  restUntil.value = Date.now() + seconds * 1000;
  now.value = Date.now();
}
function finish() {
  if (saving.value) return;
  const validation = trainingValidation(exercises.value);
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
        ><UiButton v-if="rest" kind="text" @click="restUntil = 0"
          >休息剩余 {{ rest }} 秒 · 结束休息</UiButton
        >
      </section>
      <label>训练名称<UiInput v-model="name" :maxlength="60" aria-label="训练名称" /></label
      ><TrainingExerciseEditor v-model="exercises" session @rest="startRest" /><UiButton
        kind="secondary"
        block
        :disabled="exercises.length >= 20"
        @click="picker = true"
        >＋ 添加动作</UiButton
      ><label
        >实际时长（分钟）<UiInput
          v-model="duration"
          type="number"
          inputmode="numeric"
          :min="1"
          :max="1440"
          :placeholder="`自动记录 ${elapsed} 分钟`"
          aria-label="实际训练时长" /></label
      ><UiButton block :loading="saving" @click="finish">完成并保存训练</UiButton></template
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
