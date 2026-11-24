import type { ComponentType } from 'react';

import type { Course } from './contracts';
import { Card, Copy } from './ui';

export interface CourseMapProps {
  origin: { name: string; lat: number; lng: number };
  course: Course;
}

const CourseMap: ComponentType<CourseMapProps> = () => (
  <Card><Copy>지도는 Android 앱에서 확인할 수 있어요.</Copy></Card>
);

export default CourseMap;
