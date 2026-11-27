import { create } from 'zustand';

import type { RecommendRequest, RecommendResponse } from './contracts';
import { lastEndTime, lastStartTime, minutes, nextDate, seoulNow, timeAt } from './schedule';

function initialRequest(): RecommendRequest {
  const now = seoulNow();
  const currentMinute = minutes(now.time);
  const hasTimeToday = currentMinute <= lastStartTime;
  const initialStart = hasTimeToday ? Math.min(Math.ceil((currentMinute + 1) / 30) * 30, lastStartTime) : 9 * 60;
  return {
    startPointId: 'anguk-stn',
    date: hasTimeToday ? now.date : nextDate(now.date),
    startTime: timeAt(initialStart),
    endTime: timeAt(Math.min(lastEndTime, initialStart + 120)),
    transport: 'walk',
    themes: [],
  };
}

interface CourseState {
  request: RecommendRequest;
  result: RecommendResponse | null;
  reset: () => void;
  update: (change: Partial<RecommendRequest>) => void;
  setResult: (result: RecommendResponse) => void;
}

export const useCourseStore = create<CourseState>()((set) => ({
  request: initialRequest(),
  result: null,
  reset: () => set({ request: initialRequest(), result: null }),
  update: (change) => set(state => ({ request: { ...state.request, ...change }, result: null })),
  setResult: (result) => set({ result }),
}));
