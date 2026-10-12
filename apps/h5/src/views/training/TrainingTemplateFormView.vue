<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ActionSheet, ToastPlugin } from 'tdesign-mobile-vue';
import {
  exerciseLibrary,
  makeTrainingExercise,
  trainingPresets,
  type TrainingExercise,
} from '@fit-trace/shared';
import { getTrainingTemplates, saveTrainingTemplate } from '@/api/training';
import { showRequestError } from '@/utils/request-error';
import { trainingValidation } from '@/utils/training-validation';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiButton from '@/components/ui/UiButton.vue';
import TrainingExerciseEditor from '@/components/TrainingExerciseEditor.vue';
const route = useRoute();
const router = useRouter();
const name = ref('');
const duration = ref<string | number>(30);
const exercises = ref<TrainingExercise[]>([]);
const picker = ref(false);
const loading = ref(false);
const error = ref(false);
const saving = ref(false);
const id = typeof route.params.id === 'string' ? route.params.id : undefined;
async function load() {
  loading.value = true;
  error.value = false;
  try {
    const source = id
      ? (await getTrainingTemplates()).find((t) => t.id === id)
      : trainingPresets.find((t) => t.id === route.query.copy);
    if (id && !source) throw Error('模板不存在');
    if (source) {
      name.value = source.preset ? `${source.name} · 我的` : source.name;
      duration.value = source.durationMinutes;
      exercises.value = structuredClone(source.exercises);
    } else {
      const e = exerciseLibrary.find((e) => e.id === route.query.exercise);
      if (e) exercises.value = [makeTrainingExercise(e)];
    }
  } catch (e) {
    error.value = true;
    showRequestError(e, '模板加载失败');
  } finally {
    loading.value = false;
  }
}
function add(_selected: unknown, index: number) {
  picker.value = false;
  if (exercises.value.length >= 20) {
    ToastPlugin.warning('单次最多 20 个动作');
    return;
  }
  exercises.value.push(makeTrainingExercise(exerciseLibrary[index]));
}
async function save() {
  if (saving.value || loading.value || error.value) return;
  const errorMessage = !name.value.trim() ? '请输入模板名称' : trainingValidation(exercises.value);
  if (errorMessage) {
    ToastPlugin.warning(errorMessage);
    return;
  }
  const minutes = Number(duration.value);
  if (!Number.isInteger(minutes) || minutes < 1 || minutes > 1440) {
    ToastPlugin.warning('预计时长需为 1 至 1440 分钟的整数');
    return;
  }
  saving.value = true;
  try {
    await saveTrainingTemplate(
      { name: name.value.trim(), durationMinutes: minutes, exercises: exercises.value },
      id,
    );
    ToastPlugin.success('训练模板已保存');
    await router.replace('/training/templates');
  } catch (e) {
    showRequestError(e, '模板保存失败，请重试');
  } finally {
    saving.value = false;
  }
}
onMounted(load);
</script>
<template>
  <main class="view-page ui-page template-form">
    <UiDetailHeader :title="id ? '编辑训练模板' : '创建训练模板'" subtitle="名称、动作与训练参数" />
    <p v-if="loading">正在读取模板…</p>
    <div v-if="error">模板加载失败<UiButton kind="text" @click="load">重试</UiButton></div>
    <template v-else-if="!loading"
      ><label
        >模板名称<UiInput
          v-model="name"
          :maxlength="60"
          aria-label="模板名称"
          placeholder="例如：我的全身训练" /></label
      ><TrainingExerciseEditor v-model="exercises" /><UiButton
        block
        kind="secondary"
        :disabled="exercises.length >= 20"
        @click="picker = true"
        >＋ 从动作库添加</UiButton
      ><label
        >预计时长（分钟）<UiInput
          v-model="duration"
          type="number"
          inputmode="numeric"
          :min="1"
          :max="1440"
          aria-label="模板预计时长" /></label
      ><UiButton block :loading="saving" @click="save">保存模板</UiButton></template
    ><ActionSheet
      v-model:visible="picker"
      :items="exerciseLibrary.map((e) => `${e.name} · ${e.equipment}`)"
      cancel-text="取消"
      @selected="add"
    />
  </main>
</template>
<style scoped lang="scss">
.template-form {
  display: grid;
  gap: 20px;
}
.template-form label {
  display: grid;
  gap: 8px;
  font-size: 13px;
  color: var(--ui-text-secondary);
}
</style>
