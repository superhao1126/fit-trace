import { Type } from 'class-transformer';
import {
  ArrayMaxSize,
  ArrayMinSize,
  IsArray,
  IsBoolean,
  IsIn,
  IsInt,
  IsNotEmpty,
  IsNumber,
  IsOptional,
  IsString,
  Max,
  MaxLength,
  Min,
  ValidateNested,
} from 'class-validator';
export class TrainingSetDto {
  @IsInt() @Min(1) @Max(3600) reps!: number;
  @IsNumber({ maxDecimalPlaces: 2 }) @Min(0) @Max(1000) weightKg!: number;
  @IsBoolean() completed!: boolean;
}
export class TrainingExerciseDto {
  @IsString() @IsNotEmpty() @MaxLength(60) exerciseId!: string;
  @IsString() @IsNotEmpty() @MaxLength(100) name!: string;
  @IsIn(['reps', 'seconds']) unit!: 'reps' | 'seconds';
  @IsInt() @Min(0) @Max(600) restSeconds!: number;
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(20)
  @ValidateNested({ each: true })
  @Type(() => TrainingSetDto)
  sets!: TrainingSetDto[];
}
export class TrainingTemplateDto {
  @IsString() @IsNotEmpty() @MaxLength(60) name!: string;
  @IsInt() @Min(1) @Max(1440) durationMinutes!: number;
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(20)
  @ValidateNested({ each: true })
  @Type(() => TrainingExerciseDto)
  exercises!: TrainingExerciseDto[];
}
export class TrainingPlanDayDto {
  @IsInt() @Min(0) @Max(6) weekday!: number;
  @IsOptional() @IsString() @MaxLength(60) templateId!: string | null;
}
export class TrainingPlanDto {
  @IsArray()
  @ArrayMinSize(7)
  @ArrayMaxSize(7)
  @ValidateNested({ each: true })
  @Type(() => TrainingPlanDayDto)
  days!: TrainingPlanDayDto[];
}
