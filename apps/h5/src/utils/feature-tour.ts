export type TourModule = 'home' | 'templates' | 'training' | 'meal';
export type TourStatus = 'skipped' | 'completed';
export interface TourStep {
  target: string;
  title: string;
  description: string;
  placement: 'top' | 'bottom';
}

const modules: TourModule[] = ['home', 'templates', 'training', 'meal'];
const introducedAt = Date.parse('2026-10-15T00:00:00+08:00');
const prefix = 'fit-trace:tour:v1:';
type TourStorage = Pick<Storage, 'getItem' | 'setItem'>;

function key(ownerId: string): string {
  return `${prefix}${encodeURIComponent(ownerId)}`;
}
function read(
  storage: TourStorage,
  ownerId: string,
): Partial<Record<TourModule, TourStatus>> & {
  enabled?: boolean;
} {
  try {
    const value: unknown = JSON.parse(storage.getItem(key(ownerId)) ?? '{}');
    return value && typeof value === 'object' && !Array.isArray(value) ? value : {};
  } catch {
    return {};
  }
}

export function shouldShowTour(
  storage: TourStorage,
  ownerId: string,
  createdAt: string | undefined,
  module: TourModule,
): boolean {
  if (!ownerId) return false;
  const state = read(storage, ownerId);
  const enabled = state.enabled === true || Date.parse(createdAt ?? '') >= introducedAt;
  return enabled && state[module] !== 'skipped' && state[module] !== 'completed';
}

export function finishTour(
  storage: TourStorage,
  ownerId: string,
  module: TourModule,
  status: TourStatus,
): void {
  if (!ownerId || !modules.includes(module)) return;
  try {
    storage.setItem(key(ownerId), JSON.stringify({ ...read(storage, ownerId), [module]: status }));
  } catch {
    // 本机存储不可用不能影响记录或关闭引导。
  }
}

export function resetTours(storage: TourStorage, ownerId: string): void {
  if (!ownerId) return;
  try {
    storage.setItem(key(ownerId), JSON.stringify({ enabled: true }));
  } catch {
    // 存储不可用不能阻断设置页导航。
  }
}

export function tourModuleForPath(path: string): TourModule | null {
  if (path === '/dashboard') return 'home';
  if (path === '/training/templates') return 'templates';
  if (path === '/training/session') return 'training';
  if (path === '/meals/create') return 'meal';
  return null;
}

export const tourSteps: Record<TourModule, TourStep[]> = {
  home: [
    {
      target: 'quick-records',
      title: '从这里开始记录',
      description: '体重、饮食、训练和照片，都可以从这里添加。',
      placement: 'top',
    },
    {
      target: 'today-training',
      title: '今天练什么，看这里',
      description: '有计划时开始今日训练；中途离开后，也从这里继续。',
      placement: 'bottom',
    },
    {
      target: 'diary-date',
      title: '想补记，先选日期',
      description: '点选某一天，再添加记录，就会记到那一天。',
      placement: 'bottom',
    },
    {
      target: 'navigation',
      title: '记录和变化，都在这里',
      description: '趋势看变化，档案查记录；“我的”里管理资料和设置。',
      placement: 'top',
    },
  ],
  templates: [
    {
      target: 'training-template',
      title: '先选一个现成方案',
      description: '这里列出了动作和组数，可以直接开始，不用先创建模板。',
      placement: 'bottom',
    },
    {
      target: 'start-training',
      title: '准备好后，从这里开始',
      description: '点击才会开始真实训练。看完引导，可以先继续浏览。',
      placement: 'top',
    },
  ],
  training: [
    {
      target: 'training-set',
      title: '填写这一组的实际数据',
      description: '次数可以修改，重量按实际填写。有历史成绩时会自动带入。',
      placement: 'bottom',
    },
    {
      target: 'complete-set',
      title: '练完一组，再点“待做”',
      description: '按钮变成“完成”才算练过；开启自动休息时会开始倒计时。',
      placement: 'bottom',
    },
    {
      target: 'training-summary',
      title: '中途停一下，用暂停',
      description: '暂停期间不计训练时长。离开页面后，可从首页继续草稿。',
      placement: 'bottom',
    },
    {
      target: 'save-training',
      title: '练完后，在这里保存',
      description: '只保存标记完成的组。点击后还会让你确认一次。',
      placement: 'top',
    },
  ],
  meal: [
    {
      target: 'meal-description',
      title: '直接说说这一餐吃了什么',
      description: '可以让 AI 分析，也可以直接记录原文，不必逐项填写。',
      placement: 'bottom',
    },
    {
      target: 'meal-more',
      title: '其他记录方式在这里',
      description: '食品库、最近吃过和手动填写，都可以从这里选择。',
      placement: 'bottom',
    },
    {
      target: 'save-meal',
      title: '确认内容后，再保存',
      description: 'AI 分析只是建议。核对食物和份量，保存后才会记入日记。',
      placement: 'top',
    },
  ],
};

export interface TourRect {
  left: number;
  top: number;
  width: number;
  height: number;
}

/** 将气泡约束在当前可视区内，键盘、窄屏和上下空间不足时均可使用。 */
export function placeTourCard(
  target: TourRect,
  card: { width: number; height: number },
  viewport: TourRect,
  preferred: 'top' | 'bottom',
) {
  const margin = 16;
  const gap = 20;
  const minX = viewport.left + margin;
  const maxX = Math.max(minX, viewport.left + viewport.width - card.width - margin);
  const minY = viewport.top + margin;
  const maxY = Math.max(minY, viewport.top + viewport.height - card.height - margin);
  const below = target.top + target.height + gap;
  const above = target.top - card.height - gap;
  let placement = preferred;
  if (placement === 'bottom' && below > maxY && above >= minY) placement = 'top';
  if (placement === 'top' && above < minY && below <= maxY) placement = 'bottom';
  const left = Math.max(minX, Math.min(maxX, target.left + target.width / 2 - card.width / 2));
  const top = Math.max(minY, Math.min(maxY, placement === 'bottom' ? below : above));
  return {
    left,
    top,
    placement,
    arrow: Math.max(22, Math.min(card.width - 22, target.left + target.width / 2 - left)),
  };
}
