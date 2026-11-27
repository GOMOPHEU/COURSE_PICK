import { themes, type Course, type Place, type RecommendRequest, type RecommendResponse } from './contracts';
import { mockRecommend } from './mock';
import { isClockTime, minutes, timeAt } from './schedule';

const api = {
  url: (process.env.EXPO_PUBLIC_API_URL ?? 'http://10.0.2.2:8080').replace(/\/+$/, ''),
  useMock: process.env.EXPO_PUBLIC_USE_MOCK !== 'false',
};

export const isMockMode = api.useMock;

export class RecommendInputError extends Error {}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function isInteger(value: unknown, minimum: number): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= minimum;
}

function isCoordinate(value: unknown, limit: number): value is number {
  return typeof value === 'number' && Number.isFinite(value) && Math.abs(value) <= limit;
}

function isPlace(value: unknown): value is Place {
  return isRecord(value) && isInteger(value.order, 1) && typeof value.kakaoId === 'string' &&
    typeof value.name === 'string' && typeof value.theme === 'string' && Object.hasOwn(themes, value.theme) &&
    isCoordinate(value.lat, 90) && isCoordinate(value.lng, 180) && isClockTime(value.arriveAt) &&
    isInteger(value.stayMinutes, 0) &&
    (value.openStatus === 'confirmed' || value.openStatus === 'official' || value.openStatus === 'unknown');
}

function isLeg(value: unknown): value is Course['legs'][number] {
  return isRecord(value) && (value.from === 'start' || isInteger(value.from, 1)) && isInteger(value.to, 1) &&
    (value.mode === 'walk' || value.mode === 'transit') && isInteger(value.minutes, 0);
}

function isCourse(value: unknown): value is Course {
  return isRecord(value) && typeof value.courseId === 'string' && value.courseId.length > 0 &&
    (value.source === 'ai' || value.source === 'basic') && typeof value.reason === 'string' &&
    Array.isArray(value.places) && value.places.length > 0 && value.places.every(isPlace) &&
    Array.isArray(value.legs) && value.legs.every(isLeg) && isInteger(value.totalMinutes, 0) &&
    isClockTime(value.endAt) && (value.fit === 'relaxed' || value.fit === 'tight');
}

async function requestJson(path: string, signal: AbortSignal, init?: RequestInit): Promise<unknown> {
  const response = await fetch(api.url + path, { ...init, signal });
  const body: unknown = await response.json().catch(() => null);
  if (!response.ok || !body) {
    const error = isRecord(body) ? body.error : undefined;
    const message = typeof error === 'string' ? error : isRecord(error) ? error.message : undefined;
    if (response.status === 400 && path === '/recommend') {
      throw new RecommendInputError(typeof message === 'string' ? message : '출발·종료 시각과 설문 조건을 확인해 주세요.');
    }
    if (response.status === 404 && path.startsWith('/places/')) {
      throw new Error('서버에서 이 장소의 상세 정보를 제공하지 않아요.');
    }
    throw new Error(typeof message === 'string' ? message : '서버 응답을 확인할 수 없어요 (' + response.status + ').');
  }
  return body;
}

export async function recommend(input: RecommendRequest, signal: AbortSignal): Promise<RecommendResponse> {
  if (api.useMock) return mockRecommend(input);
  const body = await requestJson('/recommend', signal, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(input),
  });
  if (isRecord(body) && body.state === 'NO_COURSE') return { state: 'NO_COURSE', courses: [] };
  if (isRecord(body) && body.state === 'ROUTE_FAIL') {
    throw new Error('이동 경로를 확인하지 못했어요. 다시 시도하거나 조건을 바꿔 주세요.');
  }
  if (!isRecord(body) || body.state !== 'OK' || !Array.isArray(body.courses) ||
      !body.courses.length || !body.courses.every(isCourse)) {
    throw new Error('추천 결과 형식이 올바르지 않아요.');
  }
  const courses = body.courses.map(course => ({
    ...course,
    notes: Array.isArray(course.notes) ? course.notes.filter(note => typeof note === 'string' && note.trim()) : [],
  }));
  return { state: 'OK', courses };
}

export async function getPlaceDetails(place: Pick<Place, 'kakaoId' | 'theme' | 'arriveAt' | 'stayMinutes'>, date: string, signal: AbortSignal) {
  const query = new URLSearchParams({
    theme: place.theme,
    date,
    arrive: place.arriveAt,
    depart: timeAt((minutes(place.arriveAt) + place.stayMinutes) % (24 * 60)),
  });
  const details = await requestJson('/places/' + encodeURIComponent(place.kakaoId) + '?' + query, signal);
  if (!isRecord(details)) throw new Error('장소 상세 응답 형식을 확인해 주세요.');
  const { openCheck } = details;
  return {
    address: typeof details.address === 'string' ? details.address : undefined,
    photoUrl: typeof details.photoUrl === 'string' && details.photoUrl.startsWith('https://') ? details.photoUrl : undefined,
    openingHours: 'openingHours' in details && typeof details.openingHours === 'string' ? details.openingHours : undefined,
    rating: 'rating' in details && typeof details.rating === 'number' ? details.rating : undefined,
    openCheck: openCheck === 'OPEN' || openCheck === 'CLOSED' || openCheck === 'UNKNOWN' ? openCheck : undefined,
  };
}
