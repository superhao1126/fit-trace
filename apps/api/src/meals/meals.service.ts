import { recordRange } from '../common/dto/record-range-query.dto';
import { BadRequestException, Injectable, Logger, NotFoundException } from '@nestjs/common';
import type { MealFavorite, MealRecord as MealRecordResponse } from '@fit-trace/shared';
import type { Prisma } from '@prisma/client';
import { PrismaService } from '../prisma/prisma.service';
import { CreateMealDto, FoodItemInputDto } from './dto/create-meal.dto';
import { ListMealsDto } from './dto/list-meals.dto';
import { MealFavoriteDto } from './dto/meal-favorite.dto';
import { UpdateMealDto } from './dto/update-meal.dto';

type MealWithFoods = Prisma.MealRecordGetPayload<{ include: { foods: true } }>;

@Injectable()
export class MealsService {
  private readonly logger = new Logger('Meals');

  constructor(private readonly prisma: PrismaService) {}

  async favorites(userId: string): Promise<MealFavorite[]> {
    const rows = await this.prisma.mealFavorite.findMany({
      where: { userId },
      orderBy: [{ updatedAt: 'desc' }, { id: 'desc' }],
    });
    return rows.map((row) => ({
      id: row.id,
      name: row.name,
      foods: row.foods as unknown as MealFavorite['foods'],
      createdAt: row.createdAt.toISOString(),
    }));
  }
  async favorite(userId: string, dto: MealFavoriteDto): Promise<MealFavorite> {
    if (!dto.name.trim() || dto.foods.some((food) => !food.name.trim()))
      throw new BadRequestException({
        code: 'VALIDATION_ERROR',
        message: '请填写常用餐与食物名称',
      });
    const foods = dto.foods.map((food) => ({
      name: food.name.trim(),
      amount: food.amount?.trim() || null,
      calories: food.calories ?? null,
      aiGenerated: food.aiGenerated ?? false,
    }));
    const row = await this.prisma.mealFavorite.create({
      data: { userId, name: dto.name.trim(), foods: foods as unknown as Prisma.InputJsonValue },
    });
    return { id: row.id, name: row.name, foods, createdAt: row.createdAt.toISOString() };
  }
  async removeFavorite(userId: string, id: string): Promise<void> {
    const result = await this.prisma.mealFavorite.deleteMany({ where: { id, userId } });
    if (!result.count)
      throw new NotFoundException({ code: 'FAVORITE_NOT_FOUND', message: '常用餐不存在' });
  }

  async create(userId: string, dto: CreateMealDto): Promise<MealRecordResponse> {
    const meal = await this.prisma.mealRecord.create({
      data: {
        userId,
        type: dto.type,
        recordedAt: dto.recordedAt ? new Date(dto.recordedAt) : new Date(),
        note: dto.note?.trim() || null,
        imageUrl: dto.imageUrl ?? null,
        foods: { create: dto.foods.map((food) => this.toFoodCreateInput(food)) },
      },
      include: { foods: true },
    });
    this.logger.log(
      `新增饮食记录 user=${this.short(userId)} id=${this.short(meal.id)} 餐次=${meal.type} 食物=${meal.foods.length} 张照片=${meal.imageUrl ? '有' : '无'}`,
    );
    return this.toResponse(meal);
  }

  async list(
    userId: string,
    query: ListMealsDto,
  ): Promise<{
    data: MealRecordResponse[];
    meta: { page: number; pageSize: number; total: number };
  }> {
    const where: Prisma.MealRecordWhereInput = {
      userId,
      ...(query.type ? { type: query.type } : {}),
      recordedAt: recordRange(query),
    };
    const [meals, total] = await this.prisma.$transaction([
      this.prisma.mealRecord.findMany({
        where,
        include: { foods: { orderBy: { createdAt: 'asc' } } },
        orderBy: [{ recordedAt: 'desc' }, { createdAt: 'desc' }],
        skip: (query.page - 1) * query.pageSize,
        take: query.pageSize,
      }),
      this.prisma.mealRecord.count({ where }),
    ]);
    return {
      data: meals.map((meal) => this.toResponse(meal)),
      meta: { page: query.page, pageSize: query.pageSize, total },
    };
  }

  async findOne(userId: string, id: string): Promise<MealRecordResponse> {
    return this.toResponse(await this.findOwnedMeal(userId, id));
  }

  async update(userId: string, id: string, dto: UpdateMealDto): Promise<MealRecordResponse> {
    await this.findOwnedMeal(userId, id);
    const meal = await this.prisma.mealRecord.update({
      where: { id },
      data: {
        ...(dto.type !== undefined ? { type: dto.type } : {}),
        ...(dto.recordedAt !== undefined ? { recordedAt: new Date(dto.recordedAt) } : {}),
        ...(dto.note !== undefined ? { note: dto.note.trim() || null } : {}),
        ...(dto.imageUrl !== undefined ? { imageUrl: dto.imageUrl } : {}),
        ...(dto.foods !== undefined
          ? {
              foods: {
                deleteMany: {},
                create: dto.foods.map((food) => this.toFoodCreateInput(food)),
              },
            }
          : {}),
      },
      include: { foods: { orderBy: { createdAt: 'asc' } } },
    });
    this.logger.log(`更新饮食记录 user=${this.short(userId)} id=${this.short(id)}`);
    return this.toResponse(meal);
  }

  async remove(userId: string, id: string): Promise<void> {
    await this.findOwnedMeal(userId, id);
    await this.prisma.mealRecord.delete({ where: { id } });
    this.logger.log(`删除饮食记录 user=${this.short(userId)} id=${this.short(id)}`);
  }

  private short(id: string): string {
    return id.slice(0, 8);
  }

  private async findOwnedMeal(userId: string, id: string): Promise<MealWithFoods> {
    const meal = await this.prisma.mealRecord.findFirst({
      where: { id, userId },
      include: { foods: { orderBy: { createdAt: 'asc' } } },
    });
    if (!meal) {
      throw new NotFoundException({ code: 'MEAL_NOT_FOUND', message: '饮食记录不存在' });
    }
    return meal;
  }

  private toFoodCreateInput(food: FoodItemInputDto) {
    return {
      name: food.name.trim(),
      amount: food.amount?.trim() || null,
      calories: food.calories,
      aiGenerated: food.aiGenerated ?? false,
    };
  }

  private toResponse(meal: MealWithFoods): MealRecordResponse {
    const calorieValues = meal.foods.flatMap((food) =>
      food.calories === null ? [] : [food.calories],
    );
    return {
      id: meal.id,
      type: meal.type,
      recordedAt: meal.recordedAt.toISOString(),
      note: meal.note,
      imageUrl: meal.imageUrl,
      foods: meal.foods.map((food) => ({
        id: food.id,
        name: food.name,
        amount: food.amount,
        calories: food.calories,
        aiGenerated: food.aiGenerated,
        createdAt: food.createdAt.toISOString(),
        updatedAt: food.updatedAt.toISOString(),
      })),
      totalCalories:
        calorieValues.length > 0
          ? calorieValues.reduce((total, calories) => total + calories, 0)
          : null,
      createdAt: meal.createdAt.toISOString(),
      updatedAt: meal.updatedAt.toISOString(),
    };
  }
}
