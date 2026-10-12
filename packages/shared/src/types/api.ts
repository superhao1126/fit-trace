/** 控制器内部返回值，由全局拦截器补齐响应字段。 */
export interface ApiPayload<T> {
  data: T;
}

export interface ApiResponse<T> extends ApiPayload<T> {
  code: 'OK';
  message: string;
  meta: ApiListMeta | null;
}

export interface ApiListMeta {
  page: number;
  pageSize: number;
  total: number;
}

export interface ApiListPayload<T> extends ApiPayload<T[]> {
  meta: ApiListMeta;
}

export interface ApiListResponse<T> extends ApiResponse<T[]> {
  meta: ApiListMeta;
}

export interface ApiErrorResponse {
  code: string;
  message: string;
  data: null;
  meta: null;
}

export interface HealthStatus {
  status: 'ok';
}

export interface PublicUser {
  id: string;
  email: string;
  /** null 表示旧账号未记录密码设置状态。 */
  hasPassword: boolean | null;
  nickname: string | null;
  avatarUrl: string | null;
  gender: UserGender;
  goal: FitnessGoal | null;
  createdAt: string;
  updatedAt: string;
}

export type UserGender = 'UNSPECIFIED' | 'MALE' | 'FEMALE';

export type FitnessGoalType = 'LOSE_FAT' | 'GAIN_MUSCLE' | 'MAINTAIN';

export interface FitnessGoal {
  type: FitnessGoalType;
  startWeight: number;
  targetWeight: number;
  targetDate: string | null;
  startedAt: string;
}

export interface AuthResult {
  accessToken: string;
  accessTokenExpiresAt: string;
  refreshToken: string;
  user: PublicUser;
}

export type EmailCodePurpose = 'LOGIN' | 'REGISTER' | 'RESET_PASSWORD';

export interface EmailCodeResult {
  retryAfterSeconds: number;
  expiresInSeconds: number;
}

export type EmailCaptchaPlatform = 'web' | 'app';

export interface EmailCaptchaProof {
  captchaVerifyParam: string;
  captchaPlatform: EmailCaptchaPlatform;
}

export type EmailCaptchaConfig =
  | { enabled: false }
  | {
      enabled: true;
      region: 'cn' | 'sgp';
      prefix: string;
      sceneId: string;
      appSceneId: string | null;
    };

export interface LoginCaptcha {
  id: string;
  image: string;
  expiresInSeconds: number;
}

export interface BodyRecord {
  id: string;
  weight: number;
  bodyFat: number | null;
  waist: number | null;
  chest: number | null;
  hip: number | null;
  recordedAt: string;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export type BodyTrendDays = 7 | 30 | 90;
export type BodyTrendMetric = 'weight' | 'bodyFat' | 'waist';

export interface BodyTrendPoint {
  recordedAt: string;
  value: number;
}

export interface BodyTrendStats {
  current: number;
  start: number;
  change: number;
  max: number;
  min: number;
  average: number;
}

export interface BodyTrendSeries {
  points: BodyTrendPoint[];
  stats: BodyTrendStats | null;
}

export interface BodyTrendData {
  days: BodyTrendDays;
  from: string;
  to: string;
  weight: BodyTrendSeries;
  bodyFat: BodyTrendSeries;
  waist: BodyTrendSeries;
}

export type MealType = 'BREAKFAST' | 'LUNCH' | 'DINNER' | 'SNACK';

export interface FoodRecognitionItem {
  name: string;
  estimatedAmount: string | null;
  estimatedCalories: number | null;
}

export interface FoodRecognitionResult {
  /** 生成该结果的识别服务，用于提示用户当前是否处于模拟模式。 */
  provider: string;
  imageUrl: string;
  foods: FoodRecognitionItem[];
}

export interface FoodItem {
  id: string;
  name: string;
  amount: string | null;
  calories: number | null;
  aiGenerated: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface MealRecord {
  id: string;
  type: MealType;
  recordedAt: string;
  note: string | null;
  imageUrl: string | null;
  foods: FoodItem[];
  totalCalories: number | null;
  createdAt: string;
  updatedAt: string;
}

export type WorkoutType = 'STRENGTH' | 'CARDIO' | 'RUNNING' | 'CYCLING' | 'SWIMMING' | 'OTHER';

export interface WorkoutRecord {
  id: string;
  type: WorkoutType;
  name: string;
  startedAt: string;
  durationMinutes: number;
  calories: number | null;
  note: string | null;
  exercises?: import('../training.js').TrainingExercise[];
  createdAt: string;
  updatedAt: string;
}

export type PhotoType = 'FRONT' | 'SIDE' | 'BACK' | 'OTHER';

export interface ProgressPhoto {
  id: string;
  type: PhotoType;
  imageUrl: string;
  recordedAt: string;
  note: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface UploadResult {
  url: string;
  key: string;
}

export type InsightDays = 7 | 30 | 90;

export interface InsightSeriesPoint {
  date: string;
  weight: number | null;
  calories: number | null;
  workoutMinutes: number;
  mealCount: number;
}

export interface InsightWeightSummary {
  start: number | null;
  current: number | null;
  change: number | null;
  lowest: number | null;
  highest: number | null;
  recordCount: number;
}

export interface InsightCaloriesSummary {
  average: number | null;
  highest: number | null;
  recordedDays: number;
}

export interface InsightWorkoutSummary {
  count: number;
  totalMinutes: number;
  totalCalories: number | null;
  byType: Array<{ type: WorkoutType; count: number; minutes: number }>;
}

export interface InsightMealSummary {
  count: number;
  recordedDays: number;
  byType: Array<{ type: MealType; count: number }>;
}

export interface InsightPhotoSummary {
  count: number;
  latestRecordedAt: string | null;
}

export interface InsightOverview {
  days: InsightDays;
  from: string;
  to: string;
  series: InsightSeriesPoint[];
  weight: InsightWeightSummary;
  calories: InsightCaloriesSummary;
  workouts: InsightWorkoutSummary;
  meals: InsightMealSummary;
  photos: InsightPhotoSummary;
}

export interface DashboardOverview {
  date: string;
  latestBodyRecord: Pick<BodyRecord, 'id' | 'weight' | 'recordedAt'> | null;
  latestMeal: Pick<MealRecord, 'id' | 'type' | 'recordedAt'> | null;
  latestWorkout: Pick<WorkoutRecord, 'id' | 'type' | 'startedAt'> | null;
  latestPhoto: Pick<ProgressPhoto, 'id' | 'recordedAt'> | null;
  bodyChanges: { weight: number | null };
  recentTrend: {
    days: 7;
    from: string;
    to: string;
    weightChange: number | null;
    bodyRecordedDays: number;
    mealRecordedDays: number;
    workoutCount: number;
    workoutMinutes: number;
  };
  today: {
    bodyRecord: Pick<BodyRecord, 'id' | 'weight' | 'recordedAt'> | null;
    mealCount: number;
    calories: number | null;
    meals: Record<MealType, boolean>;
    mealPreview: Array<{
      id: string;
      type: MealType;
      recordedAt: string;
      foodNames: string[];
      totalCalories: number | null;
    }>;
    workoutCount: number;
    workoutMinutes: number;
    workoutPreview: Array<
      Pick<WorkoutRecord, 'id' | 'name' | 'type' | 'startedAt' | 'durationMinutes'>
    >;
    photoCount: number;
    photoPreview: Array<Pick<ProgressPhoto, 'id' | 'type' | 'imageUrl' | 'recordedAt'>>;
  };
}

export interface ResetPasswordVerification {
  resetToken: string;
  expiresInSeconds: number;
}
