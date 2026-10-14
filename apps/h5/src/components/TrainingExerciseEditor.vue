<script setup lang="ts">
import type { PreviousExercise, TrainingExercise, TrainingSet } from '@fit-trace/shared';
import UiNumberInput from '@/components/ui/UiNumberInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import dayjs from 'dayjs';
import { carryPreviousSets, type EditableTrainingSet } from '@/utils/previous-training';
import { uiToast } from '@/components/ui/toast';
import { computed, ref } from 'vue';
const props = defineProps<{
  modelValue: TrainingExercise[];
  session?: boolean;
  previous?: Record<string, PreviousExercise | null>;
  historyErrors?: string[];
  autoRest?: boolean;
  paused?: boolean;
}>();
const activeIndex = ref<number | null>(null);
const ordered = computed(() =>
  props.modelValue
    .map((exercise, index) => ({ exercise, index }))
    .sort((a, b) =>
      props.session
        ? Number(a.exercise.sets.every((s) => s.completed)) -
          Number(b.exercise.sets.every((s) => s.completed))
        : 0,
    ),
);
const currentIndex = computed(
  () =>
    activeIndex.value ??
    ordered.value.find(({ exercise }) => exercise.sets.some((s) => !s.completed))?.index ??
    ordered.value[0]?.index,
);
const emit = defineEmits<{
  'update:modelValue': [value: TrainingExercise[]];
  rest: [seconds: number];
  retryHistory: [id: string];
}>();
function change(index: number, update: Partial<TrainingExercise>) {
  emit(
    'update:modelValue',
    props.modelValue.map((e, i) => (i === index ? { ...e, ...update } : e)),
  );
}
function setValue(
  index: number,
  setIndex: number,
  key: 'reps' | 'weightKg',
  value: string | number,
) {
  const sets = props.modelValue[index].sets.map((s, i) =>
    i === setIndex
      ? {
          ...s,
          edited: true,
          [key]: value === '' ? (key === 'weightKg' && props.session ? -1 : 0) : Number(value),
        }
      : s,
  );
  change(index, { sets });
}
function toggle(index: number, setIndex: number) {
  const exercise = props.modelValue[index];
  const set = exercise.sets[setIndex];
  if (
    !set.completed &&
    (!Number.isInteger(set.reps) ||
      set.reps < 1 ||
      set.reps > 3600 ||
      !Number.isFinite(set.weightKg) ||
      set.weightKg < 0 ||
      set.weightKg > 1000)
  ) {
    uiToast.warning('请先确认本组的次数和实际重量');
    return;
  }
  change(index, {
    sets: props.modelValue[index].sets.map((s, i) =>
      i === setIndex ? { ...s, completed: !s.completed } : s,
    ),
  });
  if (!set.completed && props.autoRest && exercise.restSeconds > 0)
    emit('rest', exercise.restSeconds);
  if (!set.completed && exercise.sets.every((s, i) => i === setIndex || s.completed))
    activeIndex.value = null;
}
function addSet(index: number) {
  const exercise = props.modelValue[index];
  if (exercise.sets.length >= 20) return;
  const last = exercise.sets.at(-1);
  const next: TrainingSet = {
    reps: last?.reps ?? 10,
    weightKg: last?.weightKg ?? 0,
    completed: false,
  };
  change(index, { sets: [...exercise.sets, next] });
}
function applyPrevious(index: number) {
  const exercise = props.modelValue[index];
  const previous = props.previous?.[exercise.exerciseId];
  if (!previous) return;
  const sets = carryPreviousSets(exercise.sets as EditableTrainingSet[], previous.sets);
  if (sets.every((set, i) => set === exercise.sets[i])) {
    uiToast.warning('已填写或完成的组会保留，没有可带入的组');
    return;
  }
  change(index, { sets });
  uiToast.success('已带入上次成绩，已填写和完成的组保持不变');
}
function removeSet(index: number, setIndex: number) {
  change(index, { sets: props.modelValue[index].sets.filter((_, i) => i !== setIndex) });
}
function removeExercise(index: number) {
  emit(
    'update:modelValue',
    props.modelValue.filter((_, i) => i !== index),
  );
}
</script>
<template>
  <div class="exercise-editor">
    <article
      v-for="{ exercise, index } in ordered"
      :key="`${exercise.exerciseId}-${index}`"
      class="exercise-block"
    >
      <header>
        <button
          v-if="session"
          class="exercise-toggle"
          type="button"
          :aria-expanded="currentIndex === index"
          @click="activeIndex = currentIndex === index ? -1 : index"
        >
          <h3>{{ exercise.name }}</h3>
          <small
            >{{ exercise.sets.filter((s) => s.completed).length }} /
            {{ exercise.sets.length }} 组</small
          >
        </button>
        <h3 v-else>{{ exercise.name }}</h3>
        <UiButton
          v-if="!session"
          kind="text"
          :aria-label="`移除${exercise.name}`"
          @click="removeExercise(index)"
          >移除</UiButton
        >
      </header>
      <template v-if="!session || currentIndex === index">
        <div v-if="session" class="previous-performance">
          <template v-if="previous?.[exercise.exerciseId]">
            <small
              >上次参考 ·
              {{
                dayjs(previous[exercise.exerciseId]?.startedAt).format('M月D日')
              }}，按实际调整</small
            >
            <p>
              {{
                previous[exercise.exerciseId]?.sets
                  .map(
                    (s) =>
                      `${s.weightKg}kg × ${s.reps}${exercise.unit === 'seconds' ? '秒' : '次'}`,
                  )
                  .join(' / ')
              }}
            </p>
            <UiButton kind="text" @click="applyPrevious(index)">带入上次</UiButton>
          </template>
          <template v-else-if="historyErrors?.includes(exercise.exerciseId)">
            <small>上次成绩加载失败，不影响本次记录</small>
            <UiButton kind="text" @click="emit('retryHistory', exercise.exerciseId)">重试</UiButton>
          </template>
          <small v-else>{{
            previous && exercise.exerciseId in previous
              ? '暂无上次成绩，请确认次数和实际重量'
              : '正在读取上次成绩…'
          }}</small>
        </div>
        <div class="sets-heading">
          <span>组</span><span>{{ exercise.unit === 'seconds' ? '秒数' : '次数' }}</span
          ><span>重量 kg</span><span>{{ session ? '完成' : '操作' }}</span>
        </div>
        <div v-for="(set, setIndex) in exercise.sets" :key="setIndex" class="set-row">
          <span>{{ setIndex + 1 }}</span
          ><UiNumberInput
            :model-value="set.reps || ''"
            type="number"
            inputmode="numeric"
            :min="1"
            :max="3600"
            :step="1"
            :aria-label="`${exercise.name}第${setIndex + 1}组${exercise.unit === 'seconds' ? '秒数' : '次数'}`"
            @update:model-value="setValue(index, setIndex, 'reps', $event)"
          /><UiNumberInput
            :model-value="set.weightKg < 0 ? '' : set.weightKg"
            type="number"
            inputmode="decimal"
            :min="0"
            :max="1000"
            :step="0.5"
            placeholder="实际重量"
            :aria-label="`${exercise.name}第${setIndex + 1}组重量`"
            @update:model-value="setValue(index, setIndex, 'weightKg', $event)"
          /><UiButton
            v-if="session"
            :kind="set.completed ? 'primary' : 'secondary'"
            :aria-pressed="set.completed"
            :disabled="paused"
            :aria-label="`${exercise.name}第${setIndex + 1}组完成状态`"
            @click="toggle(index, setIndex)"
            >{{ set.completed ? '完成' : '待做' }}</UiButton
          ><UiButton
            v-else
            kind="text"
            :disabled="exercise.sets.length === 1"
            :aria-label="`删除${exercise.name}第${setIndex + 1}组`"
            @click="removeSet(index, setIndex)"
            >删组</UiButton
          >
        </div>
        <footer>
          <UiButton kind="text" :disabled="exercise.sets.length >= 20" @click="addSet(index)"
            >＋ 加一组</UiButton
          >
          <details v-if="session" class="exercise-settings">
            <summary>动作设置</summary>
            <label
              >休息（秒）<UiNumberInput
                :model-value="exercise.restSeconds"
                type="number"
                :min="0"
                :max="600"
                :aria-label="`${exercise.name}休息秒数`"
                @update:model-value="change(index, { restSeconds: Number($event) })" /></label
            ><UiButton
              kind="text"
              :aria-label="`移除${exercise.name}`"
              @click="removeExercise(index)"
              >移除动作</UiButton
            >
          </details>
          <label v-else
            >休息（秒）<UiNumberInput
              :model-value="exercise.restSeconds"
              type="number"
              inputmode="numeric"
              :min="0"
              :max="600"
              :aria-label="`${exercise.name}休息秒数`"
              @update:model-value="change(index, { restSeconds: Number($event) })"
          /></label>
        </footer>
        <UiButton
          v-if="session && !autoRest"
          kind="secondary"
          block
          :disabled="paused"
          @click="emit('rest', exercise.restSeconds)"
          >休息 {{ exercise.restSeconds }} 秒</UiButton
        >
      </template>
    </article>
  </div>
</template>
<style scoped lang="scss">
.exercise-toggle {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 0;
  border: 0;
  background: none;
  color: inherit;
  text-align: left;
  cursor: pointer;
  min-height: 36px;
}
.exercise-toggle small {
  flex-shrink: 0;
  color: var(--ui-text-secondary);
  font-size: 12px;
}
.exercise-settings {
  font-size: 12px;
  color: var(--ui-text-secondary);
}
.exercise-settings label {
  display: flex;
  align-items: center;
  gap: 8px;
  max-width: 160px;
}
.previous-performance {
  margin: 12px 0;
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--ui-primary-light);
  small {
    color: var(--ui-text-secondary);
  }
  p {
    font-size: 12px;
    margin: 6px 0;
    overflow-wrap: anywhere;
  }
}
.exercise-editor {
  display: grid;
  gap: 16px;
}
.exercise-block {
  padding: 16px;
  background: var(--color-surface);
  border-radius: var(--ui-radius-card);
  header,
  footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  h3 {
    font-size: 16px;
    margin: 0;
  }
  footer {
    margin: 12px 0;
  }
  footer label {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 12px;
    color: var(--ui-text-secondary);
    :deep(.ui-input) {
      width: 72px;
    }
  }
}
.sets-heading,
.set-row {
  display: grid;
  grid-template-columns: 24px minmax(0, 1fr) minmax(0, 1fr) 56px;
  gap: 8px;
  align-items: center;
  margin: 8px 0;
  font-size: 13px;
}
.sets-heading {
  color: var(--ui-text-secondary);
  font-size: 12px;
}
.set-row :deep(.ui-input) {
  padding: 8px;
  min-height: 44px;
  font-size: 14px;
  background: var(--ui-background);
}
.set-row :deep(.ui-button) {
  font-size: 12px;
  padding: 0 8px;
  min-height: 44px;
}
@media (max-width: 360px) {
  .exercise-block {
    padding: 12px;
  }
  .sets-heading,
  .set-row {
    gap: 4px;
  }
}
</style>
