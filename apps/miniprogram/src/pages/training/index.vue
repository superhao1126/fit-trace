<script setup lang="ts">
import { computed, ref, watch, onUnmounted } from 'vue';
import { onShow, onHide } from '@dcloudio/uni-app';
import {
  exerciseLibrary,
  makeTrainingExercise,
  type PreviousExercise,
  type WorkoutRecord,
} from '@fit-trace/shared';
import { draftKey, restRemaining, validateDraft, readDraft, type Draft } from '../../draft';
import { session, request, notify } from '../../session';
import { clock } from '../../presentation';
import FitShell from '../../components/FitShell.vue';
import FitState from '../../components/FitState.vue';
const owner = session.value?.user.id ?? '';
const draft = ref<Draft | null>(readDraft(uni.getStorageSync(draftKey(owner)), owner));
watch(
  () => session.value?.user.id,
  (id) => {
    if (id !== owner) draft.value = null;
  },
);
const search = ref(''),
  choosing = ref(false),
  saving = ref(false),
  now = ref(Date.now());
const previous = ref<Record<string, PreviousExercise | null>>({});
const previousError = ref<Record<string, string>>({});
const remaining = computed(() => restRemaining(draft.value?.restEndsAt ?? null, now.value));
const choices = computed(() =>
  exerciseLibrary
    .filter((e) => e.name.includes(search.value) || e.bodyPart.includes(search.value))
    .slice(0, 40),
);
let timer: ReturnType<typeof setInterval> | undefined;
function persist() {
  if (draft.value && owner && session.value?.user.id === owner)
    uni.setStorageSync(draftKey(owner), JSON.parse(JSON.stringify(draft.value)));
}
watch(draft, persist, { deep: true, flush: 'sync' });
async function loadPrevious() {
  for (const exercise of draft.value?.exercises ?? []) {
    if (exercise.exerciseId in previous.value) continue;
    try {
      delete previousError.value[exercise.exerciseId];
      previous.value[exercise.exerciseId] = await request(
        `/training/exercises/${encodeURIComponent(exercise.exerciseId)}/previous`,
      );
    } catch (e) {
      previousError.value[exercise.exerciseId] = e instanceof Error ? e.message : '读取失败';
    }
  }
}
function add(id: string) {
  if (!draft.value || draft.value.exercises.length >= 20)
    return notify(new Error('最多 20 个动作'));
  const exercise = exerciseLibrary.find((e) => e.id === id)!;
  if (draft.value.exercises.some((e) => e.exerciseId === id))
    return notify(new Error('已添加该动作'));
  draft.value.exercises.push(makeTrainingExercise(exercise));
  expanded.value = id;
  choosing.value = false;
  void loadPrevious();
}
const guideTitle = ref(''),
  guideContent = ref(''),
  showGuide = ref(false),
  askDiscard = ref(false),
  expanded = ref(draft.value?.exercises[0]?.exerciseId ?? '');
const completed = computed(
  () =>
    draft.value?.exercises.reduce((n, e) => n + e.sets.filter((s) => s.completed).length, 0) ?? 0,
);
const total = computed(() => draft.value?.exercises.reduce((n, e) => n + e.sets.length, 0) ?? 0);
const elapsed = computed(() =>
  draft.value ? clock(Math.floor((now.value - Date.parse(draft.value.startedAt)) / 1000)) : '00:00',
);
function guide(id: string) {
  const e = exerciseLibrary.find((e) => e.id === id);
  if (e) {
    guideTitle.value = e.name;
    guideContent.value = [...e.steps, e.tips].join('\n');
    showGuide.value = true;
  }
}
function removeExercise(index: number) {
  if (!draft.value || saving.value) return;
  draft.value.exercises.splice(index, 1);
  if (!draft.value.exercises.some((e) => e.exerciseId === expanded.value))
    expanded.value = draft.value.exercises[0]?.exerciseId ?? '';
}
function toggle(e: number, s: number) {
  if (!draft.value || saving.value) return;
  const exercise = draft.value.exercises[e];
  const set = exercise.sets[s];
  set.completed = !set.completed;
  now.value = Date.now();
  if (set.completed) draft.value.restEndsAt = now.value + exercise.restSeconds * 1000;
}
async function save() {
  if (!draft.value || saving.value) return;
  const invalid = validateDraft(draft.value);
  if (invalid) return notify(new Error(invalid));
  saving.value = true;
  try {
    const d = draft.value;
    d.clientRequestId ??= `mp-${Date.now().toString(36)}-${Math.random().toString(36).slice(2)}-${Math.random().toString(36).slice(2)}`;
    persist();
    const saved = await request<WorkoutRecord>(
      d.workoutId ? `/workouts/${d.workoutId}` : '/workouts',
      d.workoutId ? 'PUT' : 'POST',
      {
        ...(!d.workoutId ? { clientRequestId: d.clientRequestId } : {}),
        type: d.type ?? 'STRENGTH',
        name: d.name.trim(),
        startedAt: d.startedAt,
        durationMinutes: d.workoutId
          ? d.durationMinutes
          : Math.min(1440, Math.max(1, Math.round((Date.now() - Date.parse(d.startedAt)) / 60000))),
        exercises: d.exercises,
        note: d.note ?? '',
      },
    );
    draft.value = null;
    uni.removeStorageSync(draftKey(owner));
    uni.showToast({ title: '训练已保存' });
    uni.redirectTo({ url: `/pages/history/detail?id=${saved.id}` });
  } catch (e) {
    notify(e);
  } finally {
    saving.value = false;
  }
}
function discard() {
  draft.value = null;
  uni.removeStorageSync(draftKey(owner));
  askDiscard.value = false;
  if (getCurrentPages().length > 1) uni.navigateBack();
  else uni.reLaunch({ url: '/pages/index/index' });
}
onShow(() => {
  if (session.value?.user.id !== owner) {
    draft.value = null;
    return;
  }
  now.value = Date.now();
  clearInterval(timer);
  timer = setInterval(() => {
    now.value = Date.now();
  }, 1000);
  void loadPrevious();
});
onHide(() => {
  persist();
  clearInterval(timer);
});
onUnmounted(() => clearInterval(timer));
</script>
<template>
  <FitShell title="力量训练" back>
    <FitState v-if="!draft" empty="没有可恢复的训练草稿" />
    <view v-if="!draft" class="spaced"
      ><t-button theme="primary" variant="light" shape="round" block @click="askDiscard = true"
        >清除失效草稿并返回</t-button
      ></view
    >
    <view v-else class="has-bottom-action">
      <view class="training-name"
        ><t-input
          v-model:value="draft.name"
          :maxlength="60"
          :disabled="saving"
          borderless
          placeholder="训练名称"
      /></view>
      <view class="sub"
        >{{ draft.workoutId ? '编辑已保存训练' : '已训练 ' + elapsed }} · 已完成 {{ completed }} /
        {{ total }} 组</view
      >
      <view v-if="remaining" class="card tint rest-banner between"
        ><text>组间休息 · {{ clock(remaining) }}</text
        ><text class="link" @tap="draft.restEndsAt = null">跳过</text></view
      >
      <view
        v-for="(exercise, ei) in draft.exercises"
        :key="exercise.exerciseId"
        class="card exercise-card"
      >
        <view class="between exercise-heading"
          ><view
            class="flex-1"
            @tap="expanded = expanded === exercise.exerciseId ? '' : exercise.exerciseId"
            ><text>{{ String(ei + 1).padStart(2, '0') }} {{ exercise.name }}</text></view
          ><text class="link" @tap="guide(exercise.exerciseId)">动作说明 ›</text></view
        >
        <template v-if="expanded === exercise.exerciseId">
          <view class="caption previous" @tap="loadPrevious">{{
            previousError[exercise.exerciseId]
              ? previousError[exercise.exerciseId] + '（点按重试）'
              : previous[exercise.exerciseId] === undefined
                ? '正在读取上次训练…'
                : previous[exercise.exerciseId]
                  ? '上次：' +
                    previous[exercise.exerciseId]!.sets.map(
                      (s) =>
                        s.weightKg +
                        ' kg × ' +
                        s.reps +
                        (exercise.unit === 'seconds' ? ' 秒' : ' 次'),
                    ).join('，')
                  : '从这一次开始积累成绩'
          }}</view>
          <view class="between rest-setting"
            ><text class="caption">组间休息（秒）</text
            ><t-stepper
              :value="exercise.restSeconds"
              :min="0"
              :max="600"
              :step="15"
              :disabled="saving"
              @change="exercise.restSeconds = Number($event.value)"
          /></view>
          <view class="set-row set-label"
            ><text class="set-index">组</text><text class="set-field">重量 kg</text
            ><text class="set-field">{{ exercise.unit === 'seconds' ? '秒数' : '次数' }}</text
            ><text class="set-check">完成</text><text class="set-remove"
          /></view>
          <view
            v-for="(set, si) in exercise.sets"
            :key="si"
            class="set-row"
            :class="{ done: set.completed }"
          >
            <text class="set-index">{{ si + 1 }}</text>
            <view class="set-field"
              ><t-input
                :value="set.weightKg"
                type="digit"
                align="center"
                :disabled="saving"
                borderless
                :maxlength="7"
                @change="set.weightKg = Number($event.value)"
            /></view>
            <view class="set-field"
              ><t-input
                :value="set.reps"
                type="number"
                align="center"
                :disabled="saving"
                borderless
                :maxlength="4"
                @change="set.reps = Number($event.value)"
            /></view>
            <view
              class="set-check"
              aria-role="button"
              :aria-label="set.completed ? '取消完成' : '标记完成'"
              @tap="toggle(ei, si)"
              ><t-icon :name="set.completed ? 'check' : 'circle'" color="#24c867" size="18px"
            /></view>
            <view
              class="set-remove"
              aria-role="button"
              aria-label="删除这一组"
              @tap="!saving && exercise.sets.length > 1 && exercise.sets.splice(si, 1)"
              ><t-icon name="remove" size="16px" color="#999"
            /></view>
          </view>
          <view class="spaced"
            ><t-button
              theme="primary"
              variant="light"
              shape="round"
              block
              :disabled="saving || exercise.sets.length >= 20"
              @click="exercise.sets.push({ reps: 10, weightKg: 0, completed: false })"
              >＋ 增加一组</t-button
            ></view
          >
          <view class="between exercise-footer"
            ><text class="caption" @tap="expanded = ''">收起动作</text
            ><text class="caption danger" @tap="removeExercise(ei)">移除动作</text></view
          >
        </template>
        <view v-else class="between spaced" @tap="expanded = exercise.exerciseId"
          ><text class="sub"
            >{{ exercise.sets.length }} 组 ·
            {{ exercise.sets.filter((s) => s.completed).length }} 组完成</text
          ><text class="link">展开录入 ›</text></view
        >
      </view>
      <view class="spaced"
        ><t-button
          theme="primary"
          variant="light"
          size="large"
          shape="round"
          block
          :disabled="saving"
          @click="choosing = true"
          >＋ 添加动作</t-button
        ></view
      >
      <view class="caption spaced">已选 {{ draft.exercises.length }} 个动作 · 草稿自动保存</view>
      <view v-if="draft.workoutId" class="card"
        ><text class="field-label">训练时长（分钟）</text
        ><t-input
          :value="draft.durationMinutes"
          type="number"
          :disabled="saving"
          borderless
          @change="draft.durationMinutes = Number($event.value)"
      /></view>
      <view class="card note-card"
        ><t-input
          v-model:value="draft.note"
          :maxlength="500"
          :disabled="saving"
          borderless
          placeholder="训练备注（可选）"
      /></view>
      <view class="spaced"
        ><t-button
          theme="danger"
          variant="text"
          shape="round"
          block
          :disabled="saving"
          @click="askDiscard = true"
          >放弃本次草稿</t-button
        ></view
      >
      <view class="bottom-action"
        ><t-button
          theme="primary"
          shape="round"
          size="large"
          block
          :loading="saving"
          :disabled="saving || !completed"
          @click="save"
          >{{ draft.workoutId ? '保存修改' : '完成并核对' }}</t-button
        ></view
      >
    </view>
    <template #overlay>
      <t-popup :visible="choosing" placement="bottom" @visible-change="choosing = $event.visible"
        ><view class="sheet"
          ><view class="heading">添加训练动作</view
          ><t-search v-model:value="search" placeholder="搜索动作 / 身体部位" /><scroll-view
            scroll-y
            class="sheet-scroll spaced"
            ><t-cell
              v-for="e in choices"
              :key="e.id"
              :title="e.name"
              :description="e.bodyPart"
              :bordered="false"
              arrow
              @click="add(e.id)"
            /><view v-if="!choices.length" class="sub spaced"
              >没有找到匹配动作，试试其他名称或部位</view
            ></scroll-view
          ></view
        ></t-popup
      >
      <t-dialog
        :visible="showGuide"
        :title="guideTitle"
        :content="guideContent"
        confirm-btn="知道了"
        @confirm="showGuide = false"
        @close="showGuide = false"
      />
      <t-dialog
        :visible="askDiscard"
        title="放弃训练草稿？"
        content="尚未保存的修改将被清除，已保存的历史记录不受影响。"
        confirm-btn="放弃草稿"
        cancel-btn="继续训练"
        @confirm="discard"
        @close="askDiscard = false"
      />
    </template>
  </FitShell>
</template>
<style scoped>
.training-name {
  --td-input-vertical-padding: 0;
  --td-font-body-large: 700 27px / 1.45 sans-serif;
  --td-input-bg-color: transparent;
  margin-bottom: 6px;
}
.rest-banner {
  padding: 8px 12px;
  font-size: 12px;
  color: #229d60;
}
.exercise-card {
  padding: 16px;
}
.exercise-heading {
  font-size: 14px;
  font-weight: 600;
}
.exercise-heading .link {
  font-weight: 400;
  flex-shrink: 0;
}
.previous {
  margin-top: 8px;
}
.rest-setting {
  margin-top: 12px;
  --td-stepper-input-width: 32px;
  --td-stepper-input-height: 26px;
  --td-stepper-border-radius: 6px;
}
.set-row {
  display: flex;
  gap: 4px;
  align-items: center;
  min-height: 40px;
  margin-top: 8px;
  text-align: center;
  border-radius: 12px;
  background: #f1f3f5;
  font-size: 13px;
  --td-input-vertical-padding: 4px 0;
  --td-input-bg-color: transparent;
  --td-font-body-large: 400 14px / 1.45 sans-serif;
}
.set-index {
  width: 20px;
  flex-shrink: 0;
}
.set-field {
  flex: 1;
  min-width: 0;
}
.set-check {
  width: 36px;
  flex-shrink: 0;
}
.set-remove {
  width: 22px;
  flex-shrink: 0;
}
.set-row.done {
  background: #e6f8ee;
}
.set-label {
  color: #777;
  font-size: 11px;
  background: transparent;
  min-height: 20px;
  margin-top: 12px;
}
.set-check,
.set-remove {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 40px;
}
.exercise-footer {
  margin-top: 14px;
}
.note-card {
  padding: 8px 12px;
}
</style>
