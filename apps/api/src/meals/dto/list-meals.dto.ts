import { RecordRangeQueryDto } from '../../common/dto/record-range-query.dto';
import { Type } from 'class-transformer';
import type { MealType } from '@fit-trace/shared';
import { IsIn, IsInt, IsOptional, Max, Min } from 'class-validator';

export class ListMealsDto extends RecordRangeQueryDto {
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
  @IsIn(['BREAKFAST', 'LUNCH', 'DINNER', 'SNACK'], { message: '请选择正确的餐次' })
  type?: MealType;
}
