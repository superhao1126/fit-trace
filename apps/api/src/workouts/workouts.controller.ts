import {
  Body,
  Controller,
  Delete,
  Get,
  Param,
  Patch,
  Put,
  Post,
  Query,
  UseGuards,
} from '@nestjs/common';
import type { ApiListPayload, ApiPayload, WorkoutRecord } from '@fit-trace/shared';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { CreateWorkoutDto } from './dto/create-workout.dto';
import { ListWorkoutsDto } from './dto/list-workouts.dto';
import { UpdateWorkoutDto } from './dto/update-workout.dto';
import { WorkoutsService } from './workouts.service';

@UseGuards(JwtAuthGuard)
@Controller('workouts')
export class WorkoutsController {
  constructor(private readonly workouts: WorkoutsService) {}

  @Post()
  async create(
    @CurrentUser('sub') userId: string,
    @Body() dto: CreateWorkoutDto,
  ): Promise<ApiPayload<WorkoutRecord>> {
    return { data: await this.workouts.create(userId, dto) };
  }

  @Get()
  list(
    @CurrentUser('sub') userId: string,
    @Query() query: ListWorkoutsDto,
  ): Promise<ApiListPayload<WorkoutRecord>> {
    return this.workouts.list(userId, query);
  }

  @Get(':id')
  async findOne(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ): Promise<ApiPayload<WorkoutRecord>> {
    return { data: await this.workouts.findOne(userId, id) };
  }

  @Put(':id')
  async replace(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: UpdateWorkoutDto,
  ) {
    return { data: await this.workouts.update(userId, id, dto) };
  }

  @Patch(':id')
  async update(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: UpdateWorkoutDto,
  ): Promise<ApiPayload<WorkoutRecord>> {
    return { data: await this.workouts.update(userId, id, dto) };
  }

  @Delete(':id')
  async remove(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ): Promise<ApiPayload<null>> {
    await this.workouts.remove(userId, id);
    return { data: null };
  }
}
