import { RecordRangeQueryDto } from '../../common/dto/record-range-query.dto';
import { Type } from 'class-transformer';
import { IsIn, IsInt, IsOptional, Max, Min } from 'class-validator';
import type { PhotoType } from '@fit-trace/shared';
import { PHOTO_TYPES } from './create-progress-photo.dto';

export class ListProgressPhotosDto extends RecordRangeQueryDto {
  @Type(() => Number)
  @IsOptional()
  @IsInt({ message: '页码必须为整数' })
  @Min(1, { message: '页码不能小于 1' })
  page = 1;

  @Type(() => Number)
  @IsOptional()
  @IsInt({ message: '每页数量必须为整数' })
  @Min(1, { message: '每页数量不能小于 1' })
  @Max(100, { message: '每页数量不能超过 100' })
  pageSize = 20;

  @IsOptional()
  @IsIn(PHOTO_TYPES, { message: '请选择正确的照片类型' })
  type?: PhotoType;
}
