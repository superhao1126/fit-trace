import { Type } from 'class-transformer';
import {
  IsString,
  IsNotEmpty,
  MaxLength,
  IsArray,
  ArrayMinSize,
  ArrayMaxSize,
  ValidateNested,
  Matches,
} from 'class-validator';
import { FoodItemInputDto } from './create-meal.dto';
export class MealFavoriteDto {
  @IsString() @IsNotEmpty() @Matches(/\S/) @MaxLength(60) name!: string;
  @IsArray()
  @ArrayMinSize(1)
  @ArrayMaxSize(50)
  @ValidateNested({ each: true })
  @Type(() => FoodItemInputDto)
  foods!: FoodItemInputDto[];
}
