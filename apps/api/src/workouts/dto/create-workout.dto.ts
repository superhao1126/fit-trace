import { Type } from 'class-transformer';
import {
  IsArray,
  ArrayMinSize,
  ArrayMaxSize,
  ValidateNested,
  ValidateIf,
  IsDateString,
  IsIn,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
} from 'class-validator';
import type { WorkoutType } from '@fit-trace/shared';
import { TrainingExerciseDto } from '../../training/training.dto';

export const WORKOUT_TYPES: WorkoutType[] = [
  'STRENGTH',
  'CARDIO',
  'RUNNING',
  'CYCLING',
  'SWIMMING',
  'OTHER',
];

export class CreateWorkoutDto {
  @IsOptional() @IsString() @IsNotEmpty() @MaxLength(100) clientRequestId?: string;
  @ValidateIf((_object, value) => value !== undefined)
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(20)
  @ValidateNested({ each: true })
  @Type(() => TrainingExerciseDto)
  exercises?: TrainingExerciseDto[];
  @IsIn(WORKOUT_TYPES, { message: '请选择正确的训练类型' })
  type!: WorkoutType;

  @IsString({ message: '请输入训练名称' })
  @IsNotEmpty({ message: '请输入训练名称' })
  @MaxLength(60, { message: '训练名称不能超过 60 个字符' })
  name!: string;

  @IsOptional()
  @IsDateString({}, { message: '开始时间格式不正确' })
  startedAt?: string;

  @Type(() => Number)
  @IsInt({ message: '训练时长必须为整数分钟' })
  @Min(1, { message: '训练时长必须大于 0 分钟' })
  @Max(1440, { message: '单次训练时长不能超过 1440 分钟' })
  durationMinutes!: number;

  @Type(() => Number)
  @IsOptional()
  @IsInt({ message: '消耗热量必须为整数' })
  @Min(0, { message: '消耗热量不能小于 0' })
  @Max(10000, { message: '消耗热量数值过大' })
  calories?: number;

  @IsOptional()
  @IsString({ message: '备注格式不正确' })
  @MaxLength(500, { message: '备注不能超过 500 个字符' })
  note?: string;
}
