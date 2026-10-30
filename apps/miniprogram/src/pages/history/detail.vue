<script setup lang="ts">
import { ref } from 'vue';
import { onLoad, onShow } from '@dcloudio/uni-app';
import type { TrainingExercise, WorkoutRecord } from '@fit-trace/shared';
import { request, notify, session } from '../../session';
import { draftKey, type Draft } from '../../draft';
import { dateTime, completedSets, workoutNames } from '../../presentation';
import FitShell from '../../components/FitShell.vue';
import FitState from '../../components/FitState.vue';
const expanded = ref<string[]>([]);
function summary(e: TrainingExercise) {
  const first = e.sets[0];
  return first && e.sets.every((s) => s.reps === first.reps && s.weightKg === first.weightKg)
    ? first.weightKg +
        ' kg × ' +
        first.reps +
        (e.unit === 'seconds' ? ' 秒 · ' : ' 次 · ') +
        e.sets.length +
        ' 组'
    : e.sets.length + ' 组 · ' + e.sets.filter((s) => s.completed).length + ' 组完成';
}
function toggleExercise(id: string) {
  expanded.value = expanded.value.includes(id)
    ? expanded.value.filter((x) => x !== id)
    : [...expanded.value, id];
}
const confirmDelete = ref(false),
  loading = ref(false);
const metadataEditing = ref(false),
  name = ref(''),
  minutes = ref(1),
  note = ref('');
async function saveMetadata() {
  if (!record.value || busy.value) return;
  if (
    !name.value.trim() ||
    name.value.length > 60 ||
    !Number.isInteger(minutes.value) ||
    minutes.value < 1 ||
    minutes.value > 1440
  )
    return notify(new Error('请检查名称与时长（1–1440 分钟）'));
  busy.value = true;
  try {
    record.value = await request(`/workouts/${id}`, 'PUT', {
      name: name.value.trim(),
      durationMinutes: minutes.value,
      note: note.value,
    });
    metadataEditing.value = false;
  } catch (e) {
    notify(e);
  } finally {
    busy.value = false;
  }
}
const record = ref<WorkoutRecord | null>(null),
  error = ref(''),
  busy = ref(false);
let id = '';
async function load() {
  if (!id) {
    error.value = '缺少训练记录编号';
    return;
  }
  loading.value = true;
  try {
    record.value = await request(`/workouts/${id}`);
    error.value = '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
function edit() {
  if (!record.value || !session.value) return;
  if (!record.value.exercises?.length) {
    name.value = record.value.name;
    minutes.value = record.value.durationMinutes;
    note.value = record.value.note ?? '';
    metadataEditing.value = true;
    return;
  }
  const owner = session.value.user.id;
  if (uni.getStorageSync(draftKey(owner))) return notify(new Error('请先保存或放弃当前训练草稿'));
  const r = record.value;
  const draft: Draft = {
    owner,
    type: r.type,
    name: r.name,
    startedAt: r.startedAt,
    durationMinutes: r.durationMinutes,
    note: r.note ?? undefined,
    workoutId: r.id,
    exercises: JSON.parse(JSON.stringify(r.exercises)),
    restEndsAt: null,
  };
  uni.setStorageSync(draftKey(owner), draft);
  uni.navigateTo({ url: '/pages/training/index' });
}
async function remove() {
  if (busy.value) return;
  busy.value = true;
  try {
    await request(`/workouts/${id}`, 'DELETE');
    confirmDelete.value = false;
    if (getCurrentPages().length > 1) uni.navigateBack();
    else uni.reLaunch({ url: '/pages/history/index' });
  } catch (e) {
    notify(e);
  } finally {
    busy.value = false;
  }
}
onLoad((options) => {
  id = options?.id ?? '';
});
onShow(() => {
  void load();
});
</script>
<template>
  <FitShell title="训练详情" back>
    <FitState :loading="loading && !record" :error="error" @retry="load" />
    <template v-if="record">
      <view class="section-intro"
        ><view class="title"
          >{{ record.name
          }}<text v-if="!record.name.includes(workoutNames[record.type])">
            · {{ workoutNames[record.type] }}</text
          ></view
        ><view class="sub">{{ dateTime(record.startedAt) }}</view></view
      >
      <view class="card tint result-card"
        ><view class="between accent"
          ><text>✓ 已保存</text><text>{{ workoutNames[record.type] }}</text></view
        ><view class="duration accent"
          ><text class="number">{{ record.durationMinutes }}</text
          ><text>分钟</text></view
        ><view class="sub accent">{{
          record.exercises?.length
            ? record.exercises.length + ' 个动作 / ' + completedSets(record) + ' 组完成'
            : '基础训练记录'
        }}</view></view
      >
      <view
        v-for="e in record.exercises"
        :key="e.exerciseId"
        class="card detail-exercise"
        @tap="toggleExercise(e.exerciseId)"
      >
        <view class="between"
          ><view class="heading">{{ e.name }}</view
          ><t-icon
            :name="expanded.includes(e.exerciseId) ? 'chevron-up' : 'chevron-down'"
            color="#999"
            size="16px"
        /></view>
        <view class="sub spaced">{{ summary(e) }}</view>
        <view v-if="expanded.includes(e.exerciseId)"
          ><view v-for="(s, i) in e.sets" :key="i" class="between set-detail"
            ><text class="sub"
              >第 {{ i + 1 }} 组 · {{ s.weightKg }} kg × {{ s.reps }}
              {{ e.unit === 'seconds' ? '秒' : '次' }}</text
            ><text class="caption" :class="{ accent: s.completed }">{{
              s.completed ? '已完成' : '未完成'
            }}</text></view
          ></view
        >
      </view>
      <view v-if="record.note" class="caption spaced note">备注：{{ record.note }}</view>
      <view class="row spaced"
        ><view class="flex-1"
          ><t-button
            theme="primary"
            variant="light"
            shape="round"
            size="large"
            block
            :disabled="busy"
            @click="edit"
            >修改记录</t-button
          ></view
        ><view class="flex-1"
          ><t-button
            theme="danger"
            variant="outline"
            shape="round"
            size="large"
            block
            :disabled="busy"
            @click="confirmDelete = true"
            >删除记录</t-button
          ></view
        ></view
      >
    </template>
    <template #overlay>
      <t-popup
        :visible="metadataEditing"
        placement="bottom"
        :close-on-overlay-click="!busy"
        @visible-change="!busy && (metadataEditing = $event.visible)"
        ><view class="sheet stack"
          ><view class="heading">修改训练记录</view
          ><view
            ><text class="field-label">训练名称</text
            ><view class="form-field"
              ><t-input
                v-model:value="name"
                :maxlength="60"
                :disabled="busy"
                borderless /></view></view
          ><view
            ><text class="field-label">时长（分钟）</text
            ><view class="form-field"
              ><t-input
                :value="minutes"
                type="number"
                :disabled="busy"
                borderless
                @change="minutes = Number($event.value)" /></view></view
          ><view class="form-field"
            ><t-input
              v-model:value="note"
              placeholder="备注（可选）"
              :maxlength="500"
              :disabled="busy"
              borderless /></view
          ><t-button
            theme="primary"
            shape="round"
            size="large"
            block
            :loading="busy"
            :disabled="busy"
            @click="saveMetadata"
            >保存修改</t-button
          ></view
        ></t-popup
      >
      <t-dialog
        :visible="confirmDelete"
        title="删除这条训练记录？"
        content="删除后无法恢复，请确认后再继续。"
        :confirm-btn="{ content: '确认删除', theme: 'danger', loading: busy, disabled: busy }"
        cancel-btn="取消"
        @confirm="remove"
        @close="!busy && (confirmDelete = false)"
      />
    </template>
  </FitShell>
</template>
<style scoped>
.result-card {
  font-size: 13px;
}
.duration {
  display: flex;
  align-items: center;
  gap: 16px;
  margin: 12px 0;
}
.result-card .accent {
  color: #229d60;
}
.detail-exercise {
  padding: 18px 20px;
}
.detail-exercise > .spaced {
  margin-top: 6px;
}
.set-detail {
  margin-top: 8px;
}
.note {
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
