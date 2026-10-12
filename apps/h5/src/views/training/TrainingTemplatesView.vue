<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import type { TrainingTemplate } from '@fit-trace/shared';
import { trainingPresets } from '@fit-trace/shared';
import { getTrainingTemplates } from '@/api/training';
import { showRequestError } from '@/utils/request-error';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
const router = useRouter();
const templates = ref<TrainingTemplate[]>(trainingPresets);
const filter = ref('all');
const loading = ref(false);
const error = ref(false);
const visibleTemplates = computed(() =>
  templates.value.filter((t) => filter.value === 'all' || !t.preset),
);
async function load() {
  loading.value = true;
  error.value = false;
  try {
    templates.value = await getTrainingTemplates();
  } catch (e) {
    error.value = true;
    showRequestError(e, '我的模板加载失败');
  } finally {
    loading.value = false;
  }
}
onMounted(load);
</script>
<template>
  <main class="view-page ui-page template-library">
    <UiDetailHeader
      title="训练模板"
      subtitle="预设模板与我的模板"
      action-label="新建"
      @action="router.push('/training/templates/create')"
    /><UiTabs
      v-model="filter"
      :options="[
        { value: 'all', label: '全部模板' },
        { value: 'mine', label: '我的模板' },
      ]"
      label="训练模板分类"
    />
    <p v-if="loading">正在加载我的模板…</p>
    <div v-if="error" class="template-error">
      我的模板加载失败，预设模板仍可查看。<UiButton kind="text" @click="load">重试</UiButton>
    </div>
    <article v-for="template in visibleTemplates" :key="template.id" class="template-card">
      <header>
        <h2>{{ template.name }}</h2>
        <small>{{ template.preset ? '预设' : '我的' }}</small>
      </header>
      <p>{{ template.exercises.length }} 个动作 · 约 {{ template.durationMinutes }} 分钟</p>
      <ul>
        <li v-for="exercise in template.exercises" :key="exercise.exerciseId">
          {{ exercise.name }} · {{ exercise.sets.length }} 组
        </li>
      </ul>
      <footer>
        <UiButton
          kind="text"
          @click="
            router.push({
              path: template.preset
                ? '/training/templates/create'
                : `/training/templates/${template.id}/edit`,
              query: template.preset ? { copy: template.id } : {},
            })
          "
          >{{ template.preset ? '查看 / 另存模板' : '编辑模板' }}</UiButton
        ><UiButton
          @click="router.push({ path: '/training/session', query: { template: template.id } })"
          >开始训练</UiButton
        >
      </footer>
    </article>
    <p v-if="!loading && !error && !visibleTemplates.length">
      还没有自己的模板，先创建一个动作组合。
    </p>
    <UiButton block @click="router.push('/training/templates/create')">创建我的模板</UiButton>
  </main>
</template>
<style scoped lang="scss">
.template-card {
  padding: 20px;
  border-radius: var(--ui-radius-card);
  background: var(--color-surface);
  margin-bottom: 16px;
  header,
  footer {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  h2 {
    margin: 0;
    font-size: 18px;
  }
  small,
  p,
  li {
    font-size: 13px;
    color: var(--ui-text-secondary);
  }
  ul {
    padding-left: 18px;
    line-height: 1.9;
  }
  footer {
    margin-top: 16px;
  }
}
.template-error {
  font-size: 13px;
  margin-bottom: 16px;
  line-height: 1.7;
  color: var(--ui-text-secondary);
}
</style>
