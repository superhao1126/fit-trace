import { Body, Controller, Post, UseGuards } from '@nestjs/common';
import type { ApiPayload, FoodRecognitionResult } from '@fit-trace/shared';
import { JwtAuthGuard } from '../common/guards/jwt-auth.guard';
import { AiService } from './ai.service';
import { FoodRecognitionDto } from './dto/food-recognition.dto';
import { FoodTextDto } from './dto/food-text.dto';

@UseGuards(JwtAuthGuard)
@Controller('ai')
export class AiController {
  constructor(private readonly ai: AiService) {}

  @Post('food-text')
  async analyzeText(@Body() dto: FoodTextDto): Promise<ApiPayload<FoodRecognitionResult>> {
    return { data: await this.ai.analyzeText(dto.description) };
  }

  @Post('food-recognition')
  async recognizeFood(@Body() dto: FoodRecognitionDto): Promise<ApiPayload<FoodRecognitionResult>> {
    return { data: await this.ai.recognizeFood(dto.imageUrl, dto.hint) };
  }
}
