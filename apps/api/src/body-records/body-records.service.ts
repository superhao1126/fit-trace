import { recordRange } from '../common/dto/record-range-query.dto';
import { Injectable, Logger, NotFoundException } from '@nestjs/common';
import type {
  BodyRecord as BodyRecordResponse,
  BodyTrendData,
  BodyTrendDays,
  BodyTrendMetric,
  BodyTrendSeries,
} from '@fit-trace/shared';
import type { BodyRecord as PrismaBodyRecord, Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { CreateBodyRecordDto } from './dto/create-body-record.dto';
import { ListBodyRecordsDto } from './dto/list-body-records.dto';
import { UpdateBodyRecordDto } from './dto/update-body-record.dto';

@Injectable()
export class BodyRecordsService {
  private readonly logger = new Logger('BodyRecords');

  constructor(private readonly prisma: PrismaService) {}

  async create(userId: string, dto: CreateBodyRecordDto): Promise<BodyRecordResponse> {
    const record = await this.prisma.bodyRecord.create({
      data: {
        userId,
        weight: dto.weight,
        bodyFat: dto.bodyFat,
        waist: dto.waist,
        chest: dto.chest,
        hip: dto.hip,
        recordedAt: dto.recordedAt ? new Date(dto.recordedAt) : new Date(),
        note: dto.note?.trim() || null,
      },
    });
    this.logger.log(
      `新增身体数据 user=${this.short(userId)} id=${this.short(record.id)} weight=${record.weight} 记录时间=${record.recordedAt.toISOString()}`,
    );
    return this.toResponse(record);
  }

  async list(
    userId: string,
    query: ListBodyRecordsDto,
  ): Promise<{
    data: BodyRecordResponse[];
    meta: { page: number; pageSize: number; total: number };
  }> {
    const where: Prisma.BodyRecordWhereInput = { userId, recordedAt: recordRange(query) };
    const [records, total] = await this.prisma.$transaction([
      this.prisma.bodyRecord.findMany({
        where,
        orderBy: [{ recordedAt: query.recordedAtOrder }, { createdAt: query.recordedAtOrder }],
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }),
      this.prisma.bodyRecord.count({ where }),
    ]);
    return {
      data: records.map((record) => this.toResponse(record)),
      meta: { page: query.page, pageSize: query.pageSize, total },
    };
  }

  async latest(userId: string): Promise<BodyRecordResponse | null> {
    const record = await this.prisma.bodyRecord.findFirst({
      where: { userId },
      orderBy: [{ recordedAt: 'desc' }, { createdAt: 'desc' }],
    });
    return record ? this.toResponse(record) : null;
  }

  async trends(userId: string, days: BodyTrendDays): Promise<BodyTrendData> {
    const to = new Date();
    const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
    const records = await this.prisma.bodyRecord.findMany({
      where: { userId, recordedAt: { gte: from, lte: to } },
      orderBy: [{ recordedAt: 'asc' }, { createdAt: 'asc' }],
    });

    return {
      days,
      from: from.toISOString(),
      to: to.toISOString(),
      weight: this.toTrendSeries(records, 'weight'),
      bodyFat: this.toTrendSeries(records, 'bodyFat'),
      waist: this.toTrendSeries(records, 'waist'),
    };
  }

  async findOne(userId: string, id: string): Promise<BodyRecordResponse> {
    return this.toResponse(await this.findOwnedRecord(userId, id));
  }

  async update(userId: string, id: string, dto: UpdateBodyRecordDto): Promise<BodyRecordResponse> {
    await this.findOwnedRecord(userId, id);
    const data: Prisma.BodyRecordUpdateInput = {
      ...(dto.weight !== undefined ? { weight: dto.weight } : {}),
      ...(dto.bodyFat !== undefined ? { bodyFat: dto.bodyFat } : {}),
      ...(dto.waist !== undefined ? { waist: dto.waist } : {}),
      ...(dto.chest !== undefined ? { chest: dto.chest } : {}),
      ...(dto.hip !== undefined ? { hip: dto.hip } : {}),
      ...(dto.recordedAt !== undefined ? { recordedAt: new Date(dto.recordedAt) } : {}),
      ...(dto.note !== undefined ? { note: dto.note.trim() || null } : {}),
    };
    const record = await this.prisma.bodyRecord.update({ where: { id }, data });
    this.logger.log(`更新身体数据 user=${this.short(userId)} id=${this.short(id)}`);
    return this.toResponse(record);
  }

  async remove(userId: string, id: string): Promise<void> {
    await this.findOwnedRecord(userId, id);
    await this.prisma.bodyRecord.delete({ where: { id } });
    this.logger.log(`删除身体数据 user=${this.short(userId)} id=${this.short(id)}`);
  }

  private short(id: string): string {
    return id.slice(0, 8);
  }

  private async findOwnedRecord(userId: string, id: string): Promise<PrismaBodyRecord> {
    const record = await this.prisma.bodyRecord.findFirst({ where: { id, userId } });
    if (!record) {
      throw new NotFoundException({
        code: 'BODY_RECORD_NOT_FOUND',
        message: '身体数据记录不存在',
      });
    }
    return record;
  }

  private toTrendSeries(records: PrismaBodyRecord[], metric: BodyTrendMetric): BodyTrendSeries {
    const points = records.flatMap((record) => {
      const rawValue = record[metric];
      return rawValue === null
        ? []
        : [{ recordedAt: record.recordedAt.toISOString(), value: Number(rawValue) }];
    });
    if (points.length === 0) return { points, stats: null };

    const values = points.map((point) => point.value);
    const start = values[0];
    const current = values.at(-1) ?? start;
    return {
      points,
      stats: {
        current,
        start,
        change: current - start,
        max: Math.max(...values),
        min: Math.min(...values),
        average: values.reduce((sum, value) => sum + value, 0) / values.length,
      },
    };
  }

  private toResponse(record: PrismaBodyRecord): BodyRecordResponse {
    return {
      id: record.id,
      weight: Number(record.weight),
      bodyFat: record.bodyFat === null ? null : Number(record.bodyFat),
      waist: record.waist === null ? null : Number(record.waist),
      chest: record.chest === null ? null : Number(record.chest),
      hip: record.hip === null ? null : Number(record.hip),
      recordedAt: record.recordedAt.toISOString(),
      note: record.note,
      createdAt: record.createdAt.toISOString(),
      updatedAt: record.updatedAt.toISOString(),
    };
  }
}
