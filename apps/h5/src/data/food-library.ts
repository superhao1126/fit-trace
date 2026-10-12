export interface LibraryFood {
  id: string;
  name: string;
  category: string;
  kcalPer100g: number;
  proteinPer100g: number;
  fatPer100g: number;
  carbsPer100g: number;
  source: string;
  sourceUrl: string;
  sourceDescription: string;
}

/** USDA 官方公开 API，2026-10-12 获取；数值均以 100 g 可食部分为基准。 */
export const foodLibrary: LibraryFood[] = [
  {
    id: '168878',
    name: '米饭（熟）',
    category: '主食',
    kcalPer100g: 130.0,
    proteinPer100g: 2.69,
    fatPer100g: 0.28,
    carbsPer100g: 28.17,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/168878/nutrients',
    sourceDescription: 'Rice, white, long-grain, regular, enriched, cooked',
  },
  {
    id: '169704',
    name: '糙米饭（熟）',
    category: '主食',
    kcalPer100g: 123.0,
    proteinPer100g: 2.74,
    fatPer100g: 0.97,
    carbsPer100g: 25.58,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/169704/nutrients',
    sourceDescription:
      "Rice, brown, long-grain, cooked (Includes foods for USDA's Food Distribution Program)",
  },
  {
    id: '173905',
    name: '燕麦粥（水煮）',
    category: '主食',
    kcalPer100g: 71.0,
    proteinPer100g: 2.54,
    fatPer100g: 1.52,
    carbsPer100g: 12.0,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/173905/nutrients',
    sourceDescription:
      'Cereals, oats, regular and quick, unenriched, cooked with water (includes boiling and microwaving), without salt',
  },
  {
    id: '173424',
    name: '鸡蛋（水煮）',
    category: '肉蛋奶',
    kcalPer100g: 155.0,
    proteinPer100g: 12.58,
    fatPer100g: 10.61,
    carbsPer100g: 1.12,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/173424/nutrients',
    sourceDescription: 'Egg, whole, cooked, hard-boiled',
  },
  {
    id: '171477',
    name: '鸡胸肉（烤熟）',
    category: '肉蛋奶',
    kcalPer100g: 165.0,
    proteinPer100g: 31.02,
    fatPer100g: 3.57,
    carbsPer100g: 0.0,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/171477/nutrients',
    sourceDescription: 'Chicken, broilers or fryers, breast, meat only, cooked, roasted',
  },
  {
    id: '173944',
    name: '香蕉',
    category: '蔬果',
    kcalPer100g: 89.0,
    proteinPer100g: 1.09,
    fatPer100g: 0.33,
    carbsPer100g: 22.84,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/173944/nutrients',
    sourceDescription: 'Bananas, raw',
  },
  {
    id: '171688',
    name: '苹果（带皮）',
    category: '蔬果',
    kcalPer100g: 52.0,
    proteinPer100g: 0.26,
    fatPer100g: 0.17,
    carbsPer100g: 13.81,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/171688/nutrients',
    sourceDescription:
      "Apples, raw, with skin (Includes foods for USDA's Food Distribution Program)",
  },
  {
    id: '169967',
    name: '西兰花（水煮）',
    category: '蔬果',
    kcalPer100g: 35.0,
    proteinPer100g: 2.38,
    fatPer100g: 0.41,
    carbsPer100g: 7.18,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/169967/nutrients',
    sourceDescription: 'Broccoli, cooked, boiled, drained, without salt',
  },
  {
    id: '170093',
    name: '土豆（烤熟）',
    category: '主食',
    kcalPer100g: 93.0,
    proteinPer100g: 2.5,
    fatPer100g: 0.13,
    carbsPer100g: 21.15,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/170093/nutrients',
    sourceDescription: 'Potatoes, baked, flesh and skin, without salt',
  },
  {
    id: '168483',
    name: '红薯（烤熟）',
    category: '主食',
    kcalPer100g: 90.0,
    proteinPer100g: 2.01,
    fatPer100g: 0.15,
    carbsPer100g: 20.71,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/168483/nutrients',
    sourceDescription: 'Sweet potato, cooked, baked in skin, flesh, without salt',
  },
  {
    id: '171705',
    name: '牛油果',
    category: '蔬果',
    kcalPer100g: 160.0,
    proteinPer100g: 2.0,
    fatPer100g: 14.66,
    carbsPer100g: 8.53,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/171705/nutrients',
    sourceDescription: 'Avocados, raw, all commercial varieties',
  },
  {
    id: '167762',
    name: '草莓',
    category: '蔬果',
    kcalPer100g: 32.0,
    proteinPer100g: 0.67,
    fatPer100g: 0.3,
    carbsPer100g: 7.68,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/167762/nutrients',
    sourceDescription: 'Strawberries, raw',
  },
  {
    id: '170567',
    name: '杏仁',
    category: '豆类坚果',
    kcalPer100g: 579.0,
    proteinPer100g: 21.15,
    fatPer100g: 49.93,
    carbsPer100g: 21.55,
    source: 'USDA FoodData Central · SR Legacy',
    sourceUrl: 'https://fdc.nal.usda.gov/food-details/170567/nutrients',
    sourceDescription: 'Nuts, almonds',
  },
];

export function foodCalories(food: Pick<LibraryFood, 'kcalPer100g'>, grams: number): number {
  if (!Number.isFinite(grams) || grams <= 0 || grams > 10000)
    throw new Error('份量需大于 0 且不超过 10000 克');
  return Math.round((food.kcalPer100g * grams) / 100);
}
