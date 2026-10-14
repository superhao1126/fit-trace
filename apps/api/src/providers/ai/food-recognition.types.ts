import type { FoodRecognitionResult } from '@fit-trace/shared';

/**
 * 饮食识别抽象：业务层只依赖该接口，不绑定任何具体 AI 厂商。
 */
export interface FoodRecognitionProvider {
  /** hint 是用户对识别结果的更正说明，例如“左边那碗是鸡蛋羹”。 */
  recognize(imageUrl: string, hint?: string): Promise<FoodRecognitionResult>;
  analyzeText(description: string): Promise<FoodRecognitionResult>;
}

export const FOOD_RECOGNITION_PROVIDER = Symbol('FOOD_RECOGNITION_PROVIDER');
