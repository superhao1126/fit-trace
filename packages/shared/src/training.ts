import { gymExercises } from './training-gym-exercises.js';
import { additionalExercises } from './training-exercises.js';

export interface Exercise {
  id: string;
  name: string;
  bodyPart: string;
  equipment: string;
  difficulty: string;
  unit: 'reps' | 'seconds';
  steps: string[];
  tips: string;
  sourceUrl: string;
}
export interface TrainingSet {
  reps: number;
  weightKg: number;
  completed: boolean;
}
export interface TrainingExercise {
  exerciseId: string;
  name: string;
  unit: 'reps' | 'seconds';
  restSeconds: number;
  sets: TrainingSet[];
}
export interface TrainingTemplate {
  id: string;
  name: string;
  durationMinutes: number;
  exercises: TrainingExercise[];
  preset?: boolean;
  location?: 'HOME' | 'GYM';
}
export interface TrainingPlanDay {
  weekday: number;
  templateId: string | null;
}
export const exerciseLibrary: Exercise[] = [
  {
    id: 'squat',
    name: '徒手深蹲',
    bodyPart: '腿部',
    equipment: '徒手',
    difficulty: '入门',
    unit: 'reps',
    steps: [
      '双脚略宽于髋部站立，脚尖稍向外。',
      '收紧躯干，髋部向后并屈膝下蹲。',
      '在能控制的幅度内下蹲，保持脚掌着地，再平稳站起。',
    ],
    tips: '膝盖随脚尖方向移动，保持自然呼吸。',
    sourceUrl:
      'https://www.acefitness.org/resources/everyone/exercise-library/135/bodyweight-squat/',
  },
  {
    id: 'bridge',
    name: '臀桥',
    bodyPart: '臀部',
    equipment: '徒手',
    difficulty: '入门',
    unit: 'reps',
    steps: ['仰卧屈膝，双脚平放地面。', '收紧腹部，抬起髋部。', '短暂停留，再缓慢回到地面。'],
    tips: '抬髋时避免用腰部过度后仰代偿。',
    sourceUrl: 'https://www.acefitness.org/resources/everyone/exercise-library/49/glute-bridge/',
  },
  {
    id: 'knee-pushup',
    name: '跪姿俯卧撑',
    bodyPart: '胸部',
    equipment: '徒手',
    difficulty: '入门',
    unit: 'reps',
    steps: ['双手和膝盖支撑地面，保持躯干稳定。', '屈肘缓慢降低身体。', '推地回到起始位置。'],
    tips: '保持肩部稳定，避免塌腰。',
    sourceUrl:
      'https://www.acefitness.org/resources/everyone/exercise-library/13/bent-knee-push-up/',
  },
  {
    id: 'row',
    name: '单臂哑铃划船',
    bodyPart: '背部',
    equipment: '哑铃',
    difficulty: '入门',
    unit: 'reps',
    steps: [
      '一侧手和膝支撑在稳定平面，另一手握哑铃。',
      '保持躯干稳定，将哑铃拉向身体侧面。',
      '缓慢放下，每侧分别完成。',
    ],
    tips: '避免转动躯干或耸肩；重量按单只哑铃填写。',
    sourceUrl: 'https://www.acefitness.org/resources/everyone/exercise-library/126/single-arm-row/',
  },
  {
    id: 'plank',
    name: '平板支撑',
    bodyPart: '核心',
    equipment: '徒手',
    difficulty: '进阶',
    unit: 'seconds',
    steps: ['前臂撑地，肘部位于肩下。', '抬起身体并保持躯干稳定。', '持续自然呼吸，再缓慢放下。'],
    tips: '避免塌腰或过度抬高臀部；按秒记录每组。',
    sourceUrl: 'https://www.acefitness.org/resources/everyone/exercise-library/32/front-plank/',
  },
  ...additionalExercises,
  ...gymExercises,
];
export function makeTrainingExercise(exercise: Exercise, sets = 3, reps = 10): TrainingExercise {
  return {
    exerciseId: exercise.id,
    name: exercise.name,
    unit: exercise.unit,
    restSeconds: 60,
    sets: Array.from({ length: sets }, () => ({
      reps: exercise.unit === 'seconds' ? 20 : reps,
      weightKg: 0,
      completed: false,
    })),
  };
}
function preset(
  id: string,
  name: string,
  minutes: number,
  exerciseIds: string[],
  location: 'HOME' | 'GYM' = 'HOME',
): TrainingTemplate {
  return {
    id,
    name,
    durationMinutes: minutes,
    preset: true,
    location,
    exercises: exerciseIds.map((id) =>
      makeTrainingExercise(exerciseLibrary.find((e) => e.id === id)!),
    ),
  };
}
/** 可自行调整的记录模板，不代表个体化训练处方。 */
export const trainingPresets: TrainingTemplate[] = [
  preset('preset-full', '全身基础', 30, ['squat', 'knee-pushup', 'row', 'bridge']),
  preset('preset-upper', '上肢基础', 25, ['knee-pushup', 'row', 'plank']),
  preset('preset-lower', '下肢基础', 25, ['squat', 'bridge', 'plank']),
  preset('preset-home-bodyweight', '居家全身 · 徒手', 25, [
    'squat',
    'knee-pushup',
    'bridge',
    'plank',
  ]),
  preset('preset-home-dumbbell', '居家全身 · 哑铃', 35, [
    'goblet-squat',
    'row',
    'front-raise',
    'bridge',
    'plank',
  ]),
  preset('preset-home-core', '居家核心', 20, ['bird-dog', 'crunch', 'plank']),
  preset(
    'preset-gym-full',
    '健身房全身',
    45,
    ['leg-press', 'chest-press-machine', 'lat-pulldown', 'machine-row', 'plank'],
    'GYM',
  ),
  preset(
    'preset-gym-push',
    '健身房上肢推',
    40,
    ['dumbbell-press', 'incline-press', 'front-raise', 'knee-pushup'],
    'GYM',
  ),
  preset(
    'preset-gym-pull',
    '健身房上肢拉',
    40,
    ['lat-pulldown', 'machine-row', 'row', 'hammer-curl'],
    'GYM',
  ),
  preset(
    'preset-gym-lower',
    '健身房下肢',
    40,
    ['leg-press', 'goblet-squat', 'leg-curl', 'calf-raise', 'bridge'],
    'GYM',
  ),
];

export interface TrainingPlanState {
  configured: boolean;
  days: TrainingPlanDay[];
}
export interface PreviousExercise {
  exerciseId: string;
  unit: 'reps' | 'seconds';
  startedAt: string;
  sets: TrainingSet[];
}
