import { Transform } from 'class-transformer';
import { IsString, Length } from 'class-validator';

export class FoodTextDto {
  @Transform(({ value }) => (typeof value === 'string' ? value.trim() : value))
  @IsString({ message: '请描述这一餐吃了什么' })
  @Length(1, 500, { message: '餐食描述需为 1 至 500 个字符' })
  description!: string;
}
