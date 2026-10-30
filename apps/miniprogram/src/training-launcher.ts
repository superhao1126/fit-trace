import { ref } from 'vue';
import type { TrainingTemplate } from '@fit-trace/shared';
import { draftKey, type Draft } from './draft';
import { session } from './session';
export function useTrainingLauncher() {
  const draftPrompt = ref(false);
  function resume() {
    draftPrompt.value = false;
    uni.navigateTo({ url: '/pages/training/index' });
  }
  function start(template?: TrainingTemplate) {
    const owner = session.value?.user.id;
    if (!owner) return;
    if (uni.getStorageSync(draftKey(owner))) {
      draftPrompt.value = true;
      return;
    }
    const draft: Draft = {
      owner,
      name: template?.name ?? '自由训练',
      startedAt: new Date().toISOString(),
      exercises: template ? JSON.parse(JSON.stringify(template.exercises)) : [],
      restEndsAt: null,
    };
    draft.exercises.forEach((e) =>
      e.sets.forEach((s) => {
        s.completed = false;
      }),
    );
    uni.setStorageSync(draftKey(owner), draft);
    resume();
  }
  return { draftPrompt, start, resume };
}
