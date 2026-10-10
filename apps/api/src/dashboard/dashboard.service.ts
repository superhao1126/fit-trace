import { BadRequestException, Injectable } from '@nestjs/common';
import type { DashboardOverview } from '@fit-trace/shared';
import { PrismaService } from '../prisma/prisma.service';

@Injectable()
export class DashboardService {
  constructor(private readonly prisma: PrismaService) {}

  async overview(
    userId: string,
    tzOffset: number,
    selectedDate?: string,
  ): Promise<DashboardOverview> {
    const now = new Date();
    const offset = tzOffset * 60_000;
    const today = new Date(now.getTime() + offset).toISOString().slice(0, 10);
    const date = selectedDate ?? today;
    if (
      !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
      !Number.isFinite(Date.parse(`${date}T00:00:00Z`)) ||
      new Date(`${date}T00:00:00Z`).toISOString().slice(0, 10) !== date ||
      date > today
    ) {
      throw new BadRequestException({
        code: 'VALIDATION_ERROR',
        message: '请选择今天或过去的日期',
      });
    }
    const from = new Date(`${date}T00:00:00.000Z`).getTime() - offset;
    const until = new Date(Math.min(now.getTime(), from + 86_400_000 - 1));
    const todayRange = { gte: new Date(from), lte: until };
    const recentRange = { gte: new Date(from - 6 * 86_400_000), lte: until };
    const dayKey = (value: Date): string =>
      new Date(value.getTime() + offset).toISOString().slice(0, 10);
    const orderBy = [{ recordedAt: 'desc' }, { createdAt: 'desc' }] as const;
    const [
      bodies,
      meals,
      workoutSummary,
      latestMeal,
      latestWorkout,
      latestPhoto,
      recentBodies,
      recentMeals,
      recentWorkouts,
      dayWorkouts,
      dayPhotos,
    ] = await this.prisma.$transaction(
      async (db) =>
        Promise.all([
          db.bodyRecord.findMany({
            where: { userId, recordedAt: { lte: until } },
            orderBy: [...orderBy],
            take: 2,
            select: { id: true, weight: true, recordedAt: true },
          }),
          db.mealRecord.findMany({
            where: { userId, recordedAt: todayRange },
            orderBy: [...orderBy],
            select: {
              id: true,
              type: true,
              recordedAt: true,
              foods: { orderBy: { createdAt: 'asc' }, select: { name: true, calories: true } },
            },
          }),
          db.workoutRecord.aggregate({
            where: { userId, startedAt: todayRange },
            _count: true,
            _sum: { durationMinutes: true },
          }),
          db.mealRecord.findFirst({
            where: { userId, recordedAt: { lte: until } },
            orderBy: [...orderBy],
            select: { id: true, type: true, recordedAt: true },
          }),
          db.workoutRecord.findFirst({
            where: { userId, startedAt: { lte: until } },
            orderBy: [{ startedAt: 'desc' }, { createdAt: 'desc' }],
            select: { id: true, type: true, startedAt: true },
          }),
          db.progressPhoto.findFirst({
            where: { userId, recordedAt: { lte: until } },
            orderBy: [...orderBy],
            select: { id: true, recordedAt: true },
          }),
          db.bodyRecord.findMany({
            where: { userId, recordedAt: recentRange },
            orderBy: [...orderBy],
            select: { weight: true, recordedAt: true },
          }),
          db.mealRecord.findMany({
            where: { userId, recordedAt: recentRange },
            select: { recordedAt: true },
          }),
          db.workoutRecord.aggregate({
            where: { userId, startedAt: recentRange },
            _count: true,
            _sum: { durationMinutes: true },
          }),
          db.workoutRecord.findMany({
            where: { userId, startedAt: todayRange },
            orderBy: [{ startedAt: 'desc' }, { createdAt: 'desc' }],
            take: 4,
            select: { id: true, name: true, type: true, startedAt: true, durationMinutes: true },
          }),
          db.progressPhoto.findMany({
            where: { userId, recordedAt: todayRange },
            orderBy: [...orderBy],
            select: { id: true, type: true, imageUrl: true, recordedAt: true },
          }),
        ]),
      { isolationLevel: 'RepeatableRead' },
    );
    // 查询按时间倒序排列，同一天仅使用最后一次体重，与趋势页的每日口径一致。
    const dailyWeights = new Map<string, number>();
    for (const record of recentBodies) {
      const day = dayKey(record.recordedAt);
      if (!dailyWeights.has(day)) dailyWeights.set(day, Number(record.weight));
    }
    const weights = [...dailyWeights.values()];
    const latestBody = bodies[0];
    const body = latestBody
      ? {
          id: latestBody.id,
          weight: Number(latestBody.weight),
          recordedAt: latestBody.recordedAt.toISOString(),
        }
      : null;
    const sumCalories = (values: Array<number | null>): number | null => {
      const known = values.filter((value): value is number => value !== null);
      return known.length ? known.reduce((sum, value) => sum + value, 0) : null;
    };
    return {
      date,
      latestBodyRecord: body,
      latestMeal: latestMeal
        ? { ...latestMeal, recordedAt: latestMeal.recordedAt.toISOString() }
        : null,
      latestWorkout: latestWorkout
        ? { ...latestWorkout, startedAt: latestWorkout.startedAt.toISOString() }
        : null,
      latestPhoto: latestPhoto
        ? { ...latestPhoto, recordedAt: latestPhoto.recordedAt.toISOString() }
        : null,
      bodyChanges: {
        weight:
          bodies.length > 1
            ? Number((Number(bodies[0].weight) - Number(bodies[1].weight)).toFixed(2))
            : null,
      },
      recentTrend: {
        days: 7,
        from: dayKey(recentRange.gte),
        to: date,
        weightChange:
          weights.length > 1 ? Number((weights[0] - weights[weights.length - 1]).toFixed(2)) : null,
        bodyRecordedDays: weights.length,
        mealRecordedDays: new Set(recentMeals.map((meal) => dayKey(meal.recordedAt))).size,
        workoutCount: recentWorkouts._count,
        workoutMinutes: recentWorkouts._sum.durationMinutes ?? 0,
      },
      today: {
        bodyRecord: latestBody && latestBody.recordedAt.getTime() >= from ? body : null,
        mealCount: meals.length,
        calories: sumCalories(meals.flatMap((meal) => meal.foods.map((food) => food.calories))),
        meals: {
          BREAKFAST: meals.some((meal) => meal.type === 'BREAKFAST'),
          LUNCH: meals.some((meal) => meal.type === 'LUNCH'),
          DINNER: meals.some((meal) => meal.type === 'DINNER'),
          SNACK: meals.some((meal) => meal.type === 'SNACK'),
        },
        mealPreview: meals.slice(0, 4).map((meal) => ({
          id: meal.id,
          type: meal.type,
          recordedAt: meal.recordedAt.toISOString(),
          foodNames: meal.foods.map((food) => food.name),
          totalCalories: sumCalories(meal.foods.map((food) => food.calories)),
        })),
        workoutCount: workoutSummary._count,
        workoutMinutes: workoutSummary._sum.durationMinutes ?? 0,
        workoutPreview: dayWorkouts.map((workout) => ({
          ...workout,
          startedAt: workout.startedAt.toISOString(),
        })),
        photoCount: dayPhotos.length,
        photoPreview: dayPhotos
          .slice(0, 4)
          .map((photo) => ({ ...photo, recordedAt: photo.recordedAt.toISOString() })),
      },
    };
  }
}
