import { Injectable } from '@nestjs/common';
import type {
  InsightCaloriesSummary,
  InsightDays,
  InsightMealSummary,
  InsightOverview,
  InsightPhotoSummary,
  InsightSeriesPoint,
  InsightWeightSummary,
  InsightWorkoutSummary,
  MealType,
  WorkoutType,
} from '@fit-trace/shared';
import { PrismaService } from '../prisma/prisma.service';

const DAY_MS = 24 * 60 * 60 * 1000;

@Injectable()
export class InsightsService {
  constructor(private readonly prisma: PrismaService) {}

  /**
   * 跨模块趋势：把身体、饮食与训练按同一条时间轴对齐，供前端做关联观察。
   * 所有统计都基于当前用户数据，日期按传入的时区偏移归组。
   */
  async overview(userId: string, days: InsightDays, tzOffset: number): Promise<InsightOverview> {
    const offsetMs = tzOffset * 60 * 1000;
    const now = new Date();
    const todayStart = new Date(
      Math.floor((now.getTime() + offsetMs) / DAY_MS) * DAY_MS - offsetMs,
    );
    const from = new Date(todayStart.getTime() - (days - 1) * DAY_MS);
    const range = { gte: from, lte: now };

    const [bodyRecords, meals, workouts, photos] = await Promise.all([
      this.prisma.bodyRecord.findMany({
        where: { userId, recordedAt: range },
        orderBy: [{ recordedAt: 'asc' }, { createdAt: 'asc' }],
      }),
      this.prisma.mealRecord.findMany({
        where: { userId, recordedAt: range },
        include: { foods: true },
        orderBy: [{ recordedAt: 'asc' }, { createdAt: 'asc' }],
      }),
      this.prisma.workoutRecord.findMany({
        where: { userId, startedAt: range },
        orderBy: [{ startedAt: 'asc' }, { createdAt: 'asc' }],
      }),
      this.prisma.progressPhoto.findMany({
        where: { userId, recordedAt: range },
        orderBy: [{ recordedAt: 'asc' }, { createdAt: 'asc' }],
      }),
    ]);

    const dayKey = (value: Date): string =>
      new Date(value.getTime() + offsetMs).toISOString().slice(0, 10);
    const emptySeries = (): InsightSeriesPoint[] =>
      Array.from({ length: days }, (_, index) => ({
        date: dayKey(new Date(from.getTime() + index * DAY_MS)),
        weight: null,
        calories: null,
        workoutMinutes: 0,
        mealCount: 0,
      }));

    const series = emptySeries();
    const pointByDate = new Map(series.map((point) => [point.date, point]));

    for (const record of bodyRecords) {
      const point = pointByDate.get(dayKey(record.recordedAt));
      if (point) point.weight = Number(record.weight);
    }
    for (const meal of meals) {
      const point = pointByDate.get(dayKey(meal.recordedAt));
      if (!point) continue;
      point.mealCount += 1;
      const calories = meal.foods.flatMap((food) =>
        food.calories === null ? [] : [food.calories],
      );
      if (calories.length > 0) {
        point.calories = (point.calories ?? 0) + calories.reduce((sum, value) => sum + value, 0);
      }
    }
    for (const workout of workouts) {
      const point = pointByDate.get(dayKey(workout.startedAt));
      if (point) point.workoutMinutes += workout.durationMinutes;
    }

    return {
      days,
      from: from.toISOString(),
      to: now.toISOString(),
      series,
      weight: this.summarizeWeight(series, bodyRecords.length),
      calories: this.summarizeCalories(series),
      workouts: this.summarizeWorkouts(workouts),
      meals: this.summarizeMeals(meals, dayKey),
      photos: this.summarizePhotos(photos),
    };
  }

  private summarizeWeight(series: InsightSeriesPoint[], recordCount: number): InsightWeightSummary {
    const values = series.flatMap((point) => (point.weight === null ? [] : [point.weight]));
    if (values.length === 0) {
      return { start: null, current: null, change: null, lowest: null, highest: null, recordCount };
    }
    const start = values[0];
    const current = values.at(-1) ?? start;
    return {
      start,
      current,
      change: values.length > 1 ? Number((current - start).toFixed(2)) : null,
      lowest: Math.min(...values),
      highest: Math.max(...values),
      recordCount,
    };
  }

  private summarizeCalories(series: InsightSeriesPoint[]): InsightCaloriesSummary {
    const values = series.flatMap((point) => (point.calories === null ? [] : [point.calories]));
    if (values.length === 0) return { average: null, highest: null, recordedDays: 0 };
    return {
      average: Math.round(values.reduce((sum, value) => sum + value, 0) / values.length),
      highest: Math.max(...values),
      recordedDays: values.length,
    };
  }

  private summarizeWorkouts(
    workouts: Array<{ type: WorkoutType; durationMinutes: number; calories: number | null }>,
  ): InsightWorkoutSummary {
    const byType = new Map<WorkoutType, { count: number; minutes: number }>();
    for (const workout of workouts) {
      const entry = byType.get(workout.type) ?? { count: 0, minutes: 0 };
      entry.count += 1;
      entry.minutes += workout.durationMinutes;
      byType.set(workout.type, entry);
    }
    const calories = workouts.flatMap((workout) =>
      workout.calories === null ? [] : [workout.calories],
    );
    return {
      count: workouts.length,
      totalMinutes: workouts.reduce((sum, workout) => sum + workout.durationMinutes, 0),
      totalCalories: calories.length > 0 ? calories.reduce((sum, value) => sum + value, 0) : null,
      byType: [...byType.entries()]
        .map(([type, entry]) => ({ type, ...entry }))
        .sort((a, b) => b.count - a.count),
    };
  }

  private summarizeMeals(
    meals: Array<{ type: MealType; recordedAt: Date }>,
    dayKey: (value: Date) => string,
  ): InsightMealSummary {
    const byType = new Map<MealType, number>();
    const dates = new Set<string>();
    for (const meal of meals) {
      byType.set(meal.type, (byType.get(meal.type) ?? 0) + 1);
      dates.add(dayKey(meal.recordedAt));
    }
    return {
      count: meals.length,
      recordedDays: dates.size,
      byType: [...byType.entries()]
        .map(([type, count]) => ({ type, count }))
        .sort((a, b) => b.count - a.count),
    };
  }

  private summarizePhotos(photos: Array<{ recordedAt: Date }>): InsightPhotoSummary {
    return {
      count: photos.length,
      latestRecordedAt: photos.at(-1)?.recordedAt.toISOString() ?? null,
    };
  }
}
