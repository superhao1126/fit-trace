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
import type { ApiListPayload, ApiPayload, BodyRecord, BodyTrendData } from '@fit-trace/shared';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { BodyRecordsService } from './body-records.service';
import { BodyTrendsQueryDto } from './dto/body-trends-query.dto';
import { CreateBodyRecordDto } from './dto/create-body-record.dto';
import { ListBodyRecordsDto } from './dto/list-body-records.dto';
import { UpdateBodyRecordDto } from './dto/update-body-record.dto';

@UseGuards(JwtAuthGuard)
@Controller('body-records')
export class BodyRecordsController {
  constructor(private readonly bodyRecords: BodyRecordsService) {}

  @Post()
  async create(
    @CurrentUser('sub') userId: string,
    @Body() dto: CreateBodyRecordDto,
  ): Promise<ApiPayload<BodyRecord>> {
    return { data: await this.bodyRecords.create(userId, dto) };
  }

  @Get()
  list(
    @CurrentUser('sub') userId: string,
    @Query() query: ListBodyRecordsDto,
  ): Promise<ApiListPayload<BodyRecord>> {
    return this.bodyRecords.list(userId, query);
  }

  @Get('latest')
  async latest(@CurrentUser('sub') userId: string): Promise<ApiPayload<BodyRecord | null>> {
    return { data: await this.bodyRecords.latest(userId) };
  }

  @Get('trends')
  async trends(
    @CurrentUser('sub') userId: string,
    @Query() query: BodyTrendsQueryDto,
  ): Promise<ApiPayload<BodyTrendData>> {
    return { data: await this.bodyRecords.trends(userId, query.days) };
  }

  @Get(':id')
  async findOne(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ): Promise<ApiPayload<BodyRecord>> {
    return { data: await this.bodyRecords.findOne(userId, id) };
  }

  @Put(':id')
  async replace(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: UpdateBodyRecordDto,
  ) {
    return { data: await this.bodyRecords.update(userId, id, dto) };
  }

  @Patch(':id')
  async update(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
    @Body() dto: UpdateBodyRecordDto,
  ): Promise<ApiPayload<BodyRecord>> {
    return { data: await this.bodyRecords.update(userId, id, dto) };
  }

  @Delete(':id')
  async remove(
    @CurrentUser('sub') userId: string,
    @Param('id') id: string,
  ): Promise<ApiPayload<null>> {
    await this.bodyRecords.remove(userId, id);
    return { data: null };
  }
}
