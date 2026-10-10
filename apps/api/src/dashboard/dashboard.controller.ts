import { Controller, Get, Query, UseGuards } from '@nestjs/common';
import type { ApiPayload, DashboardOverview } from '@fit-trace/shared';
import { CurrentUser } from '../common/decorators/current-user.decorator';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { DashboardQueryDto } from './dto/dashboard-query.dto';
import { DashboardService } from './dashboard.service';

@UseGuards(JwtAuthGuard)
@Controller('dashboard')
export class DashboardController {
  constructor(private readonly dashboard: DashboardService) {}

  @Get()
  async overview(
    @CurrentUser('sub') userId: string,
    @Query() query: DashboardQueryDto,
  ): Promise<ApiPayload<DashboardOverview>> {
    return { data: await this.dashboard.overview(userId, query.tzOffset, query.date) };
  }
}
