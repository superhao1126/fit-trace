import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import type { ApiListPayload, ApiPayload, MealRecord } from '@fit-trace/shared';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { MealFavoriteDto } from './dto/meal-favorite.dto';
import { CreateMealDto } from './dto/create-meal.dto';
import { ListMealsDto } from './dto/list-meals.dto';
import { UpdateMealDto } from './dto/update-meal.dto';
import { MealsService } from './meals.service';

@UseGuards(JwtAuthGuard)
@Controller('meals')
export class MealsController {
  constructor(private readonly meals: MealsService) {}

  @Get('favorites') async favorites(@CurrentUser('sub') userId: string) {
    return { data: await this.meals.favorites(userId) };
  }
  @Post('favorites') async favorite(
    @CurrentUser('sub') userId: string,
    @Body() dto: MealFavoriteDto,
  ) {
    return { data: await this.meals.favorite(userId, dto) };
  }
  @Delete('favorites/:id') async removeFavorite(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ) {
    await this.meals.removeFavorite(userId, id);
    return { data: null };
  }

  @Post()
  async create(
    @CurrentUser('sub') userId: string,
    @Body() dto: CreateMealDto,
  ): Promise<ApiPayload<MealRecord>> {
    return { data: await this.meals.create(userId, dto) };
  }

  @Get()
  list(
    @CurrentUser('sub') userId: string,
    @Query() query: ListMealsDto,
  ): Promise<ApiListPayload<MealRecord>> {
    return this.meals.list(userId, query);
  }

  @Get(':id')
  async findOne(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ): Promise<ApiPayload<MealRecord>> {
    return { data: await this.meals.findOne(userId, id) };
  }

  @Patch(':id')
  async update(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: UpdateMealDto,
  ): Promise<ApiPayload<MealRecord>> {
    return { data: await this.meals.update(userId, id, dto) };
  }

  @Delete(':id')
  async remove(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ): Promise<ApiPayload<null>> {
    await this.meals.remove(userId, id);
    return { data: null };
  }
}
