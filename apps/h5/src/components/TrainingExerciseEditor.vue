<script setup lang="ts">
import type { TrainingExercise, TrainingSet } from '@fit-trace/shared';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
const props = defineProps<{ modelValue: TrainingExercise[]; session?: boolean }>();
const emit = defineEmits<{
  'update:modelValue': [value: TrainingExercise[]];
  rest: [seconds: number];
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
    i === setIndex ? { ...s, [key]: value === '' ? 0 : Number(value) } : s,
  );
  change(index, { sets });
}
function toggle(index: number, setIndex: number) {
  change(index, {
    sets: props.modelValue[index].sets.map((s, i) =>
      i === setIndex ? { ...s, completed: !s.completed } : s,
    ),
  });
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
      v-for="(exercise, index) in modelValue"
      :key="`${exercise.exerciseId}-${index}`"
      class="exercise-block"
    >
      <header>
        <h3>{{ exercise.name }}</h3>
        <UiButton kind="text" :aria-label="`移除${exercise.name}`" @click="removeExercise(index)"
          >移除</UiButton
        >
      </header>
      <div class="sets-heading">
        <span>组</span><span>{{ exercise.unit === 'seconds' ? '秒数' : '次数' }}</span
        ><span>重量 kg</span><span>{{ session ? '完成' : '操作' }}</span>
      </div>
      <div v-for="(set, setIndex) in exercise.sets" :key="setIndex" class="set-row">
        <span>{{ setIndex + 1 }}</span
        ><UiInput
          :model-value="set.reps || ''"
          type="number"
          inputmode="numeric"
          :min="1"
          :max="3600"
          :step="1"
          :aria-label="`${exercise.name}第${setIndex + 1}组${exercise.unit === 'seconds' ? '秒数' : '次数'}`"
          @update:model-value="setValue(index, setIndex, 'reps', $event)"
        /><UiInput
          :model-value="set.weightKg"
          type="number"
          inputmode="decimal"
          :min="0"
          :max="1000"
          :step="0.5"
          :aria-label="`${exercise.name}第${setIndex + 1}组重量`"
          @update:model-value="setValue(index, setIndex, 'weightKg', $event)"
        /><UiButton
          v-if="session"
          :kind="set.completed ? 'primary' : 'secondary'"
          :aria-pressed="set.completed"
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
        ><label
          >休息（秒）<UiInput
            :model-value="exercise.restSeconds"
            type="number"
            inputmode="numeric"
            :min="0"
            :max="600"
            :aria-label="`${exercise.name}休息秒数`"
            @update:model-value="change(index, { restSeconds: Number($event) })"
        /></label>
      </footer>
      <UiButton v-if="session" kind="secondary" block @click="emit('rest', exercise.restSeconds)"
        >休息 {{ exercise.restSeconds }} 秒</UiButton
      >
    </article>
  </div>
</template>
<style scoped lang="scss">
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
