import type { WorkoutRecord, WorkoutType, BodyRecord } from '@fit-trace/shared';
export const dayNames = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];
export const workoutNames: Record<WorkoutType, string> = {
  STRENGTH: '力量训练',
  CARDIO: '有氧训练',
  RUNNING: '跑步',
  CYCLING: '骑行',
  SWIMMING: '游泳',
  OTHER: '其他训练',
};
export function shortDate(value: string | Date) {
  const d = new Date(value);
  return d.getMonth() + 1 + '月' + d.getDate() + '日';
}
export function dateTime(value: string) {
  const d = new Date(value);
  return (
    shortDate(d) +
    ' ' +
    String(d.getHours()).padStart(2, '0') +
    ':' +
    String(d.getMinutes()).padStart(2, '0')
  );
}
export function clock(seconds: number) {
  const n = Math.max(0, Math.floor(seconds));
  return String(Math.floor(n / 60)).padStart(2, '0') + ':' + String(n % 60).padStart(2, '0');
}
export function weekDays(now = new Date()) {
  const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
  return Array.from({ length: 7 }, (_, i) => {
    const d = new Date(monday);
    d.setDate(d.getDate() + i);
    return {
      timestamp: d.getTime(),
      fullLabel: shortDate(d) + ' · ' + dayNames[d.getDay()],
      weekday: d.getDay(),
      label: dayNames[d.getDay()].slice(1),
      date: d.getDate(),
      today: d.toDateString() === now.toDateString(),
    };
  });
}
export function completedSets(record: Pick<WorkoutRecord, 'exercises'>) {
  return record.exercises?.reduce((n, e) => n + e.sets.filter((s) => s.completed).length, 0) ?? 0;
}
export function exerciseSummary(record: Pick<WorkoutRecord, 'exercises'>) {
  return record.exercises?.length
    ? record.exercises.length + ' 个动作 · ' + completedSets(record) + ' 组完成'
    : '基础训练记录';
}
export function monthRange(value: string) {
  const [year, month] = value.split('-').map(Number);
  return (
    '?from=' +
    encodeURIComponent(new Date(year, month - 1, 1).toISOString()) +
    '&to=' +
    encodeURIComponent(new Date(year, month, 1).toISOString())
  );
}
export function weightChart(records: BodyRecord[]) {
  const values = records.slice(0, 7).reverse();
  if (values.length < 2) return { segments: [], points: [] };
  const min = Math.min(...values.map((r) => r.weight)),
    max = Math.max(...values.map((r) => r.weight));
  const points = values.map((r, i) => ({
    x: 4 + (i * 292) / (values.length - 1),
    y: 16 + (max === min ? 34 : ((max - r.weight) / (max - min)) * 68),
  }));
  const segments = points.slice(1).map((p, i) => {
    const prev = points[i],
      dx = p.x - prev.x,
      dy = p.y - prev.y;
    return {
      x: prev.x,
      y: prev.y,
      length: Math.sqrt(dx * dx + dy * dy),
      angle: (Math.atan2(dy, dx) * 180) / Math.PI,
    };
  });
  return { points, segments };
}
