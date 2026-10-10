import dayjs from 'dayjs';

export function recordDateTime(date: unknown): string {
  const now = dayjs();
  if (
    typeof date !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}$/.test(date) ||
    dayjs(date).format('YYYY-MM-DD') !== date ||
    date > now.format('YYYY-MM-DD')
  )
    return now.format('YYYY-MM-DD HH:mm:ss');
  return `${date} ${now.format('HH:mm:ss')}`;
}

export function dateRange(date: string, unit: 'day' | 'month' = 'day') {
  return {
    from: dayjs(date).startOf(unit).toISOString(),
    to: dayjs(date).startOf(unit).add(1, unit).toISOString(),
  };
}
