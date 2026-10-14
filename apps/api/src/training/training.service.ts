import { BadRequestException, Injectable, NotFoundException } from '@nestjs/common';
import {
  exerciseLibrary,
  trainingPresets,
  type TrainingExercise,
  type TrainingPlanDay,
  type TrainingTemplate,
  type TrainingPlanState,
  type PreviousExercise,
} from '@fit-trace/shared';
import { Prisma, type TrainingTemplate as DbTemplate } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { TrainingPlanDto, TrainingTemplateDto } from './training.dto';
export function normalizeExercises(
  exercises: TrainingExercise[],
  template = false,
): TrainingExercise[] {
  return exercises.map((item) => {
    const exercise = exerciseLibrary.find((e) => e.id === item.exerciseId);
    if (!exercise || exercise.unit !== item.unit)
      throw new BadRequestException({
        code: 'VALIDATION_ERROR',
        message: '请选择正确的训练动作与计量单位',
      });
    return {
      ...item,
      name: exercise.name,
      sets: item.sets.map((set) => ({ ...set, completed: template ? false : set.completed })),
    };
  });
}
@Injectable()
export class TrainingService {
  constructor(private readonly prisma: PrismaService) {}
  private response(value: DbTemplate): TrainingTemplate {
    return {
      id: value.id,
      name: value.name,
      durationMinutes: value.durationMinutes,
      exercises: value.exercises as unknown as TrainingExercise[],
    };
  }
  async templates(userId: string) {
    const mine = await this.prisma.trainingTemplate.findMany({
      where: { userId },
      orderBy: { updatedAt: 'desc' },
    });
    return [...trainingPresets, ...mine.map((value) => this.response(value))];
  }
  async saveTemplate(userId: string, dto: TrainingTemplateDto, id?: string) {
    if (!dto.name.trim())
      throw new BadRequestException({ code: 'VALIDATION_ERROR', message: '请输入模板名称' });
    const data = {
      name: dto.name.trim(),
      durationMinutes: dto.durationMinutes,
      exercises: normalizeExercises(dto.exercises, true) as unknown as Prisma.InputJsonValue,
    };
    if (id) {
      const owned = await this.prisma.trainingTemplate.findFirst({ where: { id, userId } });
      if (!owned)
        throw new NotFoundException({ code: 'TEMPLATE_NOT_FOUND', message: '训练模板不存在' });
      return this.response(
        await this.prisma.trainingTemplate.update({ where: { id, userId }, data }),
      );
    }
    return this.response(await this.prisma.trainingTemplate.create({ data: { ...data, userId } }));
  }
  async planState(userId: string): Promise<TrainingPlanState> {
    const value = await this.prisma.trainingPlan.findUnique({ where: { userId } });
    return {
      configured: Boolean(value),
      days: value
        ? (value.days as unknown as TrainingPlanDay[])
        : Array.from({ length: 7 }, (_, weekday) => ({ weekday, templateId: null })),
    };
  }
  async previous(userId: string, exerciseId: string): Promise<PreviousExercise | null> {
    const exercise = exerciseLibrary.find((item) => item.id === exerciseId);
    if (!exercise)
      throw new BadRequestException({ code: 'VALIDATION_ERROR', message: '训练动作不存在' });
    // 翻页查找，避免近期手动记录或旧格式数据挡住最近一次有效成绩。
    for (let skip = 0; ; skip += 20) {
      const records = await this.prisma.workoutRecord.findMany({
        where: {
          userId,
          startedAt: { lte: new Date() },
          exercises: { array_contains: [{ exerciseId }] },
        },
        orderBy: [{ startedAt: 'desc' }, { createdAt: 'desc' }, { id: 'desc' }],
        select: { startedAt: true, exercises: true },
        take: 20,
        skip,
      });
      for (const record of records) {
        if (!Array.isArray(record.exercises)) continue;
        const sets = (record.exercises as unknown as TrainingExercise[])
          .filter(
            (item) =>
              item &&
              item.exerciseId === exerciseId &&
              item.unit === exercise.unit &&
              Array.isArray(item.sets),
          )
          .flatMap((item) => item.sets)
          .filter(
            (set) =>
              set &&
              set.completed === true &&
              Number.isInteger(set.reps) &&
              set.reps >= 1 &&
              set.reps <= 3600 &&
              Number.isFinite(set.weightKg) &&
              set.weightKg >= 0 &&
              set.weightKg <= 1000,
          );
        if (sets.length)
          return {
            exerciseId,
            unit: exercise.unit,
            startedAt: record.startedAt.toISOString(),
            sets: sets.map((set) => ({ reps: set.reps, weightKg: set.weightKg, completed: true })),
          };
      }
      if (records.length < 20) return null;
    }
  }

  async plan(userId: string): Promise<TrainingPlanDay[]> {
    const value = await this.prisma.trainingPlan.findUnique({ where: { userId } });
    return value
      ? (value.days as unknown as TrainingPlanDay[])
      : Array.from({ length: 7 }, (_, weekday) => ({ weekday, templateId: null }));
  }
  async savePlan(userId: string, dto: TrainingPlanDto) {
    if (new Set(dto.days.map((d) => d.weekday)).size !== 7)
      throw new BadRequestException({ code: 'VALIDATION_ERROR', message: '每个星期只能安排一次' });
    const available = new Set((await this.templates(userId)).map((t) => t.id));
    if (dto.days.some((d) => d.templateId && !available.has(d.templateId)))
      throw new BadRequestException({
        code: 'VALIDATION_ERROR',
        message: '计划中包含不存在的训练模板',
      });
    const days = dto.days
      .map((d) => ({ weekday: d.weekday, templateId: d.templateId || null }))
      .sort((a, b) => a.weekday - b.weekday);
    const json = days as unknown as Prisma.InputJsonValue;
    await this.prisma.trainingPlan.upsert({
      where: { userId },
      create: { userId, days: json },
      update: { days: json },
    });
    return days;
  }
}
