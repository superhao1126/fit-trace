import { recordRange } from '../common/dto/record-range-query.dto';
import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import type { WorkoutRecord as WorkoutRecordResponse } from '@fit-trace/shared';
import { Prisma, type WorkoutRecord } from '@prisma/client';
import type { TrainingExercise } from '@fit-trace/shared';
import { normalizeExercises } from '../training/training.service';
import { PrismaService } from '../prisma/prisma.service';
import { CreateWorkoutDto } from './dto/create-workout.dto';
import { ListWorkoutsDto } from './dto/list-workouts.dto';
import { UpdateWorkoutDto } from './dto/update-workout.dto';

@Injectable()
export class WorkoutsService {
  private readonly logger = new Logger('Workouts');

  constructor(private readonly prisma: PrismaService) {}

  async create(userId: string, dto: CreateWorkoutDto): Promise<WorkoutRecordResponse> {
    const workout = await this.prisma.workoutRecord.create({
      data: {
        userId,
        type: dto.type,
        name: dto.name.trim(),
        startedAt: dto.startedAt ? new Date(dto.startedAt) : new Date(),
        durationMinutes: dto.durationMinutes,
        calories: dto.calories ?? null,
        note: dto.note?.trim() || null,
        ...(dto.exercises
          ? { exercises: normalizeExercises(dto.exercises) as unknown as Prisma.InputJsonValue }
          : {}),
      },
    });
    this.logger.log(
      `新增训练记录 user=${this.short(userId)} id=${this.short(workout.id)} 类型=${workout.type} 时长=${workout.durationMinutes}min`,
    );
    return this.toResponse(workout);
  }

  async list(
    userId: string,
    query: ListWorkoutsDto,
  ): Promise<{
    data: WorkoutRecordResponse[];
    meta: { page: number; pageSize: number; total: number };
  }> {
    const where: Prisma.WorkoutRecordWhereInput = {
      userId,
      ...(query.type ? { type: query.type } : {}),
      startedAt: recordRange(query),
    };
    const [workouts, total] = await this.prisma.$transaction([
      this.prisma.workoutRecord.findMany({
        where,
        orderBy: [{ startedAt: 'desc' }, { createdAt: 'desc' }],
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }),
      this.prisma.workoutRecord.count({ where }),
    ]);
    return {
      data: workouts.map((workout) => this.toResponse(workout)),
      meta: { page: query.page, pageSize: query.pageSize, total },
    };
  }

  async findOne(userId: string, id: string): Promise<WorkoutRecordResponse> {
    return this.toResponse(await this.findOwnedWorkout(userId, id));
  }

  async update(userId: string, id: string, dto: UpdateWorkoutDto): Promise<WorkoutRecordResponse> {
    await this.findOwnedWorkout(userId, id);
    const workout = await this.prisma.workoutRecord.update({
      where: { id },
      data: {
        ...(dto.type !== undefined ? { type: dto.type } : {}),
        ...(dto.name !== undefined ? { name: dto.name.trim() } : {}),
        ...(dto.startedAt !== undefined ? { startedAt: new Date(dto.startedAt) } : {}),
        ...(dto.durationMinutes !== undefined ? { durationMinutes: dto.durationMinutes } : {}),
        ...(dto.calories !== undefined ? { calories: dto.calories } : {}),
        ...(dto.note !== undefined ? { note: dto.note.trim() || null } : {}),
        ...(dto.exercises !== undefined
          ? { exercises: normalizeExercises(dto.exercises) as unknown as Prisma.InputJsonValue }
          : {}),
      },
    });
    this.logger.log(`更新训练记录 user=${this.short(userId)} id=${this.short(id)}`);
    return this.toResponse(workout);
  }

  async remove(userId: string, id: string): Promise<void> {
    await this.findOwnedWorkout(userId, id);
    await this.prisma.workoutRecord.delete({ where: { id } });
    this.logger.log(`删除训练记录 user=${this.short(userId)} id=${this.short(id)}`);
  }

  private short(id: string): string {
    return id.slice(0, 8);
  }

  private async findOwnedWorkout(userId: string, id: string): Promise<WorkoutRecord> {
    const workout = await this.prisma.workoutRecord.findFirst({ where: { id, userId } });
    if (!workout) {
      throw new NotFoundException({ code: 'WORKOUT_NOT_FOUND', message: '训练记录不存在' });
    }
    return workout;
  }

  private toResponse(workout: WorkoutRecord): WorkoutRecordResponse {
    return {
      id: workout.id,
      type: workout.type,
      name: workout.name,
      startedAt: workout.startedAt.toISOString(),
      durationMinutes: workout.durationMinutes,
      calories: workout.calories,
      note: workout.note,
      exercises: workout.exercises
        ? (workout.exercises as unknown as TrainingExercise[])
        : undefined,
      createdAt: workout.createdAt.toISOString(),
      updatedAt: workout.updatedAt.toISOString(),
    };
  }
}
