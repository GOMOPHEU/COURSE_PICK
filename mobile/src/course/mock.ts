import { startPoints, themes, type Course, type Place, type RecommendRequest, type RecommendResponse } from './contracts';
import { minutes, timeAt } from './schedule';

type Stop = Omit<Place, 'order' | 'arriveAt' | 'openStatus'>;
const stops: Stop[] = [
  { kakaoId: '8130598', name: '경복궁', theme: 'history', lat: 37.5796, lng: 126.977, stayMinutes: 45 },
  { kakaoId: 'demo-cafe', name: '삼청동 카페', theme: 'cafe', lat: 37.581, lng: 126.9838, stayMinutes: 30 },
  { kakaoId: 'demo-walk', name: '북촌 한옥길', theme: 'walk', lat: 37.5826, lng: 126.9849, stayMinutes: 25 },
  { kakaoId: 'demo-food', name: '인사동 식당', theme: 'food', lat: 37.573, lng: 126.9864, stayMinutes: 45 },
  { kakaoId: 'demo-shopping', name: '인사동 쌈지길', theme: 'shopping', lat: 37.5743, lng: 126.9846, stayMinutes: 30 },
  { kakaoId: 'demo-museum', name: '국립현대미술관 서울', theme: 'culture', lat: 37.5795, lng: 126.9801, stayMinutes: 45 },
];

function travelMinutes(from: { lat: number; lng: number }, to: Stop, mode: RecommendRequest['transport']) {
  const distance = Math.hypot((to.lat - from.lat) * 111000, (to.lng - from.lng) * 88000);
  return mode === 'walk' ? Math.max(1, Math.ceil(distance * 1.3 / 67)) : Math.ceil(distance * 1.3 / 150) + 5;
}

export function mockReplacementCourses(request: RecommendRequest, course: Course, order: number) {
  const origin = startPoints.find(point => point.id === request.startPointId);
  if (!origin || !course.places.some(place => place.order === order)) return [];

  const start = minutes(request.startTime);
  const limit = minutes(request.endTime) - start;
  return stops.filter(stop => course.places.every(place => place.kakaoId !== stop.kakaoId)).flatMap(stop => {
    let elapsed = 0;
    let previous: { lat: number; lng: number } = origin;
    const legs: Course['legs'] = [];
    const places = course.places.map((place, index) => {
      const current = place.order === order ? { ...place, ...stop, openStatus: 'unknown' as const } : place;
      const travel = travelMinutes(previous, current, request.transport);
      legs.push({ from: index ? course.places[index - 1].order : 'start', to: current.order, mode: request.transport, minutes: travel });
      previous = current;
      elapsed += travel;
      const arriveAt = timeAt(start + elapsed);
      elapsed += current.stayMinutes;
      return { ...current, arriveAt };
    });
    if (elapsed > limit) return [];

    const updated: Course = {
      ...course, places, legs, totalMinutes: elapsed, endAt: timeAt(start + elapsed),
      fit: elapsed <= limit * 0.9 ? 'relaxed' : 'tight',
      reason: stop.name + '으로 한 곳을 바꾸고 기존 방문 순서대로 시간을 다시 계산했어요.',
    };
    return [{ place: stop, course: updated }];
  }).sort((a, b) => a.course.totalMinutes - b.course.totalMinutes);
}

export function mockRecommend(request: RecommendRequest): RecommendResponse {
  const origin = startPoints.find(point => point.id === request.startPointId) ?? startPoints[0];
  const limit = minutes(request.endTime) - minutes(request.startTime);
  const preferred = stops.filter(stop => request.themes.includes(stop.theme));
  const courses = preferred.slice(0, 3).flatMap((first, index): Course[] => {
    const places: Place[] = [];
    const legs: Course['legs'] = [];
    let elapsed = 0;
    let previous: { lat: number; lng: number } = origin;
    const ordered = [first, ...preferred.filter(stop => stop !== first), ...stops.filter(stop => !preferred.includes(stop))];

    for (const stop of ordered) {
      if (places.length === 3) break;
      const travel = travelMinutes(previous, stop, request.transport);
      if (elapsed + travel + stop.stayMinutes > limit) continue;

      const order = places.length + 1;
      legs.push({ from: order === 1 ? 'start' : order - 1, to: order, mode: request.transport, minutes: travel });
      places.push({ ...stop, order, arriveAt: timeAt(minutes(request.startTime) + elapsed + travel), openStatus: 'unknown' });
      elapsed += travel + stop.stayMinutes;
      previous = stop;
    }

    if (!places.some(place => request.themes.includes(place.theme))) return [];
    return [{
      courseId: 'demo-' + (index + 1),
      source: 'basic',
      places,
      legs,
      totalMinutes: elapsed,
      endAt: timeAt(minutes(request.startTime) + elapsed),
      fit: elapsed <= limit * 0.9 ? 'relaxed' : 'tight',
      reason: request.themes.map(theme => themes[theme]).join(' · ') + ' 테마를 우선해서 구성한 시연용 코스입니다.',
    }];
  });
  const unique = courses.filter((course, index) => {
    const signature = (item: Course) => item.places.map(place => place.kakaoId).sort().join(',');
    return courses.findIndex(item => signature(item) === signature(course)) === index;
  });
  return { state: unique.length ? 'OK' : 'NO_COURSE', courses: unique };
}
