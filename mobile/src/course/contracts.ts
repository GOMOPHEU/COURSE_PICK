export const startPoints = [
  { id: 'anguk-stn', name: '안국역', lat: 37.5765, lng: 126.9854 },
  { id: 'gyeongbokgung-stn', name: '경복궁역', lat: 37.5758, lng: 126.9737 },
] as const;

export const themes = {
  cafe: '카페', food: '맛집', walk: '산책', shopping: '쇼핑', history: '역사', culture: '전시·문화',
} as const;

export const transportLabels = { walk: '도보', transit: '대중교통' } as const;
export type Theme = keyof typeof themes;
export type Transport = keyof typeof transportLabels;

export interface RecommendRequest {
  startPointId: string;
  date: string;
  startTime: string;
  endTime: string;
  transport: Transport;
  themes: Theme[];
  companion?: 'solo' | 'friends' | 'family' | 'couple';
  partySize?: number;
  mobility?: 'minimizeWalking';
  ageGroup?: '10s' | '20s' | '30s' | '40s' | '50plus';
}

export interface Place {
  order: number;
  kakaoId: string;
  name: string;
  theme: Theme;
  lat: number;
  lng: number;
  arriveAt: string;
  stayMinutes: number;
  openStatus: 'confirmed' | 'official' | 'unknown';
}

export interface Course {
  courseId: string;
  source: 'ai' | 'basic';
  places: Place[];
  legs: { from: 'start' | number; to: number; mode: Transport; minutes: number }[];
  totalMinutes: number;
  endAt: string;
  fit: 'relaxed' | 'tight';
  reason: string;
}

export interface RecommendResponse {
  state: 'OK' | 'NO_COURSE';
  courses: Course[];
}
