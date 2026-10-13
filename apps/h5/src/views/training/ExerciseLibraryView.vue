<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import UiBottomSheet from '@/components/ui/UiBottomSheet.vue';
import { exerciseLibrary, type Exercise } from '@fit-trace/shared';
import UiDetailHeader from '@/components/ui/UiDetailHeader.vue';
import UiButton from '@/components/ui/UiButton.vue';
import UiTabs from '@/components/ui/UiTabs.vue';
import UiInput from '@/components/ui/UiInput.vue';
import UiRecordRow from '@/components/ui/UiRecordRow.vue';
const router = useRouter();
const search = ref('');
const bodyPart = ref('全部');
const equipment = ref('全部器械');
const selected = ref<Exercise | null>(null);
const parts = ['全部', ...new Set(exerciseLibrary.map((e) => e.bodyPart))].map((value) => ({
  value,
  label: value,
}));
const equipments = ['全部器械', ...new Set(exerciseLibrary.map((e) => e.equipment))].map(
  (value) => ({ value, label: value }),
);
const exercises = computed(() =>
  exerciseLibrary.filter(
    (e) =>
      e.name.includes(search.value.trim()) &&
      (bodyPart.value === '全部' || bodyPart.value === e.bodyPart) &&
      (equipment.value === '全部器械' || equipment.value === e.equipment),
  ),
);
</script>
<template>
  <main class="view-page ui-page exercise-library">
    <UiDetailHeader title="动作库" subtitle="按部位与器械选择，查看动作要点" />
    <UiInput v-model="search" aria-label="搜索动作" placeholder="搜索动作名称" />
    <div class="filter-scroll"><UiTabs v-model="bodyPart" :options="parts" label="目标部位" /></div>
    <UiTabs v-model="equipment" :options="equipments" label="训练器械" />
    <div class="exercise-list">
      <UiRecordRow
        v-for="exercise in exercises"
        :key="exercise.id"
        kind="workout"
        :title="exercise.name"
        :subtitle="`${exercise.bodyPart} · ${exercise.equipment} · ${exercise.difficulty}`"
        @click="selected = exercise"
      />
      <p v-if="!exercises.length">没有匹配的动作，试试其他条件</p>
    </div>
    <UiBottomSheet
      :visible="Boolean(selected)"
      :title="selected?.name || '动作详情'"
      @update:visible="!$event && (selected = null)"
    >
      <section v-if="selected" class="exercise-detail">
        <p>{{ selected.bodyPart }} · {{ selected.equipment }} · {{ selected.difficulty }}</p>
        <h3>动作步骤</h3>
        <ol>
          <li v-for="step in selected.steps" :key="step">{{ step }}</li>
        </ol>
        <h3>动作提示</h3>
        <p>{{ selected.tips }}</p>
        <a :href="selected.sourceUrl" target="_blank" rel="noopener noreferrer"
          >查看 ACE 动作参考</a
        >
        <UiButton
          block
          @click="
            router.push({ path: '/training/templates/create', query: { exercise: selected.id } })
          "
          >加入我的模板</UiButton
        >
      </section>
    </UiBottomSheet>
  </main>
</template>
<style scoped lang="scss">
.exercise-library > .ui-input {
  margin-bottom: 16px;
}
.filter-scroll {
  overflow-x: auto;
  :deep(.ui-tabs) {
    min-width: 400px;
  }
}
.exercise-list {
  display: grid;
  gap: 12px;
}
.exercise-detail {
  padding: 0;
  header {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  h2 {
    font-size: 22px;
    margin: 0;
  }
  h3 {
    font-size: 16px;
  }
  p,
  li {
    font-size: 14px;
    line-height: 1.8;
    color: var(--ui-text-secondary);
  }
  li {
    margin: 8px 0;
  }
  a {
    display: block;
    font-size: 13px;
    color: var(--ui-primary);
    margin: 20px 0;
  }
}
</style>
