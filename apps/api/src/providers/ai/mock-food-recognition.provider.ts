import { Injectable, ServiceUnavailableException } from '@nestjs/common';
import type { FoodRecognitionItem, FoodRecognitionResult } from '@fit-trace/shared';
import type { FoodRecognitionProvider } from './food-recognition.types';

/**
 * 开发环境使用的模拟识别器。
 * 它不会真正分析图片，只是按图片地址稳定地返回一份常见餐食组合，
 * 用于在没有配置真实 AI 服务时打通“识别 → 用户确认 → 保存”的流程。
 */
const MEAL_TEMPLATES: FoodRecognitionItem[][] = [
  [
    { name: '鸡胸肉', estimatedAmount: '150g', estimatedCalories: 248 },
    { name: '糙米饭', estimatedAmount: '200g', estimatedCalories: 248 },
    { name: '西兰花', estimatedAmount: '120g', estimatedCalories: 42 },
  ],
  [
    { name: '全麦吐司', estimatedAmount: '2 片', estimatedCalories: 160 },
    { name: '水煮蛋', estimatedAmount: '2 个', estimatedCalories: 144 },
    { name: '无糖豆浆', estimatedAmount: '300ml', estimatedCalories: 93 },
  ],
  [
    { name: '牛肉面', estimatedAmount: '1 碗', estimatedCalories: 560 },
    { name: '凉拌黄瓜', estimatedAmount: '100g', estimatedCalories: 32 },
  ],
  [
    { name: '三文鱼', estimatedAmount: '120g', estimatedCalories: 250 },
    { name: '藜麦沙拉', estimatedAmount: '180g', estimatedCalories: 210 },
    { name: '牛油果', estimatedAmount: '半个', estimatedCalories: 130 },
  ],
  [
    { name: '虾仁炒蛋', estimatedAmount: '200g', estimatedCalories: 260 },
    { name: '白米饭', estimatedAmount: '150g', estimatedCalories: 174 },
    { name: '炒时蔬', estimatedAmount: '150g', estimatedCalories: 88 },
  ],
];

@Injectable()
export class MockFoodRecognitionProvider implements FoodRecognitionProvider {
  async analyzeText(): Promise<FoodRecognitionResult> {
    throw new ServiceUnavailableException('尚未配置真实 AI，请直接记录原文或手动添加');
  }

  async recognize(imageUrl: string): Promise<FoodRecognitionResult> {
    return {
      provider: 'mock',
      imageUrl,
      foods: MEAL_TEMPLATES[this.templateIndex(imageUrl)].map((food) => ({ ...food })),
    };
  }

  private templateIndex(imageUrl: string): number {
    let hash = 0;
    for (const char of imageUrl) {
      hash = (hash * 31 + char.charCodeAt(0)) % 100_000;
    }
    return hash % MEAL_TEMPLATES.length;
  }
}
