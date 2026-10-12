import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AiModule } from './ai/ai.module';
import { AuthModule } from './auth/auth.module';
import { BodyRecordsModule } from './body-records/body-records.module';
import { DashboardModule } from './dashboard/dashboard.module';
import { HealthModule } from './health/health.module';
import { InsightsModule } from './insights/insights.module';
import { MealsModule } from './meals/meals.module';
import { PrismaModule } from './prisma/prisma.module';
import { ProgressPhotosModule } from './progress-photos/progress-photos.module';
import { AiProviderModule } from './providers/ai/ai.module';
import { StorageModule } from './providers/storage/storage.module';
import { UploadsModule } from './uploads/uploads.module';
import { UsersModule } from './users/users.module';
import { WorkoutsModule } from './workouts/workouts.module';
import { TrainingModule } from './training/training.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      envFilePath: ['../../.env', '.env'],
    }),
    StorageModule,
    AiProviderModule,
    PrismaModule,
    UsersModule,
    AuthModule,
    BodyRecordsModule,
    MealsModule,
    WorkoutsModule,
    TrainingModule,
    UploadsModule,
    ProgressPhotosModule,
    InsightsModule,
    DashboardModule,
    AiModule,
    HealthModule,
  ],
})
export class AppModule {}
