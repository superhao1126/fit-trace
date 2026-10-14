import { Body, Controller, Get, Param, Patch, Post, Put, UseGuards } from '@nestjs/common';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { TrainingPlanDto, TrainingTemplateDto } from './training.dto';
import { TrainingService } from './training.service';
@UseGuards(JwtAuthGuard)
@Controller('training')
export class TrainingController {
  constructor(private readonly training: TrainingService) {}
  @Get('templates') async templates(@CurrentUser('sub') userId: string) {
    return { data: await this.training.templates(userId) };
  }
  @Post('templates') async create(
    @CurrentUser('sub') userId: string,
    @Body() dto: TrainingTemplateDto,
  ) {
    return { data: await this.training.saveTemplate(userId, dto) };
  }
  @Patch('templates/:id') async update(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: TrainingTemplateDto,
  ) {
    return { data: await this.training.saveTemplate(userId, dto, id) };
  }
  @Get('plan/state') async planState(@CurrentUser('sub') userId: string) {
    return { data: await this.training.planState(userId) };
  }
  @Get('exercises/:id/previous') async previous(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ) {
    return { data: await this.training.previous(userId, id) };
  }
  @Get('plan') async plan(@CurrentUser('sub') userId: string) {
    return { data: await this.training.plan(userId) };
  }
  @Put('plan') async savePlan(@CurrentUser('sub') userId: string, @Body() dto: TrainingPlanDto) {
    return { data: await this.training.savePlan(userId, dto) };
  }
}
