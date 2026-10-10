import { BadRequestException } from '@nestjs/common';
import { IsISO8601, IsOptional } from 'class-validator';

export class RecordRangeQueryDto {
  @IsOptional()
  @IsISO8601({ strict: true }, { message: '开始时间格式不正确' })
  from?: string;

  @IsOptional()
  @IsISO8601({ strict: true }, { message: '结束时间格式不正确' })
  to?: string;
}

/** 左闭右开，避免本地午夜属于两个日期。 */
export function recordRange(query: RecordRangeQueryDto) {
  const from = query.from ? new Date(query.from) : undefined;
  const to = query.to ? new Date(query.to) : undefined;
  if (
    (from && !Number.isFinite(from.getTime())) ||
    (to && !Number.isFinite(to.getTime())) ||
    (from && to && from >= to)
  ) {
    throw new BadRequestException({ code: 'VALIDATION_ERROR', message: '请选择正确的时间范围' });
  }
  return from || to ? { ...(from ? { gte: from } : {}), ...(to ? { lt: to } : {}) } : undefined;
}
