import { startPoints, type RecommendRequest } from './contracts';

export function seoulNow(now = new Date()) {
  const iso = new Date(now.getTime() + 9 * 60 * 60 * 1000).toISOString();
  return { date: iso.slice(0, 10), time: iso.slice(11, 16) };
}

export function nextDate(date: string) {
  return new Date(Date.parse(date) + 86400000).toISOString().slice(0, 10);
}

export function minutes(time: string) {
  const [hour, minute] = time.split(':').map(Number);
  return hour * 60 + minute;
}

export function isClockTime(value: unknown): value is string {
  return typeof value === 'string' && /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(value);
}

export function timeAt(total: number) {
  return [Math.floor(total / 60), total % 60].map(value => String(value).padStart(2, '0')).join(':');
}

export const timeStep = 5;
export const lastEndTime = 24 * 60 - timeStep;
export const lastStartTime = lastEndTime - timeStep;

export function nextTimeSlot(total: number) {
  return Math.ceil(total / timeStep) * timeStep;
}

export function alarmTime(time: string) {
  const hour = Math.floor(minutes(time) / 60);
  return (hour < 12 ? '오전 ' : '오후 ') + (hour % 12 || 12) + ':' + time.slice(3);
}

export function moveStart(request: RecommendRequest, start: number) {
  const previousEnd = Math.min(lastEndTime, nextTimeSlot(minutes(request.endTime)));
  const duration = Math.max(timeStep, nextTimeSlot(previousEnd - minutes(request.startTime)));
  return {
    startTime: timeAt(start),
    endTime: timeAt(start < previousEnd ? previousEnd : Math.min(lastEndTime, start + duration)),
  };
}

type SurveyField = 'startPointId' | 'date' | 'startTime' | 'endTime' | 'transport' | 'themes';
export type SurveyErrors = Partial<Record<SurveyField, string>>;

export function validateFields(request: RecommendRequest, now = seoulNow()): SurveyErrors {
  const errors: SurveyErrors = {};
  if (!startPoints.some(point => point.id === request.startPointId)) errors.startPointId = '출발지를 선택해 주세요.';
  const date = new Date(request.date + 'T00:00:00Z');
  if (!/^\d{4}-\d{2}-\d{2}$/.test(request.date) || !Number.isFinite(date.getTime()) ||
      date.toISOString().slice(0, 10) !== request.date) errors.date = '방문 날짜를 선택해 주세요.';
  else if (request.date < now.date) errors.date = '오늘 또는 이후 날짜를 선택해 주세요.';
  if (!isClockTime(request.startTime)) errors.startTime = '출발 시각을 선택해 주세요.';
  else if (request.date === now.date && request.startTime < now.time) {
    errors.startTime = '이미 지난 시각에는 출발할 수 없어요.';
  }
  if (!isClockTime(request.endTime)) errors.endTime = '종료 시각을 선택해 주세요.';
  else if (isClockTime(request.startTime) && request.endTime <= request.startTime) {
    errors.endTime = '종료 시각은 출발 시각보다 늦어야 해요.';
  }
  if (request.transport !== 'walk' && request.transport !== 'transit') errors.transport = '이동수단을 선택해 주세요.';
  if (!request.themes.length) errors.themes = '테마를 1개 이상 선택해 주세요.';
  else if (request.themes.length > 3) errors.themes = '테마는 최대 3개까지 선택할 수 있어요.';
  return errors;
}

export function validate(request: RecommendRequest, now = seoulNow()) {
  return Object.values(validateFields(request, now))[0] ?? null;
}
